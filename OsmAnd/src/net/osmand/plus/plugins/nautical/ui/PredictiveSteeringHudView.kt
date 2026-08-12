package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin

class PredictiveSteeringHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val txtBias: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_predictive_steering_hud, this, true)
        txtBias = findViewById(R.id.ps_bias_text)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        isVisible = false
    }

    fun update() {
        val autopilot = NauticalPlugin.autopilot ?: return
        val bias = autopilot.activeWaveBias
        val absBias = Math.abs(bias)
        
        // Bug #12: Hysteresis (Show at 0.5, hide at 0.3)
        val shouldBeVisible = if (isVisible) absBias >= 0.3 else absBias >= 0.5
        
        if (shouldBeVisible) {
            isVisible = true
            txtBias.text = context.getString(R.string.nautical_wave_bias_hud_label, bias)
        } else {
            isVisible = false
        }
    }

    override fun setCompactMode(enabled: Boolean) {}
}
