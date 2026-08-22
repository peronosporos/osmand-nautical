package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.EditText
import android.text.InputType
import android.widget.ImageView
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
        val recyclerView = view.findViewById<RecyclerView?>(R.id.recycler_view)
        
        adapter = BuddyAdapter { mmsi ->
            confirmDeleteBuddy(mmsi)
        }
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter

        view.findViewById<View>(R.id.fab_add_buddy)?.setOnClickListener {
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

    private fun confirmDeleteBuddy(mmsi: Int) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.nautical_buddy_delete_confirm, mmsi))
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                val current = NauticalPlugin.engine?.getCurrentState()?.aisBuddies?.toMutableSet() ?: mutableSetOf()
                if (current.remove(mmsi)) {
                    NauticalPlugin.engine?.sendDelta(net.osmand.plus.plugins.nautical.engine.SignalKPaths.NAV_AIS_BUDDIES, current.toList())
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showAddBuddyDialog() {
        val context = context ?: return
        val aisObjects = NauticalPlugin.getInstance()?.aisManager?.getAisObjects() ?: emptyList()
        val names = aisObjects.map { it.shipName ?: "MMSI: ${it.mmsi}" }.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle(R.string.nautical_add_to_buddies)
            .setItems(names) { _, which ->
                val selected = aisObjects[which]
                val current = NauticalPlugin.engine?.getCurrentState()?.aisBuddies?.toMutableSet() ?: mutableSetOf()
                current.add(selected.mmsi)
                NauticalPlugin.engine?.sendDelta(net.osmand.plus.plugins.nautical.engine.SignalKPaths.NAV_AIS_BUDDIES, current.toList())
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
                    val current = NauticalPlugin.engine?.getCurrentState()?.aisBuddies?.toMutableSet() ?: mutableSetOf()
                    current.add(mmsi)
                    NauticalPlugin.engine?.sendDelta(net.osmand.plus.plugins.nautical.engine.SignalKPaths.NAV_AIS_BUDDIES, current.toList())
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private class BuddyAdapter(private val onDelete: (Int) -> Unit) : ListAdapter<Int, BuddyViewHolder>(BuddyDiffCallback()) {


        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BuddyViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_icon_and_menu, parent, false)
            return BuddyViewHolder(view)
        }

        override fun onBindViewHolder(holder: BuddyViewHolder, position: Int) {
            val ctx = holder.itemView.context
            val mmsi = getItem(position)
            val ais = NauticalPlugin.getAisObject(mmsi)
            holder.txtName.text = ais?.shipName ?: ctx.getString(R.string.nautical_buddy_mmsi, mmsi)
            
            val ownLoc = NauticalPlugin.getInstance()?.application?.locationProvider?.lastKnownLocation
            val pos = ais?.position
            if (ownLoc != null && pos != null) {
                val targetLoc = net.osmand.Location("AIS").apply {
                    latitude = pos.latitude
                    longitude = pos.longitude
                }
                val distNm = ownLoc.distanceTo(targetLoc) / 1852.0
                val bearingDeg = (ownLoc.bearingTo(targetLoc) + 360f) % 360f
                val type = ais.getShipTypeString().ifEmpty { "Vessel" }
                holder.txtDesc.text = String.format(java.util.Locale.US, "Range: %.2f nm • %03.0f° | %s", distNm, bearingDeg, type)
            } else if (ais != null) {
                holder.txtDesc.text = ctx.getString(R.string.nautical_buddy_type_status, ais.getShipTypeString(), ais.getNavStatusString())
            } else {
                holder.txtDesc.text = ctx.getString(R.string.nautical_vessel_data_unavailable)
            }
            
            holder.icon.setImageResource(R.drawable.ic_action_sail_boat_dark)
            holder.secondaryIcon.setImageResource(R.drawable.ic_action_delete_dark)
            holder.secondaryIcon.visibility = View.VISIBLE
            holder.secondaryIcon.setOnClickListener { onDelete(mmsi) }
            holder.toggle.visibility = View.GONE
        }
    }

    private class BuddyDiffCallback : DiffUtil.ItemCallback<Int>() {
        override fun areItemsTheSame(oldItem: Int, newItem: Int): Boolean = oldItem == newItem
        override fun areContentsTheSame(oldItem: Int, newItem: Int): Boolean = oldItem == newItem
    }

    private class BuddyViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val txtName: TextView = view.findViewById(R.id.title)
        val txtDesc: TextView = view.findViewById(R.id.description)
        val icon: ImageView = view.findViewById(R.id.icon)
        val secondaryIcon: ImageView = view.findViewById(R.id.secondary_icon)
        val toggle: View = view.findViewById(R.id.toggle_item)
    }
}

