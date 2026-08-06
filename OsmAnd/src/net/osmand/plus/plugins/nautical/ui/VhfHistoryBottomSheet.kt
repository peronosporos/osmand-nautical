package net.osmand.plus.plugins.nautical.ui

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.network.VhfTransmission
import java.util.*

class VhfHistoryBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val pad = toPx(context, 16)
            setPadding(pad, pad, pad, 0)
        }
        
        val title = TextView(context).apply {
            text = getString(R.string.nautical_vhf_history)
            setTextAppearance(android.R.style.TextAppearance_Material_Headline)
            setPadding(0, 0, 0, toPx(context, 16))
        }
        root.addView(title)
        
        val recycler = RecyclerView(context).apply {
            id = View.generateViewId()
            layoutManager = LinearLayoutManager(context)
        }
        root.addView(
            recycler,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f,
            ),
        )
        
        setupRecycler(recycler)
        return root
    }

    private fun setupRecycler(recycler: RecyclerView) {
        val adapter = VhfAdapter { transmission ->
            NauticalPlugin.getInstance()?.vhfManager?.playReplay(transmission)
            dismiss()
        }
        recycler.adapter = adapter
        
        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.getInstance()?.vhfManager?.history?.collectLatest { 
                adapter.submitList(it)
            }
        }
    }

    private fun toPx(context: Context, dp: Int): Int = (dp * context.resources.displayMetrics.density).toInt()

    inner class VhfAdapter(private val onClick: (VhfTransmission) -> Unit) : ListAdapter<VhfTransmission, VhfAdapter.ViewHolder>(VhfDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val context = parent.context
            val view = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
                setPadding(toPx(context, 8), toPx(context, 12), toPx(context, 8), toPx(context, 12))
                isClickable = true
                val out = android.util.TypedValue()
                context.theme.resolveAttribute(android.R.attr.selectableItemBackground, out, true)
                setBackgroundResource(out.resourceId)
            }
            
            val infoLayout = LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
            }
            
            val name = TextView(context).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Body1)
            }
            infoLayout.addView(name)
            
            val meta = TextView(context).apply {
                setTextAppearance(android.R.style.TextAppearance_Material_Caption)
            }
            infoLayout.addView(meta)
            
            view.addView(infoLayout, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            
            val play = ImageView(context).apply {
                setImageResource(R.drawable.ic_action_play_dark)
                setPadding(toPx(context, 8), toPx(context, 8), toPx(context, 8), toPx(context, 8))
            }
            view.addView(play)
            
            return ViewHolder(view, name, meta)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.name.text = item.vesselName ?: "Unknown Vessel"
            val time = java.text.SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date(item.timestamp))
            holder.meta.text = getString(R.string.nautical_vhf_history_meta_fmt, time, item.channel ?: "---")
            holder.itemView.setOnClickListener { onClick(item) }
        }

        inner class ViewHolder(view: View, val name: TextView, val meta: TextView) : RecyclerView.ViewHolder(view)
    }

    private class VhfDiffCallback : DiffUtil.ItemCallback<VhfTransmission>() {
        override fun areItemsTheSame(oldItem: VhfTransmission, newItem: VhfTransmission): Boolean {
            return (oldItem.timestamp == newItem.timestamp) && (oldItem.vesselName == newItem.vesselName)
        }

        override fun areContentsTheSame(oldItem: VhfTransmission, newItem: VhfTransmission): Boolean {
            return oldItem == newItem
        }
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            VhfHistoryBottomSheet().show(fragmentManager, "VhfHistoryBottomSheet")
        }
    }
}
