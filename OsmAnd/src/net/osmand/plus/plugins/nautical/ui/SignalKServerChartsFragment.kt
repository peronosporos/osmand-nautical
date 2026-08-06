package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKChart

class SignalKServerChartsFragment : BaseOsmAndFragment() {

    private lateinit var adapter: ChartsAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.recyclerview_fragment, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = ChartsAdapter { chart -> enableChartOverlay(chart) }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        refreshCharts()

        return view
    }

    private fun refreshCharts() {
        lifecycleScope.launch {
            val charts = NauticalPlugin.engine?.getRestService()?.getCharts()?.body()
            if (charts != null) {
                adapter.submitList(charts.values.toList())
                view?.findViewById<View>(R.id.txt_empty_list)?.visibility = if (charts.isEmpty()) View.VISIBLE else View.GONE
            }
        }
    }

    private fun enableChartOverlay(chart: SignalKChart) {
        // For now, we reuse SignalKRasterLayer logic or similar
        // Implementation might involve updating a setting that the layer observes
        app.showToastMessage("Enabling overlay for ${chart.name ?: chart.identifier}")
        // Future: settings.NAUTICAL_ACTIVE_SERVER_CHART.set(chart.identifier)
    }

    private class ChartsAdapter(private val onEnable: (SignalKChart) -> Unit) : ListAdapter<SignalKChart, ChartViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_server_chart, parent, false)
            return ChartViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
            holder.bind(getItem(position), onEnable)
        }
    }

    private class DiffCallback : DiffUtil.ItemCallback<SignalKChart>() {
        override fun areItemsTheSame(oldItem: SignalKChart, newItem: SignalKChart): Boolean = oldItem.identifier == newItem.identifier
        override fun areContentsTheSame(oldItem: SignalKChart, newItem: SignalKChart): Boolean = oldItem == newItem
    }

    private class ChartViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val txtName: TextView = view.findViewById(R.id.txt_chart_name)
        private val txtDesc: TextView = view.findViewById(R.id.txt_chart_desc)
        private val btnEnable: MaterialButton = view.findViewById(R.id.btn_enable_overlay)

        fun bind(chart: SignalKChart, onEnable: (SignalKChart) -> Unit) {
            txtName.text = chart.name ?: chart.identifier
            txtDesc.text = "${chart.type ?: "Raster"} | MinZ:${chart.minzoom ?: 0} MaxZ:${chart.maxzoom ?: 18}"
            btnEnable.setOnClickListener { onEnable(chart) }
        }
    }
}
