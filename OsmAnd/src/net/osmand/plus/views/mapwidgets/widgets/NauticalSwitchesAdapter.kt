package net.osmand.plus.views.mapwidgets.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.widget.SwitchCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R

class NauticalSwitchesAdapter(
    private var switches: Map<String, Boolean>,
    private val onSwitchClicked: (String) -> Unit,
) : RecyclerView.Adapter<NauticalSwitchesAdapter.SwitchViewHolder>() {

    private var switchList = switches.asSequence().sortedBy { it.key }.toList()

    class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val name: TextView = view.findViewById(R.id.txt_switch_name)
        val toggle: SwitchCompat = view.findViewById(R.id.switch_toggle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_switch, parent, false)
        return SwitchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SwitchViewHolder, position: Int) {
        val entry = switchList[position]
        val path = entry.key
        val state = entry.value
        holder.name.text = path.substringAfterLast(".").replaceFirstChar { it.uppercase() }

        holder.toggle.setOnCheckedChangeListener(null)
        holder.toggle.isChecked = state
        holder.toggle.setOnCheckedChangeListener { _, isChecked -> 
            if (isChecked != switches[path]) {
                onSwitchClicked(path) 
            }
        }

        holder.itemView.setOnClickListener { holder.toggle.toggle() }
    }

    override fun getItemCount(): Int = switchList.size

    fun updateSwitches(newSwitches: Map<String, Boolean>) {
        if (newSwitches != switches) {
            val oldList = switchList
            switches = newSwitches
            switchList = switches.asSequence().sortedBy { it.key }.toList()
            
            val diffResult = DiffUtil.calculateDiff(
                object : DiffUtil.Callback() {
                    override fun getOldListSize(): Int = oldList.size
                    override fun getNewListSize(): Int = switchList.size
                    override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return oldList[oldItemPosition].key == switchList[newItemPosition].key
                    }
                    override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                        return oldList[oldItemPosition].value == switchList[newItemPosition].value
                    }
                },
            )
            diffResult.dispatchUpdatesTo(this)
        }
    }
}
