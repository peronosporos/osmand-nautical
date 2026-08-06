package net.osmand.plus.plugins.nautical.ui.wizard

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.viewmodel.PolarConfigViewModel
import net.osmand.plus.plugins.nautical.viewmodel.WizardState

class ConfigurePolarsDialogFragment : DialogFragment() {

    private lateinit var viewModel: PolarConfigViewModel

    companion object {
        const val TAG = "ConfigurePolarsDialogFragment"
        fun newInstance(): ConfigurePolarsDialogFragment {
            return ConfigurePolarsDialogFragment()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val plugin = NauticalPlugin.getInstance()
        viewModel = plugin?.polarConfigViewModel ?: ViewModelProvider(this)[PolarConfigViewModel::class.java]
        plugin?.polarConfigViewModel = viewModel
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.wizard_polar_title)

        // Observe wizard state to update UI
        lifecycleScope.launch {
            viewModel.wizardState.collectLatest { state ->
                activity?.runOnUiThread {
                    updateWizardView(state)
                }
            }
        }

        // Observe recorded points
        lifecycleScope.launch {
            viewModel.heatmapCells.collectLatest { cells ->
                val samples = cells.sumOf { it.sampleCount }
                if (samples > 0 && viewModel.wizardState.value == WizardState.ACTIVE_LOGGING) {
                    activity?.runOnUiThread {
                        val dialog = dialog as? AlertDialog
                        dialog?.setMessage("${getString(R.string.wizard_step_logging)}\n\nPoints recorded: $samples")
                    }
                }
            }
        }

        // Initial step view: Step 1 Conditions Check
        builder.setMessage(R.string.wizard_step_conditions)
        builder.setPositiveButton(R.string.shared_string_ok) { _, _ ->
            viewModel.setEngineOff(true)
            viewModel.setSensorsCalibrated(true)
            viewModel.transitionTo(WizardState.PROFILE_SETUP)
        }
        builder.setNegativeButton(R.string.shared_string_cancel, null)

        return builder.create()
    }

    private fun updateWizardView(state: WizardState) {
        val dialog = dialog as? AlertDialog ?: return
        when (state) {
            WizardState.PROFILE_SETUP -> {
                dialog.setMessage(getString(R.string.wizard_step_metadata))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    viewModel.setProfileMetadata("New Boat", "Full Rig")
                    viewModel.transitionTo(WizardState.ACTIVE_LOGGING)
                }
            }
            WizardState.ACTIVE_LOGGING -> {
                dialog.setMessage(getString(R.string.wizard_step_logging))
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = getString(R.string.wizard_btn_review)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    viewModel.transitionTo(WizardState.REVIEW_AND_SMOOTH)
                }
            }
            WizardState.REVIEW_AND_SMOOTH -> {
                dialog.setMessage("Step 4: Review and Smooth the recorded data.")
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).text = getString(R.string.shared_string_finish)
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                    viewModel.transitionTo(WizardState.SAVING)
                }
            }
            WizardState.SAVING -> {
                dialog.setMessage("Saving profile...")
                dismiss()
            }
            else -> {}
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If we want the recording to continue in background, we keep the VM in the plugin.
        // If we want it to stop, we clear it. For now, keep it for "ACTIVE_LOGGING".
        if (viewModel.wizardState.value != WizardState.ACTIVE_LOGGING) {
            NauticalPlugin.getInstance()?.polarConfigViewModel = null
        }
    }
}
