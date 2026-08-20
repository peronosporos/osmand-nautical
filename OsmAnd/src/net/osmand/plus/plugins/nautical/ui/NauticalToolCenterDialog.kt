package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.fragment.app.FragmentManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.settings.fragments.SettingsScreenType

class NauticalToolCenterDialog : BaseMaterialBottomSheetDialogFragment() {

    companion object {
        const val TAG = "NauticalToolCenterDialog"
        fun show(fragmentManager: FragmentManager) {
            NauticalToolCenterDialog().show(fragmentManager, TAG)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            val p = (16 * resources.displayMetrics.density).toInt()
            setPadding(p, p, p, p)
        }
        
        val titleView = TextView(requireContext()).apply {
            text = getString(R.string.nautical_tool_center)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            val p = (8 * resources.displayMetrics.density).toInt()
            setPadding(0, 0, 0, p)
            setTextColor(if (nightMode) 0xFFFFFFFF.toInt() else 0xFF000000.toInt())
        }
        root.addView(titleView)

        val recyclerView = RecyclerView(requireContext()).apply {
            layoutManager = LinearLayoutManager(requireContext())
        }
        root.addView(recyclerView)
        
        val items = listOf(
            ToolItem(getString(R.string.logbook_title), R.drawable.ic_action_track_16) {
                net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.MARINE_LOGBOOK)
                dismiss()
            },
            ToolItem("Sail Inventory", R.drawable.ic_action_sail_boat_dark) {
                net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.SAIL_INVENTORY)
                dismiss()
            },
            ToolItem(getString(R.string.nautical_ais_targets_title), R.drawable.mm_ais_vessel) {
                val aisObjects = NauticalPlugin.getInstance()?.aisManager?.getAisObjects() ?: emptyList()
                if (aisObjects.isNotEmpty()) {
                    NauticalTargetPicker.newInstance(aisObjects).show(parentFragmentManager, "ais_target_picker")
                } else {
                    NauticalPlugin.getInstance()?.application?.showToastMessage(R.string.nautical_ais_no_targets)
                }
                dismiss()
            },
            ToolItem("AIS Buddies", R.drawable.ic_action_group_list) {
                net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.NAUTICAL_AIS_BUDDIES)
                dismiss()
            },
            ToolItem(getString(R.string.nautical_compass_wizard), R.drawable.ic_action_compass) {
                net.osmand.plus.views.mapwidgets.widgets.NauticalCompassWizardDialog.show(this)
                dismiss()
            },
            ToolItem("Polar Manager", R.drawable.ic_action_settings) {
                net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.SAILING_PERFORMANCE_SETTINGS)
                dismiss()
            }
        )

        recyclerView.adapter = ToolAdapter(items)
        return root
    }

    private data class ToolItem(val title: String, val icon: Int, val action: () -> Unit)

    private inner class ToolAdapter(private val items: List<ToolItem>) : RecyclerView.Adapter<ToolViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.list_item_icon_and_menu, parent, false)
            return ToolViewHolder(view)
        }
        override fun onBindViewHolder(holder: ToolViewHolder, position: Int) = holder.bind(items[position])
        override fun getItemCount(): Int = items.size
    }

    private class ToolViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        fun bind(item: ToolItem) {
            val title = itemView.findViewById<TextView>(R.id.title)
            title.text = item.title
            val icon = itemView.findViewById<android.widget.ImageView>(R.id.icon)
            icon.visibility = View.VISIBLE
            icon.setImageResource(item.icon)
            itemView.setOnClickListener { item.action() }
        }
    }
}
