package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.FrameLayout
import android.widget.TextView
import androidx.core.view.isVisible
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.utils.AndroidUtils
import kotlin.math.abs

class TacticsHudHeader @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr), INauticalHudHeader {

    private val tacticLabel: TextView

    init {
        LayoutInflater.from(context).inflate(R.layout.nautical_tactics_hud, this, true)
        tacticLabel = findViewById(R.id.tactic_label)
        isVisible = false
    }

    override fun setCompactMode(enabled: Boolean) {
        val p = if (enabled) 2f else 8f
        val px = AndroidUtils.dpToPx(context, p)
        setPadding(px, px, px, px)
    }

    fun update() {
        val plugin = NauticalPlugin.getInstance() ?: return
        val processor = plugin.tacticalProcessor ?: return
        val state = NauticalPlugin.engine?.getCurrentState() ?: return
        
        // Item 15: Optimal Tack/Gybe Indicator
        val lat = state.latitude ?: 0.0
        val lon = state.longitude ?: 0.0
        val target = processor.targetWaypoint
        
        if (target == null) {
            isVisible = false
            return
        }
        
        val dPort = processor.portLaylineEnd?.let { 
             processor.distanceToSegment(lat, lon, it.first, it.second, target.first, target.second)
        } ?: Double.MAX_VALUE
        
        val dStbd = processor.starboardLaylineEnd?.let {
             processor.distanceToSegment(lat, lon, it.first, it.second, target.first, target.second)
        } ?: Double.MAX_VALUE

        // If we are within 50m of a layline, show the prompt
        if (dPort < 50.0 || dStbd < 50.0) {
            isVisible = true
            val twa = state.trueWindAngle ?: 0.0
            val action = if (abs(twa) < Math.PI / 2.0) {
                context.getString(R.string.nautical_tack)
            } else {
                context.getString(R.string.nautical_gybe)
            }
            tacticLabel.text = context.getString(R.string.nautical_tactics_prompt, action)
            tacticLabel.setTextColor(NauticalColorResolver.getColor(context, NauticalSemanticColor.STATUS_WARNING))
        } else {
            isVisible = false
        }
    }
}
