package net.osmand.plus.plugins.nautical.ui

import android.app.Dialog
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import com.google.android.material.button.MaterialButton
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.Locale

class VhfChannelPickerDialog : DialogFragment() {

    private var facilityChannels: List<String> = emptyList()

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val app = activity?.application as? net.osmand.plus.OsmandApplication
        val isNightVision = app?.let { NauticalPlugin.isNightVision(it) } ?: false
        val density = requireContext().resources.displayMetrics.density

        // Extract nearby S-57 port communication channels (COMCHA) if available
        val state = NauticalPlugin.engine?.getCurrentState()
        val lat = state?.latitude
        val lon = state?.longitude
        if (app != null && lat != null && lon != null) {
            val dbHelper = net.osmand.plus.plugins.nautical.s57.S57SqliteHelper(app)
            val degRadius = 0.05 // ~3 NM
            val features = try {
                dbHelper.queryFeatures(lat - degRadius, lat + degRadius, lon - degRadius, lon + degRadius, listOf("HRBFAC", "BERTHS", "LOCKS"), limit = 20)
            } catch (e: Exception) {
                emptyList()
            }
            val extracted = mutableListOf<String>()
            for (f in features) {
                val comcha = f.attributes["COMCHA"] ?: f.attributes["comcha"]
                if (!comcha.isNullOrBlank()) {
                    val split = comcha.split(";", ",", " ", "/").map { it.trim().padStart(2, '0') }.filter { it.isNotEmpty() }
                    extracted.addAll(split)
                }
            }
            facilityChannels = extracted.distinct()
        }

        val mainLayout = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val padH = (16f * density).toInt()
            val padV = (12f * density).toInt()
            setPadding(padH, padV, padH, padV)
            if (isNightVision) {
                setBackgroundColor(0xEE120000.toInt())
            }
        }

        // 1. Facility Presets (if detected in S-57 area)
        if (facilityChannels.isNotEmpty()) {
            val lblFacility = TextView(requireContext()).apply {
                text = "Port & Facility Channels"
                textSize = 12f
                setTextColor(if (isNightVision) 0xFFFF8A80.toInt() else 0xFF666666.toInt())
                setPadding(0, 0, 0, (4f * density).toInt())
            }
            mainLayout.addView(lblFacility)

            val facilityRow = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 0, 0, (12f * density).toInt())
            }
            for (ch in facilityChannels.take(4)) {
                val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = "Ch $ch"
                    minHeight = (48f * density).toInt()
                    textSize = 13f
                    val lp = LinearLayout.LayoutParams(0, (48f * density).toInt(), 1f).apply {
                        marginEnd = (4f * density).toInt()
                    }
                    layoutParams = lp
                    if (isNightVision) {
                        setTextColor(0xFFFF1744.toInt())
                        strokeColor = ColorStateList.valueOf(0x80FF1744.toInt())
                        backgroundTintList = ColorStateList.valueOf(0x30FF1744.toInt())
                    }
                    setOnClickListener {
                        tuneChannel(ch)
                        dismiss()
                    }
                }
                facilityRow.addView(btn)
            }
            mainLayout.addView(facilityRow)
        }

        // 2. Standard Marine Presets Grid
        val lblPresets = TextView(requireContext()).apply {
            text = "Quick Presets"
            textSize = 12f
            setTextColor(if (isNightVision) 0xFFFF8A80.toInt() else 0xFF666666.toInt())
            setPadding(0, 0, 0, (4f * density).toInt())
        }
        mainLayout.addView(lblPresets)

        val presetList = listOf(
            Pair("16", getString(R.string.nautical_vhf_preset_distress)),
            Pair("13", getString(R.string.nautical_vhf_preset_bridge)),
            Pair("12", getString(R.string.nautical_vhf_preset_port_ops)),
            Pair("14", getString(R.string.nautical_vhf_preset_port_ops_14)),
            Pair("06", getString(R.string.nautical_vhf_preset_intership)),
            Pair("72", getString(R.string.nautical_vhf_preset_intership_72))
        )

        // 2x3 Grid
        for (i in 0 until presetList.size step 2) {
            val row = LinearLayout(requireContext()).apply {
                orientation = LinearLayout.HORIZONTAL
                val lp = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
                    bottomMargin = (6f * density).toInt()
                }
                layoutParams = lp
            }

            for (j in i until minOf(i + 2, presetList.size)) {
                val (chNum, chDesc) = presetList[j]
                val btn = MaterialButton(requireContext(), null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                    text = chDesc
                    minHeight = (48f * density).toInt()
                    textSize = 12f
                    val lp = LinearLayout.LayoutParams(0, (48f * density).toInt(), 1f).apply {
                        if (j % 2 == 0) marginEnd = (4f * density).toInt() else marginStart = (4f * density).toInt()
                    }
                    layoutParams = lp
                    if (isNightVision) {
                        setTextColor(if (chNum == "16") 0xFFFF1744.toInt() else 0xFFFF8A80.toInt())
                        strokeColor = ColorStateList.valueOf(if (chNum == "16") 0xFFFF1744.toInt() else 0x80FF1744.toInt())
                        backgroundTintList = ColorStateList.valueOf(if (chNum == "16") 0x40FF1744.toInt() else 0x20FF1744.toInt())
                    }
                    setOnClickListener {
                        tuneChannel(chNum)
                        dismiss()
                    }
                }
                row.addView(btn)
            }
            mainLayout.addView(row)
        }

        // 3. Custom Channel Input
        val input = AutoCompleteTextView(requireContext()).apply {
            minHeight = (48f * density).toInt()
            if (isNightVision) {
                setTextColor(0xFFFF1744.toInt())
                setHintTextColor(0x80FF1744.toInt())
            }
        }
        val channels = (1..28).map { String.format(Locale.US, "%02d", it) } + 
                       (60..88).map { String.format(Locale.US, "%02d", it) } + 
                       listOf("16", "09", "13", "70")
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, channels.distinct().sorted())
        input.setAdapter(adapter)
        input.hint = getString(R.string.nautical_vhf_picker_hint)
        mainLayout.addView(input)

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_vhf_picker_title)
            .setView(mainLayout)
            .setPositiveButton(R.string.shared_string_set) { _, _ ->
                val channel = input.text.toString().trim()
                if (validateChannel(channel)) {
                    tuneChannel(channel)
                } else {
                    context?.let { Toast.makeText(it, getString(R.string.nautical_vhf_error_invalid), Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .create()

        dialog.setOnShowListener {
            if (isNightVision) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setTextColor(0xFFFF1744.toInt())
                dialog.getButton(AlertDialog.BUTTON_NEGATIVE)?.setTextColor(0xFFFF8A80.toInt())
                dialog.window?.decorView?.setBackgroundColor(0xEE120000.toInt())
            }
        }
        return dialog
    }

    private fun tuneChannel(channel: String) {
        val padded = channel.padStart(2, '0')
        NauticalPlugin.engine?.sendDelta("communication.vhf.channel", padded)
    }

    private fun validateChannel(channel: String): Boolean {
        val num = channel.toIntOrNull() ?: return false
        return (num in 1..28) || (num in 60..88) || (num in 1000..2999)
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            VhfChannelPickerDialog().show(fragmentManager, "vhf_channel_picker")
        }
    }
}
