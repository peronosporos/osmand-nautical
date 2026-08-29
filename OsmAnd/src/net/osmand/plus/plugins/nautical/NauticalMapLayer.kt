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
import kotlin.time.Duration.Companion.seconds
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.engine.SailingWorkflowState
import net.osmand.plus.plugins.nautical.engine.TrajectoryPoint
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

    private val app = context.applicationContext as OsmandApplication
    private val safetyManager = NauticalSafetyManager.getInstance(app)
    private val wearOsManager = WearOsNauticalManager(context)
    private var lastKnownTileBox: RotatedTileBox? = null
    private val trailPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.MAGENTA
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val themedContext: Context get() = androidx.appcompat.view.ContextThemeWrapper(context, R.style.OsmandLightTheme)
    private val waypointIcon: Drawable? by lazy { androidx.appcompat.content.res.AppCompatResources.getDrawable(themedContext, R.drawable.ic_action_waypoint) }
    private val lockIcon: Drawable? by lazy { androidx.appcompat.content.res.AppCompatResources.getDrawable(themedContext, R.drawable.ic_action_lock) }
    private val anchorIcon: Drawable? by lazy { androidx.appcompat.content.res.AppCompatResources.getDrawable(themedContext, R.drawable.ic_action_anchor) }
    private val buildingIcon: Drawable? by lazy { androidx.appcompat.content.res.AppCompatResources.getDrawable(themedContext, R.drawable.ic_action_building) }

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

    private val startLinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 215, 0)
        style = Paint.Style.STROKE
        strokeWidth = 5f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
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

    private val isochronePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 140, 0) // Dark Orange
        style = Paint.Style.STROKE
        strokeWidth = 2f
        alpha = 120
    }

    private val polarPerformancePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.YELLOW
        style = Paint.Style.STROKE
        strokeWidth = 3f
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

    private val drPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.rgb(255, 140, 0) // Dark Orange for DR
        style = Paint.Style.STROKE
        strokeWidth = 4f
        pathEffect = DashPathEffect(floatArrayOf(15f, 10f), 0f)
    }

    private val polygonPath = Path()
    private val trajectoryPath = Path()
    private val localTrajectoryHistory = mutableListOf<TrajectoryPoint>()
    private var lastTrajectoryTime = 0L
    private var lastDrawTileBox: RotatedTileBox? = null
    
    private var trajectoryUpdateJob: Job? = null

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

        trajectoryUpdateJob = layerScope.launch {
            while (isActive) {
                val engine = NauticalPlugin.engine
                if (engine != null) {
                    engine.trajectoryEventFlow.collect {
                        invalidateTrajectory()
                    }
                } else {
                    delay(2.seconds)
                }
            }
        }
    }

    private var lastRefreshTime = 0L
    private val minRefreshIntervalMs = 500L

    private fun requestThrottledMapRefresh() {
        val pm = context.getSystemService(Context.POWER_SERVICE) as? android.os.PowerManager
        if (pm?.isInteractive == false) return // Suppress if screen is off
        val mapActivity = view.mapActivity
        if (mapActivity == null || mapActivity.isActivityDestroyed || mapActivity.isFinishing) return // Suppress if map activity is not active
        val now = System.currentTimeMillis()
        if (now - lastRefreshTime >= minRefreshIntervalMs) {
            lastRefreshTime = now
            view.refreshMap()
        }
    }

    private fun invalidateTrajectory() {
        lastDrawTileBox = null
        requestThrottledMapRefresh()
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
            settings.NAUTICAL_TRAJECTORY_COLOR.id,
            settings.NAUTICAL_TRAJECTORY_THICKNESS.id,
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
            requestThrottledMapRefresh()
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
        val isWatch = wearOsManager.isWatchMode()
        val isAmbient = wearOsManager.isAmbientMode.value

        // Sunlight & Polarized Lens Adaptation
        // When using polarized sunglasses, colors can shift or disappear.
        // We use absolute contrast (Black/White) and thicker strokes to compensate.
        val density = context.resources.displayMetrics.density
        val watchScale = if (isWatch) 0.6f else 1.0f
        val strokeScale = (if (isSunlight) 2.5f else 1.0f) * watchScale

        if (isAmbient) {
            drawAmbientMap(canvas, tileBox, engine, density)
            return
        }

        // Window-level ColorMatrixColorFilter handles scotopic rendering.
        // Maintain standard high-contrast colors for all states.
        trailPaint.color = if (isSunlight) Color.BLACK else osmandSettings.NAUTICAL_TRAJECTORY_COLOR.get()
        trailPaint.strokeWidth = osmandSettings.NAUTICAL_TRAJECTORY_THICKNESS.get() * density * strokeScale
        
        val primaryColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(context, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.PRIMARY)
        val secondaryColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(context, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.SECONDARY)
        val accentColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(context, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.ACCENT)
        val okColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(context, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.STATUS_OK)
        val warningColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(context, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.STATUS_WARNING)

        projectionPaint.color = primaryColor
        projectionPaint.strokeWidth = 6f * density * strokeScale
        projectionPaint.alpha = if (isSunlight) 255 else 180
        projectionPaint.pathEffect = DashPathEffect(floatArrayOf(30f * density, 15f * density), 0f)
        
        cogPaint.color = okColor
        cogPaint.strokeWidth = 5f * density * strokeScale
        cogPaint.pathEffect = DashPathEffect(floatArrayOf(20f * density, 10f * density), 0f)

        cmgPaint.color = accentColor
        cmgPaint.strokeWidth = 6f * density * strokeScale
        cmgPaint.pathEffect = null
        
        currentPaint.color = secondaryColor
        currentPaint.strokeWidth = 3f * density * strokeScale
        currentPaint.pathEffect = DashPathEffect(floatArrayOf(10f * density, 5f * density), 0f)
        
        targetHeadingPaint.color = warningColor
        targetHeadingPaint.strokeWidth = 5f * density * strokeScale
        targetHeadingPaint.alpha = 255
        targetHeadingPaint.pathEffect = DashPathEffect(floatArrayOf(25f * density, 15f * density), 0f)
        
        routePaint.color = if (isSunlight) Color.BLACK else warningColor
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
            
            val engine = NauticalPlugin.engine
            if (engine != null) {
                // Task: Efficiently sync trajectory from engine only when updated
                val engineLatestTime = engine.getCurrentState().timestamps["navigation.position"] ?: 0L
                if (engineLatestTime > lastTrajectoryTime) {
                    engine.copyTrajectoryTo(localTrajectoryHistory)
                    lastTrajectoryTime = engineLatestTime
                    trajectoryPath.reset() // Force rebuild on next draw
                }
            }

            if (tileBoxChanged || trajectoryPath.isEmpty) {
                trajectoryPath.reset()
                if (localTrajectoryHistory.size >= 2) {
                    val bounds = tileBox.latLonBounds
                    val padding = 0.1
                    val culledTop = bounds.top + bounds.height() * padding
                    val culledBottom = bounds.bottom - bounds.height() * padding
                    val left = bounds.left - bounds.width() * padding
                    val right = bounds.right + bounds.width() * padding
                    val isDatelineCrossed = left > right

                    var first = true
                    for (i in localTrajectoryHistory.indices) {
                        val pt = localTrajectoryHistory[i]
                        val lat = pt.lat
                        val lon = pt.lon
                        
                        val isVisible = lat in culledBottom..culledTop && 
                            if (isDatelineCrossed) (lon >= left || lon <= right) else (lon in left..right)

                        if (isVisible || i == 0 || i == localTrajectoryHistory.size - 1) {
                            val x = tileBox.getPixXFromLatLon(lat, lon)
                            val y = tileBox.getPixYFromLatLon(lat, lon)
                            if (first) {
                                trajectoryPath.moveTo(x, y)
                                first = false
                            } else {
                                trajectoryPath.lineTo(x, y)
                            }
                        }
                    }
                }
                lastDrawTileBox = tileBox
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
                    val iconSize = (20 * density).toInt()
                    it.setBounds(x.toInt() - iconSize, y.toInt() - iconSize, x.toInt() + iconSize, y.toInt() + iconSize)
                    it.setTintList(null)
                    it.alpha = if (isCloseQuarters) 120 else 255
                    it.draw(canvas)
                } ?: run {
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

        if (plugin?.isConnectionLostAlertActive == true) {
            drawConnectionWarning(canvas)
        }

        val state = engine.getCurrentState()
        if (state.isDeadReckoning && osmandSettings.NAUTICAL_DR_START_TIME.get() != 0L && state.latitude != null && state.longitude != null) {
            val drX = tileBox.getPixXFromLatLon(state.latitude, state.longitude)
            val drY = tileBox.getPixYFromLatLon(state.latitude, state.longitude)
            drawDrIndicator(canvas, drX, drY, isSunlight)
        }

        if (state.isochrones.isNotEmpty()) {
            drawIsochrones(canvas, tileBox, state.isochrones)
        }
        if (state.polarTargetSpeed != null) {
            drawPolarPerformance(canvas, tileBox, state, density)
        }

        drawVesselProjections(canvas, tileBox, engine, osmandSettings, isSunlight)
    }

    private fun drawAmbientMap(canvas: Canvas, tileBox: RotatedTileBox, engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine, density: Float) {
        // High-contrast, low-bit drawing for Wear OS Ambient Mode
        val state = engine.getCurrentState()
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val startX = tileBox.getPixXFromLatLon(lat, lon)
        val startY = tileBox.getPixYFromLatLon(lat, lon)

        val ambientPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.WHITE
            style = Paint.Style.STROKE
            strokeWidth = 2f * density
        }

        // Draw boat as a simple triangle
        val boatPath = Path()
        val boatSize = 15f * density
        val hdg = state.headingTrue ?: 0.0
        boatPath.moveTo(startX + boatSize * sin(hdg).toFloat(), startY - boatSize * cos(hdg).toFloat())
        boatPath.lineTo(startX + boatSize * sin(hdg + 2.3).toFloat(), startY - boatSize * cos(hdg + 2.3).toFloat())
        boatPath.lineTo(startX + boatSize * sin(hdg - 2.3).toFloat(), startY - boatSize * cos(hdg - 2.3).toFloat())
        boatPath.close()
        canvas.drawPath(boatPath, ambientPaint)

        // Only draw COG vector if sog > 0.5
        if ((state.speedOverGround ?: 0.0) > 0.5) {
            val cog = state.courseOverGroundTrue ?: 0.0
            val endX = startX + 40f * density * sin(cog).toFloat()
            val endY = startY - 40f * density * cos(cog).toFloat()
            canvas.drawLine(startX, startY, endX, endY, ambientPaint)
        }
    }

    private fun drawDrIndicator(canvas: Canvas, x: Float, y: Float, isSunlight: Boolean) {
        val density = context.resources.displayMetrics.density
        drPaint.color = if (isSunlight) Color.BLACK else Color.rgb(255, 140, 0)
        drPaint.strokeWidth = 3f * density
        
        // Industry Standard DR Symbol: Circle around position with cross
        canvas.drawCircle(x, y, 40f * density, drPaint)
        
        val crossSize = 10f * density
        canvas.drawLine(x - crossSize, y, x + crossSize, y, drPaint)
        canvas.drawLine(x, y - crossSize, x, y + crossSize, drPaint)
        
        // Label with shadow for high contrast
        val oldColor = textPaint.color
        val oldSize = textPaint.textSize
        textPaint.color = drPaint.color
        textPaint.textSize = 14f * density
        canvas.drawText("(DR)", x, y + 60f * density, textPaint)
        textPaint.color = oldColor
        textPaint.textSize = oldSize
    }

    private fun drawConnectionWarning(canvas: Canvas) {
        val now = System.currentTimeMillis()
        if (((now / 500) % 2) == 0L) return // Blink 1Hz
        
        val density = context.resources.displayMetrics.density
        val text = context.getString(R.string.nautical_sk_connection_lost)
        val x = canvas.width / 2f
        val y = 200f // Top area
        
        val textWidth = warningPaint.measureText(text)
        val padding = 20f * density
        canvas.drawRect((x - (textWidth / 2)) - padding, y - 80, x + (textWidth / 2) + padding, y + 30, warningBgPaint)
        
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
        
        if (startX < -1000 || startX > canvas.width + 1000 || startY < -1000 || startY > canvas.height + 1000) {
            return
        }

        val minSpeedForVector = if (isCloseQuarters) 0.25 else 0.0
        val mercatorScale = 1.0 / cos(Math.toRadians(lat)).coerceAtLeast(0.01)
        val arrowheadScale = (tileBox.density * mercatorScale).toFloat()

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

        if (settings.NAUTICAL_SHOW_CMG_LINE.get()) {
             val stw = state.speedThroughWater
             val hdgTrue = state.headingTrue
             val leeway = state.leeway ?: 0.0
             val drift = state.drift
             val set = state.setTrue

             if (stw != null && hdgTrue != null && drift != null && set != null) {
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

        if (hdg != null && cog != null && (state.speedOverGround ?: 0.0) > 0.5) {
            var diff = Math.toDegrees(cog - hdg)
            while (diff > 180) diff -= 360
            while (diff < -180) diff += 360
            
            if (abs(diff) > 1.0) {
                canvas.drawLine(headingCache.lastEndX, headingCache.lastEndY, cogCache.lastEndX, cogCache.lastEndY, slipAnglePaint)
                val midX = (headingCache.lastEndX + cogCache.lastEndX) / 2
                val midY = (headingCache.lastEndY + cogCache.lastEndY) / 2
                canvas.drawText(String.format(Locale.US, "%.1f°", diff), midX, midY - 20, textPaint)
            }
        }

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

        // 5. Laylines Fallback (Simple/Infinite)
        plugin?.tacticalProcessor?.let { tactical ->
            tactical.portLaylineEnd?.let { end ->
                canvas.drawLine(startX, startY, tileBox.getPixXFromLatLon(end.first, end.second), tileBox.getPixYFromLatLon(end.first, end.second), laylinePaint)
            }
            tactical.starboardLaylineEnd?.let { end ->
                canvas.drawLine(startX, startY, tileBox.getPixXFromLatLon(end.first, end.second), tileBox.getPixYFromLatLon(end.first, end.second), laylinePaint)
            }
        }

        val rot = state.rateOfTurn
        val sog = state.speedOverGround
        val heading = state.headingTrue
        if (rot != null && sog != null && heading != null && abs(rot) > 0.001 && sog > 0.5) {
            drawSteeringWorm(canvas, tileBox, lat, lon, heading, sog, rot, isSunlight)
        }

        plugin?.tacticalStartManager?.let { start ->
            start.portPin?.let { p ->
                drawPin(canvas, tileBox, p.first, p.second, "P", Color.RED, isSunlight)
            }
            start.starboardPin?.let { s ->
                drawPin(canvas, tileBox, s.first, s.second, "S", Color.GREEN, isSunlight)
            }
            val p = start.portPin
            val s = start.starboardPin
            if (p != null && s != null) {
                drawStartLine(canvas, tileBox, p.first, p.second, s.first, s.second, isSunlight)
            }
        }

        val anchorLat = settings.NAUTICAL_ANCHOR_LAT.get()
        val anchorLon = settings.NAUTICAL_ANCHOR_LON.get()
        var anchorRadius = settings.NAUTICAL_ANCHOR_RADIUS.get()
        if (anchorLat != 0.0) {
            if (anchorRadius <= 0f) {
                val depth = settings.NAUTICAL_ANCHOR_DEPTH.get().toDouble()
                val scopeRatio = settings.NAUTICAL_ANCHOR_SCOPE_RATIO.get().toDouble().coerceAtLeast(1.0)
                val safetyMargin = settings.NAUTICAL_ANCHOR_SAFETY_MARGIN.get().toDouble()
                val bowOffset = settings.NAUTICAL_ANCHOR_BOW_OFFSET.get().toDouble()
                anchorRadius = ((depth * scopeRatio) + safetyMargin + bowOffset).toFloat().coerceAtLeast(15.0f)
            }
            if (anchorRadius > 0f) {
                drawAnchorZone(canvas, tileBox, anchorLat, anchorLon, anchorRadius.toDouble(), isSunlight)
            }
        }

        val previewLat = settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get()
        val previewLon = settings.NAUTICAL_ANCHOR_PREVIEW_LON.get()
        val previewRadius = settings.NAUTICAL_ANCHOR_PREVIEW_RADIUS.get()
        if (previewLat != 0.0 && previewRadius > 0) {
            drawAnchorPreviewZone(canvas, tileBox, previewLat, previewLon, previewRadius.toDouble(), isSunlight)
        }

        val activeManeuver = plugin?.maneuverManager?.activeManeuver
        if (activeManeuver is net.osmand.plus.plugins.nautical.maneuvers.MedMooringManeuver) {
             val hdgT = state.headingTrue ?: 0.0
             val sternHdg = hdgT + PI
             val dist = if (!activeManeuver.targetLat.isNaN()) {
                 net.osmand.shared.util.KMapUtils.getDistance(lat, lon, activeManeuver.targetLat, activeManeuver.targetLon)
             } else 50.0
             
             val backingDistance = (dist * 1.2).coerceIn(10.0, 100.0)
             val sternPath = generateGeodesicLatLons(lat, lon, backingDistance, Math.toDegrees(sternHdg))
             
             val path = Path()
             path.moveTo(startX, startY)
             for (p in sternPath) {
                 path.lineTo(
                     tileBox.getPixXFromLatLon(p.latitude, p.longitude),
                     tileBox.getPixYFromLatLon(p.latitude, p.longitude),
                 )
             }
             canvas.drawPath(path, backingVectorPaint)
             
             val aLat = activeManeuver.anchorDropLat
             val aLon = activeManeuver.anchorDropLon
             if (!aLat.isNaN() && !aLon.isNaN()) {
                 anchorIcon?.let { icon ->
                     val iconDensity = context.resources.displayMetrics.density
                     val size = (24 * iconDensity).toInt()
                     val px = tileBox.getPixXFromLatLon(aLat, aLon)
                     val py = tileBox.getPixYFromLatLon(aLat, aLon)
                     icon.setBounds((px - size / 2).toInt(), (py - size / 2).toInt(), (px + size / 2).toInt(), (py + size / 2).toInt())
                     icon.setTint(if (isSunlight) Color.BLACK else Color.RED)
                     icon.draw(canvas)
                 }
             }
        }

        val isTouchLocked = plugin?.workflowManager?.getScreenTouchLockManager()?.isTouchLockActive?.value ?: false
        if (isTouchLocked) {
            lockIcon?.let { icon ->
                val iconDensity = context.resources.displayMetrics.density
                val size = (48 * iconDensity).toInt()
                val margin = (16 * iconDensity).toInt()
                val left = tileBox.pixWidth - size - margin
                val top = tileBox.pixHeight - size - margin
                icon.setBounds(left, top, left + size, top + size)
                icon.setTint(if (isSunlight) Color.BLACK else Color.RED)
                icon.draw(canvas)
            }
        }

        if (activeManeuver is net.osmand.plus.plugins.nautical.maneuvers.DockingManeuver) {
            val dLat = activeManeuver.targetLat
            val dLon = activeManeuver.targetLon
            if (dLat != 0.0) {
                buildingIcon?.let { icon ->
                    val iconDensity = context.resources.displayMetrics.density
                    val size = (32 * iconDensity).toInt()
                    val px = tileBox.getPixXFromLatLon(dLat, dLon)
                    val py = tileBox.getPixYFromLatLon(dLat, dLon)
                    icon.setBounds((px - size / 2).toInt(), (py - size / 2).toInt(), (px + size / 2).toInt(), (py + size / 2).toInt())
                    icon.setTint(if (isSunlight) Color.BLACK else Color.rgb(255, 215, 0))
                    icon.draw(canvas)
                }
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

    private fun drawStartLine(canvas: Canvas, tileBox: RotatedTileBox, pLat: Double, pLon: Double, sLat: Double, sLon: Double, isSunlight: Boolean) {
        val px = tileBox.getPixXFromLatLon(pLat, pLon)
        val py = tileBox.getPixYFromLatLon(pLat, pLon)
        val sx = tileBox.getPixXFromLatLon(sLat, sLon)
        val sy = tileBox.getPixYFromLatLon(sLat, sLon)
        startLinePaint.color = if (isSunlight) Color.BLACK else Color.rgb(255, 215, 0)
        canvas.drawLine(px, py, sx, sy, startLinePaint)
    }

    private fun getPixelsPerMeter(tileBox: RotatedTileBox, lat: Double, lon: Double): Float {
        val px = tileBox.getPixXFromLatLon(lat, lon)
        val py = tileBox.getPixYFromLatLon(lat, lon)
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
        canvas.drawCircle(px, py, 10f, anchorZonePaint)
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
        canvas.drawCircle(px, py, 15f, anchorZonePaint)
        anchorZonePaint.style = Paint.Style.FILL_AND_STROKE
    }

    private fun drawSteeringWorm(canvas: Canvas, tileBox: RotatedTileBox, lat: Double, lon: Double, heading: Double, sog: Double, rot: Double, isSunlight: Boolean) {
        val wormPath = Path()
        wormPath.moveTo(tileBox.getPixXFromLatLon(lat, lon), tileBox.getPixYFromLatLon(lat, lon))
        // Task: Reduced complexity for maneuvering prediction (15 -> 8 steps)
        val steps = 8
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

    private fun generateGeodesicLatLons(startLat: Double, startLon: Double, dist: Double, bearingDeg: Double, segments: Int = 5): List<net.osmand.data.LatLon> {
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

    private fun drawIsochrones(canvas: Canvas, tileBox: RotatedTileBox, isochrones: List<net.osmand.plus.plugins.nautical.network.SignalKRegion>) {
        val engine = NauticalPlugin.engine ?: return
        val lastUpdate = engine.getCurrentState().lastIsochroneTime
        val ageMs = System.currentTimeMillis() - lastUpdate
        
        // Pulse/Fade effect: new isochrones are bright, then settle to 120 alpha
        val alpha = if (ageMs < 3000) {
            (120 + 135 * (1.0 - ageMs / 3000.0)).toInt()
        } else 120

        isochronePaint.alpha = alpha
        isochrones.forEach { region ->
            val geometry = region.feature.geometry
            val coordinates = geometry["coordinates"] as? List<*> ?: return@forEach
            val type = geometry["type"] as? String
            
            if (type == "Polygon") {
                drawGeoJsonPolygon(canvas, tileBox, coordinates)
            } else if (type == "MultiPolygon") {
                coordinates.forEach { poly ->
                    drawGeoJsonPolygon(canvas, tileBox, poly as? List<*> ?: return@forEach)
                }
            }
        }
    }

    private fun drawGeoJsonPolygon(canvas: Canvas, tileBox: RotatedTileBox, rings: List<*>) {
        rings.forEach { ring ->
            val coords = ring as? List<*> ?: return@forEach
            polygonPath.reset()
            coords.forEachIndexed { index, coord ->
                val lonLat = coord as? List<*> ?: return@forEachIndexed
                val lon = (lonLat[0] as? Number)?.toDouble() ?: 0.0
                val lat = (lonLat[1] as? Number)?.toDouble() ?: 0.0
                val x = tileBox.getPixXFromLatLon(lat, lon)
                val y = tileBox.getPixYFromLatLon(lat, lon)
                if (index == 0) polygonPath.moveTo(x, y) else polygonPath.lineTo(x, y)
            }
            canvas.drawPath(polygonPath, isochronePaint)
        }
    }

    private fun drawPolarPerformance(canvas: Canvas, tileBox: RotatedTileBox, state: net.osmand.plus.plugins.nautical.engine.MarineState, density: Float) {
        val lat = state.latitude ?: return
        val lon = state.longitude ?: return
        val targetSpeed = state.polarTargetSpeed ?: return
        val currentSpeed = state.speedThroughWater ?: state.speedOverGround ?: 0.0
        val twa = state.trueWindAngle ?: 0.0 // Bow-relative radians

        val centerX = tileBox.getPixXFromLatLon(lat, lon)
        val centerY = tileBox.getPixYFromLatLon(lat, lon)
        val pixelsPerMs = 15f * density 

        // 1. Draw Polar Curve Background Segment (Arc showing performance for nearby TWAs)
        state.polarProfile?.let { profile ->
            val tws = state.windSpeedTrue ?: return@let
            val twsList = profile.tws ?: return@let
            val twaList = profile.twa ?: return@let
            val speeds = profile.speeds ?: return@let

            // Find TWS index for interpolation
            val twsIdx = twsList.indexOfFirst { it > tws }.let { if (it == -1) twsList.size - 1 else it }.coerceAtLeast(1)
            val twsFactor = (tws - twsList[twsIdx - 1]) / (twsList[twsIdx] - twsList[twsIdx - 1]).coerceAtLeast(0.1)

            val polarPath = Path()
            twaList.forEachIndexed { i, angleDeg ->
                val angleRad = Math.toRadians(angleDeg)
                val s1 = speeds[twsIdx - 1][i]
                val s2 = speeds[twsIdx][i]
                val interpolatedSpeed = s1 + (s2 - s1) * twsFactor
                
                // Draw relative to boat heading
                val drawAngle = state.headingTrue ?: (0.0 + angleRad)
                val px = centerX + (interpolatedSpeed * pixelsPerMs * sin(drawAngle)).toFloat()
                val py = centerY - (interpolatedSpeed * pixelsPerMs * cos(drawAngle)).toFloat()
                
                if (i == 0) polarPath.moveTo(px, py) else polarPath.lineTo(px, py)
            }
            
            polarPerformancePaint.style = Paint.Style.STROKE
            polarPerformancePaint.alpha = 60
            polarPerformancePaint.color = Color.GRAY
            canvas.drawPath(polarPath, polarPerformancePaint)
        }

        // 2. Efficiency Ring
        val targetRadius = (targetSpeed * pixelsPerMs).toFloat()
        val currentRadius = (currentSpeed * pixelsPerMs).toFloat()

        polarPerformancePaint.style = Paint.Style.STROKE
        polarPerformancePaint.alpha = 100
        polarPerformancePaint.color = Color.WHITE
        canvas.drawCircle(centerX, centerY, targetRadius, polarPerformancePaint)
        
        val ratio = if (targetSpeed > 0) currentSpeed / targetSpeed else 0.0
        polarPerformancePaint.alpha = 255
        polarPerformancePaint.color = if (ratio >= 0.98) Color.CYAN else if (ratio >= 0.90) Color.GREEN else if (ratio >= 0.8) Color.YELLOW else Color.RED
        polarPerformancePaint.strokeWidth = 4f * density
        canvas.drawCircle(centerX, centerY, currentRadius, polarPerformancePaint)
    }

    private fun drawNavigationPath(canvas: Canvas, tileBox: RotatedTileBox, engine: net.osmand.plus.plugins.nautical.engine.SignalKEngine, isCloseQuarters: Boolean) {
        val route = engine.getRoutePoints()
        if (route.size < 2) return
        val app = context.applicationContext as OsmandApplication
        val currentState = engine.getCurrentState()
        val vesselPos = if ((currentState.latitude != null) && (currentState.longitude != null)) {
            Pair(currentState.latitude, currentState.longitude)
        } else null
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
                    checker = SafetyCorridorChecker(indexManager, safetyManager)
                    safetyCorridorChecker = checker
                }
                val currentRoute = route.toList()
                if (checker != null) {
                    val issues = withContext(Dispatchers.IO) {
                        val waypoints = currentRoute.map { Waypoint(it.first, it.second) }
                        val corridorIssues = checker.checkCorridor(waypoints).toMutableList()
                        vesselPos?.let { pos ->
                            if (!checker.isPointSafe(pos.first, pos.second)) {
                                corridorIssues.add(SafetyIssue(-2, app.getString(R.string.nautical_collision_danger), S57Object(0L, "DANGER", S57PrimitiveType.POINT, emptyMap(), emptyList()), Severity.DANGER))
                            }
                            corridorIssues.addAll(checker.checkLookAhead(pos.first, pos.second))
                        }
                        corridorIssues
                    }
                    cachedSafetyIssues = issues
                    hazardousSegmentsSet = issues.asSequence().map { it.segmentIndex }.toSet()
                    lastCheckedRoute = currentRoute
                    lastCheckedVesselPos = vesselPos
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
            val isSunlight = app.settings.NAUTICAL_DISPLAY_MODE.get() == net.osmand.plus.settings.enums.NauticalDisplayMode.SUNLIGHT
            val errorColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(app, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.STATUS_ERROR)
            val warningColor = net.osmand.plus.plugins.nautical.ui.NauticalColorResolver.getColor(app, net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor.STATUS_WARNING)

            routePaint.color = if (isSunlight) Color.BLACK else (if (hazardousSegments.contains(i)) errorColor else warningColor)
            routePaint.alpha = if (isCloseQuarters && !hazardousSegments.contains(i)) 80 else 140
            val baseWidth = if (hazardousSegments.contains(i)) 12f else 6f
            routePaint.strokeWidth = baseWidth * (if (isSunlight) 2.5f else 1.0f)
            canvas.drawLine(x1, y1, x2, y2, routePaint)
            if (true) {
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
