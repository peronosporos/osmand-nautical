package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import com.google.android.material.progressindicator.CircularProgressIndicator
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalCompassWizardDialog : BaseMaterialBottomSheetDialogFragment() {

    private var currentStep = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_nautical_compass_wizard, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val title = view.findViewById<TextView>(R.id.wizard_title)
        val message = view.findViewById<TextView>(R.id.wizard_message)
        val progress = view.findViewById<CircularProgressIndicator>(R.id.calibration_progress)
        val btnNext = view.findViewById<Button>(R.id.btn_next)
        val btnCancel = view.findViewById<Button>(R.id.btn_cancel)

        btnNext.setOnClickListener {
            when (currentStep) {
                1 -> {
                    currentStep = 2
                    title.text = getString(R.string.nautical_compass_wizard_step_2)
                    message.text = getString(R.string.nautical_compass_wizard_step_2_msg)
                    progress.visibility = View.VISIBLE
                    progress.isIndeterminate = true
                    btnNext.text = getString(R.string.nautical_compass_wizard_next)
                    
                    // Trigger calibration on server
                    NauticalPlugin.engine?.dispatchCommand("CALIBRATE_COMPASS:START")
                }
                2 -> {
                    currentStep = 3
                    title.text = getString(R.string.nautical_compass_wizard_step_3)
                    message.text = getString(R.string.nautical_compass_wizard_step_3_msg)
                    progress.isIndeterminate = false
                    progress.progress = 100
                    btnNext.text = getString(R.string.nautical_compass_wizard_finish)
                }
                3 -> {
                    dismiss()
                }
            }
        }

        btnCancel.setOnClickListener {
            if (currentStep == 2) {
                 NauticalPlugin.engine?.dispatchCommand("CALIBRATE_COMPASS:STOP")
            }
            dismiss()
        }
    }

    companion object {
        const val TAG = "NauticalCompassWizardDialog"

        @JvmStatic
        fun show(fragment: androidx.fragment.app.Fragment) {
            NauticalCompassWizardDialog().show(fragment.childFragmentManager, TAG)
        }
    }
}
