package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.appcompat.app.AlertDialog
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
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
        
        adapter = ChartsAdapter(
            onEnable = { chart -> enableChartOverlay(chart) },
            onDelete = { chart -> confirmDeleteChart(chart) }
        )
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        refreshCharts()

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().addMenuProvider(object : MenuProvider {
            override fun onCreateMenu(menu: android.view.Menu, menuInflater: android.view.MenuInflater) {
                menu.add(0, 1, 0, "Refresh")
                    .setIcon(R.drawable.ic_action_refresh_dark)
                    .setShowAsAction(android.view.MenuItem.SHOW_AS_ACTION_IF_ROOM)
            }

            override fun onMenuItemSelected(menuItem: android.view.MenuItem): Boolean {
                if (menuItem.itemId == 1) {
                    refreshCharts()
                    return true
                }
                return false
            }
        }, viewLifecycleOwner, Lifecycle.State.RESUMED)
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

    private fun confirmDeleteChart(chart: SignalKChart) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.nautical_chart_delete_confirm, chart.name ?: chart.identifier))
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                lifecycleScope.launch {
                    val rest = NauticalPlugin.engine?.getRestService()
                    if (rest != null) {
                        try {
                            val response = rest.deleteChart(chart.identifier)
                            if (response.isSuccessful) {
                                app.showToastMessage(R.string.nautical_chart_deleted)
                                refreshCharts()
                            } else {
                                app.showToastMessage(getString(R.string.nautical_toast_server_error, response.code()))
                            }
                        } catch (e: Exception) {
                            app.showToastMessage(getString(R.string.nautical_toast_conn_failed))
                        }
                    }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun enableChartOverlay(chart: SignalKChart) {
        app.settings.NAUTICAL_ACTIVE_SERVER_CHART.set(chart.identifier)
        app.settings.NAUTICAL_SHOW_RASTER_CHARTS.set(true)
        app.showToastMessage(getString(R.string.nautical_chart_overlay_enabled, chart.name ?: chart.identifier))
        app.osmandMap.refreshMap()
    }

    private class ChartsAdapter(
        private val onEnable: (SignalKChart) -> Unit,
        private val onDelete: (SignalKChart) -> Unit
    ) : ListAdapter<SignalKChart, ChartViewHolder>(DiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_server_chart, parent, false)
            return ChartViewHolder(view)
        }

        override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
            holder.bind(getItem(position), onEnable, onDelete)
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
        private val btnDelete: MaterialButton = view.findViewById(R.id.btn_delete_chart)

        fun bind(chart: SignalKChart, onEnable: (SignalKChart) -> Unit, onDelete: (SignalKChart) -> Unit) {
            txtName.text = chart.name ?: chart.identifier
            txtDesc.text = "${chart.type ?: "Raster"} | MinZ:${chart.minzoom ?: 0} MaxZ:${chart.maxzoom ?: 18}"
            btnEnable.setOnClickListener { onEnable(chart) }
            btnDelete.setOnClickListener { onDelete(chart) }
        }
    }
}

