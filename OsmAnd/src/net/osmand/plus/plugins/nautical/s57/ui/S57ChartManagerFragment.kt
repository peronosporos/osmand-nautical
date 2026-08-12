package net.osmand.plus.plugins.nautical.s57.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.fragments.SettingsScreenType
import java.io.File
import java.util.Locale
import kotlin.math.abs
import kotlin.math.cos

class S57ChartManagerFragment : BaseOsmAndFragment() {

    private lateinit var emptyView: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnManagePermits: Button
    private lateinit var txtCoverageStats: TextView

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = themedInflater.inflate(R.layout.fragment_s57_chart_manager, container, false)
        
        emptyView = root.findViewById(R.id.empty_view)
        recyclerView = root.findViewById(R.id.recycler_view)
        btnManagePermits = root.findViewById(R.id.btn_manage_permits)
        txtCoverageStats = root.findViewById(R.id.txt_coverage_stats)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        
        btnManagePermits.setOnClickListener {
            net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.S63_PERMIT_MANAGER)
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val plugin = NauticalPlugin.getInstance()
            plugin?.s57SpatialIndex?.indexingStatus?.collect { status ->
                txtCoverageStats.text = "Status: $status"
                refreshCharts()
            }
        }
        
        return root
    }

    private fun refreshCharts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val encFiles = withContext(Dispatchers.IO) {
                val encDir = app.getAppPath("nautical/enc")
                encDir.listFiles { _, name -> 
                    val up = name.uppercase()
                    up.endsWith(".000") || up.endsWith(".031") || up.endsWith(".ENC")
                }?.toList() ?: emptyList()
            }

            val plugin = NauticalPlugin.getInstance()
            val boundsMap = withContext(Dispatchers.IO) {
                plugin?.s57SpatialIndex?.getChartBounds() ?: emptyMap()
            }
            
            recyclerView.adapter = ChartAdapter(encFiles, boundsMap)
            emptyView.visibility = if (encFiles.isEmpty()) View.VISIBLE else View.GONE
            
            val area = calculateTotalAreaNm(boundsMap.values)
            val status = plugin?.s57SpatialIndex?.indexingStatus?.value ?: ""
            txtCoverageStats.text = String.format(Locale.US, "Indexed: %d | Coverage: %.1f sq NM\n%s", 
                boundsMap.size, area, status)
        }
    }

    private fun calculateTotalAreaNm(bounds: Collection<DoubleArray>): Double {
        // Use a grid-based approach to account for overlaps
        val grid = mutableSetOf<Pair<Int, Int>>()
        val step = 0.1 // 0.1 degree resolution
        
        bounds.forEach { b ->
            var lat = b[0]
            while (lat < b[1]) {
                var lon = b[2]
                while (lon < b[3]) {
                    grid.add((lat / step).toInt() to (lon / step).toInt())
                    lon += step
                }
                lat += step
            }
        }
        
        var totalArea = 0.0
        grid.forEach { (gLat, _) ->
            val lat = gLat * step
            val dLat = step * 60.0 // NM
            val dLon = step * 60.0 * cos(Math.toRadians(lat + step / 2.0))
            totalArea += abs(dLat * dLon)
        }
        return totalArea
    }

    private inner class ChartAdapter(private val files: List<File>, private val bounds: Map<String, DoubleArray>) : RecyclerView.Adapter<ChartViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_s57_chart, parent, false)
            return ChartViewHolder(v)
        }
        override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
            val file = files[position]
            val isS63 = file.name.uppercase().endsWith(".031") || file.name.uppercase().endsWith(".ENC")
            val hasIndex = bounds.containsKey(file.absolutePath)
            
            holder.title.text = file.name
            holder.info.text = String.format(Locale.US, "Size: %.1f KB | Type: %s", file.length() / 1024.0, if (isS63) "S-63 (Encrypted)" else "S-57")
            holder.status.text = if (hasIndex) "INDEXED" else "PENDING"
            holder.status.setTextColor(if (hasIndex) 0xFF4CAF50.toInt() else 0xFFFF9800.toInt())
            
            holder.itemView.setOnClickListener {
                if (hasIndex) {
                    val b = bounds[file.absolutePath]!!
                    app.settings.setMapLocationToShow((b[0] + b[1]) / 2.0, (b[2] + b[3]) / 2.0, 12)
                    app.runInUIThread { requireActivity().onBackPressedDispatcher.onBackPressed() }
                }
            }
        }
        override fun getItemCount(): Int = files.size
    }

    private class ChartViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.chart_title)
        val info: TextView = v.findViewById(R.id.chart_info)
        val status: TextView = v.findViewById(R.id.chart_status)
    }
}
