package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.DialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.MarineState
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

        if (engine == null || autopilot == null) {
            dismiss()
            return
        }

        val statusView = view.findViewById<TextView>(R.id.tv_pilot_status)

        listener = { state ->
            statusView?.post {
                if (!isAdded) return@post

                val mode = state.autopilotState
                statusView.text = getString(R.string.nautical_pilot_status_active, mode.uppercase(Locale.US))

                val bgColor = when {
                    mode.equals("auto", ignoreCase = true) ->
                        ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_active)
                    mode.equals("emergency", ignoreCase = true) || mode.equals("stop", ignoreCase = true) ->
                        ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_emergency)
                    else -> ContextCompat.getColor(requireContext(), R.color.nautical_status_bg_standby)
                }
                statusView.setBackgroundColor(bgColor)
            }
        }

        engine.registerListener(listener!!)
        listener?.invoke(engine.getCurrentState() ?: MarineState())

        // Button Click Listeners
        view.findViewById<View>(R.id.btn_auto)?.setOnClickListener {
            autopilot.setAutopilotMode("auto")
            dismiss()
        }

        view.findViewById<View>(R.id.btn_emergency_stop)?.setOnClickListener {
            autopilot.stopNavigation()
            Toast.makeText(requireContext(), getString(R.string.nautical_emergency_stop_executed), Toast.LENGTH_SHORT).show()
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
