package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.text.InputType
import androidx.appcompat.app.AlertDialog
import com.google.android.material.floatingactionbutton.FloatingActionButton
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseOsmAndFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalBuddyListFragment : BaseOsmAndFragment() {

    private lateinit var adapter: BuddyAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_buddy_list, container, false)
        val recyclerView: RecyclerView = view.findViewById(R.id.recycler_view)
        
        adapter = BuddyAdapter()
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fab_add_buddy).setOnClickListener {
            showAddBuddyDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val buddies = state.aisBuddies.toList()
                adapter.submitList(buddies)
                view.findViewById<View>(R.id.txt_empty_list)?.visibility = if (buddies.isEmpty()) View.VISIBLE else View.GONE
            }
        }

        return view
    }

    private fun showAddBuddyDialog() {
        val context = context ?: return
        val aisObjects = NauticalPlugin.getInstance()?.aisManager?.getAisObjects() ?: emptyList()
        val names = aisObjects.map { it.shipName ?: "MMSI: ${it.mmsi}" }.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle(R.string.nautical_add_to_buddies)
            .setItems(names) { _, which ->
                val selected = aisObjects[which]
                NauticalPlugin.engine?.sendDelta("navigation.aisBuddies", listOf(selected.mmsi))
            }
            .setNeutralButton(R.string.shared_string_add_manually) { _, _ ->
                 showManualMmsiDialog()
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showManualMmsiDialog() {
        val context = context ?: return
        val input = EditText(context).apply {
            hint = "Enter MMSI"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        
        AlertDialog.Builder(context)
            .setTitle(R.string.nautical_add_to_buddies)
            .setView(input)
            .setPositiveButton(R.string.shared_string_add) { _, _ ->
                val mmsi = input.text.toString().toIntOrNull()
                if (mmsi != null) {
                    NauticalPlugin.engine?.sendDelta("navigation.aisBuddies", listOf(mmsi))
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private class BuddyAdapter : ListAdapter<Int, BuddyViewHolder>(BuddyDiffCallback()) {


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuddyViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_with_descr, parent, false)
            return BuddyViewHolder(view)
        }

        override fun onBindViewHolder(holder: BuddyViewHolder, position: Int) {
            val ctx = holder.itemView.context
            val mmsi = getItem(position)
            val ais = NauticalPlugin.getAisObject(mmsi)
            holder.txtName.text = ais?.shipName ?: ctx.getString(R.string.nautical_buddy_mmsi, mmsi)
            holder.txtDesc.text = if (ais != null) {
                ctx.getString(R.string.nautical_buddy_type_status, ais.getShipTypeString(), ais.getNavStatusString())
            } else {
                ctx.getString(R.string.nautical_vessel_data_unavailable)
            }
        }
    }

    private class BuddyDiffCallback : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean = oldItem == newItem
    }

    private class BuddyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.title)
        val txtDesc: TextView = view.findViewById(R.id.description)
    }
}
