package net.osmand.plus.plugins.nautical.tide.ui

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.ui.widgets.BaseNauticalBottomSheet

class TideStationBottomSheet : BaseNauticalBottomSheet() {

    private lateinit var viewModel: TideViewModel
    private var graphView: TideGraphView? = null
    private var stationName: TextView? = null

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

    override fun onStart() {
        super.onStart()
        (dialog as? BottomSheetDialog)?.let { sheetDialog ->
            sheetDialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)?.let { bottomSheet ->
                val behavior = BottomSheetBehavior.from(bottomSheet)
                val metrics = resources.displayMetrics
                val isLandscape = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
                val screenHeightDp = metrics.heightPixels / metrics.density
                
                if (isLandscape || (screenHeightDp < 600)) {
                    behavior.peekHeight = (metrics.heightPixels * 0.7).toInt()
                    behavior.maxHeight = metrics.heightPixels
                } else {
                    behavior.maxHeight = (metrics.heightPixels * 0.6).toInt()
                    behavior.peekHeight = (metrics.heightPixels * 0.4).toInt()
                }
                behavior.state = BottomSheetBehavior.STATE_COLLAPSED
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
        
        // Red filter handled by BaseMaterialBottomSheetDialogFragment via BaseNauticalBottomSheet
        val plugin = NauticalPlugin.getInstance()


        viewModel = ViewModelProvider(this)[TideViewModel::class.java]
        
        stationName = view.findViewById(R.id.graph_title)
        // We replace the generic NauticalGraphView with our specialized TideGraphView programmatically 
        // if the XML doesn't have it, or assume it's there.
        val oldGraph = view.findViewById<View>(R.id.graph_view)
        val container = oldGraph?.parent as? ViewGroup
        
        graphView = TideGraphView(requireContext()).apply {
            val h = if (resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE) {
                (resources.displayMetrics.heightPixels * 0.4).toInt()
            } else {
                (resources.displayMetrics.heightPixels * 0.3).toInt()
            }
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, h)
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
        val skStationId = arguments?.getString("signalk_station_id")

        if (skStationId != null) {
            viewModel.selectSignalKStation(skStationId)
        } else {
            viewModel.findNearbyStations(lat, lon)
        }

        lifecycleScope.launch {
            viewModel.selectedStation.collectLatest { station ->
                val source = if (station?.constituents?.isEmpty() == true) "Signal K" else "Local"
                val name = station?.name ?: getString(R.string.tide_dialog_title)
                stationName?.text = getString(R.string.tide_station_title_with_source, name, source)
            }
        }

        lifecycleScope.launch {
            viewModel.predictions.collectLatest { predictions ->
                graphView?.setPredictions(predictions)
            }
        }

        lifecycleScope.launch {
            viewModel.vesselTide.collectLatest { vesselTide ->
                if (vesselTide?.stationName == viewModel.selectedStation.value?.name) {
                    graphView?.setVesselTide(vesselTide)
                } else {
                    graphView?.setVesselTide(null)
                }
            }
        }
    }
}
