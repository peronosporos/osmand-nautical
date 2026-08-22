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
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.ui.widgets.BaseNauticalBottomSheet

class TideStationBottomSheet : BaseNauticalBottomSheet() {

    private lateinit var viewModel: TideViewModel
    private var graphView: TideGraphView? = null

    companion object {
        const val TAG = "tide_station"
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

        fun show(fm: androidx.fragment.app.FragmentManager, lat: Double, lon: Double, skStationId: String? = null) {
            if (fm.isStateSaved) return
            if (fm.findFragmentByTag(TAG) == null) {
                val sheet = newInstance(lat, lon)
                if (skStationId != null) {
                    val args = sheet.arguments ?: Bundle()
                    args.putString("signalk_station_id", skStationId)
                    sheet.arguments = args
                }
                sheet.show(fm, TAG)
            }
        }
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        viewModel = ViewModelProvider(this)[TideViewModel::class.java]

        val lat = arguments?.getDouble(LAT) ?: 0.0
        val lon = arguments?.getDouble(LON) ?: 0.0
        val skStationId = arguments?.getString("signalk_station_id")

        if (skStationId != null) {
            viewModel.selectSignalKStation(skStationId)
        } else {
            viewModel.findNearbyStations(lat, lon)
        }

        // Add Header
        addTitleItem(getString(R.string.tide_dialog_title))

        // Add Graph
        val themedCtx = net.osmand.plus.utils.UiUtilities.getThemedContext(requireContext(), nightMode)
        val graphContainer = LayoutInflater.from(themedCtx).inflate(R.layout.bottom_sheet_nautical_data, null)
        graphContainer.findViewById<View>(R.id.graph_title)?.visibility = View.GONE
        val oldGraph = graphContainer.findViewById<View>(R.id.graph_view)
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

        items.add(BaseBottomSheetItem.Builder().setCustomView(graphContainer).create())

        observeViewModel()
    }

    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.selectedStation.collectLatest { station ->
                val source = if (station?.constituents?.isEmpty() == true) "Signal K" else "Local"
                val name = station?.name ?: getString(R.string.tide_dialog_title)
                // In MenuBottomSheet, we'd need to update the title item if we want it dynamic.
                // For now, let's keep it simple or use a custom view for title.
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
}
