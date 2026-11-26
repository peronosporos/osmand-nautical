package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.OsmandApplication
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.utils.AndroidUtils
import java.util.Locale
import kotlin.math.abs

class StartLineHudHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val rootLayout: View
    private val flagIcon: ImageView
    private val timerLabel: TextView
    private val distLabel: TextView
    private val timeLabel: TextView
    private val biasLabel: TextView
    private val btnSync: Button
    private val btnClear: Button

    var isNightVision: Boolean = false
        set(value) {
            if (field != value) {
                field = value
                applyNightVisionTheme(value)
            }
        }

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_start_line_hud, this, true)
        rootLayout = findViewById(R.id.start_line_hud_root)
        flagIcon = findViewById(R.id.start_line_flag_icon)
        timerLabel = findViewById(R.id.start_timer_label)
        distLabel = findViewById(R.id.start_dist_label)
        timeLabel = findViewById(R.id.start_time_label)
        biasLabel = findViewById(R.id.start_bias_label)
        btnSync = findViewById(R.id.btn_sync_timer)
        btnClear = findViewById(R.id.btn_clear_line)

        btnSync.setOnClickListener {
            NauticalPlugin.getInstance()?.tacticalStartManager?.syncTimer()
            update()
        }

        btnClear.setOnClickListener {
            NauticalPlugin.getInstance()?.tacticalStartManager?.clear()
            isVisible = false
        }

        val app = context.applicationContext as? OsmandApplication
        isNightVision = NauticalPlugin.isNightVision(app)
        isVisible = false
    }

    override fun setCompactMode(enabled: Boolean) {
        biasLabel.isVisible = !enabled
        val p = if (enabled) 2f else 6f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    private fun applyNightVisionTheme(enabled: Boolean) {
        val density = context.resources.displayMetrics.density
        if (enabled) {
            val bg = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8f * density
                setColor(ContextCompat.getColor(context, R.color.nautical_night_vision_bg)) // Pitch dark red/black
                setStroke((1.5f * density).toInt(), ContextCompat.getColor(context, R.color.nautical_race_red))
            }
            rootLayout.background = bg
            flagIcon.setColorFilter(ContextCompat.getColor(context, R.color.nautical_race_red))
            timerLabel.setTextColor(ContextCompat.getColor(context, R.color.nautical_race_red))
            distLabel.setTextColor(0xFFFF8A80.toInt())
            timeLabel.setTextColor(0xFFFF5252.toInt())
            biasLabel.setTextColor(0xFFFF8A80.toInt())
            btnSync.setTextColor(Color.WHITE)
            btnSync.setBackgroundColor(0xFFB71C1C.toInt())
            btnClear.setTextColor(0xFFFF8A80.toInt())
            btnClear.setBackgroundColor(0xFF8B0000.toInt())
        } else {
            rootLayout.setBackgroundResource(R.drawable.bg_side_widget_day)
            flagIcon.setColorFilter(ContextCompat.getColor(context, R.color.nautical_race_green))
            timerLabel.setTextColor(android.graphics.Color.WHITE)
            distLabel.setTextColor(ContextCompat.getColor(context, R.color.nautical_race_dim))
            timeLabel.setTextColor(ContextCompat.getColor(context, R.color.nautical_race_red))
            biasLabel.setTextColor(ContextCompat.getColor(context, R.color.nautical_race_cyan))
            btnSync.setTextColor(Color.WHITE)
            btnSync.setBackgroundColor(ContextCompat.getColor(context, R.color.nautical_race_blue))
            btnClear.setTextColor(Color.WHITE)
            btnClear.setBackgroundColor(ContextCompat.getColor(context, R.color.nautical_race_neutral))
        }
    }

    fun update() {
        val app = context.applicationContext as? OsmandApplication
        isNightVision = NauticalPlugin.isNightVision(app)

        val manager = NauticalPlugin.getInstance()?.tacticalStartManager ?: return
        if (!manager.isLineSet()) {
            isVisible = false
            return
        }

        isVisible = true
        val state = NauticalPlugin.engine?.getCurrentState()

        // Distance and time-to-burn need the boat position; without a fix
        // they show placeholders instead of being computed from a fabricated
        // (0, 0) position and presented as real numbers.
        val lat = state?.latitude
        val lon = state?.longitude

        // Direct null-checks so Kotlin smart-casts Double? -> Double for the
        // manager calls below.
        val dist = if (lat != null && lon != null) manager.getDistanceToLine(lat, lon) else null
        val time = if (lat != null && lon != null) manager.getTimeToBurn(lat, lon) else null
        val advantage = manager.getFavoredEndAdvantage()?.let { fav ->
            if (fav.isStarboardFavored == null) context.getString(R.string.nautical_start_square_line) else {
                val end = context.getString(if (fav.isStarboardFavored == true) R.string.nautical_start_starboard else R.string.nautical_start_port)
                context.getString(R.string.nautical_start_favored_fmt, end, String.format(Locale.US, "%.1f", fav.biasDeg), String.format(Locale.US, "%.1f", fav.boatLengthsAdvantage))
            }
        } ?: context.getString(R.string.nautical_start_line_bias_none)

        // Format Race Countdown Timer
        val remainingSec = manager.remainingSeconds.value
        val absSec = abs(remainingSec).toInt()
        val mins = absSec / 60
        val secs = absSec % 60
        val timerSign = if (remainingSec < 0) "+" else ""
        timerLabel.text = String.format(Locale.US, "%s%02d:%02d", timerSign, mins, secs)
        if (remainingSec <= 0 && remainingSec > -10) {
            timerLabel.setTextColor(if (isNightVision) ContextCompat.getColor(context, R.color.nautical_race_red) else ContextCompat.getColor(context, R.color.nautical_race_green))
        } else {
            timerLabel.setTextColor(if (isNightVision) ContextCompat.getColor(context, R.color.nautical_race_red) else android.graphics.Color.WHITE)
        }

        distLabel.text = dist?.let {
            context.getString(R.string.nautical_start_dist_fmt, String.format(Locale.US, "%.0f", it))
        } ?: context.getString(R.string.nautical_start_dist_placeholder)

        if (time == null) {
            timeLabel.text = context.getString(R.string.nautical_start_burn_placeholder)
        } else if (abs(time) > 3600) {
            timeLabel.text = context.getString(R.string.nautical_start_burn_none)
        } else {
            val absBurn = abs(time).toInt()
            val bMins = absBurn / 60
            val bSecs = absBurn % 60
            val sign = if (time < 0) "-" else "+"
            timeLabel.text = context.getString(R.string.nautical_start_burn_fmt, sign, bMins, bSecs)
        }

        biasLabel.text = advantage
    }
}
