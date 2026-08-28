package net.osmand.plus.plugins.nautical.map.controller

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.dr.ui.DeadReckoningMapLayer
import net.osmand.plus.plugins.nautical.hazard.ui.DynamicHazardLayer
import net.osmand.plus.plugins.nautical.hazard.ui.NavtexMapLayer
import net.osmand.plus.plugins.nautical.laylines.ui.SailingLaylinesMapLayer
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

        // 1. Raster Charts (Z = 1.10f)
        if (settings.NAUTICAL_SHOW_RASTER_CHARTS.get()) {
            if (!mapView.layers.contains(rasterLayer)) mapView.addLayer(rasterLayer, 1.10f)
        } else {
            mapView.removeLayer(rasterLayer)
        }

        // 2. PMTiles (Z = 1.20f)
        if (settings.NAUTICAL_SHOW_PMTILES.get()) {
            if (!mapView.layers.contains(skPmtilesLayer)) mapView.addLayer(skPmtilesLayer, 1.20f)
        } else {
            mapView.removeLayer(skPmtilesLayer)
        }

        // 3. S-57 Vector Objects (Z = 1.30f)
        s57Layer?.let {
            if (settings.NAUTICAL_SHOW_S57_CHARTS.get()) {
                if (!mapView.layers.contains(it)) mapView.addLayer(it, 1.30f)
            } else {
                mapView.removeLayer(it)
            }
        }

        // 4. GRIB Oceanographic Overlay (Z = 2.10f)
        if (settings.NAUTICAL_SHOW_GRIB_OVERLAY.get()) {
            if (!mapView.layers.contains(gribLayer)) mapView.addLayer(gribLayer, 2.10f)
        } else {
            mapView.removeLayer(gribLayer)
        }

        // 5. Weather Routing & Isochrones (Z = 3.10f)
        if (weatherRoutingLayer.optimalRouteResult != null) {
            if (!mapView.layers.contains(weatherRoutingLayer)) mapView.addLayer(weatherRoutingLayer, 3.10f)
        } else {
            mapView.removeLayer(weatherRoutingLayer)
        }

        // 6. Sailing Laylines & Dead Reckoning (Z = 3.20f)
        if (settings.NAUTICAL_SHOW_LAYLINES.get()) {
            if (!mapView.layers.contains(laylinesLayer)) mapView.addLayer(laylinesLayer, 3.20f)
        } else {
            mapView.removeLayer(laylinesLayer)
        }

        if (settings.NAUTICAL_DR_START_TIME.get() != 0L) {
            if (!mapView.layers.contains(drLayer)) mapView.addLayer(drLayer, 3.20f)
        } else {
            mapView.removeLayer(drLayer)
        }

        // 7. Navtex & Hazards (Z = 4.10f)
        if (settings.NAVTEX_MAX_DISTANCE.get() > 0f) {
            if (!mapView.layers.contains(navtexLayer)) mapView.addLayer(navtexLayer, 4.10f)
        } else {
            mapView.removeLayer(navtexLayer)
        }

        if (!mapView.layers.contains(hazardLayer)) {
            mapView.addLayer(hazardLayer, 4.10f)
        }

        // 8. Anchor Watch (Z = 4.80f)
        if (settings.NAUTICAL_ANCHOR_LAT.get() != 0.0) {
            if (!mapView.layers.contains(anchorLayer)) mapView.addLayer(anchorLayer, 4.80f)
        } else {
            mapView.removeLayer(anchorLayer)
        }

        // 9. MOB Emergency (Z = 5.50f)
        if (settings.NAUTICAL_MOB_ACTIVE.get()) {
            if (!mapView.layers.contains(mobLayer)) mapView.addLayer(mobLayer, 5.50f)
        } else {
            mapView.removeLayer(mobLayer)
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
