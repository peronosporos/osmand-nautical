package net.osmand.plus.plugins.nautical.map.controller

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.dr.ui.DeadReckoningMapLayer
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexMapLayer
import net.osmand.plus.plugins.nautical.laylines.ui.SailingLaylinesMapLayer
import net.osmand.plus.plugins.nautical.map.layers.WeatherRoutingMapLayer
import net.osmand.plus.plugins.nautical.mob.ui.MobMapLayer
import net.osmand.plus.plugins.nautical.raster.MarineRasterMapLayer
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.s57.ui.S57MapLayer
import net.osmand.plus.plugins.nautical.ui.anchor.AnchorWatchMapLayer

class SailingMapLayerController(private val mapActivity: MapActivity, s57SpatialIndex: S57SpatialIndex? = null) {

    val laylinesLayer = SailingLaylinesMapLayer(mapActivity)
    private val weatherRoutingLayer = WeatherRoutingMapLayer(mapActivity)
    val mobLayer = MobMapLayer(mapActivity)
    val drLayer = DeadReckoningMapLayer(mapActivity)
    val anchorLayer = AnchorWatchMapLayer(mapActivity)
    val navtexLayer = NavtexMapLayer(mapActivity)
    val rasterLayer = MarineRasterMapLayer(mapActivity)
    val s57Layer = s57SpatialIndex?.let { S57MapLayer(mapActivity, it) }

    fun registerLayers() {
        val mapView = mapActivity.mapView
        if (!mapView.layers.contains(rasterLayer)) {
            mapView.addLayer(rasterLayer, 0.5f) // Above background, below most vectors
        }
        s57Layer?.let {
            if (!mapView.layers.contains(it)) {
                mapView.addLayer(it, 0.6f) // Just above raster
            }
        }
        if (!mapView.layers.contains(laylinesLayer)) {
            mapView.addLayer(laylinesLayer, 0f)
        }
        if (!mapView.layers.contains(weatherRoutingLayer)) {
            mapView.addLayer(weatherRoutingLayer, 0f)
        }
        if (!mapView.layers.contains(mobLayer)) {
            mapView.addLayer(mobLayer, 10f)
        }
        if (!mapView.layers.contains(drLayer)) {
            mapView.addLayer(drLayer, 6.5f) // Above location layer (6.0)
        }
        if (!mapView.layers.contains(anchorLayer)) {
            mapView.addLayer(anchorLayer, 4.0f)
        }
        if (!mapView.layers.contains(navtexLayer)) {
            mapView.addLayer(navtexLayer, 7.5f) // Above navigation layer (7.0)
        }
    }

    fun unregisterLayers() {
        val mapView = mapActivity.mapView
        mapView.removeLayer(rasterLayer)
        s57Layer?.let { mapView.removeLayer(it) }
        mapView.removeLayer(laylinesLayer)
        mapView.removeLayer(weatherRoutingLayer)
        mapView.removeLayer(mobLayer)
        mapView.removeLayer(drLayer)
        mapView.removeLayer(anchorLayer)
        mapView.removeLayer(navtexLayer)
    }

    fun setWeatherRoute(result: net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult?) {
        weatherRoutingLayer.optimalRouteResult = result
        mapActivity.mapView.refreshMap()
    }
}
