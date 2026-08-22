package net.osmand.plus.plugins.nautical.raster

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import java.io.File
import java.util.Locale

class MarineRasterManagerFragment : BaseOsmAndFragment() {

    private lateinit var importer: MarineRasterImporter
    private lateinit var adapter: RasterFilesAdapter
    private lateinit var progressBar: ProgressBar
    private lateinit var emptyView: View
    private lateinit var recyclerView: RecyclerView
    private lateinit var txtChartCount: TextView

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importer = MarineRasterImporter(app)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_marine_raster_manager, container, false)

        progressBar = view.findViewById(R.id.progress)
        emptyView = view.findViewById(R.id.empty_view)
        recyclerView = view.findViewById(R.id.recycler_view)
        txtChartCount = view.findViewById(R.id.txt_chart_count)

        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        adapter = RasterFilesAdapter(
            onItemClicked = { file ->
                app.showToastMessage(file.name)
            },
            onItemLongClicked = { file ->
                showDeleteDialog(file)
            }
        )
        recyclerView.adapter = adapter

        view.findViewById<View>(R.id.btn_import_raster)?.setOnClickListener {
            importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
        }

        refreshList()
        return view
    }

    private fun refreshList() {
        val files = importer.getImportedCharts()
        adapter.setFiles(files)
        emptyView.isVisible = files.isEmpty()
        recyclerView.isVisible = files.isNotEmpty()
        txtChartCount.text = getString(R.string.raster_manager_title) + if (files.isNotEmpty()) " (${files.size})" else ""
    }

    private fun startImport(uri: Uri) {
        val fileName = getFileName(uri) ?: "chart_${System.currentTimeMillis()}.mbtiles"
        progressBar.isVisible = true

        lifecycleScope.launch {
            val result = importer.importRaster(uri, fileName)
            progressBar.isVisible = false
            if (result.isSuccess) {
                app.showToastMessage(R.string.raster_import_success)
                app.osmandMap.mapView.getLayerByClass(MarineRasterMapLayer::class.java)?.updateSources()
                refreshList()
                app.osmandMap.refreshMap()
            } else {
                app.showToastMessage(R.string.raster_import_error)
            }
        }
    }

    private fun getFileName(uri: Uri): String? {
        var name: String? = null
        app.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index != -1) {
                    name = cursor.getString(index)
                }
            }
        }
        return name
    }

    private fun showDeleteDialog(file: File) {
        AlertDialog.Builder(requireContext())
            .setTitle(R.string.raster_manager_title)
            .setMessage(R.string.raster_delete_confirm)
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                progressBar.isVisible = true
                lifecycleScope.launch {
                    val deleted = withContext(Dispatchers.IO) {
                        importer.deleteChart(file)
                    }
                    progressBar.isVisible = false
                    if (deleted) {
                        refreshList()
                        app.osmandMap.mapView.getLayerByClass(MarineRasterMapLayer::class.java)?.updateSources()
                        app.osmandMap.refreshMap()
                    }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private inner class RasterFilesAdapter(
        private val onItemClicked: (File) -> Unit,
        private val onItemLongClicked: (File) -> Unit
    ) : RecyclerView.Adapter<RasterFilesAdapter.ViewHolder>() {

        private var files = emptyList<File>()

        fun setFiles(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_marine_raster_chart, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val file = files[position]
            holder.title.text = file.name

            val sizeMb = file.length() / (1024.0 * 1024.0)
            val ext = file.extension.uppercase(Locale.US)
            val sizeFormatted = String.format(Locale.US, "%.1f MB", sizeMb)
            holder.description.text = "$sizeFormatted • $ext"
            holder.badgeChartType.text = ext

            holder.itemView.setOnClickListener {
                onItemClicked(file)
            }
            holder.itemView.setOnLongClickListener {
                onItemLongClicked(file)
                true
            }
        }

        override fun getItemCount(): Int = files.size

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val title: TextView = view.findViewById(R.id.title)
            val description: TextView = view.findViewById(R.id.description)
            val badgeChartType: TextView = view.findViewById(R.id.badge_chart_type)
            val icon: ImageView = view.findViewById(R.id.icon)
        }
    }
}
