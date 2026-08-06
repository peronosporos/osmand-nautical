package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.ForwardHazard
import net.osmand.plus.plugins.nautical.engine.NotificationState

class ForwardWatchHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val txtHazard: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_environment_hud, this, true)
        txtHazard = findViewById(R.id.env_humidity)
        setBackgroundResource(R.drawable.bg_nautical_status_emergency)
        isVisible = false
    }

    fun updateHazards(hazards: List<ForwardHazard>) {
        val mostUrgent = hazards.maxByOrNull { it.severity }
        if (mostUrgent != null && mostUrgent.severity >= NotificationState.WARN) {
            isVisible = true
            txtHazard.text = context.getString(R.string.nautical_forward_hazard_warning, mostUrgent.name, mostUrgent.distance)
            
            val bg = when (mostUrgent.severity) {
                NotificationState.EMERGENCY -> R.drawable.bg_nautical_status_emergency
                NotificationState.ALARM -> R.drawable.widget_nautical_alert_red
                else -> R.drawable.bg_nautical_hud_panel
            }
            setBackgroundResource(bg)
        } else {
            isVisible = false
        }
    }

    override fun setCompactMode(enabled: Boolean) {}
}
