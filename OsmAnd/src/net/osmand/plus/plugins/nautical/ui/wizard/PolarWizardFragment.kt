package net.osmand.plus.plugins.nautical.ui.wizard

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.progressindicator.LinearProgressIndicator
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.viewmodel.PolarConfigViewModel
import net.osmand.plus.plugins.nautical.viewmodel.WizardState

class PolarWizardFragment : BaseOsmAndFragment() {

    private lateinit var viewModel: PolarConfigViewModel
    private lateinit var statusText: TextView
    private lateinit var recommendationText: TextView
    private lateinit var progressIndicator: LinearProgressIndicator
    private lateinit var nextBtn: MaterialButton
    private lateinit var backBtn: MaterialButton
    private var heatmapView: PolarHeatmapView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val plugin = NauticalPlugin.getInstance()
        viewModel = plugin?.polarConfigViewModel ?: ViewModelProvider(this)[PolarConfigViewModel::class.java]
        plugin?.polarConfigViewModel = viewModel
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = themedInflater.inflate(R.layout.fragment_nautical_wizard, container, false)
        
        statusText = root.findViewById(R.id.wizard_status)
        recommendationText = root.findViewById(R.id.wizard_recommendation)
        progressIndicator = root.findViewById(R.id.wizard_progress)
        nextBtn = root.findViewById(R.id.btn_next)
        backBtn = root.findViewById(R.id.btn_back)

        nextBtn.setOnClickListener {
            handleNext()
        }
        
        backBtn.setOnClickListener {
            if (viewModel.wizardState.value == WizardState.INITIAL_CHECK) {
                viewModel.downloadPolars()
                (activity?.application as? net.osmand.plus.OsmandApplication)?.showToastMessage(getString(net.osmand.plus.R.string.nautical_toast_polar_refreshing))
            } else {
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.wizardState.collectLatest { state ->
                updateUi(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recommendation.collectLatest { resId ->
                recommendationText.text = getString(resId)
            }
        }

        return root
    }

    private fun updateUi(state: WizardState) {
        val root = view as? ViewGroup
        when (state) {
            WizardState.INITIAL_CHECK -> {
                statusText.text = getString(R.string.nautical_polar_wizard_step1_title)
                recommendationText.text = getString(R.string.nautical_polar_wizard_step1_desc)
                progressIndicator.progress = 20
                nextBtn.text = getString(R.string.nautical_polar_wizard_start_setup)
                backBtn.text = getString(R.string.nautical_polar_wizard_download_existing)
                heatmapView?.visibility = View.GONE
            }
            WizardState.PROFILE_SETUP -> {
                statusText.text = getString(R.string.nautical_polar_wizard_step2_title)
                recommendationText.text = getString(R.string.nautical_polar_wizard_step2_desc)
                progressIndicator.progress = 40
                nextBtn.text = getString(R.string.nautical_polar_wizard_engage_logging)
                heatmapView?.visibility = View.GONE
            }
            WizardState.ACTIVE_LOGGING -> {
                statusText.text = getString(R.string.nautical_polar_wizard_step3_title)
                recommendationText.text = getString(R.string.nautical_polar_wizard_step3_desc)
                progressIndicator.progress = 60
                nextBtn.text = getString(R.string.nautical_polar_wizard_review_points)
                ensureHeatmap(root)
                heatmapView?.visibility = View.VISIBLE
            }
            WizardState.REVIEW_AND_SMOOTH -> {
                statusText.text = getString(R.string.nautical_polar_wizard_step4_title)
                recommendationText.text = getString(R.string.nautical_polar_wizard_step4_desc)
                progressIndicator.progress = 80
                nextBtn.text = getString(R.string.nautical_polar_wizard_save_to_server)
                heatmapView?.visibility = View.GONE
            }
            WizardState.SAVING -> {
                statusText.text = getString(R.string.nautical_polar_wizard_finalizing)
                recommendationText.text = getString(R.string.nautical_polar_wizard_syncing)
                progressIndicator.progress = 100
                nextBtn.isEnabled = false
                heatmapView?.visibility = View.GONE
            }
        }
    }

    private fun ensureHeatmap(root: ViewGroup?) {
        if (heatmapView == null && root != null) {
            heatmapView = PolarHeatmapView(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    (200 * resources.displayMetrics.density).toInt()
                )
            }
            root.addView(heatmapView, 3) // Inject before buttons
            
            viewLifecycleOwner.lifecycleScope.launch {
                viewModel.heatmapCells.collectLatest { cells ->
                    heatmapView?.setCells(cells)
                }
            }
        }
    }

    private inner class PolarHeatmapView(context: android.content.Context) : View(context) {
        private val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
        private var cells: List<net.osmand.plus.plugins.nautical.viewmodel.PolarCell> = emptyList()

        fun setCells(newCells: List<net.osmand.plus.plugins.nautical.viewmodel.PolarCell>) {
            this.cells = newCells
            invalidate()
        }

        override fun onDraw(canvas: android.graphics.Canvas) {
            if (cells.isEmpty()) return
            val w = width.toFloat()
            val h = height.toFloat()
            
            val twsUnique = cells.map { it.tws }.distinct().sorted()
            val twaUnique = cells.map { it.twa }.distinct().sorted()
            
            val cellW = w / twaUnique.size
            val cellH = h / twsUnique.size
            
            cells.forEach { cell ->
                val xIdx = twaUnique.indexOf(cell.twa)
                val yIdx = twsUnique.indexOf(cell.tws)
                
                val left = xIdx * cellW
                val top = yIdx * cellH
                
                paint.color = when {
                    cell.sampleCount > 5 -> 0xFF4CAF50.toInt() // Green
                    cell.sampleCount > 0 -> 0xFFFFC107.toInt() // Amber
                    else -> 0xFF333333.toInt() // Dark Grey
                }
                canvas.drawRect(left + 2, top + 2, left + cellW - 2, top + cellH - 2, paint)
            }
        }
    }

    private fun handleNext() {
        val currentState = viewModel.wizardState.value
        when (currentState) {
            WizardState.INITIAL_CHECK -> {
                viewModel.setEngineOff(true)
                viewModel.setSensorsCalibrated(true)
                viewModel.transitionTo(WizardState.PROFILE_SETUP)
            }
            WizardState.PROFILE_SETUP -> {
                viewModel.setProfileMetadata("OsmAnd-Polar-1", "Cruising")
                viewModel.transitionTo(WizardState.ACTIVE_LOGGING)
            }
            WizardState.ACTIVE_LOGGING -> {
                viewModel.transitionTo(WizardState.REVIEW_AND_SMOOTH)
            }
            WizardState.REVIEW_AND_SMOOTH -> {
                viewModel.transitionTo(WizardState.SAVING)
                requireActivity().onBackPressedDispatcher.onBackPressed()
            }
            else -> {}
        }
    }
}
