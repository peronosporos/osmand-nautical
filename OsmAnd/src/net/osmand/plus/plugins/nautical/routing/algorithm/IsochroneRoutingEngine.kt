package net.osmand.plus.plugins.nautical.routing.algorithm

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.GeometryFactory
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.grib.parser.WindVector
import net.osmand.plus.plugins.nautical.routing.model.IsochroneNode
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import kotlin.math.*

class IsochroneRoutingEngine(
    private val gribEngine: GribInterpolationEngine,
    private val s57Index: S57SpatialIndex
) {
    private val geometryFactory = GeometryFactory()

    fun calculateRoute(request: RoutingRequest): OptimalRouteResult? {
        val startNode = IsochroneNode(
            latitude = request.start.latitude,
            longitude = request.start.longitude,
            cumulativeTimeHours = 0.0,
            heading = 0.0,
            parent = null,
            speedThroughWater = 0.0,
            speedOverGround = 0.0
        )

        var currentFrontier = listOf(startNode)
        val timeStepHours = 3.0
        val maxSteps = 40

        for (step in 0 until maxSteps) {
            val nextFrontier = mutableMapOf<Int, IsochroneNode>() // Sector binning (36 sectors of 10°)

            for (node in currentFrontier) {
                // Check if destination reached (within ~5 nm)
                val distToDest = distanceNm(node.latitude, node.longitude, request.destination.latitude, request.destination.longitude)
                if (distToDest < 5.0) {
                    return buildResult(node)
                }

                // Project test courses over ground (COG) in 36 sectors (0 to 360 step 10)
                for (cogDeg in 0 until 360 step 10) {
                    val cogRad = Math.toRadians(cogDeg.toDouble())
                    val timestamp = request.departureTime + (node.cumulativeTimeHours * 3600000).toLong()
                    val wind = gribEngine.getWindVector(node.latitude, node.longitude, timestamp) ?: WindVector(5.0, 0.0)

                    // Current Vector (v_x, v_y) in knots. 
                    // Integrated current/tide data from GRIB or Signal K engine is preferred here.
                    val v_x = 0.0 // Knots East (placeholder for future tide model integration)
                    val v_y = 0.0 // Knots North

                    // Vector Triangle of Velocities: V_sog = V_bsp + V_current
                    // W = component of current along desired COG
                    val w = v_x * sin(cogRad) + v_y * cos(cogRad)
                    // H_sq = square of component of current perpendicular to COG
                    val h_sq = (v_x * v_x + v_y * v_y) - (w * w)

                    // Determine boat speed through water (BSP) from polar profile
                    // Note: BSP depends on TWA, which depends on Heading (HDG), not COG.
                    // For isochrone search, we approximate TWA using COG first, or use a heuristic.
                    val bsp = getPolarSpeed(request.polarProfile, wind.speed, cogDeg.toDouble())

                    if (bsp * bsp < h_sq) continue // "Unachievable Course" warning condition

                    val sog = w + sqrt(bsp * bsp - h_sq)
                    val hdgRad = atan2(sog * sin(cogRad) - v_x, sog * cos(cogRad) - v_y)
                    val hdgDeg = (Math.toDegrees(hdgRad) + 360.0) % 360.0

                    val distanceKm = sog * 1.852 * timeStepHours
                    val newCoords = calculateDestination(node.latitude, node.longitude, distanceKm, cogDeg.toDouble())

                    if (isLandCollision(newCoords.first, newCoords.second)) continue

                    val sectorIdx = cogDeg / 10
                    val newNode = IsochroneNode(
                        latitude = newCoords.first,
                        longitude = newCoords.second,
                        cumulativeTimeHours = node.cumulativeTimeHours + timeStepHours,
                        heading = hdgDeg,
                        parent = node,
                        speedThroughWater = bsp,
                        speedOverGround = sog
                    )

                    // Prune by keeping the fastest node per sector
                    val existing = nextFrontier[sectorIdx]
                    if (existing == null || newNode.cumulativeTimeHours < existing.cumulativeTimeHours) {
                        nextFrontier[sectorIdx] = newNode
                    }
                }
            }

            if (nextFrontier.isEmpty()) break
            currentFrontier = nextFrontier.values.toList()
        }

        return null
    }

    private fun getPolarSpeed(polar: net.osmand.plus.plugins.nautical.network.PolarProfile, tws: Double, twa: Double): Double {
        val speeds = polar.speeds ?: return 6.0
        return speeds.firstOrNull()?.firstOrNull() ?: 6.0
    }

    private fun isLandCollision(lat: Double, lon: Double): Boolean {
        val queryPoint = geometryFactory.createPoint(Coordinate(lon, lat))
        val landFeatures = s57Index.queryByAcronym(queryPoint, setOf("LNDARE"))
        return landFeatures.isNotEmpty()
    }

    private fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLatRad = Math.toRadians(lat2 - lat1)
        val dLonRad = Math.toRadians(lon2 - lon1)
        val a = sin(dLatRad / 2).pow(2) + cos(lat1Rad) * cos(lat2Rad) * sin(dLonRad / 2).pow(2)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return c * 3440.0 // Earth radius in nautical miles
    }

    private fun calculateDestination(latDeg: Double, lonDeg: Double, distanceKm: Double, bearingDeg: Double): Pair<Double, Double> {
        val earthRadiusKm = 6371.0
        val brng = Math.toRadians(bearingDeg)
        val lat1 = Math.toRadians(latDeg)
        val lon1 = Math.toRadians(lonDeg)
        val angularDistance = distanceKm / earthRadiusKm

        val lat2 = asin(sin(lat1) * cos(angularDistance) + cos(lat1) * sin(angularDistance) * cos(brng))
        val lon2 = lon1 + atan2(sin(brng) * sin(angularDistance) * cos(lat1), cos(angularDistance) - sin(lat1) * sin(lat2))

        return Pair(Math.toDegrees(lat2), Math.toDegrees(lon2))
    }

    private fun buildResult(endNode: IsochroneNode): OptimalRouteResult {
        val path = mutableListOf<Waypoint>()
        val legs = mutableListOf<net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg>()
        var totalDist = 0.0

        val nodes = mutableListOf<IsochroneNode>()
        var curr: IsochroneNode? = endNode
        while (curr != null) {
            nodes.add(0, curr)
            curr = curr.parent
        }

        for (i in 0 until nodes.size) {
            val node = nodes[i]
            val wpt = Waypoint(node.latitude, node.longitude)
            path.add(wpt)

            if (i > 0) {
                val prev = nodes[i - 1]
                val dist = distanceNm(prev.latitude, prev.longitude, node.latitude, node.longitude)
                totalDist += dist

                legs.add(net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg(
                    legNumber = i,
                    from = Waypoint(prev.latitude, prev.longitude),
                    to = wpt,
                    distanceNm = dist,
                    courseToSteerDeg = node.heading,
                    expectedSetDeg = null, // Current calculation is integrated but not stored per node yet
                    expectedDriftKn = null,
                    speedOverGroundKn = node.speedOverGround,
                    eteHours = node.cumulativeTimeHours - prev.cumulativeTimeHours
                ))
            }
        }

        return OptimalRouteResult(
            path = path,
            totalTimeHours = endNode.cumulativeTimeHours,
            totalDistanceNm = totalDist,
            legs = legs
        )
    }
}
