package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.appcompat.widget.SwitchCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputLayout
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.engine.PatternSteeringEngine

class NauticalSarConfigDialog : BottomSheetDialogFragment() {

    companion object {
        private const val ARG_IS_MOB = "is_mob"

        fun newInstance(isMob: Boolean = false): NauticalSarConfigDialog {
            val fragment = NauticalSarConfigDialog()
            val args = Bundle()
            args.putBoolean(ARG_IS_MOB, isMob)
            fragment.arguments = args
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.dialog_nautical_sar_config, container, false)
        
        val toggleGroup = view.findViewById<MaterialButtonToggleGroup>(R.id.pattern_toggle_group)
        val inputSpacing = view.findViewById<TextInputLayout>(R.id.input_track_spacing)
        val inputIterRadius = view.findViewById<TextInputLayout>(R.id.input_iterations_radius)
        val areaLayout = view.findViewById<View>(R.id.area_dimensions_layout)
        val inputLength = view.findViewById<TextInputLayout>(R.id.input_length)
        val inputWidth = view.findViewById<TextInputLayout>(R.id.input_width)
        val inputOrientation = view.findViewById<TextInputLayout>(R.id.input_orientation)
        val switchMob = view.findViewById<SwitchCompat>(R.id.switch_use_mob_datum)
        val switchRight = view.findViewById<SwitchCompat>(R.id.switch_turns_right)
        val btnExecute = view.findViewById<Button>(R.id.btn_execute)

        val isMobContext = arguments?.getBoolean(ARG_IS_MOB) ?: false
        switchMob.isChecked = isMobContext
        switchMob.isEnabled = isMobContext // Only allow MOB datum if it exists

        toggleGroup.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                when (checkedId) {
                    R.id.btn_expanding_square -> {
                        inputIterRadius.hint = getString(R.string.nautical_sar_iterations_hint)
                        inputIterRadius.editText?.setText("4")
                        areaLayout.visibility = View.GONE
                    }
                    R.id.btn_sector_search -> {
                        inputIterRadius.hint = getString(R.string.nautical_sar_radius_hint)
                        inputIterRadius.editText?.setText("0.5")
                        areaLayout.visibility = View.GONE
                    }
                    R.id.btn_creeping_line, R.id.btn_parallel_sweep -> {
                        inputIterRadius.visibility = View.GONE
                        areaLayout.visibility = View.VISIBLE
                    }
                }
            }
        }
        
        // Default selection
        toggleGroup.check(R.id.btn_expanding_square)

        val app = activity?.application as? net.osmand.plus.OsmandApplication
        val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
        if (isNightVision) {
            view.setBackgroundColor(0xEE120000.toInt())
            btnExecute.backgroundTintList = android.content.res.ColorStateList.valueOf(0xFFB71C1C.toInt())
        }

        // Stepper: Track Spacing (-0.5 / +0.5 NM)
        view.findViewById<View>(R.id.btn_spacing_minus)?.setOnClickListener {
            val current = inputSpacing.editText?.text?.toString()?.toDoubleOrNull() ?: 0.20
            val next = (current - 0.5).coerceAtLeast(0.05)
            inputSpacing.editText?.setText(String.format(java.util.Locale.US, "%.2f", next))
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        view.findViewById<View>(R.id.btn_spacing_plus)?.setOnClickListener {
            val current = inputSpacing.editText?.text?.toString()?.toDoubleOrNull() ?: 0.20
            val next = current + 0.5
            inputSpacing.editText?.setText(String.format(java.util.Locale.US, "%.2f", next))
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        // Stepper: Iterations / Search Radius (-1.0 / +1.0)
        view.findViewById<View>(R.id.btn_radius_minus)?.setOnClickListener {
            val current = inputIterRadius.editText?.text?.toString()?.toDoubleOrNull() ?: 4.0
            val isExpandingSquare = toggleGroup.checkedButtonId == R.id.btn_expanding_square
            val next = if (isExpandingSquare) (current - 1.0).toInt().coerceAtLeast(1).toDouble() else (current - 1.0).coerceAtLeast(0.25)
            inputIterRadius.editText?.setText(if (isExpandingSquare) next.toInt().toString() else String.format(java.util.Locale.US, "%.1f", next))
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }
        view.findViewById<View>(R.id.btn_radius_plus)?.setOnClickListener {
            val current = inputIterRadius.editText?.text?.toString()?.toDoubleOrNull() ?: 4.0
            val isExpandingSquare = toggleGroup.checkedButtonId == R.id.btn_expanding_square
            val next = if (isExpandingSquare) (current + 1.0).toInt().toDouble() else current + 1.0
            inputIterRadius.editText?.setText(if (isExpandingSquare) next.toInt().toString() else String.format(java.util.Locale.US, "%.1f", next))
            it.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
        }

        btnExecute.setOnClickListener {
            val engine = NauticalPlugin.engine ?: return@setOnClickListener
            val state = engine.getCurrentState()
            
            var startLat: Double
            var startLon: Double
            
            if (switchMob.isChecked && state.isMobActive) {
                val mobLoc = NauticalPlugin.getInstance()?.mobViewModel?.uiState?.value?.mobLocation
                if (mobLoc != null) {
                    startLat = mobLoc.latitude
                    startLon = mobLoc.longitude
                } else {
                    startLat = state.mobLatitude ?: state.latitude ?: 0.0
                    startLon = state.mobLongitude ?: state.longitude ?: 0.0
                }
            } else {
                startLat = state.latitude ?: 0.0
                startLon = state.longitude ?: 0.0
            }

            val spacing = inputSpacing.editText?.text?.toString()?.toDoubleOrNull() ?: 0.20
            val orient = inputOrientation.editText?.text?.toString()?.toDoubleOrNull() ?: 0.0
            val turnsRight = switchRight.isChecked

            val waypoints = when (toggleGroup.checkedButtonId) {
                R.id.btn_expanding_square -> {
                    val iters = inputIterRadius.editText?.text?.toString()?.toIntOrNull() ?: 4
                    PatternSteeringEngine.generateExpandingSquare(
                        startLat, startLon, spacing, iters, orient, turnsRight,
                        driftMps = state.drift ?: 0.0,
                        driftDeg = Math.toDegrees(state.setTrue ?: 0.0),
                        avgSpeedMps = state.speedOverGround ?: 3.0
                    )
                }
                R.id.btn_sector_search -> {
                    val radius = inputIterRadius.editText?.text?.toString()?.toDoubleOrNull() ?: 0.5
                    PatternSteeringEngine.generateSectorSearch(startLat, startLon, radius, orient, turnsRight)
                }
                R.id.btn_creeping_line, R.id.btn_parallel_sweep -> {
                    val l = inputLength.editText?.text?.toString()?.toDoubleOrNull() ?: 2.0
                    val w = inputWidth.editText?.text?.toString()?.toDoubleOrNull() ?: 1.0
                    PatternSteeringEngine.generateParallelSweep(startLat, startLon, orient, l, w, spacing)
                }
                else -> emptyList()
            }

            if (waypoints.isNotEmpty()) {
                val latLons = waypoints.map { net.osmand.data.LatLon(it.first, it.second) }
                NauticalPlugin.getInstance()?.mobViewModel?.setSarPatternWaypoints(latLons)
                NauticalPlugin.autopilot?.executePattern(waypoints)
                dismiss()
            }
        }

        return view
    }
}
