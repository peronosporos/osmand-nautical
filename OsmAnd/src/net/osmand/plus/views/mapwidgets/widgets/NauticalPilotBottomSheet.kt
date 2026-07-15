package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.SeekBar
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
import net.osmand.plus.plugins.nautical.ui.RudderView
import java.util.*

class NauticalPilotBottomSheet : DialogFragment() {

    private var listener: ((MarineState) -> Unit)? = null

    companion object {
        @JvmStatic
        fun newInstance(): NauticalPilotBottomSheet {
            return NauticalPilotBottomSheet()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_FRAME, R.style.BottomSheet_Dialog)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.bottom_sheet_nautical_pilot, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val engine = NauticalPlugin.engine
        val autopilot = NauticalPlugin.autopilot

        if ((engine == null) || (autopilot == null)) {
            dismiss()
            return
        }

        val statusView = view.findViewById<TextView>(R.id.tv_pilot_status)
        val targetHeadingView = view.findViewById<TextView>(R.id.tv_target_heading)
        val rudderView = view.findViewById<RudderView>(R.id.rudder_view)
        val seaStateView = view.findViewById<TextView>(R.id.tv_sea_state)
        val seaStateSeekBar = view.findViewById<SeekBar>(R.id.sb_sea_state)

        listener = { state ->
            statusView?.post {
                if (!isAdded) return@post

                val mode = state.autopilotState
                statusView.text = getString(R.string.nautical_pilot_status_active, mode.uppercase(Locale.US))

                val bgColor = when {
                    mode.equals("auto", ignoreCase = true) || mode.equals("wind", ignoreCase = true) || mode.equals("track", ignoreCase = true) ->
                        ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_active)
                    mode.equals("emergency", ignoreCase = true) || mode.equals("stop", ignoreCase = true) ->
                        ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_emergency)
                    else -> ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_standby)
                }
                statusView.setBackgroundColor(bgColor)

                val target = state.targetHeading ?: state.headingTrue ?: 0.0
                targetHeadingView?.text = getString(R.string.nautical_target_heading_val, Math.toDegrees(target))

                state.rudderAngle?.let { rudderView?.setRudderAngle(it) }

                val seaState = state.seaState ?: 1
                seaStateView?.text = getString(R.string.nautical_sea_state_label, seaState)
                seaStateSeekBar?.progress = seaState - 1
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        // Mode Buttons
        view.findViewById<View>(R.id.btn_auto)?.setOnClickListener { autopilot.setAutopilotMode("auto") }
        view.findViewById<View>(R.id.btn_wind)?.setOnClickListener { autopilot.setAutopilotMode("wind") }
        view.findViewById<View>(R.id.btn_track)?.setOnClickListener { autopilot.setAutopilotMode("route") }
        view.findViewById<View>(R.id.btn_pattern)?.setOnClickListener { autopilot.executePattern("zigzag") }

        // Step Buttons
        view.findViewById<View>(R.id.btn_minus_10)?.setOnClickListener { autopilot.adjustHeading(-10.0) }
        view.findViewById<View>(R.id.btn_minus_1)?.setOnClickListener { autopilot.adjustHeading(-1.0) }
        view.findViewById<View>(R.id.btn_plus_1)?.setOnClickListener { autopilot.adjustHeading(1.0) }
        view.findViewById<View>(R.id.btn_plus_10)?.setOnClickListener { autopilot.adjustHeading(10.0) }

        // Sea State
        seaStateSeekBar?.setOnSeekBarChangeListener(
            object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) autopilot.setSeaState(progress + 1)
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            },
        )

        view.findViewById<View>(R.id.btn_emergency_stop)?.setOnClickListener {
            autopilot.stopNavigation()
            (activity?.application as? OsmandApplication)?.showToastMessage(R.string.nautical_emergency_stop_executed)
            dismiss()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        val engine = NauticalPlugin.engine
        listener?.let { engine?.unregisterListener(it) }
        listener = null
    }
}
