package net.osmand.plus.plugins.nautical.ui

import android.app.Dialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.AutoCompleteTextView
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import java.util.Locale

class VhfChannelPickerDialog : DialogFragment() {

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val input = AutoCompleteTextView(requireContext())
        val channels = (1..28).map { String.format(Locale.US, "%02d", it) } + 
                       (60..88).map { String.format(Locale.US, "%02d", it) } + 
                       listOf("16", "09", "13", "70")
        
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_dropdown_item_1line, channels.distinct().sorted())
        input.setAdapter(adapter)
        input.hint = getString(R.string.nautical_vhf_picker_hint)

        return AlertDialog.Builder(requireContext())
            .setTitle(R.string.nautical_vhf_picker_title)
            .setView(input)
            .setPositiveButton(R.string.shared_string_set) { _, _ ->
                val channel = input.text.toString()
                if (validateChannel(channel)) {
                    NauticalPlugin.engine?.sendDelta("communication.vhf.channel", channel)
                } else {
                    context?.let { android.widget.Toast.makeText(it, getString(R.string.nautical_vhf_error_invalid), android.widget.Toast.LENGTH_SHORT).show() }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .create()
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
