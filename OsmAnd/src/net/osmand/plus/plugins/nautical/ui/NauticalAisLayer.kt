package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.*
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

class NauticalAisLayer(
    context: Context,
    private val plugin: NauticalPlugin
) : OsmandMapLayer(context), ContextMenuLayer.IContextMenuProvider, NauticalAisManager.AisObjectListener {

    companion object {
        const val START_ZOOM = 1
        const val START_ZOOM_SHOW_SHAPE = 16
        const val START_ZOOM_SHOW_DIRECTION = 10
        private const val AIS_RENDER_REFRESH_INTERVAL_MS = 200L
    }

    private val imagesCache by lazy { net.osmand.plus.plugins.aistracker.AisImagesCache(application) }
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
    private var followedMmsi: Int? = null

    fun onManagerBound(manager: NauticalAisManager) {
        manager.addListener(this)
        mapActivityInvalidated = true
        manager.getAisObjects().forEach { onAisObjectReceived(it) }
        getApplication().runInUIThread {
            tileView?.refreshMap()
        }
    }

    override fun initLayer(view: OsmandMapTileView) {
        super.initLayer(view)
        
        plugin.aisManager?.let { onManagerBound(it) }

        val density = context.resources.displayMetrics.density
        val size = (16 * density).toInt()
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2 * density
            color = Color.WHITE
        }
        canvas.drawCircle(size / 2f, size / 2f, (size / 2f) - (2 * density), paint)
        aisRestBitmap = bitmap
    }

    override fun destroyLayer() {
        plugin.aisManager?.removeListener(this)
        super.destroyLayer()
    }

    public override fun cleanupResources() {
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

    fun clearAisData() {
        cleanupResources()
    }

    private var lastMapRefreshTimeMs: Long = 0L
    private var mapRefreshScheduled: Boolean = false

    private fun scheduleThrottledMapRefresh() {
        val now = System.currentTimeMillis()
        if (now - lastMapRefreshTimeMs >= 1000L) {
            lastMapRefreshTimeMs = now
            getApplication().runInUIThread {
                tileView?.refreshMap()
            }
        } else if (!mapRefreshScheduled) {
            mapRefreshScheduled = true
            val delayMs = 1000L - (now - lastMapRefreshTimeMs)
            getApplication().runInUIThread({
                lastMapRefreshTimeMs = System.currentTimeMillis()
                mapRefreshScheduled = false
                tileView?.refreshMap()
            }, delayMs)
        }
    }

    override fun onAisObjectReceived(ais: AisObject) {
        val mmsi = ais.mmsi
        val own = isOwnObject(ais)
        val manager = plugin.aisManager
        val extras = manager?.getAisExtras(mmsi)
        val engine = NauticalPlugin.engine
        val virtual = (manager != null) && (manager.getAisObject(mmsi) != null)
        
        // Task: Local Follow Mode handling
        if (mmsi == followedMmsi && ais.position != null) {
            getApplication().runInUIThread {
                tileView?.setLatLon(ais.position!!.latitude, ais.position!!.longitude)
            }
        }

        scheduleThrottledMapRefresh()

        var drawable = objectDrawables[mmsi]
        if (drawable == null) {
            if (isOwnObjectHidden(ais)) return
            drawable = NauticalAisObjectDrawable(plugin, ais, imagesCache)
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

    override fun onAisObjectRemoved(ais: AisObject) {
        if ((markersCollection != null) && (vectorLinesCollection != null)) {
            objectDrawables[ais.mmsi]?.clearAisRenderData(markersCollection!!, vectorLinesCollection!!)
        }
        objectDrawables.remove(ais.mmsi)
        mapActivityInvalidated = true
        scheduleThrottledMapRefresh()
    }

    private fun isOwnObject(ais: AisObject): Boolean {
        val ownMmsi = plugin.aisOwnMmsi.get()
        return (ownMmsi != 0) && (ais.mmsi == ownMmsi)
    }

    private fun isOwnObjectHidden(ais: AisObject): Boolean {
        return isOwnObject(ais) && (!plugin.aisDisplayOwnPosition.get())
    }

    fun refreshOwnObjectVisibility() {
        val aisObjects = plugin.aisManager?.getAisObjects() ?: return
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

    fun isLocationVisible(tileBox: RotatedTileBox?, lat: Double, lon: Double): Boolean {
        if (tileBox == null) {
            return false
        }
        return tileBox.containsLatLon(lat, lon)
    }

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = Color.rgb(0, 180, 216)
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
    }
    private val trackPath = Path()

    private fun drawTracks(canvas: Canvas, tileBox: RotatedTileBox) {
        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()
        for (ais in aisObjects) {
            if (manager.isTrackEnabled(ais.mmsi)) {
                val breadcrumbs = manager.getBreadcrumbs(ais.mmsi)
                if (breadcrumbs.size >= 2) {
                    trackPath.reset()
                    var first = true
                    for ((lat, lon) in breadcrumbs) {
                        val x = tileBox.getPixXFromLatLon(lat, lon)
                        val y = tileBox.getPixYFromLatLon(lat, lon)
                        if (first) {
                            trackPath.moveTo(x, y)
                            first = false
                        } else {
                            trackPath.lineTo(x, y)
                        }
                    }
                    canvas.drawPath(trackPath, trackPaint)
                }
            }
        }
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        drawTracks(canvas, tileBox)
        if (mapRenderer == null && tileBox.zoom >= START_ZOOM) {
            val aisObjects = plugin.aisManager?.getAisObjects() ?: return
            for (ais in aisObjects) {
                if (isOwnObjectHidden(ais)) continue
                val pos = ais.position
                if (pos != null && isLocationVisible(tileBox, pos.latitude, pos.longitude)) {
                    val drawable = objectDrawables.getOrPut(ais.mmsi) {
                        NauticalAisObjectDrawable(plugin, ais, imagesCache).apply {
                            setOwnObject(isOwnObject(ais))
                        }
                    }
                    drawable.draw(bitmapPaint, canvas, tileBox)
                }
            }
        }
    }

    override fun onPrepareBufferImage(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        super.onPrepareBufferImage(canvas, tileBox, settings)
        drawTracks(canvas, tileBox)
        val mapRenderer = mapRenderer

        val currentTextScale = textScale
        val textScaleChanged = this.textScale != currentTextScale
        this.textScale = currentTextScale
        if (textScaleChanged) {
            imagesCache.clearCache()
        }

        val aisObjects = plugin.aisManager?.getAisObjects() ?: emptyList()
        if (mapRenderer != null) {
            if (mapActivityInvalidated || mapRendererChanged || textScaleChanged) {
                cleanupResources()

                if ((aisRestImage == null) && (aisRestBitmap != null)) {
                    aisRestImage = NativeUtilities.createSkImageFromBitmap(aisRestBitmap!!)
                }
                markersCollection = MapMarkersCollection()
                vectorLinesCollection = VectorLinesCollection()
                mapRenderer.addSymbolsProvider(markersCollection)
                mapRenderer.addSymbolsProvider(vectorLinesCollection)

                val currentWorkflow = plugin.workflowEngine?.currentWorkflow?.value
                val isCloseQuarters = currentWorkflow == SailingWorkflowState.CLOSE_QUARTERS

                for (ais in aisObjects) {
                    if (isOwnObjectHidden(ais)) continue
                    
                    val drawable = NauticalAisObjectDrawable(plugin, ais, imagesCache)
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
                    if (aisRestImage != null && markersCollection != null && vectorLinesCollection != null) {
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
        } else if (tileBox.zoom >= START_ZOOM) {
            for (ais in aisObjects) {
                if (isOwnObjectHidden(ais)) continue
                val pos = ais.position
                if (pos != null && isLocationVisible(tileBox, pos.latitude, pos.longitude)) {
                    val drawable = objectDrawables.getOrPut(ais.mmsi) {
                        NauticalAisObjectDrawable(plugin, ais, imagesCache).apply {
                            setOwnObject(isOwnObject(ais))
                        }
                    }
                    drawable.draw(bitmapPaint, canvas, tileBox)
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
        val aisObjects = plugin.aisManager?.getAisObjects() ?: return
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
            val resolved = plugin.aisManager?.getAisObject(ais.mmsi) ?: ais
            val shipName = resolved.shipName?.trim()
            val name = if (!shipName.isNullOrEmpty()) shipName else "MMSI: ${resolved.mmsi}"
            PointDescription("AIS object", name + (if (isSignalLost(resolved)) " (signal lost)" else ""))
        }
    }

    fun setFollowedTarget(mmsi: Int?) {
        this.followedMmsi = mmsi
        if (mmsi != null) {
            plugin.aisManager?.getAisObjects()?.find { it.mmsi == mmsi }?.let { onAisObjectReceived(it) }
        }
    }

    private fun isSignalLost(ais: AisObject): Boolean {
        val timeout = plugin.aisShipLostTimeout.get()
        // Signal is lost if timestamp is too old, regardless of movement status
        return ais.isLost(timeout)
    }
}
