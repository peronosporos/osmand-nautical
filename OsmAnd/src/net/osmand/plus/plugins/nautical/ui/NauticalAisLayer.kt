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
import net.osmand.shared.extensions.toDegrees
import net.osmand.shared.util.KMapUtils
import java.util.Locale
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
        application.runInUIThread {
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
        cpaLabelCache.clear()
        cpaLastDistCache.clear()
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
        cpaLabelCache.remove(ais.mmsi)
        cpaLastDistCache.remove(ais.mmsi)
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

    // Preallocated CPA Intercept Vector rendering resources for zero per-frame allocations in onDraw
    private val cpaVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFF1744.toInt() // High-visibility danger red vector
        pathEffect = DashPathEffect(floatArrayOf(16f, 8f), 0f)
    }

    private val cpaConnectingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = 0xFFFF5252.toInt()
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
    }

    private val cpaDiamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xCCFF1744.toInt()
    }

    private val cpaDiamondStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    private val cpaRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFF1744.toInt()
        alpha = 160
    }

    private val cpaRingStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }

    private val cpaRingCenterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = Color.WHITE
    }

    private val cpaBadgeBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDD1E0000.toInt() // Dark red-black badge background
    }

    private val cpaBadgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        color = 0xFFFF5252.toInt()
    }

    private val cpaBadgeTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 22f
        textAlign = Paint.Align.LEFT
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
    }

    private val cpaVectorPath = Path()
    private val cpaConnectingPath = Path()
    private val cpaDiamondPath = Path()
    private val cpaBadgeRect = RectF()
    private val cpaLabelCache = ConcurrentHashMap<Int, String>()
    private val cpaLastDistCache = ConcurrentHashMap<Int, Float>()

    private fun getCpaLabel(mmsi: Int, cpaNm: Float): String {
        val lastDist = cpaLastDistCache[mmsi]
        if (lastDist != null && kotlin.math.abs(lastDist - cpaNm) < 0.05f) {
            val cached = cpaLabelCache[mmsi]
            if (cached != null) return cached
        }
        val label = String.format(java.util.Locale.US, "CPA: %.1f NM", cpaNm)
        cpaLastDistCache[mmsi] = cpaNm
        cpaLabelCache[mmsi] = label
        return label
    }

    private fun drawTracks(canvas: Canvas, tileBox: RotatedTileBox) {
        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()
        val isNight = NauticalPlugin.isNightVision(application)
        trackPaint.color = if (isNight) 0x80FF1744.toInt() else Color.rgb(0, 180, 216)

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

    private fun drawCpaInterceptVectors(canvas: Canvas, tileBox: RotatedTileBox) {
        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()
        if (aisObjects.isEmpty()) return

        val isNight = NauticalPlugin.isNightVision(application)
        cpaVectorPaint.color = 0xFFFF1744.toInt()
        cpaConnectingPaint.color = if (isNight) 0xFFFF5252.toInt() else 0xFFFF5722.toInt()
        cpaDiamondPaint.color = if (isNight) 0xCCFF1744.toInt() else 0xCCFF1744.toInt()
        cpaDiamondStrokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.WHITE
        cpaRingPaint.color = 0xFFFF1744.toInt()
        cpaRingStrokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.WHITE
        cpaRingCenterPaint.color = if (isNight) 0xFFFF1744.toInt() else Color.WHITE
        cpaBadgeBgPaint.color = if (isNight) 0xEE120000.toInt() else 0xDD1E0000.toInt()
        cpaBadgeStrokePaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFFFF5252.toInt()
        cpaBadgeTextPaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.WHITE

        val density = context.resources.displayMetrics.density
        val baseRingRadius = 6f * density // 12dp diameter -> 6dp radius
        val now = System.currentTimeMillis()
        val pulsePhase = (now % 1000L) / 1000f
        val pulseFactor = kotlin.math.sin(pulsePhase * Math.PI * 2.0).toFloat()
        val pulseRadius = baseRingRadius + (2f * density * pulseFactor.coerceAtLeast(0f))
        val pulseAlpha = (140 + 100 * pulseFactor).toInt().coerceIn(60, 240)
        cpaRingPaint.alpha = pulseAlpha
        cpaDiamondPaint.alpha = pulseAlpha

        val cpaWarningDist = plugin.aisCpaWarningDistance.get().toDouble()
        val cpaWarningTimeSec = plugin.aisCpaWarningTime.get().toDouble()

        val ownLoc = application.locationProvider.lastKnownLocation
        val liveState = NauticalPlugin.engine?.getCurrentState()
        val ownLat = liveState?.latitude ?: ownLoc?.latitude
        val ownLon = liveState?.longitude ?: ownLoc?.longitude
        val ownSogMps = (liveState?.speedOverGround ?: (ownLoc?.speed?.toDouble() ?: 0.0))
        val ownCogDeg = liveState?.courseOverGroundTrue ?: (ownLoc?.bearing?.toDouble() ?: 0.0)

        for (ais in aisObjects) {
            if (isOwnObjectHidden(ais)) continue
            val pos = ais.position ?: continue

            val extras = manager.getAisExtras(ais.mmsi)
            val hasCpaWarning = extras.hasCpaWarning
            val isThreatDist = ais.cpa.valid && ais.cpa.cpa <= cpaWarningDist
            val isThreatTime = ais.cpa.valid && (ais.cpa.tcpa * 3600.0) <= cpaWarningTimeSec && ais.cpa.tcpa > 0
            val isDangerous = hasCpaWarning || (extras.threatLevel >= 2) || (ais.isMovable() && isThreatDist && isThreatTime && ais.cpa.t1 >= 0 && ais.cpa.t2 >= 0)

            if (!isDangerous && !hasCpaWarning) continue

            val tcpaSec = ais.cpa.tcpa * 3600.0

            // 1. Calculate & project target vessel future position at t = TCPA
            val targetCpaLat: Double
            val targetCpaLon: Double
            val cpaPos2 = ais.cpa.cpaPos2
            if (cpaPos2 != null && cpaPos2.latitude != net.osmand.shared.aistracker.AisObjectConstants.INVALID_LAT && cpaPos2.longitude != net.osmand.shared.aistracker.AisObjectConstants.INVALID_LON) {
                targetCpaLat = cpaPos2.latitude
                targetCpaLon = cpaPos2.longitude
            } else if (ais.cpa.valid && ais.cpa.tcpa > 0 && ais.sog > 0 && ais.cog != net.osmand.shared.aistracker.AisObjectConstants.INVALID_COG) {
                val speedMs = ais.sog * 1852.0 / 3600.0
                val distM = speedMs * tcpaSec
                val dest = net.osmand.util.MapUtils.rhumbDestinationPoint(pos.latitude, pos.longitude, distM, ais.cog)
                targetCpaLat = dest.latitude
                targetCpaLon = dest.longitude
            } else {
                continue
            }

            // 2. Calculate own-vessel future position at t = TCPA
            var ownCpaLat: Double? = null
            var ownCpaLon: Double? = null
            val cpaPos1 = ais.cpa.cpaPos1
            if (cpaPos1 != null && cpaPos1.latitude != net.osmand.shared.aistracker.AisObjectConstants.INVALID_LAT && cpaPos1.longitude != net.osmand.shared.aistracker.AisObjectConstants.INVALID_LON) {
                ownCpaLat = cpaPos1.latitude
                ownCpaLon = cpaPos1.longitude
            } else if (ownLat != null && ownLon != null && tcpaSec > 0) {
                val ownDistM = ownSogMps * tcpaSec
                val ownDest = net.osmand.util.MapUtils.rhumbDestinationPoint(ownLat, ownLon, ownDistM, ownCogDeg)
                ownCpaLat = ownDest.latitude
                ownCpaLon = ownDest.longitude
            }

            val targetStartX = tileBox.getPixXFromLatLon(pos.latitude, pos.longitude)
            val targetStartY = tileBox.getPixYFromLatLon(pos.latitude, pos.longitude)
            val targetEndX = tileBox.getPixXFromLatLon(targetCpaLat, targetCpaLon)
            val targetEndY = tileBox.getPixYFromLatLon(targetCpaLat, targetCpaLon)

            // 3. Draw dashed danger vector from target to its CPA position
            cpaVectorPath.reset()
            cpaVectorPath.moveTo(targetStartX, targetStartY)
            cpaVectorPath.lineTo(targetEndX, targetEndY)
            canvas.drawPath(cpaVectorPath, cpaVectorPaint)

            // 4. Draw connecting segment between own-vessel at TCPA and target at TCPA
            if (ownCpaLat != null && ownCpaLon != null) {
                val ownEndX = tileBox.getPixXFromLatLon(ownCpaLat, ownCpaLon)
                val ownEndY = tileBox.getPixYFromLatLon(ownCpaLat, ownCpaLon)

                cpaConnectingPath.reset()
                cpaConnectingPath.moveTo(ownEndX, ownEndY)
                cpaConnectingPath.lineTo(targetEndX, targetEndY)
                canvas.drawPath(cpaConnectingPath, cpaConnectingPaint)

                // 5. Draw pulsing collision warning diamond at the CPA intersection/midpoint
                val midX = (ownEndX + targetEndX) / 2f
                val midY = (ownEndY + targetEndY) / 2f
                val diamondSize = 8f * density * (1f + 0.3f * pulseFactor.coerceAtLeast(0f))

                cpaDiamondPath.reset()
                cpaDiamondPath.moveTo(midX, midY - diamondSize)
                cpaDiamondPath.lineTo(midX + diamondSize, midY)
                cpaDiamondPath.lineTo(midX, midY + diamondSize)
                cpaDiamondPath.lineTo(midX - diamondSize, midY)
                cpaDiamondPath.close()

                canvas.drawPath(cpaDiamondPath, cpaDiamondPaint)
                canvas.drawPath(cpaDiamondPath, cpaDiamondStrokePaint)
            }

            // 6. Draw pulsating red ring (12dp diameter) at the CPA coordinate
            canvas.drawCircle(targetEndX, targetEndY, pulseRadius, cpaRingPaint)
            canvas.drawCircle(targetEndX, targetEndY, pulseRadius, cpaRingStrokePaint)
            canvas.drawCircle(targetEndX, targetEndY, 2.5f * density, cpaRingCenterPaint)

            // 7. Draw CPA distance badge: "CPA: x.x NM"
            val cpaDistNm = if (ais.cpa.valid && ais.cpa.cpa != net.osmand.shared.aistracker.AisObjectConstants.INVALID_CPA) ais.cpa.cpa else 0f
            val label = getCpaLabel(ais.mmsi, cpaDistNm)

            val textWidth = cpaBadgeTextPaint.measureText(label)
            val textHeight = cpaBadgeTextPaint.textSize
            val badgePaddingH = 8f * density
            val badgePaddingV = 4f * density
            val badgeWidth = textWidth + (badgePaddingH * 2f)
            val badgeHeight = textHeight + (badgePaddingV * 2f)

            val badgeLeft = targetEndX + pulseRadius + (6f * density)
            val badgeTop = targetEndY - (badgeHeight / 2f)

            cpaBadgeRect.set(badgeLeft, badgeTop, badgeLeft + badgeWidth, badgeTop + badgeHeight)
            canvas.drawRoundRect(cpaBadgeRect, 6f * density, 6f * density, cpaBadgeBgPaint)
            canvas.drawRoundRect(cpaBadgeRect, 6f * density, 6f * density, cpaBadgeStrokePaint)
            canvas.drawText(label, badgeLeft + badgePaddingH, badgeTop + badgePaddingV + (textHeight * 0.8f), cpaBadgeTextPaint)
        }
    }

    private val sartCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFF1744.toInt()
    }
    private val sartFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40FF1744.toInt()
    }
    private val sartCrosshairPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = 0xFFFF1744.toInt()
    }

    private val strobeRingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFFFF1744.toInt()
    }
    private val strobeFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x40FF1744.toInt()
    }
    private val strobeRayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        strokeCap = Paint.Cap.ROUND
        color = 0xFFFF1744.toInt()
    }

    var predictiveHorizonMinutes: Int = 0
    var isRelativeMotionVectorMode: Boolean = false

    private val relativeVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(8f, 6f), 0f)
        color = 0xFFFFB300.toInt()
    }
    private val relativeArrowHeadPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xFFFFB300.toInt()
    }
    private val relativeArrowPath = Path()

    private val predictiveVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(10f, 6f), 0f)
        color = 0xFF00E5FF.toInt()
    }
    private val predictiveGhostPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x6000E5FF.toInt()
    }
    private val predictiveGhostStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF00E5FF.toInt()
    }
    private val predictiveHaloPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3.5f
        color = 0xFFFF1744.toInt()
    }
    private val predictiveGhostPath = Path()

    data class ParallelIndexLine(
        val originLat: Double,
        val originLon: Double,
        val bearingTrueDeg: Double,
        val rangeNm: Double,
        val isStarboard: Boolean,
        val label: String
    ) {
        val anchorLat: Double get() = originLat
        val anchorLon: Double get() = originLon
        val bearingDeg: Double get() = bearingTrueDeg
        val rangeMeters: Double get() = rangeNm * 1852.0
        val isEnabled: Boolean = true
    }

    var piLine1: ParallelIndexLine? = null
    var piLine2: ParallelIndexLine? = null

    private val piLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        pathEffect = DashPathEffect(floatArrayOf(14f, 8f), 0f)
        color = 0xFF00E5FF.toInt()
    }
    private val piAnchorLinkPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 1.5f
        pathEffect = DashPathEffect(floatArrayOf(6f, 6f), 0f)
        color = 0x8000E5FF.toInt()
    }
    private val piAnchorCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = 0xFF00E5FF.toInt()
    }
    private val piTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textSize = 12f
        typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        color = 0xFF00E5FF.toInt()
    }

    private val atonDiamondPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0xDDFFB300.toInt()
    }
    private val atonStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = Color.WHITE
    }
    private val atonCrossPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = Color.BLACK
    }
    private val atonDiamondPath = Path()

    private val guardZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(16f, 10f), 0f)
        color = 0xFFFFA000.toInt()
    }
    private val guardZoneFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x12FFA000.toInt()
    }
    private val guardZoneAlertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = 0xFFFF1744.toInt()
    }

    private val collisionWedgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = 0x35FF1744.toInt()
    }
    private val collisionWedgeStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2f
        pathEffect = DashPathEffect(floatArrayOf(12f, 8f), 0f)
        color = 0xFFFF1744.toInt()
    }
    private val evasionArcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 3f
        color = 0xFF00E676.toInt()
    }
    private val collisionWedgeRect = RectF()
    private val collisionWedgePath = Path()

    private fun drawAisCollisionWedges(canvas: Canvas, tileBox: RotatedTileBox) {
        val ownLoc = plugin.application.locationProvider.lastKnownLocation ?: return
        val ownSpeedMps = (ownLoc.speed.toDouble()).coerceAtLeast(1.0)
        val ownLat = ownLoc.latitude
        val ownLon = ownLoc.longitude

        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()
        if (aisObjects.isEmpty()) return

        val isNight = NauticalPlugin.isNightVision(application)
        val density = context.resources.displayMetrics.density
        val cx = tileBox.getPixXFromLatLon(ownLat, ownLon)
        val cy = tileBox.getPixYFromLatLon(ownLat, ownLon)
        val radius = 55f * density

        val safeCpaM = plugin.aisCpaWarningDistance.get().toDouble().coerceIn(0.1, 5.0) * 1852.0

        collisionWedgePaint.color = if (isNight) 0x40FF1744.toInt() else 0x35FF1744.toInt()
        collisionWedgeStrokePaint.color = 0xFFFF1744.toInt()
        evasionArcPaint.color = if (isNight) 0x8080FF8A.toInt() else 0x9000E676.toInt()

        collisionWedgeRect.set(cx - radius, cy - radius, cx + radius, cy + radius)

        for (ais in aisObjects) {
            if (isOwnObject(ais) || isOwnObjectHidden(ais)) continue
            val pos = ais.position ?: continue
            val dist = net.osmand.util.MapUtils.getDistance(ownLat, ownLon, pos.latitude, pos.longitude)
            if (dist > 6.0 * 1852.0) continue // Only evaluate targets within 6 NM

            val cogDeg = ais.cog ?: continue
            val sogKn = ais.sog ?: continue
            if (sogKn < 0.5) continue
            val sogMps = sogKn * 0.514444

            val targetBearing = (KMapUtils.getBearing(ownLat, ownLon, pos.latitude, pos.longitude).toDegrees() + 360.0) % 360.0

            var minCollisionAngle: Double? = null
            var maxCollisionAngle: Double? = null

            val targetVx = sogMps * kotlin.math.sin(Math.toRadians(cogDeg))
            val targetVy = sogMps * kotlin.math.cos(Math.toRadians(cogDeg))
            val dx = dist * kotlin.math.sin(Math.toRadians(targetBearing))
            val dy = dist * kotlin.math.cos(Math.toRadians(targetBearing))

            for (hDeg in 0 until 360 step 3) {
                val ownVx = ownSpeedMps * kotlin.math.sin(Math.toRadians(hDeg.toDouble()))
                val ownVy = ownSpeedMps * kotlin.math.cos(Math.toRadians(hDeg.toDouble()))
                val relVx = ownVx - targetVx
                val relVy = ownVy - targetVy
                val relV2 = relVx * relVx + relVy * relVy

                if (relV2 > 0.01) {
                    val tcpaSec = -(dx * relVx + dy * relVy) / relV2
                    if (tcpaSec > 0 && tcpaSec < 1800.0) {
                        val cpaX = dx + relVx * tcpaSec
                        val cpaY = dy + relVy * tcpaSec
                        val cpaDist = kotlin.math.sqrt(cpaX * cpaX + cpaY * cpaY)
                        if (cpaDist < safeCpaM) {
                            if (minCollisionAngle == null || hDeg < minCollisionAngle) minCollisionAngle = hDeg.toDouble()
                            if (maxCollisionAngle == null || hDeg > maxCollisionAngle) maxCollisionAngle = hDeg.toDouble()
                        }
                    }
                }
            }

            if (minCollisionAngle != null && maxCollisionAngle != null) {
                val sweep = (maxCollisionAngle - minCollisionAngle).toFloat().coerceAtLeast(6f)
                val startAngle = (minCollisionAngle - 90.0 - tileBox.rotate).toFloat()

                collisionWedgePath.rewind()
                collisionWedgePath.moveTo(cx, cy)
                collisionWedgePath.arcTo(collisionWedgeRect, startAngle, sweep)
                collisionWedgePath.close()

                canvas.drawPath(collisionWedgePath, collisionWedgePaint)
                canvas.drawArc(collisionWedgeRect, startAngle, sweep, false, collisionWedgeStrokePaint)
                canvas.drawArc(collisionWedgeRect, startAngle + sweep, 360f - sweep, false, evasionArcPaint)
                break
            }
        }
    }

    private fun drawGuardZoneRing(canvas: Canvas, tileBox: RotatedTileBox) {
        val ownLoc = plugin.application.locationProvider.lastKnownLocation ?: return
        val radiusNm = plugin.aisGuardZoneRadius.get().toDouble().coerceIn(0.25, 10.0)
        val radiusM = radiusNm * 1852.0

        val cx = tileBox.getPixXFromLatLon(ownLoc.latitude, ownLoc.longitude)
        val cy = tileBox.getPixYFromLatLon(ownLoc.latitude, ownLoc.longitude)
        val northP = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(ownLoc.latitude, ownLoc.longitude, radiusM, 0.0)
        val northY = tileBox.getPixYFromLatLon(northP.latitude, northP.longitude)
        val pixRadius = kotlin.math.abs(cy - northY).coerceAtLeast(10f)

        val isNight = NauticalPlugin.isNightVision(application)
        val aisObjects = plugin.aisManager?.getAisObjects() ?: emptyList()

        var hasIntrusion = false
        for (ais in aisObjects) {
            if (isOwnObject(ais) || isOwnObjectHidden(ais)) continue
            val pos = ais.position ?: continue
            val dist = net.osmand.util.MapUtils.getDistance(ownLoc.latitude, ownLoc.longitude, pos.latitude, pos.longitude)
            if (dist <= radiusM) {
                hasIntrusion = true
                break
            }
        }

        if (isNight) {
            guardZonePaint.color = 0x80B71C1C.toInt()
            guardZoneFillPaint.color = if (hasIntrusion) 0x25FF1744.toInt() else 0x10FF1744.toInt()
            guardZoneAlertPaint.color = 0xFFFF1744.toInt()
        } else {
            guardZonePaint.color = 0xFFFFA000.toInt()
            guardZoneFillPaint.color = if (hasIntrusion) 0x20FF1744.toInt() else 0x12FFA000.toInt()
            guardZoneAlertPaint.color = 0xFFFF1744.toInt()
        }

        canvas.drawCircle(cx, cy, pixRadius, guardZoneFillPaint)

        if (hasIntrusion) {
            val pulse = (kotlin.math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2.0) * 0.5 + 0.5).toFloat()
            guardZoneAlertPaint.alpha = ((0.5f + 0.5f * pulse) * 255).toInt()
            canvas.drawCircle(cx, cy, pixRadius, guardZoneAlertPaint)
        } else {
            canvas.drawCircle(cx, cy, pixRadius, guardZonePaint)
        }
    }

    private fun drawSpecialAisSymbols(canvas: Canvas, tileBox: RotatedTileBox) {
        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()
        if (aisObjects.isEmpty()) return

        val isNight = NauticalPlugin.isNightVision(application)
        val density = context.resources.displayMetrics.density
        val now = System.currentTimeMillis()
        val pulseFactor = (kotlin.math.sin((now % 1000L) / 1000.0 * Math.PI * 2.0) * 0.5 + 0.5).toFloat()

        sartCirclePaint.color = 0xFFFF1744.toInt()
        sartFillPaint.color = if (isNight) 0x30FF1744.toInt() else 0x40FF1744.toInt()
        sartCrosshairPaint.color = if (isNight) 0xFFFF8A80.toInt() else 0xFFFF1744.toInt()
        atonStrokePaint.color = if (isNight) 0xFFFF8A80.toInt() else Color.WHITE
        atonCrossPaint.color = if (isNight) 0xEE120000.toInt() else Color.BLACK

        for (ais in aisObjects) {
            if (isOwnObjectHidden(ais)) continue
            val pos = ais.position ?: continue

            val isSart = (ais.objectClass == net.osmand.shared.aistracker.AisObjType.AIS_SART) || (ais.mmsi in 970000000..974999999)
            val isAton = (ais.objectClass == net.osmand.shared.aistracker.AisObjType.AIS_ATON) || (ais.objectClass == net.osmand.shared.aistracker.AisObjType.AIS_ATON_VIRTUAL)

            if (!isSart && !isAton) continue

            val x = tileBox.getPixXFromLatLon(pos.latitude, pos.longitude)
            val y = tileBox.getPixYFromLatLon(pos.latitude, pos.longitude)

            if (isSart) {
                val radius = (14f + 6f * pulseFactor) * density
                strobeRingPaint.color = 0xFFFF1744.toInt()
                strobeFillPaint.color = if (isNight) 0x40FF1744.toInt() else 0x50FF1744.toInt()
                strobeRayPaint.color = if (isNight) 0xFFFF8A80.toInt() else 0xFFFF1744.toInt()

                canvas.drawCircle(x, y, radius, strobeFillPaint)
                canvas.drawCircle(x, y, radius, strobeRingPaint)
                canvas.drawCircle(x, y, radius * 0.5f, strobeRingPaint)

                // 8 radial strobe rays extending outward
                val rayLen = 28f * density
                for (angleDeg in 0 until 360 step 45) {
                    val rad = Math.toRadians(angleDeg.toDouble())
                    val rx1 = x + (radius * kotlin.math.sin(rad)).toFloat()
                    val ry1 = y - (radius * kotlin.math.cos(rad)).toFloat()
                    val rx2 = x + (rayLen * kotlin.math.sin(rad)).toFloat()
                    val ry2 = y - (rayLen * kotlin.math.cos(rad)).toFloat()
                    canvas.drawLine(rx1, ry1, rx2, ry2, strobeRayPaint)
                }

                canvas.drawLine(x - radius * 1.35f, y, x + radius * 1.35f, y, sartCrosshairPaint)
                canvas.drawLine(x, y - radius * 1.35f, x, y + radius * 1.35f, sartCrosshairPaint)
            } else if (isAton) {
                val size = 9f * density
                atonDiamondPath.reset()
                atonDiamondPath.moveTo(x, y - size)
                atonDiamondPath.lineTo(x + size, y)
                atonDiamondPath.lineTo(x, y + size)
                atonDiamondPath.lineTo(x - size, y)
                atonDiamondPath.close()

                atonDiamondPaint.color = if (isNight) {
                    0xDDFF1744.toInt()
                } else if (ais.objectClass == net.osmand.shared.aistracker.AisObjType.AIS_ATON_VIRTUAL) {
                    0xDD00E5FF.toInt()
                } else {
                    0xDDFFB300.toInt()
                }

                canvas.drawPath(atonDiamondPath, atonDiamondPaint)
                canvas.drawPath(atonDiamondPath, atonStrokePaint)
                canvas.drawLine(x - size * 0.6f, y, x + size * 0.6f, y, atonCrossPaint)
                canvas.drawLine(x, y - size * 0.6f, x, y + size * 0.6f, atonCrossPaint)
            }
        }
    }

    private fun drawPredictiveForwardHorizons(canvas: Canvas, tileBox: RotatedTileBox) {
        if (predictiveHorizonMinutes <= 0) return
        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()

        val isNight = NauticalPlugin.isNightVision(application)
        val density = context.resources.displayMetrics.density
        val deltaSeconds = predictiveHorizonMinutes * 60.0

        val ownLoc = application.locationProvider.lastKnownLocation
        val liveState = NauticalPlugin.engine?.getCurrentState()
        val ownLat = liveState?.latitude ?: ownLoc?.latitude ?: return
        val ownLon = liveState?.longitude ?: ownLoc?.longitude ?: return
        val ownSogMps = (liveState?.speedOverGround ?: (ownLoc?.speed?.toDouble() ?: 0.0))
        val ownCogDeg = liveState?.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: (ownLoc?.bearing?.toDouble() ?: 0.0)

        predictiveVectorPaint.color = if (isNight) 0x80FF8A80.toInt() else 0x8000E5FF.toInt()
        predictiveGhostPaint.color = if (isNight) 0x50FF1744.toInt() else 0x5000E5FF.toInt()
        predictiveGhostStrokePaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFF00E5FF.toInt()
        predictiveHaloPaint.color = 0xFFFF1744.toInt()

        // 1. Own-vessel projected forward position
        val ownFutureP = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(ownLat, ownLon, ownSogMps * deltaSeconds, ownCogDeg)
        val ox1 = tileBox.getPixXFromLatLon(ownLat, ownLon)
        val oy1 = tileBox.getPixYFromLatLon(ownLat, ownLon)
        val ox2 = tileBox.getPixXFromLatLon(ownFutureP.latitude, ownFutureP.longitude)
        val oy2 = tileBox.getPixYFromLatLon(ownFutureP.latitude, ownFutureP.longitude)

        canvas.drawLine(ox1, oy1, ox2, oy2, predictiveVectorPaint)
        drawGhostVesselIcon(canvas, ox2, oy2, ownCogDeg, tileBox.rotate, density)

        val safeCpaM = plugin.aisCpaWarningDistance.get().toDouble().coerceIn(0.1, 5.0) * 1852.0
        val pulse = (kotlin.math.sin((System.currentTimeMillis() % 1000L) / 1000.0 * Math.PI * 2.0) * 0.5 + 0.5).toFloat()

        // 2. Targets projected forward positions
        for (ais in aisObjects) {
            if (isOwnObjectHidden(ais)) continue
            val pos = ais.position ?: continue
            val sogKn = ais.sog ?: 0.0
            val cogDeg = ais.cog ?: 0.0
            val sogMps = sogKn * 0.514444

            val tx1 = tileBox.getPixXFromLatLon(pos.latitude, pos.longitude)
            val ty1 = tileBox.getPixYFromLatLon(pos.latitude, pos.longitude)
            val targetFutureP = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(pos.latitude, pos.longitude, sogMps * deltaSeconds, cogDeg)
            val tx2 = tileBox.getPixXFromLatLon(targetFutureP.latitude, targetFutureP.longitude)
            val ty2 = tileBox.getPixYFromLatLon(targetFutureP.latitude, targetFutureP.longitude)

            canvas.drawLine(tx1, ty1, tx2, ty2, predictiveVectorPaint)
            drawGhostVesselIcon(canvas, tx2, ty2, cogDeg, tileBox.rotate, density)

            val futureDistM = net.osmand.util.MapUtils.getDistance(ownFutureP.latitude, ownFutureP.longitude, targetFutureP.latitude, targetFutureP.longitude)
            if (futureDistM < safeCpaM) {
                val haloRadius = (16f + 4f * pulse) * density
                predictiveHaloPaint.alpha = ((0.5f + 0.5f * pulse) * 255).toInt()
                canvas.drawCircle(ox2, oy2, haloRadius, predictiveHaloPaint)
                canvas.drawCircle(tx2, ty2, haloRadius, predictiveHaloPaint)
            }
        }
    }

    private fun drawGhostVesselIcon(canvas: Canvas, cx: Float, cy: Float, headingDeg: Double, mapRotate: Float, density: Float) {
        val rad = Math.toRadians(headingDeg - mapRotate)
        val vSize = 10f * density
        val sinA = kotlin.math.sin(rad).toFloat()
        val cosA = kotlin.math.cos(rad).toFloat()

        predictiveGhostPath.rewind()
        predictiveGhostPath.moveTo(cx + vSize * sinA, cy - vSize * cosA)
        val rightAngle = rad + Math.toRadians(140.0)
        predictiveGhostPath.lineTo((cx + vSize * kotlin.math.sin(rightAngle)).toFloat(), (cy - vSize * kotlin.math.cos(rightAngle)).toFloat())
        predictiveGhostPath.lineTo((cx - vSize * 0.4f * sinA), (cy + vSize * 0.4f * cosA))
        val leftAngle = rad - Math.toRadians(140.0)
        predictiveGhostPath.lineTo((cx + vSize * kotlin.math.sin(leftAngle)).toFloat(), (cy - vSize * kotlin.math.cos(leftAngle)).toFloat())
        predictiveGhostPath.close()

        canvas.drawPath(predictiveGhostPath, predictiveGhostPaint)
        canvas.drawPath(predictiveGhostPath, predictiveGhostStrokePaint)
    }

    private fun drawRelativeMotionVectors(canvas: Canvas, tileBox: RotatedTileBox) {
        if (!isRelativeMotionVectorMode) return
        val manager = plugin.aisManager ?: return
        val aisObjects = manager.getAisObjects()
        if (aisObjects.isEmpty()) return

        val isNight = NauticalPlugin.isNightVision(application)
        val density = context.resources.displayMetrics.density

        val ownLoc = application.locationProvider.lastKnownLocation
        val liveState = NauticalPlugin.engine?.getCurrentState()
        val ownLat = liveState?.latitude ?: ownLoc?.latitude ?: return
        val ownLon = liveState?.longitude ?: ownLoc?.longitude ?: return
        val ownSogMps = (liveState?.speedOverGround ?: (ownLoc?.speed?.toDouble() ?: 0.0))
        val ownCogDeg = liveState?.courseOverGroundTrue?.let { Math.toDegrees(it) } ?: (ownLoc?.bearing?.toDouble() ?: 0.0)

        val ownVx = ownSogMps * kotlin.math.sin(Math.toRadians(ownCogDeg))
        val ownVy = ownSogMps * kotlin.math.cos(Math.toRadians(ownCogDeg))

        relativeVectorPaint.color = if (isNight) 0xFFFF8A80.toInt() else 0xFFFFB300.toInt()
        relativeArrowHeadPaint.color = if (isNight) 0xFFFF1744.toInt() else 0xFFFF8F00.toInt()

        val vectorDurationSec = 360.0 // 6-minute standard motion vector

        for (ais in aisObjects) {
            if (isOwnObjectHidden(ais)) continue
            val pos = ais.position ?: continue
            val sogKn = ais.sog ?: 0.0
            val cogDeg = ais.cog ?: 0.0
            val sogMps = sogKn * 0.514444

            val targetVx = sogMps * kotlin.math.sin(Math.toRadians(cogDeg))
            val targetVy = sogMps * kotlin.math.cos(Math.toRadians(cogDeg))

            val relVx = targetVx - ownVx
            val relVy = targetVy - ownVy
            val relSpeedMps = kotlin.math.sqrt(relVx * relVx + relVy * relVy)

            if (relSpeedMps < 0.1) continue

            val relHeadingDeg = (Math.toDegrees(kotlin.math.atan2(relVx, relVy)) + 360.0) % 360.0
            val relDistM = relSpeedMps * vectorDurationSec
            val futurePos = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(pos.latitude, pos.longitude, relDistM, relHeadingDeg)

            val x1 = tileBox.getPixXFromLatLon(pos.latitude, pos.longitude)
            val y1 = tileBox.getPixYFromLatLon(pos.latitude, pos.longitude)
            val x2 = tileBox.getPixXFromLatLon(futurePos.latitude, futurePos.longitude)
            val y2 = tileBox.getPixYFromLatLon(futurePos.latitude, futurePos.longitude)

            canvas.drawLine(x1, y1, x2, y2, relativeVectorPaint)

            // Arrow head at relative vector end
            val headLen = 8f * density
            val angleRad = Math.toRadians(relHeadingDeg - tileBox.rotate)
            val leftRad = angleRad - Math.toRadians(150.0)
            val rightRad = angleRad + Math.toRadians(150.0)

            relativeArrowPath.rewind()
            relativeArrowPath.moveTo(x2, y2)
            relativeArrowPath.lineTo((x2 + headLen * kotlin.math.sin(leftRad)).toFloat(), (y2 - headLen * kotlin.math.cos(leftRad)).toFloat())
            relativeArrowPath.lineTo((x2 + headLen * kotlin.math.sin(rightRad)).toFloat(), (y2 - headLen * kotlin.math.cos(rightRad)).toFloat())
            relativeArrowPath.close()

            canvas.drawPath(relativeArrowPath, relativeArrowHeadPaint)
        }
    }

    private fun drawParallelIndexLines(canvas: Canvas, tileBox: RotatedTileBox) {
        val isNight = NauticalPlugin.isNightVision(application)
        val density = context.resources.displayMetrics.density

        val piColor = if (isNight) 0xFFFF1744.toInt() else 0xFF00E5FF.toInt()
        val linkColor = if (isNight) 0x80FF1744.toInt() else 0x8000E5FF.toInt()
        val textColor = if (isNight) 0xFFFF8A80.toInt() else 0xFF00E5FF.toInt()

        piLinePaint.color = piColor
        piAnchorLinkPaint.color = linkColor
        piAnchorCirclePaint.color = piColor
        piTextPaint.color = textColor
        piTextPaint.textSize = 11f * density

        val lines = listOfNotNull(piLine1, piLine2)
        for (pi in lines) {
            if (pi.anchorLat == 0.0 || pi.anchorLon == 0.0) continue

            val offsetAngle = if (pi.isStarboard) (pi.bearingDeg + 90.0) % 360.0 else (pi.bearingDeg - 90.0 + 360.0) % 360.0
            val offsetP = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(pi.anchorLat, pi.anchorLon, pi.rangeMeters, offsetAngle)

            val corridorLenM = 15000.0 // 15 km reference length
            val pStart = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(offsetP.latitude, offsetP.longitude, corridorLenM, (pi.bearingDeg + 180.0) % 360.0)
            val pEnd = net.osmand.shared.util.KMapUtils.rhumbDestinationPoint(offsetP.latitude, offsetP.longitude, corridorLenM, pi.bearingDeg)

            val ax = tileBox.getPixXFromLatLon(pi.anchorLat, pi.anchorLon)
            val ay = tileBox.getPixYFromLatLon(pi.anchorLat, pi.anchorLon)
            val ox = tileBox.getPixXFromLatLon(offsetP.latitude, offsetP.longitude)
            val oy = tileBox.getPixYFromLatLon(offsetP.latitude, offsetP.longitude)
            val sx = tileBox.getPixXFromLatLon(pStart.latitude, pStart.longitude)
            val sy = tileBox.getPixYFromLatLon(pStart.latitude, pStart.longitude)
            val ex = tileBox.getPixXFromLatLon(pEnd.latitude, pEnd.longitude)
            val ey = tileBox.getPixYFromLatLon(pEnd.latitude, pEnd.longitude)

            // Anchor point circle & link to corridor
            canvas.drawCircle(ax, ay, 5f * density, piAnchorCirclePaint)
            canvas.drawLine(ax, ay, ox, oy, piAnchorLinkPaint)

            // Parallel Index Corridor line
            canvas.drawLine(sx, sy, ex, ey, piLinePaint)

            // Label
            val rangeNm = pi.rangeMeters / 1852.0
            val sideStr = if (pi.isStarboard) "STBD" else "PORT"
            val labelText = String.format(Locale.US, "%s: %.0f°T / %.2fNM %s", pi.label, pi.bearingDeg, rangeNm, sideStr)
            canvas.drawText(labelText, ox + 8f * density, oy - 6f * density, piTextPaint)
        }
    }

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        drawGuardZoneRing(canvas, tileBox)
        drawAisCollisionWedges(canvas, tileBox)
        drawPredictiveForwardHorizons(canvas, tileBox)
        drawRelativeMotionVectors(canvas, tileBox)
        drawParallelIndexLines(canvas, tileBox)
        drawTracks(canvas, tileBox)
        drawCpaInterceptVectors(canvas, tileBox)
        drawSpecialAisSymbols(canvas, tileBox)
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
        drawGuardZoneRing(canvas, tileBox)
        drawAisCollisionWedges(canvas, tileBox)
        drawPredictiveForwardHorizons(canvas, tileBox)
        drawRelativeMotionVectors(canvas, tileBox)
        drawParallelIndexLines(canvas, tileBox)
        drawTracks(canvas, tileBox)
        drawCpaInterceptVectors(canvas, tileBox)
        drawSpecialAisSymbols(canvas, tileBox)
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
