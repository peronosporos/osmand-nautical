package net.osmand.plus.plugins.nautical.routing.model

import net.osmand.plus.plugins.nautical.network.PolarProfile

data class Waypoint(
    val latitude: Double,
    val longitude: Double
)

data class RoutingRequest(
    val start: Waypoint,
    val destination: Waypoint,
    val departureTime: Long,
    val polarProfile: PolarProfile,
    val timeStepHours: Double = 1.0
)

data class IsochroneNode(
    val latitude: Double,
    val longitude: Double,
    val cumulativeTimeHours: Double,
    val heading: Double,
    val parent: IsochroneNode?,
    val speedThroughWater: Double,
    val speedOverGround: Double
)

data class PassagePlanLeg(
    val legNumber: Int,
    val from: Waypoint,
    val to: Waypoint,
    val distanceNm: Double,
    val courseToSteerDeg: Double,
    val expectedSetDeg: Double?,
    val expectedDriftKn: Double?,
    val speedOverGroundKn: Double,
    val eteHours: Double,
    val windSpeedMs: Double? = null,
    val windAngleRad: Double? = null,
    val waveHeightM: Double? = null
)

data class OptimalRouteResult(
    val path: List<Waypoint>,
    val totalTimeHours: Double,
    val totalDistanceNm: Double,
    val legs: List<PassagePlanLeg> = emptyList(),
    val confidenceFactor: Double = 1.0
)
