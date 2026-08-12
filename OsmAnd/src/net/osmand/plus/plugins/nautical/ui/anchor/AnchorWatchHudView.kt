package net.osmand.plus.plugins.nautical.ui.anchor

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import com.google.android.material.button.MaterialButton
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.SignalKUnitConverter
import net.osmand.plus.plugins.nautical.maneuvers.WeighingAnchorManeuver
import net.osmand.plus.plugins.nautical.ui.INauticalHudHeader

class AnchorWatchHudView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val txtRadius: TextView
    private val txtRode: TextView
    private val btnWeigh: MaterialButton

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_anchor_watch_hud, this, true)
        txtRadius = findViewById(R.id.txt_anchor_radius)
        txtRode = findViewById(R.id.txt_rode_deployed)
        btnWeigh = findViewById(R.id.btn_weigh_anchor)
        
        setBackgroundResource(R.drawable.bg_nautical_hud_panel)
        
        btnWeigh.setOnClickListener {
            val plugin = NauticalPlugin.getInstance() ?: return@setOnClickListener
            val lat = plugin.application.settings.NAUTICAL_ANCHOR_LAT.get()
            val lon = plugin.application.settings.NAUTICAL_ANCHOR_LON.get()
            if (lat != 0.0) {
                (plugin.maneuverManager?.getManeuverById("weighing_anchor") as? WeighingAnchorManeuver)?.let {
                    it.setDropPoint(lat, lon)
                    plugin.maneuverManager?.setActiveManeuver("weighing_anchor")
                }
            }
        }
    }

    fun update() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val app = plugin.application
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        val caps = plugin.capabilityManager?.capabilities?.value ?: return
        
        val anchorLat = app.settings.NAUTICAL_ANCHOR_LAT.get()
        val radius = if (anchorLat != 0.0) app.settings.NAUTICAL_ANCHOR_RADIUS.get() else null

        if (radius != null || (caps.hasChainCounter && state.rodeDeployed != null)) {
            isVisible = true
            
            val radiusStr = radius?.let {
                val (v, u) = SignalKUnitConverter.formatValue(app, app.settings, it.toDouble(), "Radius")
                "Radius: $v $u"
            } ?: "Not Set"
            txtRadius.text = radiusStr
            
            val rodeStr = if (caps.hasChainCounter && state.rodeDeployed != null) {
                val (v, u) = SignalKUnitConverter.formatValue(app, app.settings, state.rodeDeployed, "distance")
                "Rode: $v $u"
            } else {
                "Rode: ---"
            }
            txtRode.text = rodeStr
            
            // Only show weigh anchor button if anchor is set and not already weighing
            val mm = plugin.maneuverManager
            btnWeigh.isVisible = anchorLat != 0.0 && mm?.activeManeuver !is WeighingAnchorManeuver
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
