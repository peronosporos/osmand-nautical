package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.engine.ConnectionStatus
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.utils.AndroidUtils

class HardwareHealthHudHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val icon: ImageView
    private val statusText: TextView
    private val latencyText: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.hardware_health_hud, this, true)
        icon = findViewById(R.id.health_icon)
        statusText = findViewById(R.id.health_status)
        latencyText = findViewById(R.id.health_latency)
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2f else 6f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun updateState(state: MarineState, latencyMs: Long) {
        when (state.connectionStatus) {
            ConnectionStatus.CONNECTED -> {
                icon.setColorFilter(ContextCompat.getColor(context, R.color.nautical_status_green))
                statusText.text = context.getString(R.string.nautical_system_healthy)
                latencyText.text = context.getString(R.string.nautical_system_latency_ms, latencyMs)
            }
            ConnectionStatus.STALE -> {
                icon.setColorFilter(ContextCompat.getColor(context, R.color.nautical_status_yellow))
                val age = if (state.lastMessageTime > 0) (System.currentTimeMillis() - state.lastMessageTime) / 1000 else 30
                statusText.text = context.getString(R.string.nautical_system_stale, age.toInt())
                latencyText.text = "--"
            }
            ConnectionStatus.DISCONNECTED, ConnectionStatus.CONNECTING, ConnectionStatus.UNAUTHORIZED -> {
                icon.setColorFilter(ContextCompat.getColor(context, R.color.nautical_status_red))
                statusText.text = context.getString(R.string.nautical_system_disconnected)
                latencyText.text = "--"
            }
        }
    }
}
