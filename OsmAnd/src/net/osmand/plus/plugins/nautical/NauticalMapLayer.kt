package net.osmand.plus.plugins.nautical

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import android.graphics.drawable.Drawable
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyCorridorChecker
import net.osmand.plus.plugins.nautical.hazard.engine.SafetyIssue
import net.osmand.plus.plugins.nautical.hazard.engine.Severity
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.plus.plugins.nautical.s57.S57PrimitiveType
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.views.layers.base.OsmandMapLayer
import java.util.Locale
import kotlin.math.*

class NauticalMapLayer(context: Context) : OsmandMapLayer(context), SharedPreferences.OnSharedPreferenceChangeListener {

    private var lastKnownTileBox: RotatedTileBox? = null
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val waypointIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_action_waypoint)
    private val lockIcon: Drawable? = ContextCompat.getDrawable(context, R.drawable.ic_action_lock)

    private val projectionPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        alpha = 180
    }

    private val cogPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.STROKE
    }

    private val cmgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 255) // Cyan for CMG
        style = Paint.Style.STROKE
        alpha = 220
    }

    private val currentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLUE
        style = Paint.Style.STROKE
    }

    private val targetHeadingPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 165, 0) // Orange
        style = Paint.Style.STROKE
        alpha = 200
    }

    private val routePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        alpha = 140
    }

    private val corridorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.LTGRAY
        style = Paint.Style.STROKE
        alpha = 60
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
    }

    private val laylinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        alpha = 160
    }

    private val pinPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.GREEN
        style = Paint.Style.FILL
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val anchorZonePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        style = Paint.Style.STROKE
        alpha = 120
    }

    private val backingVectorPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        alpha = 180
    }

    private val steeringWormPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(0, 255, 255) // Cyan
        style = Paint.Style.STROKE
        alpha = 180
    }

    private val warningPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.RED
        textAlign = Paint.Align.CENTER
        typeface = android.graphics.Typeface.DEFAULT_BOLD
    }

    private val warningBgPaint = Paint().apply {
        color = Color.RED
        alpha = 220
    }

    private val trajectoryPath = Path()
    private val trajectoryHistory = mutableListOf<Pair<Double, Double>>()
    private var lastTrajectoryPoint: Pair<Double, Double>? = null
    private var lastDrawTileBox: RotatedTileBox? = null

    private class GeodesicVectorCache {
        var lastHeading: Double? = null
        var lastLat: Double? = null
        var lastLon: Double? = null
        var lastSpeed: Double? = null
        var lastLookAhead: Int? = null
        var cachedLatLons: List<net.osmand.data.LatLon> = emptyList()
        
        var lastZoom: Float? = null
        var lastCenter31X: Int? = null
        var lastCenter31Y: Int? = null
        var lastRotate: Float? = null
        
        val cachedPath = Path()
        var lastEndX: Float = 0f
        var lastEndY: Float = 0f

        fun needsLatLonUpdate(heading: Double, lat: Double, lon: Double, speed: Double, lookAhead: Int): Boolean {
            val lh = lastHeading ?: return true
            val llat = lastLat ?: return true
            val llon = lastLon ?: return true
            val lspd = lastSpeed ?: return true
            val lla = lastLookAhead ?: return true

            return (abs(heading - lh) > Math.toRadians(0.5)) ||
                (abs(lat - llat) > 0.0001) ||
                (abs(lon - llon) > 0.0001) ||
                (abs(speed - lspd) > 0.1) ||
                (lookAhead != lla)
        }

        fun needsPixelUpdate(tileBox: RotatedTileBox): Boolean {
            val lz = lastZoom ?: return true
            val lcx = lastCenter31X ?: return true
            val lcy = lastCenter31Y ?: return true
            val lrot = lastRotate ?: return true
            
            val zoom = tileBox.zoom + tileBox.zoomFloatPart.toFloat()
            return (abs(zoom - lz) > 0.1f) ||
                (tileBox.center31X != lcx) ||
                (tileBox.center31Y != lcy) ||
                (abs(tileBox.rotate - lrot) > 0.01f)
        }

        fun updateLatLons(heading: Double, lat: Double, lon: Double, speed: Double, lookAhead: Int, latLons: List<net.osmand.data.LatLon>) {
            lastHeading = heading
            lastLat = lat
            lastLon = lon
            lastSpeed = speed
            lastLookAhead = lookAhead
            cachedLatLons = latLons
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
                cachedPath.moveTo(
                tileBox.getPixXFromLatLon(start.latitude, start.longitude), 
                tileBox.getPixYFromLatLon(start.latitude, start.longitude),
            )
                for (i in 1 until cachedLatLons.size) {
                    val p = cachedLatLons[i]
                    cachedPath.lineTo(
                        tileBox.getPixXFromLatLon(p.latitude, p.longitude), 
                        tileBox.getPixYFromLatLon(p.latitude, p.longitude),
                    )
                }
                val end = cachedLatLons.last()
                lastEndX = tileBox.getPixXFromLatLon(end.latitude, end.longitude)
                lastEndY = tileBox.getPixYFromLatLon(end.latitude, end.longitude)
            }
        }
    }

    private val headingCache = GeodesicVectorCache()
    private val cogCache = GeodesicVectorCache()
    private val cmgCache = GeodesicVectorCache()
    private val currentCache = GeodesicVectorCache()
    private val targetHdgCache = GeodesicVectorCache()

    private var cachedSafetyIssues: List<SafetyIssue> = emptyList()
    private var hazardousSegmentsSet: Set<Int> = emptySet()
    private var lastCheckedRoute: List<Pair<Double, Double>>? = null
    private var lastCheckedVesselPos: Pair<Double, Double>? = null
    
    @Volatile
    private var safetyCorridorChecker: SafetyCorridorChecker? = null

    private val layerScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var checkJob: Job? = null

    private val prefChangeListener = this

    override fun initLayer(view: net.osmand.plus.views.OsmandMapTileView) {
        super.initLayer(view)
        val app = context.applicationContext as OsmandApplication
        app.getSharedPreferences(net.osmand.plus.settings.backend.OsmandSettings.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .registerOnSharedPreferenceChangeListener(prefChangeListener)
    }

    override fun destroyLayer() {
        val app = context.applicationContext as OsmandApplication
        app.getSharedPreferences(net.osmand.plus.settings.backend.OsmandSettings.SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE)
            .unregisterOnSharedPreferenceChangeListener(prefChangeListener)
        layerScope.cancel()
        super.destroyLayer()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        val app = context.applicationContext as OsmandApplication
        val settings = app.settings
        val watchedKeys = setOf(
            settings.NAUTICAL_SHOW_TRAJECTORY.id,
            settings.NAUTICAL_SHOW_HEADING_LINE.id,
            settings.NAUTICAL_SHOW_COG_LINE.id,
            settings.NAUTICAL_SHOW_CMG_LINE.id,
            settings.NAUTICAL_SHOW_CURRENT_VECTOR.id,
            settings.NAUTICAL_LOOK_AHEAD_TIME.id,
            settings.NAUTICAL_DISPLAY_MODE.id,
            settings.NAUTICAL_VESSEL_DRAFT.id,
            settings.NAUTICAL_SAFETY_MARGIN.id,
            settings.NAUTICAL_CORRIDOR_WIDTH.id,
            settings.NAUTICAL_SAFETY_CORRIDOR_BUFFER.id
        )
        if (watchedKeys.contains(key)) {
            invalidateCache()
            view.refreshMap()
        }
    }

    override fun drawInScreenPixels(): Boolean = true

    override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
        val app = context.applicationContext as OsmandApplication
        if (app.settings.APPLICATION_MODE.get() != net.osmand.plus.settings.backend.ApplicationMode.BOAT) {
            return
        }
        this.lastKnownTileBox = tileBox

        val engine = NauticalPlugin.engine ?: return
        val osmandSettings = app.settings
        val mode = osmandSettings.NAUTICAL_DISPLAY_MODE.get()
        val isSunlight = mode == net.osmand.plus.settings.enums.NauticalDisplayMode.SUNLIGHT

        // Task 8.0: Sunlight & Polarized Lens Adaptation
        val density = context.resources.displayMetrics.density
        val strokeScale = if (isSunlight) 2.5f else 1.0f

        // Window-level ColorMatrixColorFilter handles scotopic rendering.
        // Maintain standard high-contrast colors for all states.
        trailPaint.color = Color.MAGENTA
        trailPaint.strokeWidth = 10f * density * strokeScale
        
        projectionPaint.color = if (isSunlight) Color.BLACK else Color.WHITE
        projectionPaint.strokeWidth = 6f * density * strokeScale
        projectionPaint.alpha = if (isSunlight) 255 else 180
        projectionPaint.pathEffect = DashPathEffect(floatArrayOf(30f * density, 15f * density), 0f)
        
        cogPaint.color = if (isSunlight) Color.rgb(0, 100, 0) else Color.GREEN // Dark Green for Sunlight
        cogPaint.strokeWidth = 5f * density * strokeScale
        cogPaint.pathEffect = DashPathEffect(floatArrayOf(20f * density, 10f * density), 0f)

        cmgPaint.color = if (isSunlight) Color.rgb(0, 0, 128) else Color.rgb(0, 255, 255) // Dark Blue for Sunlight
        cmgPaint.strokeWidth = 6f * density * strokeScale
        cmgPaint.pathEffect = null // Solid line for Resultant
        
        currentPaint.color = if (isSunlight) Color.BLACK else Color.BLUE
        currentPaint.strokeWidth = 3f * density * strokeScale
        currentPaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 5f * density), 0f)
        
        targetHeadingPaint.color = if (isSunlight) 0xFFCC6600.toInt() else Color.rgb(255, 165, 0)
        targetHeadingPaint.strokeWidth = 5f * density * strokeScale
        targetHeadingPaint.alpha = 255
        targetHeadingPaint.pathEffect = DashPathEffect(floatArrayOf(25f * density, 15f * density), 0f)
        
        routePaint.color = if (isSunlight) Color.BLACK else Color.YELLOW
        routePaint.strokeWidth = 6f * density * strokeScale
        routePaint.pathEffect = DashPathEffect(floatArrayOf(30f * density, 20f * density), 0f)

        laylinePaint.strokeWidth = 4f * density * strokeScale
        laylinePaint.pathEffect = DashPathEffect(floatArrayOf(40f * density, 20f * density), 0f)

        pinPaint.textSize = 40f * density
        anchorZonePaint.strokeWidth = 4f * density * strokeScale
        backingVectorPaint.strokeWidth = 5f * density * strokeScale
        backingVectorPaint.pathEffect = DashPathEffect(floatArrayOf(20f * density, 20f * density), 0f)
        steeringWormPaint.strokeWidth = 6f * density * strokeScale
        warningPaint.textSize = 60f * density

        if (osmandSettings.NAUTICAL_SHOW_TRAJECTORY.get()) {
            val lastTb = lastDrawTileBox
            val tileBoxChanged = (lastTb == null) || (lastTb.zoom != tileBox.zoom) || 
                (abs(lastTb.rotate - tileBox.rotate) > 0.01f) ||
                (abs(lastTb.center31X - tileBox.center31X) > 1) ||
                (abs(lastTb.center31Y - tileBox.center31Y) > 1)
            
            // Optimization: only copy and rebuild if vessel moved significantly or tilebox changed
            val currentState = engine.getCurrentState()
            val vesselPos = if ((currentState.latitude != null) && (currentState.longitude != null)) {
                Pair(currentState.latitude, currentState.longitude)
            } else null
            
            val vesselMoved = (vesselPos != null) && (lastTrajectoryPoint != null) && 
                (net.osmand.util.MapUtils.getDistance(vesselPos.first, vesselPos.second, lastTrajectoryPoint!!.first, lastTrajectoryPoint!!.second) > 10.0)

            if (vesselMoved || tileBoxChanged || trajectoryHistory.isEmpty()) {
                engine.copyTrajectoryTo(trajectoryHistory)
                if (trajectoryHistory.size >= 2) {
                    val lastPoint = trajectoryHistory.last()
                    
                    trajectoryPath.reset()
                    val bounds = tileBox.latLonBounds
                    val culledTop = bounds.top + bounds.height() * 0.1
                    val culledBottom = bounds.bottom - bounds.height() * 0.1
                    val culledLeft = bounds.left - bounds.width() * 0.1
                    val culledRight = bounds.right + bounds.width() * 0.1

                    var firstVisible = true
                    var prevVisible = false
                    for (point in trajectoryHistory) {
                        val isVisible = point.first in culledBottom..culledTop && 
                                        point.second in culledLeft..culledRight
                        
                        if (isVisible || prevVisible) {
                            val x = tileBox.getPixXFromLatLon(point.first, point.second)
                            val y = tileBox.getPixYFromLatLon(point.first, point.second)
                            if (firstVisible) {
                                trajectoryPath.moveTo(x, y)
                                firstVisible = false
                            } else {
                                trajectoryPath.lineTo(x, y)
                            }
                        } else {
                            firstVisible = true
                        }
                        prevVisible = isVisible
                    }
                    lastTrajectoryPoint = lastPoint
                    lastDrawTileBox = tileBox
                }
            }
            if (!trajectoryPath.isEmpty) {
                trailPaint.alpha = 200
                canvas.drawPath(trajectoryPath, trailPaint)
            }
        }

        val plugin = NauticalPlugin.getInstance()
        if (engine.isFollowingRoute) {
            val workflow = plugin?.workflowEngine?.currentWorkflow?.value ?: SailingWorkflowState.TACTICAL_PASSAGE
            val isCloseQuarters = workflow == SailingWorkflowState.CLOSE_QUARTERS
            
            drawNavigationPath(canvas, tileBox, engine, isCloseQuarters)
            engine.getNextWaypoint()?.let { nextPoint ->
                val x = tileBox.getPixXFromLatLon(nextPoint.first, nextPoint.second)
                val y = tileBox.getPixYFromLatLon(nextPoint.first, nextPoint.second)

                waypointIcon?.let {
                    val density = context.resources.displayMetrics.density
                    val iconSize = (20 * density).toInt()
                    it.setBounds(x.toInt() - iconSize, y.toInt() - iconSize, x.toInt() + iconSize, y.toInt() + iconSize)
                    it.setTintList(null)
                    it.alpha = if (isCloseQuarters) 120 else 255
                    it.draw(canvas)
                } ?: run {
                    // Fallback to circle if icon is missing
                    trailPaint.color = Color.RED
                    trailPaint.style = Paint.Style.FILL
                    val oldAlpha = trailPaint.alpha
                    if (isCloseQuarters) trailPaint.alpha = 120
                    canvas.drawCircle(x, y, 20f, trailPaint)
                    trailPaint.alpha = oldAlpha
                    trailPaint.style = Paint.Style.STROKE
                    trailPaint.color = Color.MAGENTA
                }
            }
        }

        if (NauticalPlugin.getInstance()?.isConnectionLostAlertActive == true) {
            drawConnectionWarning(canvas)
        }

        drawVesselProjections(canvas, tileBox, engine, osmandSettings, isSunlight)
    }

    private fun drawConnectionWarning(canvas: Canvas) {
        val now = System.currentTimeMillis()
        if (((now / 500) % 2) == 0L) return // Blink 1Hz
        
        val text = context.getString(R.string.nautical_autopilot_data_lost)
        val x = canvas.width / 2f
        val y = 200f // Top area
        
        val textWidth = warningPaint.measureText(text)
        canvas.drawRect((x - (textWidth / 2)) - 40, y - 80, x + (textWidth / 2) + 40, y + 30, warningBgPaint)
        
        val oldColor = warningPaint.color
        val oldSize = warningPaint.textSize
        val oldFakeBold = warningPaint.isFakeBoldText
        
        warningPaint.color = Color.WHITE
        warningPaint.textSize = 70f
        warningPaint.isFakeBoldText = true
        
        canvas.drawText(text, x, y, warningPaint)
        
        warningPaint.color = oldColor
        warningPaint.textSize = oldSize
        warningPaint.isFakeBoldText = oldFakeBold
    }

    private val slipAnglePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.CYAN
        strokeWidth = 4f
        style = Paint.Style.STROKE
        pathEffect = DashPathEffect(floatArrayOf(10f, 10f), 0f)
        alpha = 180
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 32f
        textAlign = Paint.Align.CENTER
        setShadowLayer(5f, 0f, 0f, Color.BLACK)
    }

    private fun drawVesselProjections(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine,
        settings: net.osmand.plus.settings.backend.OsmandSettings,
        isSunlight: Boolean
    ) {
        val state = engine.getCurrentState()
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        
        val plugin = NauticalPlugin.getInstance()
        val workflow = plugin?.workflowEngine?.currentWorkflow?.value ?: SailingWorkflowState.TACTICAL_PASSAGE
        val isCloseQuarters = workflow == SailingWorkflowState.CLOSE_QUARTERS
        
        val lookAheadMin = if (isCloseQuarters) 2 else settings.NAUTICAL_LOOK_AHEAD_TIME.get()
        val lookAheadSec = lookAheadMin * 60.0

        val startX = tileBox.getPixXFromLatLon(lat, lon)
        val startY = tileBox.getPixYFromLatLon(lat, lon)
        
        // Strict culling for vessel projections
        if (startX < -1000 || startX > canvas.width + 1000 || startY < -1000 || startY > canvas.height + 1000) {
            return
        }

        val minSpeedForVector = if (isCloseQuarters) 0.25 else 0.0
        val mercatorScale = 1.0 / cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val arrowheadScale = (tileBox.density * mercatorScale).toFloat()

        // 1. Heading Line
        val hdg = state.headingTrue
        if (hdg != null) {
            val speed = max(state.speedThroughWater ?: state.speedOverGround ?: 0.0, minSpeedForVector)
            var changed = false
            if (headingCache.needsLatLonUpdate(hdg, lat, lon, speed, lookAheadMin)) {
                val dist = speed * lookAheadSec
                headingCache.updateLatLons(hdg, lat, lon, speed, lookAheadMin, generateGeodesicLatLons(lat, lon, dist, Math.toDegrees(hdg)))
                changed = true
            }
            if (changed || headingCache.needsPixelUpdate(tileBox)) {
                headingCache.updatePixels(tileBox)
            }
            if (settings.NAUTICAL_SHOW_HEADING_LINE.get()) {
                canvas.drawPath(headingCache.cachedPath, projectionPaint)
            }
        }

        // 2. COG Line
        val cog = state.courseOverGroundTrue
        if (cog != null) {
            val sog = state.speedOverGround ?: 0.0
            val speed = max(sog, minSpeedForVector)
            var changed = false
            if (cogCache.needsLatLonUpdate(cog, lat, lon, speed, lookAheadMin)) {
                val dist = speed * lookAheadSec
                cogCache.updateLatLons(cog, lat, lon, speed, lookAheadMin, generateGeodesicLatLons(lat, lon, dist, Math.toDegrees(cog)))
                changed = true
            }
            if (changed || cogCache.needsPixelUpdate(tileBox)) {
                cogCache.updatePixels(tileBox)
            }
            if (settings.NAUTICAL_SHOW_COG_LINE.get()) {
                canvas.drawPath(cogCache.cachedPath, cogPaint)
                drawArrowHead(canvas, startX, startY, cogCache.lastEndX, cogCache.lastEndY, cogPaint, arrowheadScale)
            }
        }

        // 3. Resultant Vector (CMG - Predicted)
        if (settings.NAUTICAL_SHOW_CMG_LINE.get()) {
             val stw = state.speedThroughWater
             val hdgTrue = state.headingTrue
             val leeway = state.leeway ?: 0.0
             val drift = state.drift
             val set = state.setTrue

             if (stw != null && hdgTrue != null && drift != null && set != null) {
                 // Vector Addition in Carthesian
                 val hdgL = hdgTrue + leeway
                 val v1x = stw * sin(hdgL)
                 val v1y = stw * cos(hdgL)
                 val v2x = drift * sin(set)
                 val v2y = drift * cos(set)
                 
                 val vrx = v1x + v2x
                 val vry = v1y + v2y
                 
                 val predictedSog = sqrt(vrx * vrx + vry * vry)
                 val predictedCmg = (atan2(vrx, vry) + 2 * PI) % (2 * PI)

                 var changed = false
                 if (cmgCache.needsLatLonUpdate(predictedCmg, lat, lon, predictedSog, lookAheadMin)) {
                     val dist = predictedSog * lookAheadSec
                     cmgCache.updateLatLons(predictedCmg, lat, lon, predictedSog, lookAheadMin, generateGeodesicLatLons(lat, lon, dist, Math.toDegrees(predictedCmg)))
                     changed = true
                 }
                 if (changed || cmgCache.needsPixelUpdate(tileBox)) {
                     cmgCache.updatePixels(tileBox)
                 }
                 canvas.drawPath(cmgCache.cachedPath, cmgPaint)
                 drawArrowHead(canvas, startX, startY, cmgCache.lastEndX, cmgCache.lastEndY, cmgPaint, arrowheadScale)
             }
        }

        // 4. Slip Angle (Crab Angle) Indicator
        if (hdg != null && cog != null && (state.speedOverGround ?: 0.0) > 0.5) {
            var diff = Math.toDegrees(cog - hdg)
            while (diff > 180) diff -= 360
            while (diff < -180) diff += 360
            
            if (abs(diff) > 1.0) { // Only show if more than 1 degree slip
                canvas.drawLine(headingCache.lastEndX, headingCache.lastEndY, cogCache.lastEndX, cogCache.lastEndY, slipAnglePaint)
                val midX = (headingCache.lastEndX + cogCache.lastEndX) / 2
                val midY = (headingCache.lastEndY + cogCache.lastEndY) / 2
                canvas.drawText(String.format(Locale.US, "%.1f°", diff), midX, midY - 20, textPaint)
            }
        }

        // 3. Current Vector
        if (settings.NAUTICAL_SHOW_CURRENT_VECTOR.get()) {
            val set = state.setTrue
            val drift = state.drift
            if ((set != null) && (drift != null)) {
                var changed = false
                if (currentCache.needsLatLonUpdate(set, lat, lon, drift, lookAheadMin)) {
                    val dist = drift * lookAheadSec
                    currentCache.updateLatLons(set, lat, lon, drift, lookAheadMin, generateGeodesicLatLons(lat, lon, dist, Math.toDegrees(set)))
                    changed = true
                }
                if (changed || currentCache.needsPixelUpdate(tileBox)) {
                    currentCache.updatePixels(tileBox)
                }
                canvas.drawPath(currentCache.cachedPath, currentPaint)
                drawArrowHead(canvas, startX, startY, currentCache.lastEndX, currentCache.lastEndY, currentPaint, arrowheadScale)
            }
        }

        // 4. Target Heading (Autopilot)
        val targetHdg = state.targetHeading
        if (targetHdg != null && state.autopilotState.lowercase(Locale.US) != "standby") {
            val speed = max(state.speedThroughWater ?: state.speedOverGround ?: 5.0, minSpeedForVector)
            var changed = false
            if (targetHdgCache.needsLatLonUpdate(targetHdg, lat, lon, speed, lookAheadMin)) {
                val dist = speed * lookAheadSec
                targetHdgCache.updateLatLons(targetHdg, lat, lon, speed, lookAheadMin, generateGeodesicLatLons(lat, lon, dist, Math.toDegrees(targetHdg)))
                changed = true
            }
            if (changed || targetHdgCache.needsPixelUpdate(tileBox)) {
                targetHdgCache.updatePixels(tileBox)
            }
            canvas.drawPath(targetHdgCache.cachedPath, targetHeadingPaint)
            drawArrowHead(canvas, startX, startY, targetHdgCache.lastEndX, targetHdgCache.lastEndY, targetHeadingPaint, arrowheadScale)
        }

        // 5. Laylines
        plugin?.tacticalProcessor?.let { tactical ->
            tactical.portLaylineEnd?.let { end ->
                canvas.drawLine(startX, startY, tileBox.getPixXFromLatLon(end.first, end.second), tileBox.getPixYFromLatLon(end.first, end.second), laylinePaint)
            }
            tactical.starboardLaylineEnd?.let { end ->
                canvas.drawLine(startX, startY, tileBox.getPixXFromLatLon(end.first, end.second), tileBox.getPixYFromLatLon(end.first, end.second), laylinePaint)
            }
        }

        // 6. Steering Worm (Rate of Turn Prediction)
        val rot = state.rateOfTurn
        val sog = state.speedOverGround
        val heading = state.headingTrue
        if (rot != null && sog != null && heading != null && abs(rot) > 0.001 && sog > 0.5) {
            drawSteeringWorm(canvas, tileBox, lat, lon, heading, sog, rot, isSunlight)
        }

        // 7. Tactical Pins (Start Line)
        plugin?.tacticalStartManager?.let { start ->
            start.portPin?.let { p ->
                drawPin(canvas, tileBox, p.first, p.second, "P", Color.RED, isSunlight)
            }
            start.starboardPin?.let { s ->
                drawPin(canvas, tileBox, s.first, s.second, "S", Color.GREEN, isSunlight)
            }
        }

        // 8. Anchor Swing Radius
        val osmandSettings = (context.applicationContext as OsmandApplication).settings
        val anchorLat = osmandSettings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = osmandSettings.NAUTICAL_ANCHOR_LON.get()
        val anchorRadius = osmandSettings.NAUTICAL_ANCHOR_RADIUS.get()
        if (anchorLat != 0.0 && anchorRadius > 0) {
            drawAnchorZone(canvas, tileBox, anchorLat, anchorLon, anchorRadius.toDouble(), isSunlight)
        }

        val previewLat = osmandSettings.NAUTICAL_ANCHOR_PREVIEW_LAT.get()
        val previewLon = osmandSettings.NAUTICAL_ANCHOR_PREVIEW_LON.get()
        val previewRadius = osmandSettings.NAUTICAL_ANCHOR_PREVIEW_RADIUS.get()
        if (previewLat != 0.0 && previewRadius > 0) {
            drawAnchorPreviewZone(canvas, tileBox, previewLat, previewLon, previewRadius.toDouble(), isSunlight)
        }

        // 9. Med-Mooring Backing Vector
        val activeManeuver = plugin?.maneuverManager?.activeManeuver
        if (activeManeuver is net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver) {
             val hdg = state.headingTrue ?: 0.0
             val sternHdg = hdg + PI
             val sternPath = generateGeodesicLatLons(lat, lon, 50.0, Math.toDegrees(sternHdg))
             val path = Path()
             path.moveTo(startX, startY)
             for (p in sternPath) {
                 path.lineTo(
                     tileBox.getPixXFromLatLon(p.latitude, p.longitude),
                     tileBox.getPixYFromLatLon(p.latitude, p.longitude),
                 )
             }
             canvas.drawPath(path, backingVectorPaint)
        }

        // 10. Touch Lock Icon Overlay
        val isLocked = plugin?.workflowManager?.getScreenTouchLockManager()?.isTouchLockActive?.value ?: false
        if (isLocked) {
            lockIcon?.let { icon ->
                val density = context.resources.displayMetrics.density
                val size = (48 * density).toInt()
                val margin = (16 * density).toInt()
                val left = tileBox.pixWidth - size - margin
                val top = tileBox.pixHeight - size - margin
                icon.setBounds(left, top, left + size, top + size)
                icon.setTint(if (isSunlight) Color.BLACK else Color.RED)
                icon.draw(canvas)
            }
        }
    }


    private fun drawPin(canvas: Canvas, tileBox: RotatedTileBox, lat: Double, lon: Double, label: String, color: Int, isSunlight: Boolean) {
        val px = tileBox.getPixXFromLatLon(lat, lon)
        val py = tileBox.getPixYFromLatLon(lat, lon)
        
        pinPaint.color = if (isSunlight) Color.BLACK else color
        canvas.drawCircle(px, py, 15f, pinPaint)
        
        pinPaint.color = if (isSunlight) Color.BLACK else Color.WHITE
        canvas.drawText(label, px, py - 25f, pinPaint)
    }

    private fun getPixelsPerMeter(tileBox: RotatedTileBox, lat: Double, lon: Double): Float {
        val px = tileBox.getPixXFromLatLon(lat, lon)
        val py = tileBox.getPixYFromLatLon(lat, lon)
        
        // Use a small latitude increment to find vertical scale (pixels per degree of latitude)
        // Mercator projection has local isotropy, so horizontal scale is the same.
        val latStep = 0.0001 
        val p2x = tileBox.getPixXFromLatLon(lat + latStep, lon)
        val p2y = tileBox.getPixYFromLatLon(lat + latStep, lon)
        
        val pxDist = sqrt((p2x - px).toDouble().pow(2.0) + (p2y - py).toDouble().pow(2.0))
        val meterDist = net.osmand.util.MapUtils.getDistance(lat, lon, lat + latStep, lon)
        
        return if (meterDist > 0) (pxDist / meterDist).toFloat() else 0f
    }

    private fun drawAnchorZone(canvas: Canvas, tileBox: RotatedTileBox, lat: Double, lon: Double, radiusMeters: Double, isSunlight: Boolean) {
        val px = tileBox.getPixXFromLatLon(lat, lon)
        val py = tileBox.getPixYFromLatLon(lat, lon)
        
        val pixelsPerMeter = getPixelsPerMeter(tileBox, lat, lon)
        val radiusPx = (radiusMeters * pixelsPerMeter).toFloat()
        
        anchorZonePaint.color = if (isSunlight) Color.BLACK else Color.RED
        canvas.drawCircle(px, py, radiusPx, anchorZonePaint)
        canvas.drawCircle(px, py, 10f, anchorZonePaint) // Drop point
    }

    private fun drawAnchorPreviewZone(canvas: Canvas, tileBox: RotatedTileBox, lat: Double, lon: Double, radiusMeters: Double, isSunlight: Boolean) {
        val px = tileBox.getPixXFromLatLon(lat, lon)
        val py = tileBox.getPixYFromLatLon(lat, lon)
        
        val pixelsPerMeter = getPixelsPerMeter(tileBox, lat, lon)
        val radiusPx = (radiusMeters * pixelsPerMeter).toFloat()
        
        anchorZonePaint.color = if (isSunlight) Color.BLACK else Color.YELLOW
        anchorZonePaint.style = Paint.Style.STROKE
        anchorZonePaint.strokeWidth = 5f
        canvas.drawCircle(px, py, radiusPx, anchorZonePaint)
        canvas.drawCircle(px, py, 15f, anchorZonePaint) // Drag target
        anchorZonePaint.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawSteeringWorm(
        canvas: Canvas,
        tileBox: RotatedTileBox,
        lat: Double,
        lon: Double,
        heading: Double,
        sog: Double,
        rot: Double,
        isSunlight: Boolean
    ) {
        val wormPath = Path()
        wormPath.moveTo(tileBox.getPixXFromLatLon(lat, lon), tileBox.getPixYFromLatLon(lat, lon))
        
        // Predict next 30 seconds
        val steps = 15
        val duration = 30.0
        val dt = duration / steps
        
        var curLat = lat
        var curLon = lon
        var curHdg = heading
        
        repeat(steps) {
            val dist = sog * dt
            val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(curLat, curLon, dist, Math.toDegrees(curHdg + rot * dt / 2))
            curLat = endPoint.latitude
            curLon = endPoint.longitude
            curHdg += rot * dt
            
            wormPath.lineTo(tileBox.getPixXFromLatLon(curLat, curLon), tileBox.getPixYFromLatLon(curLat, curLon))
        }
        
        steeringWormPaint.color = if (isSunlight) Color.BLACK else Color.rgb(0, 255, 255)
        canvas.drawPath(wormPath, steeringWormPaint)
    }

    private fun generateGeodesicLatLons(
        startLat: Double,
        startLon: Double,
        dist: Double,
        bearingDeg: Double,
        segments: Int = 5
    ): List<net.osmand.data.LatLon> {
        val list = mutableListOf<net.osmand.data.LatLon>()
        list.add(net.osmand.data.LatLon(startLat, startLon))

        val endPoint = net.osmand.util.MapUtils.greatCircleDestinationPoint(startLat, startLon, dist, bearingDeg)
        
        for (i in 1..segments) {
            val coeff = i.toDouble() / segments
            list.add(net.osmand.util.MapUtils.calculateIntermediatePoint(startLat, startLon, endPoint.latitude, endPoint.longitude, coeff))
        }
        return list
    }

    private fun drawArrowHead(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, paint: Paint, scale: Float = 1.0f) {
        val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
        val headLength = 30f * scale
        val headAngle = PI / 6

        val p1x = x2 - headLength * cos(angle - headAngle).toFloat()
        val p1y = y2 - headLength * sin(angle - headAngle).toFloat()
        val p2x = x2 - headLength * cos(angle + headAngle).toFloat()
        val p2y = y2 - headLength * sin(angle + headAngle).toFloat()

        canvas.drawLine(x2, y2, p1x, p1y, paint)
        canvas.drawLine(x2, y2, p2x, p2y, paint)
    }

    fun getTileBox(): RotatedTileBox? = lastKnownTileBox

    override fun onSingleTap(point: PointF, tileBox: RotatedTileBox): Boolean {
        if (NauticalPlugin.getInstance() == null) return false
        val activity = view.mapActivity ?: return false
        val arbitrator = net.osmand.plus.plugins.nautical.ui.NauticalTouchArbitrator(activity)
        return arbitrator.handleTouch(point.x, point.y)
    }

    override fun onLongPressEvent(point: PointF, tileBox: RotatedTileBox): Boolean {
        if (NauticalPlugin.getInstance() == null) return false
        val activity = view.mapActivity ?: return false
        val arbitrator = net.osmand.plus.plugins.nautical.ui.NauticalTouchArbitrator(activity)
        return arbitrator.handleTouch(point.x, point.y)
    }

    fun invalidateCache() = synchronized(this) {
        checkJob?.cancel()
        cachedSafetyIssues = emptyList()
        lastCheckedRoute = null
        lastCheckedVesselPos = null
        safetyCorridorChecker = null
        lastDrawTileBox = null
    }

    private fun drawNavigationPath(canvas: Canvas, tileBox: RotatedTileBox, engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine, isCloseQuarters: Boolean) {
        val route = engine.getRoutePoints()
        if (route.size < 2) return

        val app = context.applicationContext as OsmandApplication
        val currentState = engine.getCurrentState()
        val vesselPos = if ((currentState.latitude != null) && (currentState.longitude != null)) {
            Pair(currentState.latitude, currentState.longitude)
        } else null

        // Caching logic for safety check
        val routeChanged = route != lastCheckedRoute
        val vesselMoved = vesselPos != null && lastCheckedVesselPos != null && 
            net.osmand.shared.util.KMapUtils.getDistance(vesselPos.first, vesselPos.second, lastCheckedVesselPos!!.first, lastCheckedVesselPos!!.second) > 50.0
        val needsRecheck = routeChanged || (vesselPos != null && (lastCheckedVesselPos == null || vesselMoved))

        if (needsRecheck && checkJob?.isActive != true) {
            checkJob = layerScope.launch(Dispatchers.Main) {
                val indexManager = NauticalPlugin.getInstance()?.s57SpatialIndex
                val safetyManager = NauticalPlugin.getInstance()?.safetyManager
                
                var checker = safetyCorridorChecker
                if (checker == null && indexManager != null && safetyManager != null) {
                    checker = SafetyCorridorChecker(
                        indexManager,
                        safetyManager
                    )
                    safetyCorridorChecker = checker
                }
                
                val currentRoute = route.toList()

                if (checker != null) {
                    val issues = withContext(Dispatchers.IO) {
                        val waypoints = currentRoute.map { Waypoint(it.first, it.second) }
                        val corridorIssues = checker.checkCorridor(waypoints).toMutableList()
                        
                        vesselPos?.let { pos ->
                            if (!checker.isPointSafe(pos.first, pos.second)) {
                                corridorIssues.add(
                                    SafetyIssue(
                                        -2,
                                        app.getString(R.string.nautical_collision_danger),
                                        S57Object(0L, "DANGER", S57PrimitiveType.POINT, emptyMap(), emptyList()),
                                        Severity.DANGER,
                                    )
                                )
                            }
                            corridorIssues.addAll(checker.checkLookAhead(pos.first, pos.second))
                        }
                        corridorIssues
                    }

                    cachedSafetyIssues = issues
                    // hazardousSegmentsSet = issues.map { it.segmentIndex }.toSet()
        // Sequence conversion for performance
        hazardousSegmentsSet = issues.asSequence().map { it.segmentIndex }.toSet()
                    lastCheckedRoute = currentRoute
                    lastCheckedVesselPos = vesselPos
                    
                    // Refresh map to show new safety status
                    NauticalPlugin.getInstance()?.requestRefresh()
                }
            }
        }

        val hazardousSegments = hazardousSegmentsSet

        for (i in 0 until route.size - 1) {
            val p1 = route[i]
            val p2 = route[i + 1]
            val x1 = tileBox.getPixXFromLatLon(p1.first, p1.second)
            val y1 = tileBox.getPixYFromLatLon(p1.first, p1.second)
            val x2 = tileBox.getPixXFromLatLon(p2.first, p2.second)
            val y2 = tileBox.getPixYFromLatLon(p2.first, p2.second)

            routePaint.color = if (hazardousSegments.contains(i)) Color.RED else Color.YELLOW
            if (isCloseQuarters && !hazardousSegments.contains(i)) {
                routePaint.alpha = 80
            } else {
                routePaint.alpha = 140
            }
            
            val isSunlight = app.settings.NAUTICAL_DISPLAY_MODE.get() == net.osmand.plus.settings.enums.NauticalDisplayMode.SUNLIGHT
            val baseWidth = if (hazardousSegments.contains(i)) 12f else 6f
            routePaint.strokeWidth = baseWidth * (if (isSunlight) 2.5f else 1.0f)
            
            canvas.drawLine(x1, y1, x2, y2, routePaint)

            // Draw Safety Corridor
            val safetyManager = NauticalPlugin.getInstance()?.safetyManager
            if (safetyManager != null) {
                val totalWidthMeters = safetyManager.getTotalCorridorWidthMeters()
                val halfWidthMeters = totalWidthMeters / 2.0
                
                val pixelsPerMeter = getPixelsPerMeter(tileBox, p1.first, p1.second)
                val offsetPx = (halfWidthMeters * pixelsPerMeter).toFloat()
                
                if (offsetPx > 0) {
                    val angle = atan2((y2 - y1).toDouble(), (x2 - x1).toDouble())
                    val dx = (offsetPx * sin(angle)).toFloat()
                    val dy = (offsetPx * cos(angle)).toFloat()
                    
                    canvas.drawLine(x1 + dx, y1 - dy, x2 + dx, y2 - dy, corridorPaint)
                    canvas.drawLine(x1 - dx, y1 + dy, x2 - dx, y2 + dy, corridorPaint)
                }
            }
        }
    }
}
