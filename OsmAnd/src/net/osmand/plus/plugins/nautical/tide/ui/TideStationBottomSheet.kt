package net.osmand.plus.plugins.nautical.tide.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin

class TideStationBottomSheet : BottomSheetDialogFragment() {

    private lateinit var viewModel: TideViewModel
    private var graphView: TideGraphView? = null
    private var stationName: TextView? = null
    private var currentLevel: TextView? = null

    companion object {
        private const val LAT = "lat"
        private const val LON = "lon"

        @JvmStatic
        fun newInstance(lat: Double, lon: Double): TideStationBottomSheet {
            return TideStationBottomSheet().apply {
                arguments = Bundle().apply {
                    putDouble(LAT, lat)
                    putDouble(LON, lon)
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        // We reuse the nautical graph layout or a custom one if available.
        // For this implementation, we assume a layout with ID R.layout.bottom_sheet_tide_station exists.
        // If not, we'd fallback to a programmatic view or existing nautical layout.
        return inflater.inflate(R.layout.bottom_sheet_nautical_data, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val plugin = NauticalPlugin.getInstance()
        plugin?.applyNightVisionFilter(view)

        viewModel = ViewModelProvider(this)[TideViewModel::class.java]
        
        stationName = view.findViewById(R.id.graph_title)
        // We replace the generic NauticalGraphView with our specialized TideGraphView programmatically 
        // if the XML doesn't have it, or assume it's there.
        val oldGraph = view.findViewById<View>(R.id.graph_view)
        val container = oldGraph?.parent as? ViewGroup
        
        graphView = TideGraphView(requireContext()).apply {
            layoutParams = oldGraph?.layoutParams ?: ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, 
                400
            )
        }
        
        container?.let {
            val index = it.indexOfChild(oldGraph)
            if (index != -1) {
                it.removeViewAt(index)
                it.addView(graphView, index)
            } else {
                it.addView(graphView)
            }
        }

        val lat = arguments?.getDouble(LAT) ?: 0.0
        val lon = arguments?.getDouble(LON) ?: 0.0
        
        viewModel.findNearbyStations(lat, lon)

        lifecycleScope.launch {
            viewModel.selectedStation.collectLatest { station ->
                stationName?.text = station?.name ?: getString(R.string.tide_dialog_title)
            }
        }

        lifecycleScope.launch {
            viewModel.predictions.collectLatest { predictions ->
                graphView?.setPredictions(predictions)
            }
        }
    }
}
