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
import kotlinx.coroutines.flow.collectLatest
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val app = requireActivity().application as OsmandApplication
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return AnchorCalculatorViewModel(app) as T
            }
        }
        viewModel = ViewModelProvider(this, factory).get(AnchorCalculatorViewModel::class.java)

        val editDepth = view.findViewById<TextInputEditText>(R.id.edit_depth)
        val editTide = view.findViewById<TextInputEditText>(R.id.edit_tide)
        val editBowOffset = view.findViewById<TextInputEditText>(R.id.edit_bow_offset)
        val editSafetyMargin = view.findViewById<TextInputEditText>(R.id.edit_safety_margin)
        val editLat = view.findViewById<TextInputEditText>(R.id.edit_anchor_lat)
        val editLon = view.findViewById<TextInputEditText>(R.id.edit_anchor_lon)
        val toggleRatio = view.findViewById<MaterialButtonToggleGroup>(R.id.toggle_ratio)
        val txtResultRode = view.findViewById<TextView>(R.id.txt_result_rode)
        val btnDropAnchor = view.findViewById<MaterialButton>(R.id.btn_drop_anchor)

        // Bind initial values
        editDepth.setText(viewModel.depth.value.toString())
        editTide.setText(viewModel.tideRise.value.toString())
        editBowOffset.setText(viewModel.bowOffset.value.toString())
        editSafetyMargin.setText(viewModel.safetyMargin.value.toString())
        
        if (viewModel.anchorLat.value != 0.0) editLat.setText(viewModel.anchorLat.value.toString())
        if (viewModel.anchorLon.value != 0.0) editLon.setText(viewModel.anchorLon.value.toString())
        
        when (viewModel.scopeRatio.value) {
            3f -> toggleRatio.check(R.id.btn_ratio_3)
            5f -> toggleRatio.check(R.id.btn_ratio_5)
            7f -> toggleRatio.check(R.id.btn_ratio_7)
            else -> toggleRatio.check(R.id.btn_ratio_5)
        }

        // Input Listeners
        editDepth.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
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

        editSafetyMargin.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toFloatOrNull()?.let { viewModel.setSafetyMargin(it) }
            }
        })

        editLat.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toDoubleOrNull()?.let { viewModel.setAnchorLat(it) }
            }
        })

        editLon.addTextChangedListener(object : SimpleTextWatcher() {
            override fun afterTextChanged(s: Editable?) {
                s?.toString()?.toDoubleOrNull()?.let { viewModel.setAnchorLon(it) }
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

        btnDropAnchor.setOnClickListener {
            viewModel.dropAnchor()
            app.showToastMessage(R.string.nautical_anchor_btn_drop)
            dismiss()
        }

        // Real-time calculation update
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(androidx.lifecycle.Lifecycle.State.STARTED) {
                viewModel.recommendedRode.collectLatest { rode ->
                    txtResultRode.text = String.format(Locale.US, "%s %.1f m", getString(R.string.nautical_anchor_result_rode), rode)
                }
            }
        }
    }

    abstract class SimpleTextWatcher : TextWatcher {
        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
    }

    companion object {
        /**
         * Shows the anchor watch configuration dialog.
         */
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            AnchorWatchDialogFragment().show(fragmentManager, "AnchorWatchDialogFragment")
        }
    }
}
