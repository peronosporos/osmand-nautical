package net.osmand.plus.plugins.nautical.ui.logbook

import android.os.Bundle
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.MenuProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.PlatformUtil
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.WearOsNauticalManager
import net.osmand.plus.plugins.nautical.engine.GpxStreamer
import net.osmand.plus.plugins.nautical.logbook.export.LogbookCsvExporter
import net.osmand.plus.plugins.nautical.viewmodel.MarineLogbookViewModel
import java.io.FileInputStream

class MarineLogbookFragment : BaseOsmAndFragment() {

    private lateinit var viewModel: MarineLogbookViewModel
    private lateinit var adapter: MarineLogbookAdapter
    private lateinit var emptyView: View
    private lateinit var swipeRefresh: androidx.swiperefreshlayout.widget.SwipeRefreshLayout
    private lateinit var wearOsManager: WearOsNauticalManager

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
                        val allEntries = viewModel.getFullLogbookForExport()
                        requireContext().contentResolver.openOutputStream(it)?.use { stream ->
                            when (format) {
                                MarineLogbookViewModel.ExportFormat.CSV -> {
                                    val result = LogbookCsvExporter.export(allEntries, stream)
                                    if (result.isFailure) {
                                        val errorMsg = result.exceptionOrNull()?.message ?: "Unknown error"
                                        app.runInUIThread {
                                            app.showToastMessage(app.getString(R.string.nautical_logbook_export_error) + ": " + errorMsg)
                                        }
                                        false
                                    } else true
                                }
                                MarineLogbookViewModel.ExportFormat.GPX -> {
                                    val gpxFile = GpxStreamer(app).exportLogbookGpx(allEntries)
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
        wearOsManager = WearOsNauticalManager(requireContext())

        val factory = object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                val repository = NauticalPlugin.getInstance()?.logbookRepository
                    ?: throw IllegalStateException("Logbook repository not initialized")
                @Suppress("UNCHECKED_CAST")
                return MarineLogbookViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[MarineLogbookViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_marine_logbook, container, false)
        
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        emptyView = view.findViewById(R.id.empty_view)
        swipeRefresh = view.findViewById(R.id.swipe_refresh)
        val fab: FloatingActionButton = view.findViewById(R.id.add_entry_fab)
        
        val isWatch = wearOsManager.isWatchMode()
        if (isWatch) {
             // Item 13: Use proper WindowInsets for round bezel padding
             view.setOnApplyWindowInsetsListener { _, insets ->
                 if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                     val type = WindowInsets.Type.systemBars()
                     val systemInsets = insets.getInsets(type)
                     recyclerView.setPadding(
                         systemInsets.left + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 16f),
                         systemInsets.top + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 32f),
                         systemInsets.right + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 16f),
                         systemInsets.bottom + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 48f)
                     )
                 } else {
                     @Suppress("DEPRECATION")
                     recyclerView.setPadding(
                         insets.systemWindowInsetLeft + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 16f),
                         insets.systemWindowInsetTop + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 32f),
                         insets.systemWindowInsetRight + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 16f),
                         insets.systemWindowInsetBottom + net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 48f)
                     )
                 }
                 recyclerView.clipToPadding = false
                 insets
             }

             // Move FAB to center for easier access on round screens
             (fab.layoutParams as? FrameLayout.LayoutParams)?.let { params ->
                 params.gravity = Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
                 params.bottomMargin = net.osmand.plus.utils.AndroidUtils.dpToPx(requireContext(), 8f)
                 fab.layoutParams = params
             }
        }

        swipeRefresh.setOnRefreshListener {
            viewModel.syncWithServer()
        }
        
        // Update empty state text
        emptyView.findViewById<TextView>(R.id.empty_state_text)?.setText(R.string.logbook_empty_state)

        adapter = MarineLogbookAdapter().apply {
            onEntryClickListener = { entry ->
                LogbookEntryEditorBottomSheet.show(parentFragmentManager, entry)
            }
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.add_entry_fab)?.setOnClickListener {
            NauticalLogbookEntryDialog.show(parentFragmentManager)
        }
        
        recyclerView.addOnScrollListener(
            object : RecyclerView.OnScrollListener() {
                override fun onScrolled(view: RecyclerView, dx: Int, dy: Int) {
                    val layoutManager = view.layoutManager as LinearLayoutManager
                    val totalItemCount = layoutManager.itemCount
                    val lastVisibleItem = layoutManager.findLastVisibleItemPosition()
                    if ((totalItemCount > 0) && (totalItemCount <= (lastVisibleItem + 5))) {
                        viewModel.loadNextPage()
                    }
                }
            },
        )

        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        requireActivity().addMenuProvider(
            object : MenuProvider {
                override fun onCreateMenu(menu: Menu, menuInflater: MenuInflater) {
                    val syncItem = menu.add(0, SYNC_SERVER_ID, 0, "Sync with Server")
                    syncItem.setShowAsAction(MenuItem.SHOW_AS_ACTION_IF_ROOM)
                    syncItem.setIcon(R.drawable.ic_action_refresh_dark)
                    
                    if (!wearOsManager.isWatchMode()) {
                        menu.add(0, EXPORT_CSV_ID, 1, getString(R.string.nautical_logbook_export_csv))
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                        menu.add(0, EXPORT_GPX_ID, 2, getString(R.string.nautical_logbook_export_gpx))
                            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
                    }
                }

                override fun onMenuItemSelected(menuItem: MenuItem): Boolean {
                    return when (menuItem.itemId) {
                        SYNC_SERVER_ID -> {
                            viewModel.syncWithServer()
                            true
                        }
                        EXPORT_CSV_ID -> {
                            viewModel.requestExport(MarineLogbookViewModel.ExportFormat.CSV)
                            true
                        }

                        EXPORT_GPX_ID -> {
                            viewModel.requestExport(MarineLogbookViewModel.ExportFormat.GPX)
                            true
                        }

                        else -> false
                    }
                }
            },
            viewLifecycleOwner,
            Lifecycle.State.RESUMED,
        )
        
        lifecycleScope.launch {
            viewModel.logEntries.collectLatest { entries ->
                adapter.submitList(entries)
                emptyView.visibility = if (entries.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        lifecycleScope.launch {
            viewModel.uiEvents.collect { event ->
                when (event) {
                    is MarineLogbookViewModel.UiEvent.ShowToast -> app.showToastMessage(event.text)
                    is MarineLogbookViewModel.UiEvent.ShowToastRes -> {
                        val text = if (event.formatArgs.isNotEmpty()) app.getString(event.resId, *event.formatArgs) else app.getString(event.resId)
                        app.showToastMessage(text)
                    }
                }
            }
        }

        lifecycleScope.launch {
            viewModel.isSyncing.collectLatest { syncing ->
                swipeRefresh.isRefreshing = syncing
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

    @Deprecated("Deprecated in Java")
    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {}

    @Deprecated("Deprecated in Java")
    override fun onOptionsItemSelected(item: MenuItem): Boolean = false

    companion object {
        private const val SYNC_SERVER_ID = 0
        private const val EXPORT_CSV_ID = 1
        private const val EXPORT_GPX_ID = 2
    }
}
