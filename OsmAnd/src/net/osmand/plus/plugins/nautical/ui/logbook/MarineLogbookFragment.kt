package net.osmand.plus.plugins.nautical.ui.logbook

import android.os.Bundle
import android.view.*
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.GpxStreamer
import net.osmand.plus.plugins.nautical.logbook.export.LogbookCsvExporter
import net.osmand.plus.plugins.nautical.viewmodel.MarineLogbookViewModel
import java.io.FileInputStream

class MarineLogbookFragment : BaseOsmAndFragment() {

    private lateinit var viewModel: MarineLogbookViewModel
    private lateinit var adapter: MarineLogbookAdapter
    private lateinit var emptyView: View

    private val createCsvLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        handleExportResult(uri, MarineLogbookViewModel.ExportFormat.CSV)
    }

    private val createGpxLauncher = registerForActivityResult(ActivityResultContracts.CreateDocument("application/gpx+xml")) { uri ->
        handleExportResult(uri, MarineLogbookViewModel.ExportFormat.GPX)
    }

    private fun handleExportResult(uri: android.net.Uri?, format: MarineLogbookViewModel.ExportFormat) {
        uri?.let {
            lifecycleScope.launch {
                val success = withContext(Dispatchers.IO) {
                    try {
                        requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                            when (format) {
                                MarineLogbookViewModel.ExportFormat.CSV -> {
                                    LogbookCsvExporter.export(viewModel.logEntries.value, stream)
                                }
                                MarineLogbookViewModel.ExportFormat.GPX -> {
                                    val gpxFile = GpxStreamer(app).exportLogbookGpx(viewModel.logEntries.value)
                                    if (gpxFile != null) {
                                        FileInputStream(gpxFile).use { input ->
                                            input.copyTo(stream)
                                        }
                                        true
                                    } else false
                                }
                            }
                        } ?: false
                    } catch (e: Exception) {
                        PlatformUtil.getLog(MarineLogbookFragment::class.java).error("Logbook export error: ${e.message}", e)
                        app.runInUIThread {
                            app.showToastMessage(R.string.nautical_logbook_export_error)
                        }
                        false
                    }
                }
                if (success) {
                    app.showToastMessage(R.string.logbook_export_success)
                } else {
                    app.showToastMessage(R.string.logbook_export_failed)
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setHasOptionsMenu(true)
        
        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = NauticalPlugin.getInstance()?.logbookRepository
                    ?: throw IllegalStateException("Logbook repository not initialized")
                @Suppress("UNCHECKED_CAST")
                return MarineLogbookViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory).get(MarineLogbookViewModel::class.java)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_marine_logbook, container, false)
        
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        
        // Update empty state text
        emptyView.findViewById<TextView>(R.id.empty_state_text)?.setText(R.string.logbook_empty_state)

        adapter = MarineLogbookAdapter().apply {
            onEntryClickListener = { entry ->
                LogbookEntryEditorBottomSheet.show(parentFragmentManager, entry)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        lifecycleScope.launch {
            viewModel.logEntries.collectLatest { entries ->
                adapter.submitList(entries)
                emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.exportTrigger.collect { format ->
                val extension = if (format == MarineLogbookViewModel.ExportFormat.CSV) "csv" else "gpx"
                val fileName = "marine_logbook_${System.currentTimeMillis()}.$extension"
                if (format == MarineLogbookViewModel.ExportFormat.CSV) {
                    createCsvLauncher.launch(fileName)
                } else {
                    createGpxLauncher.launch(fileName)
                }
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.add(0, EXPORT_CSV_ID, 0, getString(R.string.nautical_logbook_export_csv)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, EXPORT_GPX_ID, 1, getString(R.string.nautical_logbook_export_gpx)).setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            EXPORT_CSV_ID -> {
                viewModel.requestExport(MarineLogbookViewModel.ExportFormat.CSV)
                true
            }
            EXPORT_GPX_ID -> {
                viewModel.requestExport(MarineLogbookViewModel.ExportFormat.GPX)
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    companion object {
        private const val EXPORT_CSV_ID = 1
        private const val EXPORT_GPX_ID = 2
    }
}
