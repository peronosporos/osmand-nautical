package net.osmand.plus.plugins.nautical.laylines.ui

import android.content.Context
import android.content.SharedPreferences
import android.graphics.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.laylines.viewmodel.LaylineUiState
import net.osmand.plus.views.layers.base.OsmandMapLayer
import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * Custom map layer for rendering tactical laylines and wind shifts.
 */
class SailingLaylinesMapLayer(context: Context) : OsmandMapLayer(context), SharedPreferences.OnSharedPreferenceChangeListener {

    private var uiState: LaylineUiState? = null

    private val portPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val stbdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 8f
    }

    private val windShiftPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val portConePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x30FF1744.toInt() // Subtle red port header sector (#30FF1744)
        style = Paint.Style.FILL
    }

    private val stbdConePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x3000E676.toInt() // Subtle green starboard lift sector (#3000E676)
        style = Paint.Style.FILL
    }

    private val portConePath = Path()
    private val stbdConePath = Path()

    private val dashedEffect = DashPathEffect(floatArrayOf(20f, 10f), 0f)
    private val windShiftRect = RectF()

    private class LaylineCache {
        var lastBoatLat: Double? = null
        var lastBoatLon: Double? = null
        var lastInterLat: Double? = null
        var lastInterLon: Double? = null
        var lastTargetLat: Double? = null
        var lastTargetLon: Double? = null
        var cachedLatLons: List<net.osmand.data.LatLon> = emptyList()

        var lastZoom: Float? = null
        var lastCenter31X: Int? = null
        var lastCenter31Y: Int? = null
        var lastRotate: Float? = null
        val cachedPath = Path()

        fun needsLatLonUpdate(boatLat: Double, boatLon: Double, interLat: Double, interLon: Double, targetLat: Double, targetLon: Double): Boolean {
            return abs(boatLat - (lastBoatLat ?: 0.0)) > 0.00001 ||
                    abs(boatLon - (lastBoatLon ?: 0.0)) > 0.00001 ||
                    abs(interLat - (lastInterLat ?: 0.0)) > 0.00001 ||
                    abs(interLon - (lastInterLon ?: 0.0)) > 0.00001 ||
                    abs(targetLat - (lastTargetLat ?: 0.0)) > 0.00001 ||
                    abs(targetLon - (lastTargetLon ?: 0.0)) > 0.00001
        }

        fun needsPixelUpdate(tileBox: RotatedTileBox): Boolean {
            val zoom = tileBox.zoom + tileBox.zoomFloatPart.toFloat()
            return abs(zoom - (lastZoom ?: 0f)) > 0.1f ||
                    tileBox.center31X != lastCenter31X ||
                    tileBox.center31Y != lastCenter31Y ||
                    abs(tileBox.rotate - (lastRotate ?: 0f)) > 0.01f
        }

        fun updateLatLons(boatLat: Double, boatLon: Double, interLat: Double, interLon: Double, targetLat: Double, targetLon: Double) {
            lastBoatLat = boatLat
            lastBoatLon = boatLon
            lastInterLat = interLat
            lastInterLon = interLon
            lastTargetLat = targetLat
            lastTargetLon = targetLon
            
            val list = mutableListOf<net.osmand.data.LatLon>()
            list.add(net.osmand.data.LatLon(boatLat, boatLon))
            
            val segments = 50
            for (i in 1..segments) {
                val coeff = i.toDouble() / segments
                list.add(net.osmand.util.MapUtils.calculateIntermediatePoint(boatLat, boatLon, interLat, interLon, coeff))
            }
            for (i in 1..segments) {
                val coeff = i.toDouble() / segments
                list.add(net.osmand.util.MapUtils.calculateIntermediatePoint(interLat, interLon, targetLat, targetLon, coeff))
            }
            cachedLatLons = list
        }

        fun updatePixels(tileBox: RotatedTileBox) {
            val zoom = tileBox.zoom + tileBox.zoomFloatPart.toFloat()
            lastZoom = zoom
            lastCenter31X = tileBox.center31X
            lastCenter31Y = tileBox.center31Y
            lastRotate = tileBox.rotate

            cachedPath.reset()
            if (cachedLatLons.isNotEmpty()) {
                val start = cachedLatLons.first()
                cachedPath.moveTo(tileBox.getPixXFromLatLon(start.latitude, start.longitude), 
                                  tileBox.getPixYFromLatLon(start.latitude, start.longitude))
                for (i in 1 until cachedLatLons.size) {
                    val p = cachedLatLons[i]
                    cachedPath.lineTo(tileBox.getPixXFromLatLon(p.latitude, p.longitude), 
                                      tileBox.getPixYFromLatLon(p.latitude, p.longitude))
                }
            }
        }
    }

    private val portCache = LaylineCache()
    private val stbdCache = LaylineCache()

    // Colors
    private val colorFetchable = "#4CAF50".toColorInt() // Green
    private val colorTackRequired = "#F44336".toColorInt() // Red
    private val colorWindShift = "#8000BCD4".toColorInt() // Semi-transparent Cyan

    private var cachedSweepAngle = 0f
    private var cachedStartOfMaxGapPlusGap = 0f
    private var lastWindHistorySize = -1

    fun updateState(state: LaylineUiState) {
        this.uiState = state
        updateWindShiftCache()
    }

    override fun initLayer(view: net.osmand.plus.views.OsmandMapTileView) {
        super.initLayer(view)
        val app = context.applicationContext as OsmandApplication
        app.getSharedPreferences(net.osmand.plus.settings.backend.OsmandSettings.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(this)
    }

    override fun destroyLayer() {
        val app = context.applicationContext as OsmandApplication
        app.getSharedPreferences(net.osmand.plus.settings.backend.OsmandSettings.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(this)
        super.destroyLayer()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val app = context.applicationContext as OsmandApplication
        val settings = app.settings
        if (key == settings.NAUTICAL_SHOW_LAYLINES.id || 
            key == settings.NAUTICAL_SHOW_WIND_SHIFTS.id ||
            key == settings.NAUTICAL_LAYLINES_TACK_ANGLE.id) {
            view.refreshMap()
        }
    }

    private fun updateWindShiftCache() {
        val engine = NauticalPlugin.engine ?: return
        val history = engine.getHistory(SignalKPaths.ENV_WIND_DIRECTION_TRUE)
        if (history.size == lastWindHistorySize) return
        lastWindHistorySize = history.size
        if (history.isEmpty()) return

        val sortedAngles = history.map {
            val deg = Math.toDegrees(it.first)
            (deg % 360.0 + 360.0) % 360.0
        }.sorted()

        var maxGap = 0.0
        var startOfMaxGap = sortedAngles.last()

        for (i in sortedAngles.indices) {
            val a1 = sortedAngles[i]
            val a2 = if (i + 1 < sortedAngles.size) sortedAngles[i + 1] else sortedAngles[0] + 360.0
            val gap = a2 - a1
            if (gap > maxGap) {
                maxGap = gap
                startOfMaxGap = a1
            }
        }

        cachedSweepAngle = (360.0 - maxGap).coerceAtLeast(1.0).toFloat()
        cachedStartOfMaxGapPlusGap = (startOfMaxGap + maxGap).toFloat()
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val plugin = NauticalPlugin.getInstance() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value
        val marineState = NauticalPlugin.engine?.getCurrentState()
        
        val state = uiState ?: return
        val target = if (caps?.hasWindshift == true && marineState?.serverLaylines != null) {
            marineState.serverLaylines.targetWaypoint
        } else {
            state.targetWaypoint
        } ?: return
        
        val app = context.applicationContext as? OsmandApplication ?: return
        if (app.settings.NAUTICAL_SHOW_LAYLINES.get() != true) return

        val boatLat = state.boatLat ?: return
        val boatLon = state.boatLon ?: return

        val isNight = NauticalPlugin.isNightVision(app)
        val isFetchable = if (caps?.hasWindshift == true && marineState?.serverLaylines != null) {
            marineState.serverLaylines.isFetchable
        } else {
            state.isFetchable
        }
        setupPaints(isFetchable, isNight)

        val boatX = tileBox.getPixXFromLatLon(boatLat, boatLon)
        val boatY = tileBox.getPixYFromLatLon(boatLat, boatLon)
        val targetX = tileBox.getPixXFromLatLon(target.latitude, target.longitude)
        val targetY = tileBox.getPixYFromLatLon(target.latitude, target.longitude)

        // 0. Render Wind Shift Uncertainty Cones along Port and Starboard Laylines
        val portShiftCone = if (caps?.hasWindshift == true && marineState?.serverLaylines != null) {
            marineState.serverLaylines.portShiftCone
        } else {
            state.portShiftCone
        }

        val stbdShiftCone = if (caps?.hasWindshift == true && marineState?.serverLaylines != null) {
            marineState.serverLaylines.stbdShiftCone
        } else {
            state.stbdShiftCone
        }

        // Draw Port Tack Wind Shift Cone (Header Sector)
        portShiftCone?.let { (p1, p2) ->
            val p1X = tileBox.getPixXFromLatLon(p1.latitude, p1.longitude)
            val p1Y = tileBox.getPixYFromLatLon(p1.latitude, p1.longitude)
            val p2X = tileBox.getPixXFromLatLon(p2.latitude, p2.longitude)
            val p2Y = tileBox.getPixYFromLatLon(p2.latitude, p2.longitude)

            portConePath.reset()
            portConePath.moveTo(boatX, boatY)
            portConePath.lineTo(p1X, p1Y)
            portConePath.lineTo(targetX, targetY)
            portConePath.lineTo(p2X, p2Y)
            portConePath.close()
            canvas.drawPath(portConePath, portConePaint)
        }

        // Draw Starboard Tack Wind Shift Cone (Lift Sector)
        stbdShiftCone?.let { (s1, s2) ->
            val s1X = tileBox.getPixXFromLatLon(s1.latitude, s1.longitude)
            val s1Y = tileBox.getPixYFromLatLon(s1.latitude, s1.longitude)
            val s2X = tileBox.getPixXFromLatLon(s2.latitude, s2.longitude)
            val s2Y = tileBox.getPixYFromLatLon(s2.latitude, s2.longitude)

            stbdConePath.reset()
            stbdConePath.moveTo(boatX, boatY)
            stbdConePath.lineTo(s1X, s1Y)
            stbdConePath.lineTo(targetX, targetY)
            stbdConePath.lineTo(s2X, s2Y)
            stbdConePath.close()
            canvas.drawPath(stbdConePath, stbdConePaint)
        }

        val portTackPoint = if (caps?.hasWindshift == true && marineState?.serverLaylines != null) {
            marineState.serverLaylines.portTackPoint
        } else {
            state.portTackPoint
        }

        // 1. Render Port Tack Layline: Boat -> PortIntersection -> Target
        portTackPoint?.let { ptp ->
            var changed = false
            if (portCache.needsLatLonUpdate(boatLat, boatLon, ptp.latitude, ptp.longitude, target.latitude, target.longitude)) {
                portCache.updateLatLons(boatLat, boatLon, ptp.latitude, ptp.longitude, target.latitude, target.longitude)
                changed = true
            }
            if (changed || portCache.needsPixelUpdate(tileBox)) {
                portCache.updatePixels(tileBox)
            }
            canvas.drawPath(portCache.cachedPath, portPaint)
        }

        val starboardTackPoint = if (caps?.hasWindshift == true && marineState?.serverLaylines != null) {
            marineState.serverLaylines.starboardTackPoint
        } else {
            state.starboardTackPoint
        }

        // 2. Render Starboard Tack Layline: Boat -> StbdIntersection -> Target
        starboardTackPoint?.let { stp ->
            var changed = false
            if (stbdCache.needsLatLonUpdate(boatLat, boatLon, stp.latitude, stp.longitude, target.latitude, target.longitude)) {
                stbdCache.updateLatLons(boatLat, boatLon, stp.latitude, stp.longitude, target.latitude, target.longitude)
                changed = true
            }
            if (changed || stbdCache.needsPixelUpdate(tileBox)) {
                stbdCache.updatePixels(tileBox)
            }
            canvas.drawPath(stbdCache.cachedPath, stbdPaint)
        }

        // 3. Render Wind Shifts
        if (app.settings.NAUTICAL_SHOW_WIND_SHIFTS.get()) {
            drawWindShifts(canvas, tileBox, boatLat, boatLon)
        }
    }

    private fun setupPaints(isFetchable: Boolean, isNight: Boolean) {
        val baseColor = when {
            isNight -> Color.RED
            isFetchable -> colorFetchable
            else -> colorTackRequired
        }
        val pathEffect = if (isFetchable) null else dashedEffect
        
        portPaint.color = baseColor
        portPaint.pathEffect = pathEffect
        stbdPaint.color = baseColor
        stbdPaint.pathEffect = pathEffect

        windShiftPaint.color = if (isNight) Color.RED else colorWindShift
        if (isNight) windShiftPaint.alpha = 60
    }

    private fun drawWindShifts(canvas: Canvas, tileBox: RotatedTileBox, boatLat: Double, boatLon: Double) {
        if (lastWindHistorySize <= 0) return

        val centerX = tileBox.getPixXFromLatLon(boatLat, boatLon)
        val centerY = tileBox.getPixYFromLatLon(boatLat, boatLon)
        
        // Scale radius by zoom and density
        val baseRadius = 250f * tileBox.density
        val zoomFactor = (tileBox.zoom / 14f).coerceIn(0.5f, 2.0f)
        val radius = baseRadius * zoomFactor

        windShiftRect.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        
        // Correct rotation sign
        val startAngle = cachedStartOfMaxGapPlusGap - 90.0 - tileBox.rotate
        
        canvas.drawArc(windShiftRect, startAngle.toFloat(), cachedSweepAngle, true, windShiftPaint)
    }

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean = false
}
