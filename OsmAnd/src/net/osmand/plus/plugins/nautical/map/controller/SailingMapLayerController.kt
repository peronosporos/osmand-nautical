package net.osmand.plus.plugins.nautical.map.controller

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.dr.ui.DeadReckoningMapLayer
import net.osmand.plus.plugins.nautical.hazard.ui.DynamicHazardLayer
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexMapLayer
import net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer
import net.osmand.plus.plugins.nautical.map.layers.WeatherRoutingMapLayer
import net.osmand.plus.plugins.nautical.mob.ui.MobMapLayer
import net.osmand.plus.plugins.nautical.raster.MarineRasterMapLayer
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.s57.ui.S57MapLayer
import net.osmand.plus.plugins.nautical.ui.anchor.AnchorWatchMapLayer

class SailingMapLayerController(private val mapActivity: MapActivity, s57SpatialIndex: S57SpatialIndex? = null) {

    val laylinesLayer = SailingLaylinesMapLayer(mapActivity)
    val gribLayer = OceanographicGribMapLayer(mapActivity)
    private val weatherRoutingLayer = WeatherRoutingMapLayer(mapActivity)
    val mobLayer = MobMapLayer(mapActivity)
    val drLayer = DeadReckoningMapLayer(mapActivity)
    val anchorLayer = AnchorWatchMapLayer(mapActivity)
    val navtexLayer = NavtexMapLayer(mapActivity)
    val hazardLayer = DynamicHazardLayer(mapActivity)
    val rasterLayer = MarineRasterMapLayer(mapActivity)
    val skPmtilesLayer = net.osmand.plus.plugins.nautical.raster.SignalKPmtilesLayer(mapActivity)
    val s57Layer = s57SpatialIndex?.let { S57MapLayer(mapActivity, it) }

    fun registerLayers() {
        updateLayerVisibility()
    }

    fun updateLayerVisibility() {
        val mapView = mapActivity.mapView
        val settings = mapActivity.app.settings

        // Raster
        if (settings.NAUTICAL_SHOW_RASTER_CHARTS.get()) {
            if (!mapView.layers.contains(rasterLayer)) mapView.addLayer(rasterLayer, 0.5f)
        } else {
            mapView.removeLayer(rasterLayer)
        }

        // PMTiles
        if (settings.NAUTICAL_SHOW_PMTILES.get()) {
            if (!mapView.layers.contains(skPmtilesLayer)) mapView.addLayer(skPmtilesLayer, 0.55f)
        } else {
            mapView.removeLayer(skPmtilesLayer)
        }

        // S-57
        s57Layer?.let {
            if (settings.NAUTICAL_SHOW_RASTER_CHARTS.get()) { // Assuming S-57 visibility linked for now
                if (!mapView.layers.contains(it)) mapView.addLayer(it, 0.6f)
            } else {
                mapView.removeLayer(it)
            }
        }

        // Oceanographic GRIB Overlay
        if (settings.NAUTICAL_SHOW_GRIB_OVERLAY.get()) {
            if (!mapView.layers.contains(gribLayer)) mapView.addLayer(gribLayer, 0.65f)
        } else {
            mapView.removeLayer(gribLayer)
        }

        // Laylines
        if (settings.NAUTICAL_SHOW_LAYLINES.get()) {
            if (!mapView.layers.contains(laylinesLayer)) mapView.addLayer(laylinesLayer, 4.3f)
        } else {
            mapView.removeLayer(laylinesLayer)
        }

        // MOB
        if (settings.NAUTICAL_MOB_ACTIVE.get()) {
            if (!mapView.layers.contains(mobLayer)) mapView.addLayer(mobLayer, 10f)
        } else {
            mapView.removeLayer(mobLayer)
        }

        // Dead Reckoning
        if (settings.NAUTICAL_DR_START_TIME.get() != 0L) {
            if (!mapView.layers.contains(drLayer)) mapView.addLayer(drLayer, 6.5f)
        } else {
            mapView.removeLayer(drLayer)
        }

        // Anchor
        if (settings.NAUTICAL_ANCHOR_LAT.get() != 0.0) {
            if (!mapView.layers.contains(anchorLayer)) mapView.addLayer(anchorLayer, 4.0f)
        } else {
            mapView.removeLayer(anchorLayer)
        }

        // Navtex (Hazards on top of routes)
        if (settings.NAVTEX_MAX_DISTANCE.get() > 0f) {
            if (!mapView.layers.contains(navtexLayer)) mapView.addLayer(navtexLayer, 7.0f)
        } else {
            mapView.removeLayer(navtexLayer)
        }

        // Dynamic Hazards (High priority)
        if (!mapView.layers.contains(hazardLayer)) {
            mapView.addLayer(hazardLayer, 8.0f)
        }

        // Weather Routing (Below nautical map)
        if (!mapView.layers.contains(weatherRoutingLayer)) {
            mapView.addLayer(weatherRoutingLayer, 4.1f)
        }
    }

    fun unregisterLayers() {
        val mapView = mapActivity.mapView
        mapView.removeLayer(rasterLayer)
        mapView.removeLayer(skPmtilesLayer)
        s57Layer?.let { mapView.removeLayer(it) }
        mapView.removeLayer(gribLayer)
        mapView.removeLayer(laylinesLayer)
        mapView.removeLayer(weatherRoutingLayer)
        mapView.removeLayer(mobLayer)
        mapView.removeLayer(drLayer)
        mapView.removeLayer(anchorLayer)
        mapView.removeLayer(navtexLayer)
        mapView.removeLayer(hazardLayer)
    }

    fun setWeatherRoute(result: net.osmand.plus.plugins.nautical.routing.model.OptimalRouteResult?) {
        weatherRoutingLayer.optimalRouteResult = result
        mapActivity.mapView.refreshMap()
    }
}
