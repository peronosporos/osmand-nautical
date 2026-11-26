package net.osmand.plus.plugins.nautical.ui.cockpit

import android.content.Context
import android.graphics.Typeface
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.AisSortMode
import net.osmand.plus.plugins.nautical.engine.AisTargetSummary
import java.util.Locale

/**
 * VHF page of the Cockpit shell: the AIS traffic picture at a glance, sorted
 * by threat (CPA/TCPA). Each row shows name, range, bearing, and the CPA/TCPA
 * verdict with traffic-light color. Read-only in this phase: hailing,
 * DSC and mute controls stay in the map-side surfaces until scope is decided.
 */
class VhfScreen(private val app: OsmandApplication) : CockpitScreen {

    override val paneId: CockpitScreenId = CockpitScreenId.VHF

    private var scope: CoroutineScope? = null
    private var listContainer: LinearLayout? = null

    override fun onCreateView(context: Context, parent: ViewGroup): View {
        val night = CockpitScreenTheme.isDark(app)
        val bg = if (night) 0xFF101010.toInt() else 0xFFF5F5F5.toInt()
        val fg = if (night) 0xFFDDDDDD.toInt() else 0xFF202020.toInt()

        val scroll = ScrollView(context).apply { setBackgroundColor(bg) }
        listContainer = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(16), dp(24), dp(16))
        }
        scroll.addView(
            listContainer,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        return scroll
    }

    override fun onStart() {
        scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
        scope?.launch {
            // Periodic poll like the AIS target list sheet; targets are
            // recomputed inside the AIS manager on each request.
            while (isActive) {
                renderTargets()
                delay(1000L)
            }
        }
    }

    override fun onStop() {
        scope?.cancel()
        scope = null
    }

    private fun isThreat(t: AisTargetSummary): Boolean =
        (t.cpaNm ?: Double.MAX_VALUE) < 1.0 && (t.tcpaSec ?: -1.0) > 0

    private fun isCaution(t: AisTargetSummary): Boolean =
        !isThreat(t) && (
            ((t.cpaNm ?: Double.MAX_VALUE) < 2.0 && (t.tcpaSec ?: -1.0) > 0) ||
                ((t.sogKnots ?: 0.0) > 0.5 && t.rangeMeters < 3704.0)
            )

    private fun renderTargets() {
        val container = listContainer ?: return
        val night = CockpitScreenTheme.isDark(app)
        val fg = if (night) 0xFFDDDDDD.toInt() else 0xFF202020.toInt()
        val targets = NauticalPlugin.getInstance()
            ?.aisManager
            ?.getActiveTargets(AisSortMode.THREAT_CPA)
            ?: emptyList()

        container.removeAllViews()

        if (targets.isEmpty()) {
            container.addView(TextView(app).apply {
                setText(R.string.nautical_cockpit_vhf_none)
                setTextColor(fg)
                alpha = 0.7f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            })
            return
        }

        targets.take(MAX_ROWS).forEach { t ->
            val color = when {
                isThreat(t) -> 0xFFFF1744.toInt()
                isCaution(t) -> 0xFFF9A825.toInt()
                else -> if (night) 0xFF8BC34A.toInt() else 0xFF2E7D32.toInt()
            }
            val row = LinearLayout(app).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val p = dp(10)
                setPadding(0, p, 0, p)
            }
            val name = TextView(app).apply {
                text = t.name
                setTextColor(fg)
                typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            }
            val details = TextView(app).apply {
                text = buildDetails(t)
                setTextColor(fg)
                alpha = 0.85f
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            }
            val verdict = TextView(app).apply {
                text = verdictLabel(t)
                setTextColor(color)
                typeface = Typeface.DEFAULT_BOLD
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.END
            }
            row.addView(name, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
            row.addView(details, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 3f))
            row.addView(verdict, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 2f))
            container.addView(
                row,
                LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            )
        }
    }

    private fun buildDetails(t: AisTargetSummary): String {
        // Show '--' instead of fabricated range/bearing when own fix is unknown (range = sentinel).
        val rangeBearing = if (t.rangeMeters < Double.MAX_VALUE / 2) {
            String.format(Locale.US, "%.1f nm / %.0f°", t.rangeMeters / 1852.0, t.bearingDeg)
        } else {
            "--"
        }
        val sog = t.sogKnots?.let { app.getString(R.string.nautical_fmt_kn, it) } ?: "--"
        return "$rangeBearing · $sog"
    }

    private fun verdictLabel(t: AisTargetSummary): String = when {
        isThreat(t) -> app.getString(
            R.string.nautical_cockpit_vhf_threat,
            String.format(Locale.US, "%.1f", t.cpaNm ?: 0.0)
        )
        isCaution(t) -> app.getString(R.string.nautical_cockpit_vhf_caution)
        else -> app.getString(R.string.nautical_cockpit_vhf_clear)
    }

    private fun dp(v: Int): Int = (v * app.resources.displayMetrics.density).toInt()

    companion object {
        private const val MAX_ROWS = 12
    }
}
