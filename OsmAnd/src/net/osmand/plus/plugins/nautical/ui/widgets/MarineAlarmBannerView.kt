package net.osmand.plus.plugins.nautical.ui.widgets

import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.combine
import net.osmand.data.LatLon
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.AlarmPriorityManager
import net.osmand.plus.plugins.nautical.engine.MarineStateConstants
import net.osmand.plus.plugins.nautical.engine.SignalKPaths
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import net.osmand.plus.utils.AndroidUtils
import java.util.Locale

/**
 * High-visibility on-screen Marine Alarm HUD Banner attached to MapActivity.
 * Provides immediate situational awareness for critical marine alarms:
 * (MOB, AIS Collision Risk, Shallow Water Depth, Watchdog, Anchor Drag)
 * with 1-tap "Acknowledge / Snooze 5m" and "Focus on Map" camera centering.
 */
class MarineAlarmBannerView @JvmOverloads constructor(
    context: Context,
    private var alarmPriorityManager: AlarmPriorityManager? = null,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val cardLayout: LinearLayout
    private val iconView: ImageView
    private val titleTextView: TextView
    private val messageTextView: TextView
    private val snoozeButton: TextView
    private val focusButton: TextView
    private val closeButton: ImageView

    private var pulseAnimator: ObjectAnimator? = null
    private var viewScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var observeJob: Job? = null

    private var currentAlarm: ActiveAlarmInfo? = null
    private var currentLocation: LatLon? = null
    private var isBannerVisible = false

    var isNightVision: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyNightVisionTheme(value)
            }
        }

    private data class ActiveAlarmInfo(
        val key: String,
        val title: String,
        val message: String,
        val location: LatLon?,
        val priority: Int,
    )

    init {
        val density = context.resources.displayMetrics.density

        // Root container styling
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        val marginH = (10f * density).toInt()
        val marginV = (4f * density).toInt()

        cardLayout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val lp = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                setMargins(marginH, marginV, marginH, marginV)
            }
            layoutParams = lp

            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(0xEEB71C1C.toInt()) // High contrast deep red
                setStroke((2f * density).toInt(), 0xFFFF5252.toInt()) // Vibrant red border
            }
            background = shape
            val pad = (10f * density).toInt()
            setPadding(pad, pad, pad, pad)
            elevation = 8f * density
        }

        // Top Row: [Pulsing Icon] + [Title & Message Column] + [Close Button]
        val topRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }

        // Alarm Badge Icon Container
        val iconBadge = FrameLayout(context).apply {
            val size = (36f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                rightMargin = (10f * density).toInt()
            }
            background = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(0x44FFFFFF)
                setStroke((1.5f * density).toInt(), Color.WHITE)
            }
        }

        iconView = ImageView(context).apply {
            val iconSize = (22f * density).toInt()
            layoutParams = LayoutParams(iconSize, iconSize, Gravity.CENTER)
            setImageResource(R.drawable.ic_action_alert)
            setColorFilter(0xFFFFEB3B.toInt()) // Bright yellow alert icon
        }
        iconBadge.addView(iconView)
        topRow.addView(iconBadge)

        // Title and Message text column
        val textCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        titleTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            setTextColor(0xFFFFEB3B.toInt()) // Bright high-contrast yellow
            textSize = 14f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            isSingleLine = true
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(titleTextView)

        messageTextView = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (2f * density).toInt()
            }
            setTextColor(Color.WHITE)
            textSize = 12.5f
            maxLines = 2
            ellipsize = android.text.TextUtils.TruncateAt.END
        }
        textCol.addView(messageTextView)
        topRow.addView(textCol)

        // Close / Dismiss X icon
        closeButton = ImageView(context).apply {
            val size = (24f * density).toInt()
            layoutParams = LinearLayout.LayoutParams(size, size).apply {
                leftMargin = (6f * density).toInt()
            }
            setImageResource(R.drawable.ic_action_remove_dark)
            setColorFilter(0xCCFFFFFF.toInt())
            setOnClickListener {
                val key = currentAlarm?.key ?: "alarm"
                val apm = alarmPriorityManager ?: NauticalPlugin.getInstance()?.alarmPriorityManager
                apm?.snoozeAlarm(key, 300_000L)
                hideBanner()
            }
        }
        topRow.addView(closeButton)
        cardLayout.addView(topRow)

        // Bottom Action Buttons Row: [Snooze 5m] [Focus on Map]
        val buttonRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.END or Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = (8f * density).toInt()
            }
        }

        // Snooze 5m Button
        snoozeButton = TextView(context).apply {
            text = "Snooze 5m"
            setTextColor(Color.WHITE)
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val padH = (12f * density).toInt()
            val padV = (6f * density).toInt()
            setPadding(padH, padV, padH, padV)

            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(0x44000000)
                setStroke((1.5f * density).toInt(), 0xAAFFFFFF.toInt())
            }
            background = RippleDrawable(ColorStateList.valueOf(0x44FFFFFF), btnBg, null)

            setOnClickListener {
                val key = currentAlarm?.key ?: SignalKPaths.NOTIFICATIONS_COLLISION_RISK
                val apm = alarmPriorityManager ?: NauticalPlugin.getInstance()?.alarmPriorityManager
                apm?.snoozeAlarm(key, 300_000L)
                hideBanner()
            }
        }
        buttonRow.addView(snoozeButton)

        // Focus on Map Button
        focusButton = TextView(context).apply {
            text = context.getString(R.string.nautical_focus_on_map).let { base ->
                if (base.isNotEmpty() && !base.startsWith("nautical_")) base else "Focus on Map"
            }
            setTextColor(0xFF1E0000.toInt())
            textSize = 12f
            typeface = android.graphics.Typeface.DEFAULT_BOLD
            val padH = (12f * density).toInt()
            val padV = (6f * density).toInt()
            setPadding(padH, padV, padH, padV)

            val btnBg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6f * density
                setColor(Color.WHITE)
                setStroke((1.5f * density).toInt(), Color.WHITE)
            }
            background = RippleDrawable(ColorStateList.valueOf(0x44B71C1C), btnBg, null)
            val lp = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (8f * density).toInt()
            }
            layoutParams = lp

            setOnClickListener {
                val loc = currentLocation ?: currentAlarm?.location
                if (loc != null && MarineStateConstants.isValidLat(loc.latitude) && MarineStateConstants.isValidLon(loc.longitude)) {
                    val mapActivity = findMapActivity()
                    val mapView = mapActivity?.mapView
                        ?: (context.applicationContext as? OsmandApplication)?.osmandMap?.mapView
                    if (mapView != null) {
                        val targetZoom = maxOf(mapView.zoom, 14)
                        mapView.animatedDraggingThread?.startMoving(loc.latitude, loc.longitude, targetZoom)
                        mapView.refreshMap()
                    }
                }
            }
        }
        buttonRow.addView(focusButton)
        cardLayout.addView(buttonRow)

        addView(cardLayout)
        visibility = View.GONE
    }

    private fun findMapActivity(): MapActivity? {
        var ctx = context
        while (ctx is ContextWrapper) {
            if (ctx is MapActivity) return ctx
            ctx = ctx.baseContext
        }
        return null
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (alarmPriorityManager == null) {
            alarmPriorityManager = NauticalPlugin.getInstance()?.alarmPriorityManager
        }
        val apm = alarmPriorityManager ?: return

        observeJob?.cancel()
        observeJob = viewScope.launch {
            combine(
                apm.activeCriticalNotifications,
                apm.isCollisionAlarmActive,
                apm.threatDetails
            ) { _, _, _ ->
                resolveActiveAlarm()
            }.collect { activeAlarm ->
                withContext(Dispatchers.Main) {
                    updateAlarmDisplay(activeAlarm)
                }
            }
        }
    }

    override fun onDetachedFromWindow() {
        observeJob?.cancel()
        observeJob = null
        stopPulseAnimation()
        super.onDetachedFromWindow()
    }

    private fun startPulseAnimation() {
        if (pulseAnimator == null) {
            pulseAnimator = ObjectAnimator.ofFloat(iconView, "alpha", 0.35f, 1.0f).apply {
                duration = 550L
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                start()
            }
        } else if (!pulseAnimator!!.isRunning) {
            pulseAnimator!!.start()
        }
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        pulseAnimator = null
        iconView.alpha = 1.0f
    }

    private fun showBanner() {
        if (isBannerVisible) return
        isBannerVisible = true
        startPulseAnimation()
        visibility = View.VISIBLE
        alpha = 0f
        val offset = AndroidUtils.dpToPx(context, -80f).toFloat()
        translationY = offset
        animate()
            .translationY(0f)
            .alpha(1f)
            .setDuration(320L)
            .setInterpolator(DecelerateInterpolator())
            .start()
    }

    private fun hideBanner() {
        if (!isBannerVisible) {
            visibility = View.GONE
            return
        }
        isBannerVisible = false
        stopPulseAnimation()
        val offset = AndroidUtils.dpToPx(context, -80f).toFloat()
        animate()
            .translationY(offset)
            .alpha(0f)
            .setDuration(260L)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction {
                visibility = View.GONE
            }
            .start()
    }

    private fun updateAlarmDisplay(alarm: ActiveAlarmInfo?) {
        currentAlarm = alarm
        if (alarm == null) {
            hideBanner()
        } else {
            titleTextView.text = alarm.title
            messageTextView.text = alarm.message
            currentLocation = alarm.location
            focusButton.visibility = if (alarm.location != null) View.VISIBLE else View.GONE
            showBanner()
        }
    }

    private fun resolveActiveAlarm(): ActiveAlarmInfo? {
        val apm = alarmPriorityManager ?: NauticalPlugin.getInstance()?.alarmPriorityManager ?: return null
        val state = NauticalPlugin.engine?.getCurrentState()
        val ownLoc = (context.applicationContext as? OsmandApplication)?.locationProvider?.lastKnownLocation

        // 1. Man Overboard (MOB) - Priority 1
        val mobNotification = apm.activeCriticalNotifications.value[SignalKPaths.NOTIFICATIONS_MOB]
        val isMob = (state?.isMobActive == true) || (mobNotification != null)
        if (isMob && !apm.isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_MOB)) {
            val mobLat = state?.mobLatitude ?: ownLoc?.latitude
            val mobLon = state?.mobLongitude ?: ownLoc?.longitude
            val loc = if (mobLat != null && mobLon != null && MarineStateConstants.isValidLat(mobLat) && MarineStateConstants.isValidLon(mobLon)) {
                LatLon(mobLat, mobLon)
            } else null

            val msg = mobNotification?.message?.takeIf { it.isNotEmpty() }
                ?: context.getString(R.string.nautical_mob_label).let {
                    if (it.isNotEmpty() && !it.startsWith("nautical_")) "$it! Immediate rescue maneuver required." else "MOB alert active! Immediate rescue maneuver required."
                }

            return ActiveAlarmInfo(
                key = SignalKPaths.NOTIFICATIONS_MOB,
                title = "MAN OVERBOARD (MOB)",
                message = msg,
                location = loc,
                priority = 1
            )
        }

        // 2. AIS Collision Risk - Priority 2
        val isCollision = apm.isCollisionAlarmActive.value || apm.activeCriticalNotifications.value.containsKey(SignalKPaths.NOTIFICATIONS_COLLISION_RISK)
        if (isCollision && !apm.isAlarmSnoozed(SignalKPaths.NOTIFICATIONS_COLLISION_RISK) && !apm.isAlarmSnoozed("collision") && !apm.isAlarmSnoozed("collision_danger")) {
            val threat = apm.threatDetails.value
            val vesselName = threat?.vesselName ?: state?.threatName ?: "Dangerous Vessel"
            val cpaNm = threat?.cpaNm ?: 0.0
            val tcpaSec = threat?.tcpaSeconds ?: 0.0
            val tcpaMin = (tcpaSec / 60.0).toInt().coerceAtLeast(1)

            val desc = if (cpaNm > 0.0 && tcpaSec > 0.0) {
                "AIS COLLISION RISK: $vesselName (CPA ${String.format(Locale.US, "%.1f", cpaNm)} NM in ${tcpaMin}m)"
            } else {
                "AIS COLLISION RISK: $vesselName (Immediate collision danger!)"
            }

            // Find target vessel location
            val aisManager = plugin?.aisManager
            val dangerousAis = aisManager?.getAisObjects()?.firstOrNull { ais ->
                val extras = aisManager.getAisExtras(ais.mmsi)
                extras.hasCpaWarning || extras.threatLevel >= 2 || (ais.shipName != null && ais.shipName.equals(vesselName, ignoreCase = true))
            }
            val pos = dangerousAis?.position
            val targetPos = if (pos != null) LatLon(pos.latitude, pos.longitude) else null

            return ActiveAlarmInfo(
                key = SignalKPaths.NOTIFICATIONS_COLLISION_RISK,
                title = "AIS COLLISION RISK",
                message = desc,
                location = targetPos,
                priority = 2
            )
        }

        // 3. Other critical alarms (Shallow Depth, Watchdog, Anchor Drag, etc.)
        val criticalMap = apm.activeCriticalNotifications.value
        if (criticalMap.isNotEmpty()) {
            val prioritizedEntries = criticalMap.entries.sortedBy { (key, _) ->
                val lower = key.lowercase(Locale.US)
                when {
                    lower.contains("depth") -> 3
                    lower.contains("watchdog") -> 4
                    lower.contains("anchor") -> 5
                    else -> 10
                }
            }

            val (key, notif) = prioritizedEntries.first()
            val lowerKey = key.lowercase(Locale.US)
            val title: String
            val location: LatLon?

            when {
                lowerKey.contains("depth") -> {
                    title = "SHALLOW WATER DEPTH"
                    location = ownLoc?.let { LatLon(it.latitude, it.longitude) }
                }
                lowerKey.contains("watchdog") -> {
                    title = "SOLO WATCHDOG TIMEOUT"
                    location = ownLoc?.let { LatLon(it.latitude, it.longitude) }
                }
                lowerKey.contains("anchor") -> {
                    title = "ANCHOR DRAG ALARM"
                    val anchor = state?.anchor
                    val aLat = anchor?.latitude ?: anchor?.position?.latitude
                    val aLon = anchor?.longitude ?: anchor?.position?.longitude
                    location = if (aLat != null && aLon != null) {
                        LatLon(aLat, aLon)
                    } else ownLoc?.let { LatLon(it.latitude, it.longitude) }
                }
                else -> {
                    title = key.substringAfterLast('.').replace('_', ' ').uppercase(Locale.US) + " ALARM"
                    location = ownLoc?.let { LatLon(it.latitude, it.longitude) }
                }
            }

            return ActiveAlarmInfo(
                key = key,
                title = title,
                message = notif.message.ifEmpty { "Critical marine alarm active: $title" },
                location = location,
                priority = 5
            )
        }

        return null
    }

    override fun setCompactMode(enabled: Boolean) {
        val density = context.resources.displayMetrics.density
        val p = if (enabled) (6f * density).toInt() else (10f * density).toInt()
        cardLayout.setPadding(p, p, p, p)
        titleTextView.textSize = if (enabled) 12.5f else 14f
        messageTextView.textSize = if (enabled) 11f else 12.5f
    }

    private fun applyNightVisionTheme(enabled: Boolean) {
        val density = context.resources.displayMetrics.density
        if (enabled) {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(0xEE120000.toInt()) // Pitch black background
                setStroke((2f * density).toInt(), 0xFFFF1744.toInt()) // Deep red border
            }
            cardLayout.background = shape
            titleTextView.setTextColor(0xFFFF1744.toInt())
            messageTextView.setTextColor(0xFFFF8A80.toInt())
            iconView.setColorFilter(0xFFFF1744.toInt())
        } else {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10f * density
                setColor(0xEEB71C1C.toInt()) // High contrast deep red
                setStroke((2f * density).toInt(), 0xFFFF5252.toInt()) // Vibrant red border
            }
            cardLayout.background = shape
            titleTextView.setTextColor(0xFFFFEB3B.toInt())
            messageTextView.setTextColor(Color.WHITE)
            iconView.setColorFilter(0xFFFFEB3B.toInt())
        }
    }

    override fun isEmergency(): Boolean = true
}
