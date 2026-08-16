package net.osmand.plus.plugins.nautical.map

import android.content.Context
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalMapLayer
import net.osmand.plus.plugins.nautical.NauticalPlugin.NauticalModule
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.map.layers.OceanographicGribMapLayer
import net.osmand.plus.plugins.nautical.poi.ui.VhfPoiSearchLayer
import net.osmand.plus.plugins.nautical.raster.SignalKRasterLayer
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.tide.map.TidalCurrentsMapLayer
import net.osmand.plus.plugins.nautical.ui.NauticalAisLayer
import net.osmand.plus.plugins.nautical.ui.SignalKLogbookLayer
import net.osmand.plus.plugins.nautical.ui.SignalKWaypointLayer
import net.osmand.plus.plugins.nautical.view.SignalKTideLayer
import net.osmand.plus.settings.backend.ApplicationMode

class NauticalLayerManager(private val app: OsmandApplication) {

    var nauticalMapLayer: NauticalMapLayer? = null
        internal set
    var aisAisLayer: NauticalAisLayer? = null
        internal set
    var skTideLayer: SignalKTideLayer? = null
        internal set
    var tidalCurrentsMapLayer: TidalCurrentsMapLayer? = null
        internal set
    var oceanographicGribMapLayer: OceanographicGribMapLayer? = null
        internal set
    var vhfPoiLayer: VhfPoiSearchLayer? = null
        internal set
    var skRasterLayer: SignalKRasterLayer? = null
        internal set
    var skLogbookLayer: SignalKLogbookLayer? = null
        internal set
    var skWaypointLayer: SignalKWaypointLayer? = null
        internal set
    var layerController: SailingMapLayerController? = null
        internal set

    fun registerLayers(
        context: Context,
        mapActivity: MapActivity,
        s57SpatialIndex: S57SpatialIndex?,
        onInitSubsystems: (SailingMapLayerController) -> Unit
    ) {
        val mapView = mapActivity.mapView
        if (nauticalMapLayer == null) {
            nauticalMapLayer = NauticalMapLayer(app)
            mapView.addLayer(nauticalMapLayer!!, 5.0f)
        }
        if (aisAisLayer == null) {
            aisAisLayer = NauticalAisLayer(context)
            mapView.addLayer(aisAisLayer!!, 3.5f)
        }
        if (skTideLayer == null) {
            skTideLayer = SignalKTideLayer(context)
            mapView.addLayer(skTideLayer!!, 4.6f)
        }
        if (tidalCurrentsMapLayer == null) {
            tidalCurrentsMapLayer = TidalCurrentsMapLayer(app)
            mapView.addLayer(tidalCurrentsMapLayer!!, 4.5f)
        }
        if (oceanographicGribMapLayer == null) {
            oceanographicGribMapLayer = OceanographicGribMapLayer(app)
            mapView.addLayer(oceanographicGribMapLayer!!, 4.0f)
        }

        if (vhfPoiLayer == null) {
            vhfPoiLayer = VhfPoiSearchLayer(mapActivity)
            mapView.addLayer(vhfPoiLayer!!, 4.8f)
        }
        if (skRasterLayer == null) {
            skRasterLayer = SignalKRasterLayer(mapActivity)
            mapView.addLayer(skRasterLayer!!, 4.2f)
        }
        if (skLogbookLayer == null) {
            skLogbookLayer = SignalKLogbookLayer(mapActivity)
            mapView.addLayer(skLogbookLayer!!, 4.9f)
        }
        if (skWaypointLayer == null) {
            skWaypointLayer = SignalKWaypointLayer(mapActivity)
            mapView.addLayer(skWaypointLayer!!, 4.7f)
        }

        val controller = SailingMapLayerController(mapActivity, s57SpatialIndex)
        controller.registerLayers()
        layerController = controller

        onInitSubsystems(controller)
    }

    fun updateLayers(
        context: Context,
        mapActivity: MapActivity?,
        isPluginActive: Boolean,
        s57SpatialIndex: S57SpatialIndex?,
        isModuleEnabled: (NauticalModule) -> Boolean,
        onInitSubsystems: (SailingMapLayerController) -> Unit
    ) {
        val activity = mapActivity ?: return
        val isBoat = app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)
        val mapView = activity.mapView
        if (isBoat && isPluginActive) {
            if (layerController == null) {
                registerLayers(context, activity, s57SpatialIndex, onInitSubsystems)
            } else {
                nauticalMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 5.0f) }

                if (isModuleEnabled(NauticalModule.AIS)) {
                    aisAisLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 3.5f) }
                } else {
                    aisAisLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.TIDES)) {
                    skTideLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.6f) }
                    tidalCurrentsMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.5f) }
                } else {
                    skTideLayer?.let { mapView.removeLayer(it) }
                    tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.GRIB)) {
                    oceanographicGribMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.0f) }
                } else {
                    oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.VHF)) {
                    vhfPoiLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.8f) }
                } else {
                    vhfPoiLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.RASTER)) {
                    skRasterLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.2f) }
                } else {
                    skRasterLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.LOGBOOK)) {
                    skLogbookLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.9f) }
                } else {
                    skLogbookLayer?.let { mapView.removeLayer(it) }
                }

                skWaypointLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 4.7f) }

                layerController?.updateLayerVisibility()
            }
        } else {
            nauticalMapLayer?.let { mapView.removeLayer(it) }
            aisAisLayer?.let { mapView.removeLayer(it) }
            skTideLayer?.let { mapView.removeLayer(it) }
            vhfPoiLayer?.let { mapView.removeLayer(it) }
            skWaypointLayer?.let { mapView.removeLayer(it) }
            tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
            oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
            layerController?.unregisterLayers()
        }
    }

    fun clearAisLayer() {
        aisAisLayer?.cleanupResources()
    }

    fun suppressBasemap(suppress: Boolean) {
        val value = if (suppress) "true" else "false"
        app.settings.getCustomRenderProperty("hide_sea_marks", "false").set(value)
        app.settings.getCustomRenderProperty("hide_coastline", "false").set(value)
        app.settings.getCustomRenderProperty("no_osm_nautical", "false").set(value)
    }

    fun destroy() {
        layerController?.unregisterLayers()
        layerController = null
        nauticalMapLayer = null
        aisAisLayer = null
        skTideLayer = null
        tidalCurrentsMapLayer = null
        oceanographicGribMapLayer = null
        vhfPoiLayer = null
        skRasterLayer = null
        skLogbookLayer = null
        skWaypointLayer = null
    }
}
