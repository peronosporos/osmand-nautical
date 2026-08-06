package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import net.osmand.core.jni.MapMarkersCollection
import net.osmand.core.jni.SingleSkImage
import net.osmand.core.jni.VectorLinesCollection
import net.osmand.data.LatLon
import net.osmand.data.PointDescription
import net.osmand.data.RotatedTileBox
import net.osmand.plus.ChartPointsHelper
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.NauticalAisManager
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.views.layers.ContextMenuLayer
import net.osmand.plus.views.layers.MapSelectionResult
import net.osmand.plus.views.layers.MapSelectionRules
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.shared.aistracker.AisObject
import java.util.concurrent.ConcurrentHashMap

class NauticalAisLayer(context: Context) : OsmandMapLayer(context), ContextMenuLayer.IContextMenuProvider {

    companion object {
        const val START_ZOOM = 6
        const val START_ZOOM_SHOW_SHAPE = 16
        const val START_ZOOM_SHOW_DIRECTION = 10
        private const val AIS_RENDER_REFRESH_INTERVAL_MS = 1000L
    }

    private val plugin: NauticalPlugin? = NauticalPlugin.getInstance()
    private val bitmapPaint = Paint().apply {
        isAntiAlias = true
        isFilterBitmap = true
        strokeWidth = 4f
        color = Color.DKGRAY
    }
    private val objectDrawables = ConcurrentHashMap<Int, NauticalAisObjectDrawable>()

    private var markersCollection: MapMarkersCollection? = null
    private var vectorLinesCollection: VectorLinesCollection? = null
    private var aisRestBitmap: Bitmap? = null
    private var aisRestImage: SingleSkImage? = null
    private var textScale = 1f
    private var lastRenderZoom = -1
    private var lastRenderRefreshTimeMs: Long = 0
    private var aisUpdateJob: Job? = null

    override fun initLayer(view: OsmandMapTileView) {
        super.initLayer(view)
        
        val manager = plugin?.aisManager
        val activity = view.mapActivity
        if ((manager != null) && (activity != null)) {
            aisUpdateJob = activity.lifecycleScope.launch {
                manager.aisEvents.collect { event ->
                    when (event) {
                        is NauticalAisManager.AisEvent.Updated -> onAisObjectReceived(event.obj)
                        is NauticalAisManager.AisEvent.Removed -> onAisObjectRemoved(event.obj)
                    }
                }
            }
        }

        val density = 5f
        val pointColor = -0x1
        val pointsHelper = ChartPointsHelper(context)
        aisRestBitmap = pointsHelper.createXAxisPointBitmap(pointColor, density)
    }

    override fun destroyLayer() {
        aisUpdateJob?.cancel()
        aisUpdateJob = null
        super.destroyLayer()
    }

    override fun cleanupResources() {
        val mapRenderer = mapRenderer
        if ((mapRenderer != null) && (markersCollection != null) && (vectorLinesCollection != null)) {
            markersCollection?.removeAllMarkers()
            vectorLinesCollection?.removeAllLines()
            mapRenderer.removeSymbolsProvider(markersCollection)
            mapRenderer.removeSymbolsProvider(vectorLinesCollection)
            aisRestImage = null
        }
        objectDrawables.clear()
        lastRenderZoom = -1
        lastRenderRefreshTimeMs = 0
    }

    fun onAisObjectReceived(ais: AisObject) {
        val plugin = plugin ?: return
        val mmsi = ais.mmsi
        val own = isOwnObject(ais)
        val manager = plugin.aisManager
        val extras = manager?.getAisExtras(mmsi)
        val engine = NauticalPlugin.engine
        val virtual = (engine != null) && (engine.aisCache.containsKey(mmsi))
        
        var drawable = objectDrawables[mmsi]
        if (drawable == null) {
            if (isOwnObjectHidden(ais)) return
            drawable = NauticalAisObjectDrawable(plugin, ais)
            drawable.setOwnObject(own)
            drawable.setVirtual(virtual)
            extras?.let { 
                drawable.setThreatLevel(it.threatLevel)
                drawable.setRemote(it.isRemote)
                drawable.setCpaWarning(it.hasCpaWarning)
            }
            objectDrawables[mmsi] = drawable
        } else {
            if (isOwnObjectHidden(ais)) {
                onAisObjectRemoved(ais)
                return
            } else {
                drawable.setOwnObject(own)
                drawable.setVirtual(virtual)
                extras?.let { 
                    drawable.setThreatLevel(it.threatLevel)
                    drawable.setRemote(it.isRemote)
                    drawable.setCpaWarning(it.hasCpaWarning)
                }
                drawable.set(ais)
            }
        }
        
        val renderer = mapRenderer
        if ((renderer != null) && (!drawable.hasAisRenderData()) && (aisRestImage != null)
            && (markersCollection != null) && (vectorLinesCollection != null)
        ) {
            drawable.createAisRenderData(
                baseOrder,
                bitmapPaint,
                markersCollection!!,
                vectorLinesCollection!!,
                aisRestImage!!,
            )
        }
        drawable.updateAisRenderData(tileView, bitmapPaint)
    }

    fun onAisObjectRemoved(ais: AisObject) {
        if ((markersCollection != null) && (vectorLinesCollection != null)) {
            objectDrawables[ais.mmsi]?.clearAisRenderData(markersCollection!!, vectorLinesCollection!!)
        }
        objectDrawables.remove(ais.mmsi)
    }

    private fun isOwnObject(ais: AisObject): Boolean {
        return ais.mmsi == plugin?.aisOwnMmsi?.get()
    }

    private fun isOwnObjectHidden(ais: AisObject): Boolean {
        return isOwnObject(ais) && (plugin?.aisDisplayOwnPosition?.get() != true)
    }

    fun refreshOwnObjectVisibility() {
        val aisObjects = plugin?.aisManager?.getAisObjects() ?: return
        for (ais in aisObjects) {
            val drawable = objectDrawables[ais.mmsi]
            if (isOwnObjectHidden(ais)) {
                if (drawable != null) {
                    onAisObjectRemoved(ais)
                }
            } else drawable?.let {
                it.setOwnObject(isOwnObject(ais))
                it.updateAisRenderData(tileView, bitmapPaint)
            } ?: onAisObjectReceived(ais)
        }
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {}

    override fun onPrepareBufferImage(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        super.onPrepareBufferImage(canvas, tileBox, settings)
        val mapRenderer = mapRenderer

        val currentTextScale = textScale
        val textScaleChanged = this.textScale != currentTextScale
        this.textScale = currentTextScale
        // if (textScaleChanged) plugin?.aisImagesCache?.clearCache() // If we have one

        val aisObjects = plugin?.aisManager?.getAisObjects() ?: emptyList()
        mapRenderer?.let { renderer ->
            if (mapActivityInvalidated || mapRendererChanged || textScaleChanged) {
                cleanupResources()

                if ((aisRestImage == null) && (aisRestBitmap != null)) {
                    aisRestImage = NativeUtilities.createSkImageFromBitmap(aisRestBitmap!!)
                }
                markersCollection = MapMarkersCollection()
                vectorLinesCollection = VectorLinesCollection()
                renderer.addSymbolsProvider(markersCollection)
                renderer.addSymbolsProvider(vectorLinesCollection)

                val currentWorkflow = plugin?.workflowEngine?.currentWorkflow?.value
                val isCloseQuarters = currentWorkflow == SailingWorkflowState.CLOSE_QUARTERS

                for (ais in aisObjects) {
                    if (isOwnObjectHidden(ais)) continue
                    
                    val drawable = NauticalAisObjectDrawable(plugin!!, ais)
                    drawable.setOwnObject(isOwnObject(ais))
                    
                    val extras = plugin.aisManager?.getAisExtras(ais.mmsi)
                    extras?.let { 
                        drawable.setThreatLevel(it.threatLevel)
                        drawable.setRemote(it.isRemote)
                        drawable.setCpaWarning(it.hasCpaWarning)
                    }
                    
                    if (isCloseQuarters && !(ais.cpa.valid) && (plugin.application.locationProvider.lastKnownLocation != null) && (ais.position != null)) {
                        val ownLoc = plugin.application.locationProvider.lastKnownLocation!!
                        val dist = net.osmand.util.MapUtils.getDistance(
                            ownLoc.latitude,
                            ownLoc.longitude,
                            ais.position!!.latitude,
                            ais.position!!.longitude,
                        )
                        if (dist > 1000) {
                            drawable.setAlpha(80)
                        }
                    }

                    objectDrawables[ais.mmsi] = drawable
                    if (aisRestImage != null) {
                        drawable.createAisRenderData(
                            baseOrder,
                            bitmapPaint,
                            markersCollection!!,
                            vectorLinesCollection!!,
                            aisRestImage!!,
                        )
                    }
                    drawable.updateAisRenderData(tileView, bitmapPaint)
                }
                updateNativeRenderRefreshState()
            } else if (shouldRefreshNativeRenderData()) {
                refreshNativeRenderData(aisObjects)
            }
            mapActivityInvalidated = false
            mapRendererChanged = false
        } ?: run {
            if (tileBox.zoom >= START_ZOOM) {
                for (ais in aisObjects) {
                    objectDrawables[ais.mmsi]?.draw(bitmapPaint, canvas, tileBox)
                }
            }
        }
    }

    private fun shouldRefreshNativeRenderData(): Boolean {
        val tileView = tileView
        if ((mapRenderer == null) || (tileView == null) || (objectDrawables.isEmpty())) return false
        val now = System.currentTimeMillis()
        return (tileView.zoom != lastRenderZoom) || ((now - lastRenderRefreshTimeMs) >= AIS_RENDER_REFRESH_INTERVAL_MS)
    }

    private fun refreshNativeRenderData(aisObjects: List<AisObject>) {
        for (ais in aisObjects) {
            objectDrawables[ais.mmsi]?.updateAisRenderData(tileView, bitmapPaint)
        }
        updateNativeRenderRefreshState()
    }

    private fun updateNativeRenderRefreshState() {
        lastRenderZoom = tileView?.zoom ?: -1
        lastRenderRefreshTimeMs = System.currentTimeMillis()
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun collectObjectsFromPoint(result: MapSelectionResult, rules: MapSelectionRules) {
        val point = result.point
        val tileBox = result.tileBox
        val aisObjects = plugin?.aisManager?.getAisObjects() ?: return
        if ((aisObjects.isEmpty()) || (tileBox.zoom < START_ZOOM)) return

        val mapRenderer = mapRenderer
        val radius = getScaledTouchRadius(application, tileBox.defaultRadiusPoi) * 1.5f
        val touchPolygon31 = mapRenderer?.let {
            NativeUtilities.getPolygon31FromPixelAndRadius(it, point, radius)
        }

        for (obj in aisObjects) {
            if (isOwnObjectHidden(obj)) continue
            val pos = obj.position ?: continue
            val lat = pos.latitude
            val lon = pos.longitude

            val add = if ((mapRenderer != null) && (touchPolygon31 != null)) {
                NativeUtilities.isPointInsidePolygon(lat, lon, touchPolygon31)
            } else {
                tileBox.isLatLonNearPixel(lat, lon, point.x, point.y, radius)
            }
            if (add) result.collect(obj, this)
        }
    }

    override fun getObjectLocation(o: Any?): LatLon? {
        return (o as? AisObject)?.position?.let { LatLon(it.latitude, it.longitude) }
    }

    override fun getObjectName(o: Any?): PointDescription? {
        return (o as? AisObject)?.let { ais ->
            val name = ais.shipName ?: "AIS Target"
            PointDescription("AIS object", name + (if (isSignalLost(ais)) " (signal lost)" else ""))
        }
    }

    private fun isSignalLost(ais: AisObject): Boolean {
        val timeout = plugin?.aisShipLostTimeout?.get() ?: 4
        return ais.isLost(timeout) && ais.isMovable() && !ais.isVesselAtRest()
    }
}
