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

class NauticalGnssStatusFragment : BaseOsmAndFragment() {

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
        root.findViewById<TextView>(R.id.txt_vessel_title).text = getString(R.string.nautical_gnss_quality)
        
        val gnss = state.gnss ?: net.osmand.plus.plugins.nautical.engine.GnssState()
        
        val grid = root.findViewById<View>(R.id.grid_identity)
        fillCell(grid, 11, R.drawable.ic_action_device_location, "Method", gnss.method ?: "N/A")
        fillCell(grid, 12, R.drawable.ic_action_info, "Satellites", (gnss.satellites ?: 0).toString())
        fillCell(grid, 13, R.drawable.ic_action_info, "Integrity", gnss.integrity ?: "N/A")
        fillCell(grid, 21, R.drawable.ic_action_settings, "HDOP", gnss.horizontalDilution?.toString() ?: "N/A")
        fillCell(grid, 22, R.drawable.ic_action_settings, "VDOP", gnss.verticalDilution?.toString() ?: "N/A")
        fillCell(grid, 23, R.drawable.ic_action_info, "Age", "${(System.currentTimeMillis() - (state.timestamps["navigation.position"] ?: 0L)) / 1000}s")

        // Hide other grids as we reuse the stats layout
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
