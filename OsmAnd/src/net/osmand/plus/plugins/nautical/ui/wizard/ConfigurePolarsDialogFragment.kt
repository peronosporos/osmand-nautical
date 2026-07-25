package net.osmand.plus.plugins.nautical.ui.wizard

import android.app.Dialog
import android.os.Bundle
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.lifecycle.ViewModelProvider
import net.osmand.plus.R
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
        viewModel = ViewModelProvider(this)[PolarConfigViewModel::class.java]
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val context = requireContext()
        val builder = AlertDialog.Builder(context)
        builder.setTitle(R.string.wizard_polar_title)

        // Initial step view: Step 1 Conditions Check
        builder.setMessage(R.string.wizard_step_conditions)
        builder.setPositiveButton(R.string.shared_string_ok) { _, _ ->
            viewModel.setEngineOff(true)
            viewModel.setSensorsCalibrated(true)
            viewModel.transitionTo(WizardState.ACTIVE_LOGGING)
        }
        builder.setNegativeButton(R.string.shared_string_cancel, null)

        return builder.create()
    }
}
