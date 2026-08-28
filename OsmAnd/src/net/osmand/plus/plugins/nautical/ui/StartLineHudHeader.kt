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
                setColor(0xEE120000.toInt()) // Pitch dark red/black
                setStroke((1.5f * density).toInt(), 0xFFFF1744.toInt())
            }
            rootLayout.background = bg
            flagIcon.setColorFilter(0xFFFF1744.toInt())
            timerLabel.setTextColor(0xFFFF1744.toInt())
            distLabel.setTextColor(0xFFFF8A80.toInt())
            timeLabel.setTextColor(0xFFFF5252.toInt())
            biasLabel.setTextColor(0xFFFF8A80.toInt())
            btnSync.setTextColor(Color.WHITE)
            btnSync.setBackgroundColor(0xFFB71C1C.toInt())
            btnClear.setTextColor(0xFFFF8A80.toInt())
            btnClear.setBackgroundColor(0xFF8B0000.toInt())
        } else {
            rootLayout.setBackgroundResource(R.drawable.bg_side_widget_day)
            flagIcon.setColorFilter(0xFF00E676.toInt())
            timerLabel.setTextColor(0xFFFFFFFF.toInt())
            distLabel.setTextColor(0xFFB0BEC5.toInt())
            timeLabel.setTextColor(0xFFFF1744.toInt())
            biasLabel.setTextColor(0xFF00E5FF.toInt())
            btnSync.setTextColor(Color.WHITE)
            btnSync.setBackgroundColor(0xFF1E88E5.toInt())
            btnClear.setTextColor(Color.WHITE)
            btnClear.setBackgroundColor(0xFF757575.toInt())
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
        val lat = state?.latitude ?: 0.0
        val lon = state?.longitude ?: 0.0

        val dist = manager.getDistanceToLine(lat, lon) ?: 0.0
        val time = manager.getTimeToBurn(lat, lon) ?: 0.0
        val advantage = manager.getFavoredEndAdvantage() ?: "Line Bias: --"

        // Format Race Countdown Timer
        val remainingSec = manager.remainingSeconds.value
        val absSec = abs(remainingSec).toInt()
        val mins = absSec / 60
        val secs = absSec % 60
        val timerSign = if (remainingSec < 0) "+" else ""
        timerLabel.text = String.format(Locale.US, "%s%02d:%02d", timerSign, mins, secs)
        if (remainingSec <= 0 && remainingSec > -10) {
            timerLabel.setTextColor(if (isNightVision) 0xFFFF1744.toInt() else 0xFF00E676.toInt())
        } else {
            timerLabel.setTextColor(if (isNightVision) 0xFFFF1744.toInt() else 0xFFFFFFFF.toInt())
        }

        distLabel.text = String.format(Locale.US, "Dist: %.0fm", dist)

        if (abs(time) > 3600) {
            timeLabel.text = "Burn: --:--"
        } else {
            val absBurn = abs(time).toInt()
            val bMins = absBurn / 60
            val bSecs = absBurn % 60
            val sign = if (time < 0) "-" else "+"
            timeLabel.text = String.format(Locale.US, "Burn: %s%02d:%02d", sign, bMins, bSecs)
        }

        biasLabel.text = advantage
    }
}
