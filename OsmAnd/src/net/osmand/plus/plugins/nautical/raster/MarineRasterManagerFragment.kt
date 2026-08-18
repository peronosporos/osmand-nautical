package net.osmand.plus.plugins.nautical.raster

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import java.io.File

class MarineRasterManagerFragment : BaseOsmAndFragment() {

    private lateinit var importer: MarineRasterImporter
    private lateinit var adapter: RasterFilesAdapter
    private lateinit var progressBar: ProgressBar

    private val importLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { startImport(it) }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        importer = MarineRasterImporter(app)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_simple_list, container, false)
        
        view.findViewById<View>(R.id.closeButton)?.setOnClickListener {
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }
        
        view.findViewById<TextView>(R.id.titleTextView)?.text = getString(R.string.raster_manager_title)
        
        val pb = view.findViewById<ProgressBar?>(R.id.progress)
        if (pb != null) {
            progressBar = pb
            progressBar.isVisible = false
        }
        
        val listView = view.findViewById<ListView?>(android.R.id.list)
        adapter = RasterFilesAdapter()
        listView?.adapter = adapter
        
        // Add footer button for import
        if (listView != null) {
            val footerView = inflater.inflate(R.layout.bottom_sheet_button, listView, false)
            footerView.findViewById<TextView>(R.id.button_text)?.text = getString(R.string.raster_import_btn)
            footerView.setOnClickListener {
                importLauncher.launch(arrayOf("application/octet-stream", "*/*"))
            }
            listView.addFooterView(footerView)

            listView.setOnItemLongClickListener { _, _, position, _ ->
                // Fix for Item 15: Adjust position if footer is present
                val adjPos = position - listView.headerViewsCount
                if (adjPos >= 0 && adjPos < adapter.count) {
                    val file = adapter.getItem(adjPos) as? File
                    file?.let { showDeleteDialog(it) }
                    return@setOnItemLongClickListener true
                }
                false
            }
        }

        refreshList()
        return view
    }

    private fun refreshList() {
        val files = importer.getImportedCharts()
        adapter.setFiles(files)
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
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.raster_manager_title)
            .setMessage(R.string.raster_delete_confirm)
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                progressBar.isVisible = true
                lifecycleScope.launch {
                    val deleted = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
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

    private class RasterFilesAdapter : BaseAdapter() {
        private var files = emptyList<File>()

        fun setFiles(newFiles: List<File>) {
            files = newFiles
            notifyDataSetChanged()
        }

        override fun getCount(): Int = files.size
        override fun getItem(position: Int): Any = files[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(parent?.context)
                .inflate(R.layout.list_item_with_descr, parent, false)
            
            val file = files[position]
            view.findViewById<TextView>(R.id.title)?.text = file.name
            val sizeMb = file.length() / 1024 / 1024
            var desc = parent?.context?.getString(R.string.shared_string_memory_mb_desc, sizeMb.toString())
            if (file.extension.equals("kap", ignoreCase = true)) {
                desc += " | " + parent?.context?.getString(R.string.nautical_kap_metadata_only)
            }
            view.findViewById<TextView>(R.id.description)?.text = desc
            view.findViewById<ImageView>(R.id.icon)?.setImageResource(R.drawable.ic_action_world_globe)
            
            return view
        }
    }
}
