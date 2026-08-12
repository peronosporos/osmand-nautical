package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.Button
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.NauticalColorResolver
import net.osmand.plus.plugins.nautical.ui.NauticalSemanticColor
import net.osmand.plus.utils.AndroidUtils
import java.util.Locale
import kotlin.math.abs

class StartLineHudHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val distLabel: TextView
    private val timeLabel: TextView
    private val biasLabel: TextView
    private val btnClear: Button

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_start_line_hud, this, true)
        distLabel = findViewById(R.id.start_dist_label)
        timeLabel = findViewById(R.id.start_time_label)
        biasLabel = findViewById(R.id.start_bias_label)
        btnClear = findViewById(R.id.btn_clear_line)
        
        btnClear.setOnClickListener {
            NauticalPlugin.getInstance()?.tacticalStartManager?.clear()
            isVisible = false
        }
        isVisible = false
    }

    override fun setCompactMode(enabled: Boolean) {
        biasLabel.isVisible = !enabled
        val p = if (enabled) 2f else 8f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun update() {
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
        val bias = manager.getLineBias() ?: 0.0
        
        distLabel.text = String.format(Locale.US, "Dist: %.0fm", dist)
        
        if (abs(time) > 3600) {
            timeLabel.text = "Burn: --:--"
        } else {
            val absTime = abs(time).toInt()
            val mins = absTime / 60
            val secs = absTime % 60
            val sign = if (time < 0) "-" else ""
            timeLabel.text = String.format(Locale.US, "Burn: %s%02d:%02d", sign, mins, secs)
        }
        
        biasLabel.text = String.format(Locale.US, "Bias: %.1f° %s", abs(bias), if (bias > 0) "S" else "P")
        val biasColor = if (abs(bias) < 5) {
            NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_OK)
        } else {
            NauticalColorResolver.getColor(context, NauticalSemanticColor.ACCENT)
        }
        biasLabel.setTextColor(biasColor)
    }
}
