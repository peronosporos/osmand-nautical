package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CheckBox
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.SignalKChecklist
import net.osmand.plus.plugins.nautical.network.SignalKChecklistItem
import kotlinx.coroutines.*

class NauticalChecklistFragment : BaseOsmAndFragment() {

    companion object {
        fun show(fragmentManager: FragmentManager) {
            val fragment = NauticalChecklistFragment()
            fragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, fragment, "nautical_checklists")
                .addToBackStack("nautical_checklists")
                .commit()
        }
    }

    private var recyclerView: RecyclerView? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var checklists: Map<String, SignalKChecklist> = emptyMap()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.fragment_nautical_checklists, container, false)
        
        recyclerView = view.findViewById(R.id.recycler_view)
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        
        view.findViewById<View>(R.id.fab_add_checklist).setOnClickListener {
            showAddChecklistDialog()
        }

        loadChecklists()
        
        return view
    }

    private fun showAddChecklistDialog() {
        val context = context ?: return
        val input = android.widget.EditText(context).apply {
            hint = "Checklist Name"
        }
        
        androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("New Checklist")
            .setView(input)
            .setPositiveButton(R.string.shared_string_add) { _, _ ->
                val name = input.text.toString()
                if (name.isNotEmpty()) {
                    val newChecklist = SignalKChecklist(name = name, description = "", items = emptyList())
                    scope.launch {
                        NauticalPlugin.engine?.resourceManager?.pushChecklistToServer(name, newChecklist)
                        loadChecklists()
                    }
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }


    private fun loadChecklists() {
        scope.launch {
            try {
                val response = NauticalPlugin.engine?.getRestService()?.getChecklists()
                if (response?.isSuccessful == true) {
                    checklists = response.body() ?: emptyMap()
                    recyclerView?.adapter = ChecklistAdapter()
                }
            } catch (_: Exception) {}
        }
    }

    override fun onDestroyView() {
        scope.cancel()
        super.onDestroyView()
    }

    private inner class ChecklistAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
        
        private val flatItems = mutableListOf<Any>()
        private val itemToChecklist = mutableMapOf<SignalKChecklistItem, SignalKChecklist>()

        init {
            checklists.values.forEach { checklist ->
                flatItems.add(checklist)
                checklist.items.forEach { item ->
                    flatItems.add(item)
                    itemToChecklist[item] = checklist
                }
            }
        }

        override fun getItemViewType(position: Int): Int {
            return if (flatItems[position] is SignalKChecklist) 0 else 1
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            return if (viewType == 0) {
                HeaderViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_checklist_header, parent, false))
            } else {
                ItemViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_checklist_row, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val item = flatItems[position]
            if (holder is HeaderViewHolder) {
                val checklist = item as SignalKChecklist
                holder.title.text = checklist.name
                holder.btnAdd.setOnClickListener {
                    showAddItemDialog(checklist)
                }
            } else if (holder is ItemViewHolder) {
                val checkItem = item as SignalKChecklistItem
                val checklist = itemToChecklist[checkItem] ?: return
                
                holder.title.text = checkItem.title
                holder.checkBox.isChecked = checkItem.state == "completed"
                holder.checkBox.setOnCheckedChangeListener { _, isChecked ->
                    val newState = if (isChecked) "completed" else "pending"
                    val updatedItems = checklist.items.map { 
                        if (it.title == checkItem.title) it.copy(state = newState) else it
                    }
                    val updatedChecklist = checklist.copy(items = updatedItems)
                    
                    holder.title.alpha = 0.5f // Optimistic UI: Dim until confirmed
                    
                    scope.launch {
                        try {
                            val id = checklists.entries.find { it.value == checklist }?.key ?: checklist.name
                            val response = NauticalPlugin.engine?.getRestService()?.updateChecklist(id, updatedChecklist)
                            if (response?.isSuccessful == true) {
                                withContext(Dispatchers.Main) {
                                    holder.title.alpha = 1.0f
                                    app.showToastMessage(R.string.shared_string_ok)
                                }
                            } else {
                                withContext(Dispatchers.Main) {
                                    holder.title.alpha = 1.0f
                                    holder.checkBox.isChecked = !isChecked // Revert on failure
                                    app.showToastMessage("Checklist Sync Failed")
                                }
                            }
                        } catch (_: Exception) {
                            withContext(Dispatchers.Main) {
                                holder.title.alpha = 1.0f
                                holder.checkBox.isChecked = !isChecked
                            }
                        }
                    }
                }
            }
        }

        private fun showAddItemDialog(checklist: SignalKChecklist) {
            val context = context ?: return
            val input = android.widget.EditText(context).apply {
                hint = "Item Title"
            }
            
            androidx.appcompat.app.AlertDialog.Builder(context)
                .setTitle("Add Item to ${checklist.name}")
                .setView(input)
                .setPositiveButton(R.string.shared_string_add) { _, _ ->
                    val title = input.text.toString()
                    if (title.isNotEmpty()) {
                        val newItem = SignalKChecklistItem(title = title, state = "pending")
                        val updatedChecklist = checklist.copy(items = checklist.items + newItem)
                        scope.launch {
                            val id = checklists.entries.find { it.value == checklist }?.key ?: checklist.name
                            NauticalPlugin.engine?.resourceManager?.pushChecklistToServer(id, updatedChecklist)
                            loadChecklists()
                        }
                    }
                }
                .setNegativeButton(R.string.shared_string_cancel, null)
                .show()
        }

        override fun getItemCount(): Int = flatItems.size
    }

    private class HeaderViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.title)
        val btnAdd: View = view.findViewById(R.id.btn_add_item)
    }

    private class ItemViewHolder(view: View) : RecyclerView.ViewHolder(view) {

        val title: TextView = view.findViewById(R.id.title)
        val checkBox: CheckBox = view.findViewById(R.id.checkbox)
    }
}
