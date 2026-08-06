package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.utils.AndroidUtils
import java.util.Locale
import kotlin.math.abs

class WindTrendHudHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val shiftLabel: TextView
    private val icon: ImageView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_wind_trend_hud, this, true)
        shiftLabel = findViewById(R.id.wind_shift_label)
        icon = findViewById(R.id.wind_trend_icon)
        icon.setImageResource(R.drawable.ic_action_nautical_wind_true)
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2f else 8f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun update() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value
        val engine = NauticalPlugin.engine ?: return
        val state = engine.getCurrentState()

        if (caps?.hasWindshift == true && state.windShift != null) {
            val shiftDeg = Math.toDegrees(state.windShift)
            shiftLabel.text = String.format(Locale.US, "%+.1f°", shiftDeg)
            shiftLabel.setTextColor(if (abs(shiftDeg) > 5.0) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt())
            return
        }

        val history = engine.getWindDirectionHistory()
        if (history.size < 2) return
        
        val now = System.currentTimeMillis()
        val thirtyMinsAgo = now - 30 * 60 * 1000
        
        val recentHistory = history.filter { it.second > thirtyMinsAgo }
        if (recentHistory.size < 2) return
        
        val first = recentHistory.first().first // radians
        val last = recentHistory.last().first // radians
        
        var diff = Math.toDegrees(last - first)
        while (diff > 180) diff -= 360
        while (diff < -180) diff += 360
        
        shiftLabel.text = String.format(Locale.US, "%+.1f°", diff)
        
        // Green for Lift (moving towards boat heading), Red for Header?
        // Actually, just show magnitude and color based on shift type
        // For simplicity: Green if absolute shift > 5 deg
        shiftLabel.setTextColor(if (abs(diff) > 5.0) 0xFF00FF00.toInt() else 0xFFFFFF00.toInt())
    }
}
