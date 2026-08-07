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
            requireActivity().onBackPressedDispatcher.onBackPressed()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.wizardState.collectLatest { state ->
                updateUi(state)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.recommendation.collectLatest { rec ->
                recommendationText.text = rec
            }
        }

        return root
    }

    private fun updateUi(state: WizardState) {
        when (state) {
            WizardState.INITIAL_CHECK -> {
                statusText.text = "Step 1: Calibration & Safety Check"
                progressIndicator.progress = 20
                nextBtn.text = "Start Setup"
            }
            WizardState.PROFILE_SETUP -> {
                statusText.text = "Step 2: Profile Configuration"
                progressIndicator.progress = 40
                nextBtn.text = "Engage Logging"
            }
            WizardState.ACTIVE_LOGGING -> {
                statusText.text = "Step 3: Recording Polar Data"
                progressIndicator.progress = 60
                nextBtn.text = "Review Points"
            }
            WizardState.REVIEW_AND_SMOOTH -> {
                statusText.text = "Step 4: Review and Smoothing"
                progressIndicator.progress = 80
                nextBtn.text = "Save to Server"
            }
            WizardState.SAVING -> {
                statusText.text = "Finalizing..."
                progressIndicator.progress = 100
                nextBtn.isEnabled = false
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
