package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

class NauticalSwitchesBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    private lateinit var adapter: NauticalSwitchesAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val root = inflater.inflate(R.layout.bottom_sheet_nautical_data, container, false)
        root.findViewById<TextView>(R.id.graph_title).setText(R.string.nautical_switches_label)
        root.findViewById<View>(R.id.graph_view).visibility = View.GONE
        
        val recyclerView = RecyclerView(requireContext())
        recyclerView.layoutManager = GridLayoutManager(requireContext(), 2)
        (root as ViewGroup).addView(recyclerView)
        
        val electrical = NauticalPlugin.electrical
        adapter = NauticalSwitchesAdapter(emptyMap()) { path ->
            electrical?.toggleSwitch(path)
        }
        recyclerView.adapter = adapter

        viewLifecycleOwner.lifecycleScope.launch {
            NauticalPlugin.engine?.marineStateFlow?.collectLatest { state ->
                adapter.updateData(state.switches, state.dimmers, state.pathMeta)
            }
        }

        return root
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            NauticalSwitchesBottomSheet().show(fragmentManager, "NauticalSwitchesBottomSheet")
        }
    }
}
