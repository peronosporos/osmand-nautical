package net.osmand.plus.views.mapwidgets.widgets

import android.content.DialogInterface
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.progressindicator.CircularProgressIndicator
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.viewmodel.CompassWizardStep
import net.osmand.plus.plugins.nautical.viewmodel.NauticalCompassWizardViewModel
import net.osmand.plus.utils.AndroidUtils

class NauticalCompassWizardDialog : BaseMaterialBottomSheetDialogFragment() {

    private lateinit var viewModel: NauticalCompassWizardViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this)[NauticalCompassWizardViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_nautical_compass_wizard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.wizard_title)
        val message = view.findViewById<TextView>(R.id.wizard_message)
        val progressIndicator = view.findViewById<CircularProgressIndicator>(R.id.calibration_progress)
        val btnNext = view.findViewById<Button>(R.id.btn_next)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)
        val btnRetry = view.findViewById<Button>(R.id.btn_retry)

        btnNext.setOnClickListener {
            when (viewModel.step.value) {
                CompassWizardStep.PREPARATION -> viewModel.startCalibration()
                CompassWizardStep.CALIBRATING -> { /* Wait for auto-completion */ }
                CompassWizardStep.COMPLETE -> dismiss()
                CompassWizardStep.FAILED -> viewModel.retry()
            }
        }

        btnRetry.setOnClickListener { viewModel.retry() }

        btnCancel.setOnClickListener {
            viewModel.stopCalibration()
            dismiss()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.step.collect { step ->
                        updateUiForStep(step, title, message, progressIndicator, btnNext, btnRetry, btnCancel)
                    }
                }
                launch {
                    viewModel.progress.collect { progress ->
                        if (viewModel.step.value == CompassWizardStep.CALIBRATING) {
                            progressIndicator.isIndeterminate = progress == 0
                            progressIndicator.progress = progress
                            progressIndicator.visibility = View.VISIBLE
                        }
                    }
                }
            }
        }
    }

    private fun updateUiForStep(
        step: CompassWizardStep,
        title: TextView,
        message: TextView,
        progress: CircularProgressIndicator,
        btnNext: Button,
        btnRetry: Button,
        btnCancel: Button
    ) {
        val app = AndroidUtils.getApp(requireContext())
        when (step) {
            CompassWizardStep.PREPARATION -> {
                title.text = getString(R.string.nautical_compass_wizard_step_1)
                message.text = getString(R.string.nautical_compass_wizard_step_1_msg)
                progress.visibility = View.GONE
                btnNext.visibility = View.VISIBLE
                btnNext.text = getString(R.string.nautical_compass_wizard_start)
                btnNext.isEnabled = true
                btnRetry.visibility = View.GONE
                btnCancel.isEnabled = true
            }
            CompassWizardStep.CALIBRATING -> {
                title.text = getString(R.string.nautical_compass_wizard_step_2)
                message.text = getString(R.string.nautical_compass_wizard_step_2_msg)
                progress.visibility = View.VISIBLE
                btnNext.visibility = View.VISIBLE
                btnNext.text = getString(R.string.nautical_compass_wizard_next)
                btnNext.isEnabled = false
                btnRetry.visibility = View.GONE
                btnCancel.isEnabled = true
                app.showToastMessage(R.string.nautical_compass_calibration_started)
            }
            CompassWizardStep.COMPLETE -> {
                title.text = getString(R.string.nautical_compass_wizard_step_3)
                message.text = getString(R.string.nautical_compass_wizard_step_3_msg)
                progress.visibility = View.VISIBLE
                progress.isIndeterminate = false
                progress.progress = 100
                btnNext.visibility = View.VISIBLE
                btnNext.text = getString(R.string.nautical_compass_wizard_finish)
                btnNext.isEnabled = true
                btnRetry.visibility = View.GONE
                btnCancel.isEnabled = false
            }
            CompassWizardStep.FAILED -> {
                title.text = getString(R.string.nautical_error)
                message.text = getString(R.string.nautical_compass_calibration_timeout)
                progress.visibility = View.GONE
                btnNext.visibility = View.GONE
                btnRetry.visibility = View.VISIBLE
                btnCancel.isEnabled = true
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        viewModel.stopCalibration()
    }

    companion object {
        const val TAG = "NauticalCompassWizardDialog"

        @JvmStatic
        fun show(fragment: androidx.fragment.app.Fragment) {
            NauticalCompassWizardDialog().show(fragment.childFragmentManager, TAG)
        }
    }
}
