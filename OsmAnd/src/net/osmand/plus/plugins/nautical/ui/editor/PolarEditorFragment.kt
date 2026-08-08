package net.osmand.plus.plugins.nautical.ui.editor

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.viewmodel.PolarEditorViewModel
import net.osmand.plus.plugins.nautical.viewmodel.SailingPerformanceSettingsViewModel

class PolarEditorFragment : Fragment() {

    private lateinit var viewModel: PolarEditorViewModel
    private lateinit var performanceViewModel: SailingPerformanceSettingsViewModel
    private lateinit var canvasView: PolarCurveCanvasView
    private lateinit var titleTextView: TextView
    private lateinit var smoothSeekBar: SeekBar
    private lateinit var saveButton: Button
    private lateinit var btnSwitchPolar: Button
    private lateinit var btnImportPolar: Button

    companion object {
        const val TAG = "PolarEditorFragment"
        fun newInstance(): PolarEditorFragment {
            return PolarEditorFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[PolarEditorViewModel::class.java]
        net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.performanceRepository?.let { repo ->
            performanceViewModel = SailingPerformanceSettingsViewModel(repo)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View? {
        val view = inflater.inflate(R.layout.fragment_polar_editor, container, false)
        canvasView = view.findViewById(R.id.polar_canvas_view)
        titleTextView = view.findViewById(R.id.polar_editor_title)
        smoothSeekBar = view.findViewById(R.id.polar_smooth_seekbar)
        saveButton = view.findViewById(R.id.polar_save_button)
        btnSwitchPolar = view.findViewById(R.id.btn_switch_polar)
        btnImportPolar = view.findViewById(R.id.btn_import_polar)

        setupListeners()
        observeViewModel()
        return view
    }

    private fun setupListeners() {
        smoothSeekBar.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    val intensity = progress / 100f
                    viewModel.setSmoothingIntensity(intensity)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            }
        )

        canvasView.onPointDragged = { index, twa, speed ->
            viewModel.updatePoint(index, twa, speed)
        }

        saveButton.setOnClickListener {
            val app = activity?.application as? net.osmand.plus.OsmandApplication
            val ip = app?.settings?.NAUTICAL_SERVER_IP?.get() ?: ""
            val port = app?.settings?.NAUTICAL_SERVER_PORT?.get() ?: "3000"
            val secure = app?.settings?.NAUTICAL_USE_SECURE_CONNECTION?.get() ?: false
            val protocol = if (secure) "https" else "http"
            val serverUrl = "$protocol://$ip:$port"
            
            val polarId = performanceViewModel.availablePolars.value.entries.find { it.value.name == performanceViewModel.activePolarName.value }?.key ?: "default"

            viewModel.savePolarsToServer(serverUrl, polarId) { success ->
                activity?.runOnUiThread {
                    val msg = if (success) {
                        getString(R.string.nautical_polar_save_success)
                    } else {
                        getString(R.string.nautical_polar_save_failed)
                    }
                    (activity?.application as? net.osmand.plus.OsmandApplication)?.showToastMessage(msg)
                    if (success) {
                         performanceViewModel.refreshPolars()
                    }
                }
            }
        }

        btnSwitchPolar.setOnClickListener {
            showPolarLibrary()
        }
        
        btnImportPolar.setOnClickListener {
            if (::performanceViewModel.isInitialized) {
                performanceViewModel.refreshPolars()
            }
            (activity?.application as? net.osmand.plus.OsmandApplication)?.showToastMessage("Refreshing polars from server...")
        }
    }

    private fun showPolarLibrary() {
        if (!::performanceViewModel.isInitialized) return
        val polars = performanceViewModel.availablePolars.value
        if (polars.isEmpty()) {
            (activity?.application as? net.osmand.plus.OsmandApplication)?.showToastMessage("No polars available in Signal K.")
            return
        }

        val names = polars.keys.toTypedArray()
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle("Select Polar Profile")
            .setItems(names) { _, which ->
                performanceViewModel.switchActivePolar(names[which])
            }
            .show()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.rawPoints.collectLatest { points ->
                canvasView.rawPoints = points
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.smoothedPoints.collectLatest { points ->
                canvasView.smoothedPoints = points
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.selectedTws.collectLatest { tws ->
                titleTextView.text = getString(
                    R.string.nautical_polar_editor_title_fmt,
                    getString(R.string.editor_title),
                    getString(R.string.nautical_tws),
                    tws.toString(),
                    getString(R.string.nautical_unit_knots)
                )
            }
        }

        titleTextView.setOnClickListener {
            showTwsPicker()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.smoothingIntensity.collectLatest { intensity ->
                smoothSeekBar.progress = (intensity * 100).toInt()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            performanceViewModel.isConnected.collectLatest { connected ->
                if (!connected) {
                    (activity?.application as? net.osmand.plus.OsmandApplication)?.showToastMessage("Warning: Performance data repository is offline.")
                }
            }
        }
    }

    private fun showTwsPicker() {
        val profile = net.osmand.plus.plugins.nautical.di.SailingDependencyContainer.performanceRepository?.activePolarProfile?.value
        val availableTws = profile?.tws ?: emptyList()
        
        val twsOptions = mutableListOf(6.0, 8.0, 10.0, 12.0, 14.0, 16.0, 20.0)
        // Add existing TWS from profile if not in default list
        availableTws.forEach { if (!twsOptions.contains(it)) twsOptions.add(it) }
        twsOptions.sort()

        val names = twsOptions.map { tws ->
            val hasData = availableTws.any { kotlin.math.abs(it - tws) < 0.1 }
            val suffix = if (hasData) " (Active)" else " (New)"
            "$tws ${getString(R.string.nautical_unit_knots)}$suffix"
        }.toTypedArray()
        
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_tws)
            .setItems(names) { _, which ->
                viewModel.setSelectedTws(twsOptions[which])
            }
            .show()
    }
}
