package net.osmand.plus.plugins.nautical.ui.anchor

import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.button.MaterialButtonToggleGroup
import com.google.android.material.textfield.TextInputEditText
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.viewmodel.AnchorCalculatorViewModel
import java.util.Locale

/**
 * Fragment for configuring the enhanced anchor watch.
 */
class AnchorWatchDialogFragment : BaseMaterialBottomSheetDialogFragment() {

    private lateinit var viewModel: AnchorCalculatorViewModel

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.dialog_anchor_watch, container, false)
    }

    private var btnQuickDropBow: MaterialButton? = null
    private var btnDropAnchor: MaterialButton? = null
    private var btnDisarmAnchor: MaterialButton? = null
    private var btnPreviewMap: MaterialButton? = null
    private var txtSensorWarning: TextView? = null
    private var txtResultRode: TextView? = null

    private var layoutSkInfo: View? = null
    private var txtRodeDeployed: TextView? = null
    private var layoutWindlass: View? = null
    private var btnWindlassUp: MaterialButton? = null
    private var btnWindlassDown: MaterialButton? = null
    private var isDepthManuallyEdited: Boolean = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as OsmandApplication
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnchorCalculatorViewModel(app) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[AnchorCalculatorViewModel::class.java]

        val editDepth = view.findViewById<TextInputEditText>(R.id.edit_depth)
        val editTide = view.findViewById<TextInputEditText>(R.id.edit_tide)
        val editBowOffset = view.findViewById<TextInputEditText>(R.id.edit_bow_offset)
        val editFreeboard = view.findViewById<TextInputEditText>(R.id.edit_freeboard)
        val editSafetyMargin = view.findViewById<TextInputEditText>(R.id.edit_safety_margin)
        val editLat = view.findViewById<TextInputEditText>(R.id.edit_anchor_lat)
        val editLon = view.findViewById<TextInputEditText>(R.id.edit_anchor_lon)
        val toggleRatio = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_ratio)
        txtResultRode = view.findViewById(R.id.txt_result_rode)
        btnQuickDropBow = view.findViewById(R.id.btn_quick_drop_bow)
        btnDropAnchor = view.findViewById(R.id.btn_drop_anchor)
        btnDisarmAnchor = view.findViewById(R.id.btn_disarm_anchor)
        btnPreviewMap = view.findViewById(R.id.btn_preview_map)
        txtSensorWarning = view.findViewById(R.id.txt_sensor_warning)

        layoutSkInfo = view.findViewById(R.id.layout_sk_anchor_info)
        txtRodeDeployed = view.findViewById(R.id.txt_rode_deployed)
        layoutWindlass = view.findViewById(R.id.layout_windlass_quick_control)
        btnWindlassUp = view.findViewById(R.id.btn_dlg_windlass_up)
        btnWindlassDown = view.findViewById(R.id.btn_dlg_windlass_down)

        val btnDepthMinus5 = view.findViewById<MaterialButton>(R.id.btn_depth_minus_5)
        val btnDepthPlus5 = view.findViewById<MaterialButton>(R.id.btn_depth_plus_5)
        val btnRodeMinus10 = view.findViewById<MaterialButton>(R.id.btn_rode_minus_10)
        val btnRodePlus10 = view.findViewById<MaterialButton>(R.id.btn_rode_plus_10)

        // Bind initial values
        editDepth.setText(viewModel.depth.value.toString())
        editTide.setText(viewModel.tideRise.value.toString())
        editBowOffset.setText(viewModel.bowOffset.value.toString())
        editFreeboard.setText(viewModel.freeboard.value.toString())
        editSafetyMargin.setText(viewModel.safetyMargin.value.toString())
        
        if (viewModel.anchorLat.value != 0.0) editLat.setText(viewModel.anchorLat.value.toString())
        if (viewModel.anchorLon.value != 0.0) editLon.setText(viewModel.anchorLon.value.toString())
        
        when (viewModel.scopeRatio.value) {
            3f -> toggleRatio.check(R.id.btn_ratio_3)
            5f -> toggleRatio.check(R.id.btn_ratio_5)
            7f -> toggleRatio.check(R.id.btn_ratio_7)
            else -> toggleRatio.check(R.id.btn_ratio_5)
        }

        // Stepper Button Listeners
        btnDepthMinus5?.setOnClickListener {
            val cur = editDepth.text?.toString()?.toFloatOrNull() ?: viewModel.depth.value
            val next = (cur - 5f).coerceAtLeast(1f)
            isDepthManuallyEdited = true
            editDepth.setText(String.format(Locale.US, "%.1f", next))
        }

        btnDepthPlus5?.setOnClickListener {
            val cur = editDepth.text?.toString()?.toFloatOrNull() ?: viewModel.depth.value
            val next = cur + 5f
            isDepthManuallyEdited = true
            editDepth.setText(String.format(Locale.US, "%.1f", next))
        }

        btnRodeMinus10?.setOnClickListener {
            val curMargin = editSafetyMargin.text?.toString()?.toFloatOrNull() ?: viewModel.safetyMargin.value
            val next = (curMargin - 10f).coerceAtLeast(0f)
            editSafetyMargin.setText(String.format(Locale.US, "%.1f", next))
        }

        btnRodePlus10?.setOnClickListener {
            val curMargin = editSafetyMargin.text?.toString()?.toFloatOrNull() ?: viewModel.safetyMargin.value
            val next = curMargin + 10f
            editSafetyMargin.setText(String.format(Locale.US, "%.1f", next))
        }

        // Input Listeners
        editDepth.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                if (editDepth.hasFocus()) {
                    isDepthManuallyEdited = true
                }
                s?.toString()?.toFloatOrNull()?.let { viewModel.setDepth(it) }
            }
        })

        editTide.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toFloatOrNull()?.let { viewModel.setTideRise(it) }
            }
        })

        editBowOffset.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toFloatOrNull()?.let { viewModel.setBowOffset(it) }
            }
        })

        editFreeboard.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toFloatOrNull()?.let { viewModel.setFreeboard(it) }
            }
        })

        editSafetyMargin.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toFloatOrNull()?.let { viewModel.setSafetyMargin(it) }
            }
        })

        editLat.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toDoubleOrNull()?.let { 
                    viewModel.setAnchorLat(it)
                    app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(it)
                    app.osmandMap?.refreshMap()
                }
            }
        })

        editLon.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toDoubleOrNull()?.let { 
                    viewModel.setAnchorLon(it)
                    app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(it)
                    app.osmandMap?.refreshMap()
                }
            }
        })

        toggleRatio.addOnButtonCheckedListener { _, checkedId, isChecked ->
            if (isChecked) {
                val ratio = when (checkedId) {
                    R.id.btn_ratio_3 -> 3f
                    R.id.btn_ratio_5 -> 5f
                    R.id.btn_ratio_7 -> 7f
                    else -> 5f
                }
                viewModel.setScopeRatio(ratio)
            }
        }

        btnQuickDropBow?.setOnClickListener {
            viewModel.dropAnchorAtBow()
            app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(0.0)
            app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(0.0)
            app.showToastMessage(R.string.nautical_anchor_btn_drop)
            dismiss()
        }

        btnDropAnchor?.setOnClickListener {
            viewModel.dropAnchor()
            app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(0.0)
            app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(0.0)
            app.showToastMessage(R.string.nautical_anchor_btn_drop)
            dismiss()
        }

        btnPreviewMap?.setOnClickListener {
            val currentLat = viewModel.anchorLat.value
            val currentLon = viewModel.anchorLon.value
            
            app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(currentLat)
            app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(currentLon)
            app.settings.NAUTICAL_ANCHOR_PREVIEW_RADIUS.set(viewModel.recommendedRode.value.toFloat())
            
            app.osmandMap?.refreshMap()
            app.showToastMessage(R.string.nautical_anchor_moved_to_tap)
            
            // TASK-049: Implement manual drag via interaction with map layers
            dismiss()
        }

        btnDisarmAnchor?.setOnClickListener {
            androidx.appcompat.app.AlertDialog.Builder(requireContext())
                .setTitle(R.string.nautical_disarm_anchor_watch)
                .setMessage(R.string.nautical_confirm_disarm_anchor)
                .setPositiveButton(R.string.shared_string_yes) { _, _ ->
                    viewModel.clearAnchor()
                    app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(0.0)
                    app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(0.0)
                    dismiss()
                }
                .setNegativeButton(R.string.shared_string_no, null)
                .show()
        }

        // Real-time calculation and sensor validation
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                launch {
                    viewModel.anchorLat.collectLatest { lat ->
                        if (editLat.text?.toString()?.toDoubleOrNull() != lat) {
                            editLat.setText(lat.toString())
                        }
                    }
                }
                launch {
                    viewModel.anchorLon.collectLatest { lon ->
                        if (editLon.text?.toString()?.toDoubleOrNull() != lon) {
                            editLon.setText(lon.toString())
                        }
                    }
                }
                launch {
                    viewModel.recommendedRode.collectLatest { rode ->
                        txtResultRode?.text = String.format(Locale.US, "%s %.1f m", getString(R.string.nautical_anchor_result_rode), rode)
                        app.settings.NAUTICAL_ANCHOR_PREVIEW_RADIUS.set(rode.toFloat())
                    }
                }
                launch {
                    NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                        val caps = NauticalPlugin.getInstance()?.capabilityManager?.capabilities?.value
                        
                        layoutSkInfo?.visibility = if (caps?.hasChainCounter == true || caps?.hasWindlassControl == true) View.VISIBLE else View.GONE
                        
                        if (caps?.hasChainCounter == true && state.rodeDeployed != null) {
                            txtRodeDeployed?.text = String.format(Locale.US, "Rode Deployed: %.1f m", state.rodeDeployed)
                        } else {
                            txtRodeDeployed?.text = getString(R.string.nautical_chain_counter_offline)
                        }

                        layoutWindlass?.visibility = if (caps?.hasWindlassControl == true) View.VISIBLE else View.GONE
                        val engineOk = state.isEngineRunning
                        btnWindlassUp?.isEnabled = engineOk
                        btnWindlassDown?.isEnabled = engineOk

                        // Item 13 Fix: Reflect real-time switch state
                        val upActive = state.switches["electrical.switches.windlass.up"] == true
                        val downActive = state.switches["electrical.switches.windlass.down"] == true
                        
                        btnWindlassUp?.isPressed = upActive
                        btnWindlassDown?.isPressed = downActive

                        setupWindlassButton(btnWindlassUp, "electrical.switches.windlass.up")
                        setupWindlassButton(btnWindlassDown, "electrical.switches.windlass.down")

                        val hasGps = state.latitude != null && state.longitude != null && !state.stalePaths.contains("navigation.position")
                        
                        // Relaxed requirement: Allow manual depth fallback
                        val isSafe = hasGps
                        btnQuickDropBow?.isEnabled = isSafe
                        btnDropAnchor?.isEnabled = isSafe
                        
                        val liveDepth = state.depthBelowTransducer ?: state.depthBelowSurface ?: state.depthBelowKeel
                        val hasDepth = liveDepth != null && !state.stalePaths.contains("environment.depth.belowTransducer")
                        txtSensorWarning?.visibility = if (hasDepth) View.GONE else View.VISIBLE
                        if (!hasDepth) {
                            txtSensorWarning?.text = getString(R.string.nautical_depth_unavailable)
                        } else if (!isDepthManuallyEdited && (viewModel.depth.value == 0f || editDepth.text.isNullOrEmpty() || editDepth.text.toString() == "0.0")) {
                            viewModel.setDepth(liveDepth.toFloat())
                            editDepth.setText(String.format(Locale.US, "%.1f", liveDepth))
                        }
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        btnQuickDropBow = null
        btnDropAnchor = null
        btnDisarmAnchor = null
        btnPreviewMap = null
        txtSensorWarning = null
        txtResultRode = null
        layoutSkInfo = null
        txtRodeDeployed = null
        layoutWindlass = null
        btnWindlassUp = null
        btnWindlassDown = null
    }

    private fun setupWindlassButton(button: MaterialButton?, path: String) {
        button?.setOnTouchListener { v, event ->
            if (!button.isEnabled) return@setOnTouchListener false
            when (event.action) {
                android.view.MotionEvent.ACTION_DOWN -> {
                    NauticalPlugin.engine?.setSwitch(path, true)
                    v.isPressed = true
                    true
                }
                android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                    NauticalPlugin.engine?.setSwitch(path, false)
                    // Item 12 Fix: Redundant fail-safe off commands
                    viewLifecycleOwner.lifecycleScope.launch {
                        delay(500)
                        NauticalPlugin.engine?.setSwitch(path, false)
                        delay(1000)
                        NauticalPlugin.engine?.setSwitch(path, false)
                    }
                    v.isPressed = false
                    v.performClick()
                    true
                }
                else -> false
            }
        }
    }

    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    companion object {
        const val TAG = "AnchorWatchDialogFragment"

        /**
         * Shows the anchor watch configuration dialog safely.
         */
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                AnchorWatchDialogFragment().show(fragmentManager, TAG)
            }
        }
    }
}
