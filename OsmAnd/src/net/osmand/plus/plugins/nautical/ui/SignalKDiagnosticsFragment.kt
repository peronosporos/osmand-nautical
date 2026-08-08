package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.CapabilityManager

class SignalKDiagnosticsFragment : BaseOsmAndFragment() {

    private lateinit var adapter: DiagnosticAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = DiagnosticAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.collectLatest { caps ->
                adapter.submitList(generateDiagnosticItems(caps))
            }
        }

        return view
    }

    private fun generateDiagnosticItems(caps: CapabilityManager.ServerCapabilityMap): List<DiagnosticItem> {
        val items = mutableListOf<DiagnosticItem>()
        
        items.add(DiagnosticItem("Polar Performance", caps.hasPolarPerformance, "Used for VMG and target speed. Install signalk-polar-performance."))
        items.add(DiagnosticItem("Autopilot Control", caps.hasAutopilot, "Enables Pilot UI. requires pypilot or signalk-autopilot."))
        items.add(DiagnosticItem("Marine Logbook", caps.hasLogging, "Server-side trip logging. Install signalk-logbook."))
        items.add(DiagnosticItem("Tide Predictions", caps.hasSignalKTides, "Tidal heights and stations. Install signalk-tides."))
        items.add(DiagnosticItem("GRIB Weather", caps.hasGrib, "Weather overlays. Install signalk-grib-weather-provider."))
        items.add(DiagnosticItem("AIS Prioritization", caps.hasAisPrioritizer, "Smart AIS target pruning and CPA offloading."))
        items.add(DiagnosticItem("Digital Switching", caps.hasDigitalSwitching, "Electrical panel control for switches."))
        items.add(DiagnosticItem("Environment Sensors", caps.hasEnvironmentSensors, "Pressure, humidity, and air temperature data."))
        items.add(DiagnosticItem("Forward Watch", caps.hasForwardWatch, "Hazard detection for depths and obstructions."))
        items.add(DiagnosticItem("Collision Risk", caps.hasAdvancedSafety, "Server-side CPA/TCPA alarm generation."))

        return items
    }

    private data class DiagnosticItem(val feature: String, val active: Boolean, val guidance: String)

    private class DiagnosticAdapter : ListAdapter<DiagnosticItem, DiagnosticViewHolder>(
        object : androidx.recyclerview.widget.DiffUtil.ItemCallback<DiagnosticItem>() {
            override fun areItemsTheSame(oldItem: DiagnosticItem, newItem: DiagnosticItem): Boolean = oldItem.feature == newItem.feature
            override fun areContentsTheSame(oldItem: DiagnosticItem, newItem: DiagnosticItem): Boolean = oldItem == newItem
        }
    ) {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DiagnosticViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_with_descr, parent, false)
            return DiagnosticViewHolder(view)
        }

        override fun onBindViewHolder(holder: DiagnosticViewHolder, position: Int) {
            val item = getItem(position)
            holder.title.text = item.feature
            holder.description.text = if (item.active) "Active / Configured" else item.guidance
            val colorRes = if (item.active) R.color.nautical_status_green else R.color.nautical_status_red
            holder.description.setTextColor(ContextCompat.getColor(holder.itemView.context, colorRes))
        }
    }

    private class DiagnosticViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val description: TextView = view.findViewById(R.id.description)
    }
}
