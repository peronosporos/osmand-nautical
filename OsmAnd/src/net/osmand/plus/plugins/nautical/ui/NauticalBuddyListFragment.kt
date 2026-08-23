package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
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
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType

class NauticalBuddyListFragment : BaseOsmAndFragment() {

    companion object {
        const val TAG = "nautical_buddy_list"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            if (fragmentManager.isStateSaved) return
            val fragment = NauticalBuddyListFragment()
            fragmentManager.beginTransaction()
                .add(R.id.fragmentContainer, fragment, TAG)
                .addToBackStack(TAG)
                .commit()
        }
    }

    private lateinit var adapter: BuddyAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = themedInflater.inflate(R.layout.fragment_nautical_buddy_list, container, false)
        
        view.findViewById<View>(R.id.close_button)?.setOnClickListener {
            activity?.onBackPressedDispatcher?.onBackPressed()
        }

        view.findViewById<View>(R.id.btn_own_vessel_profile)?.setOnClickListener {
            val mapActivity = activity as? net.osmand.plus.activities.MapActivity
            if (mapActivity != null) {
                BaseSettingsFragment.showInstance(mapActivity, SettingsScreenType.AIS_SETTINGS)
            }
        }

        val recyclerView = view.findViewById<RecyclerView?>(R.id.recycler_view)
        val emptyLayout = view.findViewById<View>(R.id.layout_empty_list)
        
        adapter = BuddyAdapter(
            onClick = { mmsi ->
                if (!parentFragmentManager.isStateSaved) {
                    NauticalAisDetailsDialog.show(parentFragmentManager, mmsi)
                }
            },
            onDelete = { mmsi ->
                confirmDeleteBuddy(mmsi)
            }
        )
        recyclerView?.layoutManager = LinearLayoutManager(requireContext())
        recyclerView?.adapter = adapter

        view.findViewById<View>(R.id.fab_add_buddy)?.setOnClickListener {
            showAddBuddyDialog()
        }

        view.findViewById<View>(R.id.btn_empty_add_buddy)?.setOnClickListener {
            showAddBuddyDialog()
        }

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                val buddies = state.aisBuddies.toList()
                adapter.submitList(buddies)
                emptyLayout?.visibility = if (buddies.isEmpty()) View.VISIBLE else View.GONE
                recyclerView?.visibility = if (buddies.isEmpty()) View.GONE else View.VISIBLE
            }
        }

        return view
    }

    private fun confirmDeleteBuddy(mmsi: Int) {
        AlertDialog.Builder(requireContext())
            .setMessage(getString(R.string.nautical_buddy_delete_confirm, mmsi))
            .setPositiveButton(R.string.shared_string_delete) { _, _ ->
                NauticalPlugin.getInstance()?.aisManager?.removeBuddy(mmsi)
                app.showToastMessage(getString(R.string.nautical_removed_from_buddies, mmsi.toString()))
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private fun showAddBuddyDialog() {
        val context = context ?: return
        val aisObjects = NauticalPlugin.getInstance()?.aisManager?.getAisObjects() ?: emptyList()
        if (aisObjects.isEmpty()) {
            showManualMmsiDialog()
            return
        }
        val names = aisObjects.map { it.shipName ?: "MMSI: ${it.mmsi}" }.toTypedArray()
        
        AlertDialog.Builder(context)
            .setTitle(R.string.nautical_add_to_buddies)
            .setItems(names) { _, which ->
                val selected = aisObjects[which]
                NauticalPlugin.getInstance()?.aisManager?.addBuddy(selected.mmsi)
                val label = selected.shipName ?: selected.mmsi.toString()
                app.showToastMessage(getString(R.string.nautical_added_to_buddies, label))
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
            hint = "Enter 9-digit MMSI"
            inputType = InputType.TYPE_CLASS_NUMBER
        }
        
        AlertDialog.Builder(context)
            .setTitle(R.string.nautical_add_to_buddies)
            .setView(input)
            .setPositiveButton(R.string.shared_string_add) { _, _ ->
                val text = input.text.toString().trim()
                val mmsi = text.toIntOrNull()
                if (mmsi != null && text.length == 9) {
                    NauticalPlugin.getInstance()?.aisManager?.addBuddy(mmsi)
                    app.showToastMessage(getString(R.string.nautical_added_to_buddies, mmsi.toString()))
                } else {
                    app.showToastMessage("Invalid MMSI. Please enter a valid 9-digit MMSI.")
                }
            }
            .setNegativeButton(R.string.shared_string_cancel, null)
            .show()
    }

    private class BuddyAdapter(
        private val onClick: (Int) -> Unit,
        private val onDelete: (Int) -> Unit
    ) : ListAdapter<Int, BuddyViewHolder>(BuddyDiffCallback()) {

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
            
            holder.icon.setImageResource(R.drawable.ic_action_favorite)
            holder.icon.setColorFilter(android.graphics.Color.rgb(255, 215, 0))
            holder.secondaryIcon.setImageResource(R.drawable.ic_action_delete_dark)
            holder.secondaryIcon.visibility = View.VISIBLE
            holder.secondaryIcon.setOnClickListener { onDelete(mmsi) }
            holder.itemView.setOnClickListener { onClick(mmsi) }
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
