package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.Locale

class NauticalGnssStatusFragment : BaseOsmAndFragment() {

    private var posUpdateCount = 0
    private var lastPosTs = 0L
    private var posDropouts = 0
    private var lastSampleTime = System.currentTimeMillis()
    private var posHzRate = 0.0

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_technical_stats, container, false)
        
        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                updateMetrics(state)
                updateView(view, state)
            }
        }
        
        return view
    }

    private fun updateMetrics(state: net.osmand.plus.plugins.nautical.engine.MarineState) {
        val now = System.currentTimeMillis()
        val posTs = state.timestamps["navigation.position"] ?: 0L
        if (posTs != 0L && posTs != lastPosTs) {
            if (lastPosTs != 0L && (posTs - lastPosTs) > 3000L) {
                posDropouts++
            }
            posUpdateCount++
            lastPosTs = posTs
        }

        val dt = (now - lastSampleTime) / 1000.0
        if (dt >= 1.0) {
            posHzRate = posUpdateCount / dt
            posUpdateCount = 0
            lastSampleTime = now
        }
    }

    private fun updateView(root: View, state: net.osmand.plus.plugins.nautical.engine.MarineState) {
        val isNightVision = NauticalPlugin.isNightVision(app)
        if (isNightVision) {
            root.setBackgroundColor(0xEE120000.toInt())
            root.findViewById<View>(R.id.stats_container)?.setBackgroundColor(0xEE120000.toInt())
        }

        val title = root.findViewById<TextView>(R.id.txt_vessel_title)
        title?.text = getString(R.string.nautical_gnss_quality)
        if (isNightVision) {
            title?.setTextColor(0xFFFF1744.toInt())
        }
        
        val gnss = state.gnss ?: net.osmand.plus.plugins.nautical.engine.GnssState()
        val now = System.currentTimeMillis()
        val posTs = state.timestamps["navigation.position"] ?: 0L
        val ageSec = if (posTs > 0L) ((now - posTs) / 1000.0).coerceAtLeast(0.0) else -1.0
        val isStale = ageSec < 0 || ageSec > 5.0

        val ageStr = if (ageSec >= 0) String.format(Locale.US, "%.1fs", ageSec) else "N/A"
        val rateStr = String.format(Locale.US, "%.1f Hz", posHzRate)
        
        val grid = root.findViewById<View?>(R.id.grid_identity)
        fillCell(grid, 11, R.drawable.ic_action_device_location, "Method", gnss.method ?: "N/A", isNightVision, false)
        fillCell(grid, 12, R.drawable.ic_action_info, "Satellites", (gnss.satellites ?: 0).toString(), isNightVision, false)
        fillCell(grid, 13, R.drawable.ic_action_info, "Integrity", gnss.integrity ?: "N/A", isNightVision, false)
        fillCell(grid, 21, R.drawable.ic_action_settings, "HDOP / VDOP", "${gnss.horizontalDilution ?: "--"}/${gnss.verticalDilution ?: "--"}", isNightVision, false)
        fillCell(grid, 22, R.drawable.ic_action_speed, "Rate / Drops", "$rateStr ($posDropouts)", isNightVision, false)
        fillCell(grid, 23, R.drawable.ic_action_time, "Age", ageStr, isNightVision, isStale)

        // Hide other grids as we reuse the stats layout
        root.findViewById<View>(R.id.grid_systems)?.visibility = View.GONE
        root.findViewById<View>(R.id.grid_power)?.visibility = View.GONE
        root.findViewById<View>(R.id.grid_environment)?.visibility = View.GONE
        root.findViewById<View>(R.id.header_rigging)?.visibility = View.GONE
        root.findViewById<View>(R.id.grid_rigging)?.visibility = View.GONE
        root.findViewById<View>(R.id.header_pypilot)?.visibility = View.GONE
        root.findViewById<View>(R.id.grid_pypilot)?.visibility = View.GONE
    }

    private fun fillCell(
        root: View?,
        cellIdx: Int,
        iconId: Int,
        label: String,
        value: String,
        isNightVision: Boolean,
        isStale: Boolean
    ) {
        if (root == null) return
        val icon: ImageView? = when(cellIdx) {
            11 -> root.findViewById(R.id.img_icon_1_1)
            12 -> root.findViewById(R.id.img_icon_1_2)
            13 -> root.findViewById(R.id.img_icon_1_3)
            21 -> root.findViewById(R.id.img_icon_2_1)
            22 -> root.findViewById(R.id.img_icon_2_2)
            23 -> root.findViewById(R.id.img_icon_2_3)
            else -> null
        }
        val lbl: TextView? = when(cellIdx) {
            11 -> root.findViewById(R.id.txt_label_1_1)
            12 -> root.findViewById(R.id.txt_label_1_2)
            13 -> root.findViewById(R.id.txt_label_1_3)
            21 -> root.findViewById(R.id.txt_label_2_1)
            22 -> root.findViewById(R.id.txt_label_2_2)
            23 -> root.findViewById(R.id.txt_label_2_3)
            else -> null
        }
        val valTxt: TextView? = when(cellIdx) {
            11 -> root.findViewById(R.id.txt_value_1_1)
            12 -> root.findViewById(R.id.txt_value_1_2)
            13 -> root.findViewById(R.id.txt_value_1_3)
            21 -> root.findViewById(R.id.txt_value_2_1)
            22 -> root.findViewById(R.id.txt_value_2_2)
            23 -> root.findViewById(R.id.txt_value_2_3)
            else -> null
        }

        icon?.setImageResource(iconId)
        lbl?.text = label
        valTxt?.text = value

        if (isNightVision) {
            icon?.setColorFilter(if (isStale) 0x80FF1744.toInt() else 0xFFFF1744.toInt())
            lbl?.setTextColor(0xFFFF8A80.toInt())
            valTxt?.setTextColor(if (isStale) 0x80FF1744.toInt() else 0xFFFF1744.toInt())
        }
    }
}
