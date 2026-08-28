package net.osmand.plus.plugins.nautical.map

import android.content.Context
import net.osmand.plus.OsmandApplication
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalMapLayer
import net.osmand.plus.plugins.nautical.NauticalPlugin
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

class NauticalLayerManager(
    private val app: OsmandApplication,
    private val plugin: NauticalPlugin
) {

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
        if (skRasterLayer == null) {
            skRasterLayer = SignalKRasterLayer(mapActivity)
            mapView.addLayer(skRasterLayer!!, 1.10f)
        } else if (!mapView.layers.contains(skRasterLayer!!)) {
            mapView.addLayer(skRasterLayer!!, 1.10f)
        }

        if (oceanographicGribMapLayer == null) {
            oceanographicGribMapLayer = OceanographicGribMapLayer(app)
            mapView.addLayer(oceanographicGribMapLayer!!, 2.10f)
        } else if (!mapView.layers.contains(oceanographicGribMapLayer!!)) {
            mapView.addLayer(oceanographicGribMapLayer!!, 2.10f)
        }

        if (skTideLayer == null) {
            skTideLayer = SignalKTideLayer(context)
            mapView.addLayer(skTideLayer!!, 2.20f)
        } else if (!mapView.layers.contains(skTideLayer!!)) {
            mapView.addLayer(skTideLayer!!, 2.20f)
        }

        if (tidalCurrentsMapLayer == null) {
            tidalCurrentsMapLayer = TidalCurrentsMapLayer(app)
            mapView.addLayer(tidalCurrentsMapLayer!!, 2.20f)
        } else if (!mapView.layers.contains(tidalCurrentsMapLayer!!)) {
            mapView.addLayer(tidalCurrentsMapLayer!!, 2.20f)
        }

        if (skWaypointLayer == null) {
            skWaypointLayer = SignalKWaypointLayer(mapActivity)
            mapView.addLayer(skWaypointLayer!!, 3.30f)
        } else if (!mapView.layers.contains(skWaypointLayer!!)) {
            mapView.addLayer(skWaypointLayer!!, 3.30f)
        }

        if (skLogbookLayer == null) {
            skLogbookLayer = SignalKLogbookLayer(mapActivity)
            mapView.addLayer(skLogbookLayer!!, 3.30f)
        } else if (!mapView.layers.contains(skLogbookLayer!!)) {
            mapView.addLayer(skLogbookLayer!!, 3.30f)
        }

        if (vhfPoiLayer == null) {
            vhfPoiLayer = VhfPoiSearchLayer(mapActivity)
            mapView.addLayer(vhfPoiLayer!!, 3.30f)
        } else if (!mapView.layers.contains(vhfPoiLayer!!)) {
            mapView.addLayer(vhfPoiLayer!!, 3.30f)
        }

        if (aisAisLayer == null) {
            aisAisLayer = NauticalAisLayer(context, plugin)
            mapView.addLayer(aisAisLayer!!, 4.50f)
        } else if (!mapView.layers.contains(aisAisLayer!!)) {
            mapView.addLayer(aisAisLayer!!, 4.50f)
        }

        if (nauticalMapLayer == null) {
            nauticalMapLayer = NauticalMapLayer(app)
            mapView.addLayer(nauticalMapLayer!!, 5.00f)
        } else if (!mapView.layers.contains(nauticalMapLayer!!)) {
            mapView.addLayer(nauticalMapLayer!!, 5.00f)
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
                nauticalMapLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 5.00f) }

                if (isModuleEnabled(NauticalModule.RASTER)) {
                    val raster = skRasterLayer ?: SignalKRasterLayer(activity).also { skRasterLayer = it }
                    if (!mapView.layers.contains(raster)) mapView.addLayer(raster, 1.10f)
                } else {
                    skRasterLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.GRIB)) {
                    val grib = oceanographicGribMapLayer ?: OceanographicGribMapLayer(app).also { oceanographicGribMapLayer = it }
                    if (!mapView.layers.contains(grib)) mapView.addLayer(grib, 2.10f)
                } else {
                    oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.TIDES)) {
                    val tide = skTideLayer ?: SignalKTideLayer(context).also { skTideLayer = it }
                    val current = tidalCurrentsMapLayer ?: TidalCurrentsMapLayer(app).also { tidalCurrentsMapLayer = it }
                    if (!mapView.layers.contains(tide)) mapView.addLayer(tide, 2.20f)
                    if (!mapView.layers.contains(current)) mapView.addLayer(current, 2.20f)
                } else {
                    skTideLayer?.let { mapView.removeLayer(it) }
                    tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
                }

                skWaypointLayer?.let { if (!mapView.layers.contains(it)) mapView.addLayer(it, 3.30f) }

                if (isModuleEnabled(NauticalModule.LOGBOOK)) {
                    val logbook = skLogbookLayer ?: SignalKLogbookLayer(activity).also { skLogbookLayer = it }
                    if (!mapView.layers.contains(logbook)) mapView.addLayer(logbook, 3.30f)
                } else {
                    skLogbookLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.VHF)) {
                    val vhf = vhfPoiLayer ?: VhfPoiSearchLayer(activity).also { vhfPoiLayer = it }
                    if (!mapView.layers.contains(vhf)) mapView.addLayer(vhf, 3.30f)
                } else {
                    vhfPoiLayer?.let { mapView.removeLayer(it) }
                }

                if (isModuleEnabled(NauticalModule.AIS)) {
                    val ais = aisAisLayer ?: NauticalAisLayer(context, plugin).also { aisAisLayer = it }
                    if (!mapView.layers.contains(ais)) mapView.addLayer(ais, 4.50f)
                } else {
                    aisAisLayer?.let { mapView.removeLayer(it) }
                }

                layerController?.updateLayerVisibility()
            }
        } else {
            nauticalMapLayer?.let { mapView.removeLayer(it) }
            skRasterLayer?.let { mapView.removeLayer(it) }
            oceanographicGribMapLayer?.let { mapView.removeLayer(it) }
            skTideLayer?.let { mapView.removeLayer(it) }
            tidalCurrentsMapLayer?.let { mapView.removeLayer(it) }
            skWaypointLayer?.let { mapView.removeLayer(it) }
            skLogbookLayer?.let { mapView.removeLayer(it) }
            vhfPoiLayer?.let { mapView.removeLayer(it) }
            aisAisLayer?.let { mapView.removeLayer(it) }
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
