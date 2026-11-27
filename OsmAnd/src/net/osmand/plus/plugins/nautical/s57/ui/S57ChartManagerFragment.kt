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
import net.osmand.plus.plugins.nautical.s57.S57CellFiles
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
                txtCoverageStats.text = getString(R.string.nautical_s57_status_fmt, status.localized(app))
                refreshCharts()
            }
        }
        
        return root
    }

    private fun refreshCharts() {
        viewLifecycleOwner.lifecycleScope.launch {
            val encFiles = withContext(Dispatchers.IO) {
                val encDir = app.getAppPath("nautical/enc")
                (encDir.listFiles { _, name ->
                    S57CellFiles.isChartFile(name)
                }?.toList() ?: emptyList()).sortedWith(
                        // Each base cell followed by its update files, cells alphabetically
                        compareBy({ S57CellFiles.cellName(it.name) }, { !S57CellFiles.isUpdateFile(it.name) }, { S57CellFiles.updateNumber(it.name) })
                )
            }

            val plugin = NauticalPlugin.getInstance()
            val index = plugin?.s57SpatialIndex
            val boundsMap = withContext(Dispatchers.IO) {
                index?.getChartBounds() ?: emptyMap()
            }
            // Files whose current content was already applied; update files
            // that only delete or modify features leave no rows of their own.
            val appliedPaths = withContext(Dispatchers.IO) {
                encFiles.filter { index?.isFileIndexed(it) == true }.map { it.absolutePath }.toSet()
            }

            recyclerView.adapter = ChartAdapter(encFiles, boundsMap, appliedPaths)
            emptyView.visibility = if (encFiles.isEmpty()) View.VISIBLE else View.GONE
            
            val area = calculateTotalAreaNm(boundsMap.values)
            val status = plugin?.s57SpatialIndex?.indexingStatus?.value
            txtCoverageStats.text = getString(
                R.string.nautical_s57_coverage_fmt,
                boundsMap.size,
                String.format(Locale.US, "%.1f", area),
                status?.localized(app) ?: ""
            )
        }
    }

    private fun calculateTotalAreaNm(bounds: Collection<DoubleArray>): Double {
        // Grid-based coverage with overlap dedup (0.1 degree cells).
        // Cell indices use Math.floor: toInt() truncates toward zero and
        // merges the -0.1..0 band with the 0..0.1 band in the southern and
        // western hemispheres.
        val grid = mutableSetOf<Pair<Int, Int>>()
        val step = 0.1 // 0.1 degree resolution

        bounds.forEach { b ->
            // b = [latMin, latMax, lonMin, lonMax]; antimeridian-wrapping
            // cells have lonMin > lonMax and are split into west/east parts
            val lonRanges = if (b[2] <= b[3]) {
                listOf(b[2] to b[3])
            } else {
                listOf(b[2] to 180.0, -180.0 to b[3])
            }
            for ((lonFrom, lonTo) in lonRanges) {
                var lat = b[0]
                while (lat < b[1]) {
                    var lon = lonFrom
                    while (lon < lonTo) {
                        grid.add(Math.floor(lat / step).toInt() to Math.floor(lon / step).toInt())
                        lon += step
                    }
                    lat += step
                }
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

    private inner class ChartAdapter(
        private val files: List<File>,
        private val bounds: Map<String, DoubleArray>,
        private val appliedPaths: Set<String>
    ) : RecyclerView.Adapter<ChartViewHolder>() {
        private val colorGreen = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.nautical_status_green)
        private val colorOrange = androidx.core.content.ContextCompat.getColor(requireContext(), R.color.nautical_status_orange)

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ChartViewHolder {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_s57_chart, parent, false)
            return ChartViewHolder(v)
        }
        override fun onBindViewHolder(holder: ChartViewHolder, position: Int) {
            val file = files[position]
            val isS63 = file.name.uppercase(Locale.US).endsWith(".031") || file.name.uppercase(Locale.US).endsWith(".ENC")
            val isUpdate = S57CellFiles.isUpdateFile(file.name)
            // "S-57" / "S-63" are IHO transfer-standard names and stay
            // untranslated, like other product names
            val baseType = if (isS63) "S-63 (Encrypted)" else "S-57"
            val typeLabel = if (isUpdate) getString(R.string.nautical_s57_chart_type_update_fmt, baseType) else baseType
            val isApplied = bounds.containsKey(file.absolutePath) || appliedPaths.contains(file.absolutePath)

            holder.title.text = file.name
            holder.info.text = String.format(Locale.US, getString(R.string.nautical_s57_chart_info_fmt), file.length() / 1024.0, typeLabel)
            holder.status.text = getString(if (isApplied) R.string.nautical_s57_status_indexed else R.string.nautical_s57_status_pending)
            holder.status.setTextColor(if (isApplied) colorGreen else colorOrange)

            holder.itemView.setOnClickListener {
                val b = bounds[file.absolutePath]
                if (b != null) {
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
