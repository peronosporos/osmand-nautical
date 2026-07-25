package net.osmand.plus.views.mapwidgets.widgets

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalSwitchesAdapter(
    private var switches: Map<String, Boolean>,
    private val onSwitchClicked: (String) -> Unit
) : RecyclerView.Adapter<NauticalSwitchesAdapter.SwitchViewHolder>() {

    private var switchList = switches.toList().sortedBy { it.first }

    class SwitchViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.switch_icon)
        val name: TextView = view.findViewById(R.id.switch_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SwitchViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_nautical_switch, parent, false)
        return SwitchViewHolder(view)
    }

    override fun onBindViewHolder(holder: SwitchViewHolder, position: Int) {
        val (path, state) = switchList[position]
        holder.name.text = path.substringAfterLast(".").replaceFirstChar { it.uppercase() }
        
        val color = if (state) {
            ContextCompat.getColor(holder.itemView.context, R.color.color_ok)
        } else {
            ContextCompat.getColor(holder.itemView.context, R.color.color_unknown)
        }
        holder.icon.setColorFilter(color)
        
        holder.itemView.setOnClickListener { onSwitchClicked(path) }
    }

    override fun getItemCount(): Int = switchList.size

    fun updateSwitches(newSwitches: Map<String, Boolean>) {
        if (newSwitches != switches) {
            switches = newSwitches
            switchList = switches.toList().sortedBy { it.first }
            notifyDataSetChanged()
        }
    }
}
