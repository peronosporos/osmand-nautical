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

class PolarEditorFragment : Fragment() {

    private lateinit var viewModel: PolarEditorViewModel
    private lateinit var canvasView: PolarCurveCanvasView
    private lateinit var titleTextView: TextView
    private lateinit var smoothSeekBar: SeekBar
    private lateinit var saveButton: Button

    companion object {
        const val TAG = "PolarEditorFragment"
        fun newInstance(): PolarEditorFragment {
            return PolarEditorFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[PolarEditorViewModel::class.java]
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_polar_editor, container, false)
        canvasView = view.findViewById(R.id.polar_canvas_view)
        titleTextView = view.findViewById(R.id.polar_editor_title)
        smoothSeekBar = view.findViewById(R.id.polar_smooth_seekbar)
        saveButton = view.findViewById(R.id.polar_save_button)

        setupListeners()
        observeViewModel()
        return view
    }

    private fun setupListeners() {
        smoothSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val intensity = progress / 100f
                viewModel.setSmoothingIntensity(intensity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        canvasView.onPointDragged = { index, twa, speed ->
            viewModel.updatePoint(index, twa, speed)
        }

        saveButton.setOnClickListener {
            val serverUrl = "http://127.0.0.1:3000"
            viewModel.savePolarsToServer(serverUrl, "default-polar") { success ->
                activity?.runOnUiThread {
                    val msg = if (success) getString(R.string.nautical_polar_save_success) else getString(R.string.nautical_polar_save_failed)
                    (activity?.application as? net.osmand.plus.OsmandApplication)?.showToastMessage(msg)
                }
            }
        }
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
                titleTextView.text = "${getString(R.string.editor_title)} - ${getString(R.string.nautical_tws)}: $tws ${getString(R.string.nautical_unit_knots)}"
            }
        }
    }
}
