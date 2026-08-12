package net.osmand.plus.plugins.nautical.laylines.engine

import kotlin.math.*

/**
 * Utility object for Current-Adjusted Tactical Layline calculations.
 * All internal math is performed in Radians.
 */
object LaylineMathEngine {

    /**
     * Calculates tactically adjusted laylines factoring in tidal current and leeway.
     * @param optimalTwa Optimal True Wind Angle in Radians (positive).
     * @param trueWindDirection True Wind Direction in Radians (0 to 2PI).
     * @param boatSpeed Vessel speed through water (STW) in m/s.
     * @param current Tidal current vector (m/s).
     * @param leewayRadians Leeway angle in Radians (positive).
     * @param magneticVariation Live magnetic variation in Radians (optional, for frame transform).
     */
    fun calculateApparentLaylines(
        boatPosition: LatLon,
        targetWaypoint: LatLon,
        optimalTwa: Double,
        trueWindDirection: Double,
        boatSpeed: Double,
        current: TidalCurrentVector,
        leewayRadians: Double,
        magneticVariation: Double = 0.0,
        isMagneticInput: Boolean = false,
        isInfinite: Boolean = false
    ): LaylineData {
        // Transform to True frame if inputs are Magnetic (TASK-08D)
        val twdTrue = if (isMagneticInput) toTrue(trueWindDirection, magneticVariation) else trueWindDirection
        val currentDirTrue = if (isMagneticInput) toTrue(current.directionRadians, magneticVariation) else current.directionRadians

        // 0. Determine if we are on a downwind leg (TASK-GYBE)
        val bearingToTarget = calculateBearing(boatPosition, targetWaypoint)
        val isDownwind = cos(bearingToTarget - twdTrue) < 0
        
        // For downwind, we use the gybe angle (180 - target_twa)
        val targetTwa = if (isDownwind && optimalTwa < PI / 2) PI - optimalTwa else optimalTwa

        // 1. Calculate headings for Port and Starboard tacks/gybes
        val stbdHeading = normalizeRadians(twdTrue - targetTwa)
        val portHeading = normalizeRadians(twdTrue + targetTwa)

        // 2. Apply leeway to STW headings to get "Course Through Water" (CTW)
        // Leeway always pushes the boat leeward (away from the wind)
        val stbdCtw = normalizeRadians(stbdHeading - leewayRadians)
        val portCtw = normalizeRadians(portHeading + leewayRadians)

        // 3. Convert CTW + BoatSpeed and Current to Vectors
        val stbdStwVector = headingToVector(stbdCtw, boatSpeed)
        val portStwVector = headingToVector(portCtw, boatSpeed)
        val currentVector = headingToVector(currentDirTrue, current.speedMs)

        // 4. Combine with Current to get Course Over Ground (COG) vectors
        val stbdCogVector = stbdStwVector + currentVector
        val portCogVector = portStwVector + currentVector

        // 5. Derive COG headings
        val stbdCogHeading = vectorToHeading(stbdCogVector)
        val portCogHeading = vectorToHeading(portCogVector)

        if (isInfinite) {
             // Project laylines far out from boat position if infinite mode requested
             // Using rhumb line destination for simplicity in UI projection
             val distMeters = 1852.0 * 100.0 // 100 NM
             val pStbd = projectPoint(boatPosition, stbdCogHeading, distMeters)
             val pPort = projectPoint(boatPosition, portCogHeading, distMeters)
             
             return LaylineData(
                 portTackPoint = pPort,
                 starboardTackPoint = pStbd,
                 isFetchable = true, // Infinite lines are always "active"
                 targetWaypoint = targetWaypoint
             )
        }

        // 6. Find intersection points
        // Tacking FROM current Port tack TO Starboard tack to reach the mark.
        val portTackPoint = calculateIntersection(
            boatPosition, portCogHeading,
            targetWaypoint, normalizeRadians(stbdCogHeading + PI)
        )

        // Tacking FROM current Starboard tack TO Port tack to reach the mark.
        val stbdTackPoint = calculateIntersection(
            boatPosition, stbdCogHeading,
            targetWaypoint, normalizeRadians(portCogHeading + PI)
        )

        // 7. Determine fetchability
        val isFetchable = !isWithinArc(bearingToTarget, stbdCogHeading, portCogHeading)

        return LaylineData(
            portTackPoint = portTackPoint,
            starboardTackPoint = stbdTackPoint,
            isFetchable = isFetchable,
            targetWaypoint = targetWaypoint
        )
    }

    fun projectPoint(start: LatLon, bearingRad: Double, distanceMeters: Double): LatLon {
        val R = 6371000.0 // Earth radius
        val lat1 = Math.toRadians(start.latitude)
        val lon1 = Math.toRadians(start.longitude)
        
        val lat2 = asin(sin(lat1) * cos(distanceMeters / R) + 
                   cos(lat1) * sin(distanceMeters / R) * cos(bearingRad))
        val lon2 = lon1 + atan2(sin(bearingRad) * sin(distanceMeters / R) * cos(lat1),
                          cos(distanceMeters / R) - sin(lat1) * sin(lat2))
                          
        return LatLon(Math.toDegrees(lat2), normalizeLongitude(Math.toDegrees(lon2)))
    }

    private fun headingToVector(headingRad: Double, speed: Double): Vector2D {
        if (headingRad.isNaN() || headingRad.isInfinite() || speed.isNaN() || speed.isInfinite()) {
            return Vector2D(0.0, 0.0)
        }
        // Nautical heading (0=N, clockwise) to Math angle (0=E, counter-clockwise)
        val angleRad = PI / 2.0 - headingRad
        return Vector2D(speed * cos(angleRad), speed * sin(angleRad))
    }

    private fun vectorToHeading(vector: Vector2D): Double {
        if (vector.x.isNaN() || vector.x.isInfinite() || vector.y.isNaN() || vector.y.isInfinite()) {
            return 0.0
        }
        val angleRad = atan2(vector.y, vector.x)
        return normalizeRadians(PI / 2.0 - angleRad)
    }

    /**
     * Normalizes an angle to 0..2PI range.
     */
    private fun normalizeRadians(rad: Double): Double {
        if (rad.isNaN() || rad.isInfinite()) return 0.0
        return (rad % (2 * PI) + 2 * PI) % (2 * PI)
    }

    /**
     * Transforms a bearing from Magnetic to True frame.
     */
    fun toTrue(magneticBearing: Double, variation: Double): Double {
        return normalizeRadians(magneticBearing + variation)
    }

    /**
     * Transforms a bearing from True to Magnetic frame.
     */
    fun toMagnetic(trueBearing: Double, variation: Double): Double {
        return normalizeRadians(trueBearing - variation)
    }

    private fun isWithinArc(bearing: Double, start: Double, end: Double): Boolean {
        val diff = normalizeRadians(end - start)
        val relBearing = normalizeRadians(bearing - start)
        return relBearing <= diff
    }

    private fun calculateIntersection(p1: LatLon, brng1: Double, p2: LatLon, brng2: Double): LatLon? {
        if (brng1.isNaN() || brng2.isNaN()) return null

        val lat1 = Math.toRadians(p1.latitude)
        val lon1 = Math.toRadians(p1.longitude)
        val lat2 = Math.toRadians(p2.latitude)
        val lon2 = Math.toRadians(p2.longitude)
        
        // brng1, brng2 are already in Radians
        val theta13 = brng1

        val deltaLat = lat2 - lat1
        val deltaLon = lon2 - lon1

        val dist12 = 2 * asin(sqrt(sin(deltaLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(deltaLon / 2).pow(2)))
        if (dist12 == 0.0 || dist12.isNaN()) return null

        val cosThetaA = (sin(lat2) - sin(lat1) * cos(dist12)) / (sin(dist12) * cos(lat1))
        val cosThetaB = (sin(lat1) - sin(lat2) * cos(dist12)) / (sin(dist12) * cos(lat2))
        
        if (cosThetaA !in -1.0..1.0 || cosThetaB !in -1.0..1.0) return null

        val thetaA = acos(cosThetaA)
        val thetaB = acos(cosThetaB)

        val theta12 = if (sin(deltaLon) > 0) thetaA else 2 * PI - thetaA
        val theta21 = if (sin(deltaLon) > 0) 2 * PI - thetaB else thetaB

        val alpha1 = (theta13 - theta12 + PI) % (2 * PI) - PI
        val alpha2 = (theta21 - brng2 + PI) % (2 * PI) - PI

        if (sin(alpha1) == 0.0 || sin(alpha2) == 0.0) return null
        if (sin(alpha1) * sin(alpha2) < 0) return null

        val cosAlpha3 = -cos(alpha1) * cos(alpha2) + sin(alpha1) * sin(alpha2) * cos(dist12)
        if (cosAlpha3 !in -1.0..1.0) return null
        
        val alpha3 = acos(cosAlpha3)
        val dist13 = atan2(sin(dist12) * sin(alpha1) * sin(alpha2), cos(alpha2) + cos(alpha1) * cos(alpha3))
        
        if (dist13.isNaN()) return null

        val lat3 = asin(sin(lat1) * cos(dist13) + cos(lat1) * sin(dist13) * cos(theta13))
        val deltaLon13 = atan2(sin(theta13) * sin(dist13) * cos(lat1), cos(dist13) - sin(lat1) * sin(lat3))
        val lon3 = lon1 + deltaLon13

        if (lat3.isNaN() || lon3.isNaN()) return null

        return LatLon(Math.toDegrees(lat3), normalizeLongitude(Math.toDegrees(lon3)))
    }

    private fun calculateBearing(start: LatLon, end: LatLon): Double {
        val lat1 = Math.toRadians(start.latitude)
        val lat2 = Math.toRadians(end.latitude)
        val deltaLon = Math.toRadians(end.longitude - start.longitude)

        val y = sin(deltaLon) * cos(lat2)
        val x = cos(lat1) * sin(lat2) - sin(lat1) * cos(lat2) * cos(deltaLon)
        val brng = atan2(y, x)
        return normalizeRadians(brng)
    }

    private fun normalizeLongitude(lon: Double): Double {
        return (lon + 540.0) % 360.0 - 180.0
    }
}
