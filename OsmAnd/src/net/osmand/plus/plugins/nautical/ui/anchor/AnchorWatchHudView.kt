package net.osmand.plus.plugins.nautical.ui.anchor

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader
import java.util.Locale

class AnchorWatchHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val txtRadius: TextView
    private val txtRode: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_anchor_watch_hud, this, true)
        txtRadius = findViewById(R.id.txt_anchor_radius)
        txtRode = findViewById(R.id.txt_rode_deployed)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
    }

    fun update() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val app = plugin.application
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value ?: return
        
        val radius = app.settings.NAUTICAL_ANCHOR_LAT.get().let { 
            if (it != 0.0) app.settings.NAUTICAL_ANCHOR_RADIUS.get() else null
        }

        if (radius != null || (caps.hasChainCounter && state.rodeDeployed != null)) {
            isVisible = true
            txtRadius.text = radius?.let { String.format(Locale.US, "Radius: %.0fm", it) } ?: "Not Set"
            
            val rodeStr = if (caps.hasChainCounter && state.rodeDeployed != null) {
                String.format(Locale.US, "Rode: %.1fm", state.rodeDeployed)
            } else {
                "Rode: ---"
            }
            txtRode.text = rodeStr
        } else {
            isVisible = false
        }
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2 else 8
        val px = (p * resources.displayMetrics.density).toInt()
        setPadding(px, px, px, px)
    }
}
