package net.osmand.shared.aistracker

data class AisLocation(
    var latitude: Double,
    var longitude: Double,
    var speed: Float,    // in m/s
    var bearing: Float,  // in degrees
    var rot: Float? = null, // in degrees/minute
    var hasSpeed: Boolean = true,
    var hasBearing: Boolean = true
)
