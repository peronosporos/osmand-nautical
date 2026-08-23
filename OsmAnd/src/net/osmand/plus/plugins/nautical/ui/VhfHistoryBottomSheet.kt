package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
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
import java.util.Date

class VhfHistoryBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val root = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_vhf_history, container, false)
        
        val recycler = root.findViewById<RecyclerView>(R.id.recycler_view)
        recycler.layoutManager = LinearLayoutManager(requireContext())
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

    inner class VhfAdapter(private val onClick: (VhfTransmission) -> Unit) : ListAdapter<VhfTransmission, VhfAdapter.ViewHolder>(VhfDiffCallback()) {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_vhf_transmission, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = getItem(position)
            holder.name.text = item.vesselName ?: getString(R.string.nautical_unknown_vessel)
            val time = java.text.DateFormat.getTimeInstance(java.text.DateFormat.SHORT).format(Date(item.timestamp))
            holder.meta.text = getString(R.string.nautical_vhf_history_meta_fmt, time, item.channel ?: "---")
            holder.itemView.setOnClickListener { onClick(item) }
            holder.playIcon.setOnClickListener { onClick(item) }
        }

        inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val name: TextView = view.findViewById(R.id.txt_transmission_title)
            val meta: TextView = view.findViewById(R.id.txt_transmission_time)
            val playIcon: ImageView = view.findViewById(R.id.img_play_icon)
        }
    }

    private class VhfDiffCallback : DiffUtil.ItemCallback<VhfTransmission>() {
        override fun areItemsTheSame(oldItem: VhfTransmission, newItem: VhfTransmission): Boolean = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: VhfTransmission, newItem: VhfTransmission): Boolean = oldItem == newItem
    }

    companion object {
        const val TAG = "VhfHistoryBottomSheet"

        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            if (fragmentManager.isStateSaved) return
            if (fragmentManager.findFragmentByTag(TAG) == null) {
                VhfHistoryBottomSheet().show(fragmentManager, TAG)
            }
        }
    }
}
