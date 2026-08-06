package net.osmand.plus.plugins.nautical.ui.logbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalLogbookEntryDialog : BaseMaterialBottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val root = inflater.inflate(R.layout.dialog_nautical_logbook_entry, container, false)
        val input = root.findViewById<EditText>(R.id.edit_entry_text)
        
        root.findViewById<View>(R.id.btn_save).setOnClickListener {
            val text = input.text.toString()
            if (text.isNotEmpty()) {
                submitEntry(text)
                dismiss()
            }
        }
        
        return root
    }

    private fun submitEntry(text: String) {
        val engine = NauticalPlugin.engine
        engine?.dispatchCommand("LOGBOOK_ENTRY:$text")
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            NauticalLogbookEntryDialog().show(fragmentManager, "NauticalLogbookEntryDialog")
        }
    }
}
