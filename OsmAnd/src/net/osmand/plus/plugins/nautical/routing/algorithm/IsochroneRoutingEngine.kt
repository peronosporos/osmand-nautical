package net.osmand.plus.plugins.nautical.routing.algorithm

import com.vividsolutions.jts.geom.Coordinate
import com.vividsolutions.jts.geom.Envelope
import com.vividsolutions.jts.geom.GeometryFactory
import kotlinx.coroutines.*
import net.osmand.plus.plugins.nautical.engine.CapabilityManager
import net.osmand.plus.plugins.nautical.grib.parser.GribInterpolationEngine
import net.osmand.plus.plugins.nautical.grib.parser.WindVector
import net.osmand.plus.plugins.nautical.routing.model.IsochroneNode
import net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.shared.extensions.toRadians
import kotlin.math.*

class IsochroneRoutingEngine(
    private val gribEngine: GribInterpolationEngine,
    private val s57Index: S57SpatialIndex,
    private val safetyChecker: net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker,
    private val capabilityManager: CapabilityManager? = null,
    private val vesselDraft: Double = 2.0
) {
    private val geometryFactory = GeometryFactory()
    private val reusableCoordinate = Coordinate()
    private val collisionCache = mutableMapOf<Pair<Int, Int>, Boolean>()

    suspend fun calculateRoute(request: RoutingRequest, liveSet: Double? = null, liveDrift: Double? = null): OptimalRouteResult? = withContext(Dispatchers.Default) {
        val caps = capabilityManager?.capabilities?.value ?: CapabilityManager.ServerCapabilityMap()
        if (caps.hasWingaRouting || caps.hasRouteIq) {
            val serverResult = tryCalculateOnServer(request, caps)
            if (serverResult != null) return@withContext serverResult
        }

        val gribCurrent = gribEngine.getCurrentVector(request.start.latitude, request.start.longitude, request.departureTime)
        var confidence = 1.0
        if (gribCurrent != null && liveSet != null && liveDrift != null) {
            val gribDrift = gribCurrent.speed
            val gribSet = Math.toRadians((atan2(gribCurrent.u, gribCurrent.v) * 180.0 / PI + 360.0) % 360.0)

            val driftDiff: Double = abs(gribDrift - liveDrift)
            var setDiff = abs(gribSet - liveSet)
            if (setDiff > PI) setDiff = 2 * PI - setDiff

            if (driftDiff > 0.5) confidence -= 0.2
            if (setDiff > 30.0.toRadians()) confidence -= 0.3
        }
        val finalConfidence = confidence.coerceIn(0.1, 1.0)

        // 1. Spatial Bounding Box Pre-filter
        val workingEnvelope = Envelope(request.start.longitude, request.destination.longitude, request.start.latitude, request.destination.latitude)
        workingEnvelope.expandBy(0.5) // 0.5 degree buffer

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
        val timeStepHours = 1.0
        val maxSteps = 120
        var step = 0

        while (step < maxSteps) {
            val candidateNodes = mutableListOf<IsochroneNode>()

            for (node in currentFrontier) {
                val distToDest = distanceNm(node.latitude, node.longitude, request.destination.latitude, request.destination.longitude)
                if (distToDest < 5.0) {
                    return@withContext buildResult(node, finalConfidence)
                }

                for (cogDeg in 0 until 360 step 5) {
                    val cogRad = Math.toRadians(cogDeg.toDouble())
                    val timestamp = request.departureTime + (node.cumulativeTimeHours * 3600000).toLong()
                    val wind = gribEngine.getWindVector(node.latitude, node.longitude, timestamp) ?: WindVector(5.0, 0.0)

                    val current = gribEngine.getCurrentVector(node.latitude, node.longitude, timestamp)
                    val vx = current?.u ?: 0.0
                    val vy = current?.v ?: 0.0

                    val w = vx * sin(cogRad) + vy * cos(cogRad)
                    val hSq = ((vx * vx) + (vy * vy)) - (w * w)

                    val bsp = getPolarSpeed(request.polarProfile, wind.speed, cogDeg.toDouble())
                    if ((bsp * bsp) < hSq) continue

                    val sog = w + sqrt((bsp * bsp) - hSq)
                    val hdgRad = atan2(sog * sin(cogRad) - vx, sog * cos(cogRad) - vy)
                    val hdgDeg = (Math.toDegrees(hdgRad) + 360.0) % 360.0

                    val distanceKm = sog * 1.852 * timeStepHours
                    val newCoords = calculateDestination(node.latitude, node.longitude, distanceKm, cogDeg.toDouble())

                    // Quick spatial reject
                    if (!workingEnvelope.contains(newCoords.second, newCoords.first)) continue

                    if (isLandCollision(newCoords.first, newCoords.second)) continue

                    candidateNodes.add(IsochroneNode(
                        latitude = newCoords.first,
                        longitude = newCoords.second,
                        cumulativeTimeHours = node.cumulativeTimeHours + timeStepHours,
                        heading = hdgDeg,
                        parent = node,
                        speedThroughWater = bsp,
                        speedOverGround = sog
                    ))
                }
            }

            // 2. Offload Safety Checks to Background with Chunking
            val validNodes = candidateNodes.asSequence().chunked(100).flatMap { batch ->
                batch.filter { newNode ->
                    val parent = newNode.parent ?: return@filter true
                    !safetyChecker.checkCorridorIntersection(
                        parent.latitude, parent.longitude,
                        newNode.latitude, newNode.longitude
                    )
                }
            }.toList()

            val nextFrontier = mutableMapOf<Int, IsochroneNode>()
            for (newNode in validNodes) {
                val parent = newNode.parent ?: continue
                val sectorIdx = (atan2(newNode.latitude - parent.latitude, newNode.longitude - parent.longitude) * 180 / PI).toInt() / 5
                val existing = nextFrontier[sectorIdx]
                if (existing == null || newNode.cumulativeTimeHours < existing.cumulativeTimeHours) {
                    nextFrontier[sectorIdx] = newNode
                }
            }

            if (nextFrontier.isEmpty()) break
            currentFrontier = nextFrontier.values.toList()
            step++
        }

        null
    }

    private suspend fun tryCalculateOnServer(request: RoutingRequest, caps: CapabilityManager.ServerCapabilityMap): OptimalRouteResult? {
        val plugin = net.osmand.plus.plugins.nautical.NauticalPlugin.getInstance() ?: return null
        val client = plugin.okHttpClient ?: return null
        val ip = plugin.getSettings().NAUTICAL_SERVER_IP.get()
        val port = plugin.getSettings().NAUTICAL_SERVER_PORT.get()
        val protocol = if (plugin.getSettings().NAUTICAL_USE_SECURE_CONNECTION.get()) "https" else "http"
        val rest = net.osmand.plus.plugins.nautical.network.SignalKRestService.create("$protocol://$ip:$port", client)

        return try {
            val pluginId = if (caps.hasWingaRouting) "winga-weather-routing" else "signalk-routeiq"
            val body = mapOf(
                "start" to mapOf("lat" to request.start.latitude, "lon" to request.start.longitude),
                "destination" to mapOf("lat" to request.destination.latitude, "lon" to request.destination.longitude),
                "polar" to (request.polarProfile.name ?: "default"),
                "startTime" to request.departureTime
            )

            val response = rest.triggerPluginCalculation(pluginId, body)
            if (response.isSuccessful) {
                // Parse server response to OptimalRouteResult
                // This is a placeholder for actual parsing logic once specific plugin APIs are known.
                // For now, we return null to fallback to local if parsing fails.
                null
            } else null
        } catch (_: Exception) {
            null
        }
    }

    private fun getPolarSpeed(
        polar: net.osmand.plus.plugins.nautical.network.PolarProfile,
        tws: Double,
        twaIn: Double
    ): Double {
        val speeds = polar.speeds ?: return 1.0 // Use 1.0kn as minimal safety fallback instead of hardcoded 6.0
        val twsList = polar.tws ?: return 1.0
        val twaList = polar.twa ?: return 1.0

        val twa = abs(twaIn) % 180.0 // Polars are symmetric

        // 1. Find TWS indices
        val twsIdx = twsList.zipWithNext().indexOfFirst { (a, b) -> tws in a..b }
        val twaIdx = twaList.zipWithNext().indexOfFirst { (a, b) -> twa in a..b }

        if (twsIdx == -1 || twaIdx == -1) {
            // Out of bounds - use nearest or fallback
            if (tws < twsList.first()) return getPolarSpeed(polar, twsList.first(), twa)
            if (tws > twsList.last()) return getPolarSpeed(polar, twsList.last(), twa)
            // Use the absolute minimum from the polar table if available
            return speeds.flatten().minOrNull()?.coerceAtLeast(1.0) ?: 1.0
        }

        val tws0 = twsList[twsIdx]
        val tws1 = twsList[twsIdx + 1]
        val twa0 = twaList[twaIdx]
        val twa1 = twaList[twaIdx + 1]

        val rTws = (tws - tws0) / (tws1 - tws0)
        val rTwa = (twa - twa0) / (twa1 - twa0)

        val v00 = speeds[twsIdx][twaIdx]
        val v10 = speeds[twsIdx + 1][twaIdx]
        val v01 = speeds[twsIdx][twaIdx + 1]
        val v11 = speeds[twsIdx + 1][twaIdx + 1]

        return v00 * (1 - rTws) * (1 - rTwa) +
                v10 * rTws * (1 - rTwa) +
                v01 * (1 - rTws) * rTwa +
                v11 * rTws * rTwa
    }

    private fun isLandCollision(lat: Double, lon: Double): Boolean {
        // Cache quantized coordinates to reduce S57 spatial queries
        val qKey = Pair((lat * 1000).toInt(), (lon * 1000).toInt())
        collisionCache[qKey]?.let { return it }

        reusableCoordinate.x = lon
        reusableCoordinate.y = lat
        val queryPoint = geometryFactory.createPoint(reusableCoordinate)
        
        // 1. Land Check
        val landFeatures = s57Index.queryByAcronym(queryPoint, setOf("LNDARE"))
        val isLand = landFeatures.any { feature ->
            feature.geometries.any { geo ->
                geo.toJtsGeometry(geometryFactory)?.intersects(queryPoint) == true
            }
        }
        if (isLand) {
            collisionCache[qKey] = true
            return true
        }

        // 2. Grounding Risk (Depth Area check)
        val depthFeatures = s57Index.queryByAcronym(queryPoint, setOf("DEPARE"))
        val isShallow = depthFeatures.any { feature ->
            val drval1 = feature.attributes["DRVAL1"]?.toDoubleOrNull() ?: 0.0
            if (drval1 < vesselDraft) {
                feature.geometries.any { geo ->
                    geo.toJtsGeometry(geometryFactory)?.intersects(queryPoint) == true
                }
            } else false
        }

        collisionCache[qKey] = isShallow
        return isShallow
    }

    private fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val dLatRad = Math.toRadians(lat2 - lat1)
        val dLonRad = Math.toRadians(lon2 - lon1)
        val a = sin(dLatRad / 2).pow(2) + (cos(lat1Rad) * cos(lat2Rad) * sin(dLonRad / 2).pow(2))
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return c * 3440.0
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

    private fun buildResult(endNode: IsochroneNode, confidence: Double): OptimalRouteResult {
        val path = mutableListOf<Waypoint>()
        val legs = mutableListOf<net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg>()
        var totalDist = 0.0

        val nodes = mutableListOf<IsochroneNode>()
        var curr: IsochroneNode? = endNode
        while (curr != null) {
            nodes.add(0, curr)
            curr = curr.parent
        }

        for ((i, node) in nodes.withIndex()) {
            val wpt = Waypoint(node.latitude, node.longitude)
            path.add(wpt)

            if (i > 0) {
                val prev = nodes[i - 1]
                val dist = distanceNm(prev.latitude, prev.longitude, node.latitude, node.longitude)
                totalDist += dist

                legs.add(
                    net.osmand.plus.plugins.nautical.routing.model.PassagePlanLeg(
                        legNumber = i,
                        from = Waypoint(prev.latitude, prev.longitude),
                        to = wpt,
                        distanceNm = dist,
                        courseToSteerDeg = node.heading,
                        expectedSetDeg = null,
                        expectedDriftKn = null,
                        speedOverGroundKn = node.speedOverGround,
                        eteHours = node.cumulativeTimeHours - prev.cumulativeTimeHours,
                    ),
                )
            }
        }

        return OptimalRouteResult(
            path = path,
            totalTimeHours = endNode.cumulativeTimeHours,
            totalDistanceNm = totalDist,
            legs = legs,
            confidenceFactor = confidence
        )
    }
}
