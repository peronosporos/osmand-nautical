package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.*

class SignalKTideTableFragment : BaseOsmAndFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_technical_stats, container, false)
        
        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                updateView(view, state)
            }
        }
        
        return view
    }

    private fun updateView(root: View, state: net.osmand.plus.plugins.nautical.engine.MarineState) {
        root.findViewById<TextView>(R.id.txt_vessel_title).text = getString(R.string.layer_tides_title)
        
        val tide = state.tide ?: net.osmand.plus.plugins.nautical.engine.TideState()
        
        val grid = root.findViewById<View>(R.id.grid_identity)
        fillCell(grid, 11, R.drawable.ic_action_nautical_water_temp, "Station", tide.stationName ?: "N/A")
        fillCell(grid, 12, R.drawable.ic_action_altitude, "Height Now", "${tide.heightNow ?: "N/A"}m")
        fillCell(grid, 13, R.drawable.ic_action_settings, "State", tide.state ?: "N/A")
        
        val nextExtreme = tide.nextExtremeTime?.let {
            val sdf = java.text.SimpleDateFormat("HH:mm", Locale.US)
            sdf.format(Date(it))
        } ?: "N/A"
        
        fillCell(grid, 21, R.drawable.ic_action_time, "Next Extr.", nextExtreme)
        fillCell(grid, 22, R.drawable.ic_action_altitude, "Next Height", "${tide.nextExtremeHeight ?: "N/A"}m")
        fillCell(grid, 23, R.drawable.ic_action_settings, "Next Type", tide.nextExtremeType ?: "N/A")

        // Hide other grids
        root.findViewById<View>(R.id.grid_systems).visibility = View.GONE
        root.findViewById<View>(R.id.grid_power).visibility = View.GONE
        root.findViewById<View>(R.id.grid_environment).visibility = View.GONE
        root.findViewById<View>(R.id.header_rigging).visibility = View.GONE
        root.findViewById<View>(R.id.grid_rigging).visibility = View.GONE
        root.findViewById<View>(R.id.header_pypilot).visibility = View.GONE
        root.findViewById<View>(R.id.grid_pypilot).visibility = View.GONE
    }

    private fun fillCell(root: View, cellIdx: Int, iconId: Int, label: String, value: String) {
        val icon: android.widget.ImageView? = when(cellIdx) {
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
    }
}
