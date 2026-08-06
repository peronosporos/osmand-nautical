package net.osmand.plus.plugins.nautical.ui.logbook

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.ViewModelProvider
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.logbook.data.LogbookEntry
import net.osmand.plus.plugins.nautical.viewmodel.MarineLogbookViewModel

class LogbookEntryEditorBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    private lateinit var viewModel: MarineLogbookViewModel
    private var entry: LogbookEntry? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        entry = arguments?.getSerializable(ENTRY_KEY) as? LogbookEntry
        
        val repository = NauticalPlugin.getInstance()?.logbookRepository
            ?: throw IllegalStateException("Logbook repository not initialized")
            
        val factory = object : ViewModelProvider.Factory {
            override fun <T : androidx.lifecycle.ViewModel> create(modelClass: Class<T>): T {
                @Suppress("UNCHECKED_CAST")
                return MarineLogbookViewModel(repository) as T
            }
        }
        viewModel = ViewModelProvider(this, factory)[MarineLogbookViewModel::class.java]
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_logbook_entry_editor, container, false)

        val sailPlanEdit: EditText = view.findViewById(R.id.sail_plan_edit)
        val notesEdit: EditText = view.findViewById(R.id.notes_edit)
        val saveButton: Button = view.findViewById(R.id.save_button)

        entry?.let {
            sailPlanEdit.setText(it.sailPlan)
            notesEdit.setText(it.notes)
        }

        saveButton.setOnClickListener {
            val updatedSailPlan = sailPlanEdit.text.toString()
            val updatedNotes = notesEdit.text.toString()
            entry?.let {
                viewModel.updateEntryDetails(it.id, updatedSailPlan, updatedNotes)
                dismiss()
            }
        }

        return view
    }

    companion object {
        const val TAG = "LogbookEntryEditorBottomSheet"
        private const val ENTRY_KEY = "entry_key"

        fun show(fragmentManager: FragmentManager, entry: LogbookEntry) {
            val sheet = LogbookEntryEditorBottomSheet()
            val args = Bundle()
            args.putSerializable(ENTRY_KEY, entry)
            sheet.arguments = args
            sheet.show(fragmentManager, TAG)
        }
    }
}
