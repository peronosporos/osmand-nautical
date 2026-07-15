package net.osmand.plus.plugins.nautical

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BottomSheetDialogFragment

class NauticalActionBottomSheet : BottomSheetDialogFragment() {

    companion object {
        const val TAG = "NauticalActionBottomSheet"
        private const val LAT_KEY = "target_lat"
        private const val LON_KEY = "target_lon"

        @JvmStatic
        fun newInstance(lat: Double, lon: Double): NauticalActionBottomSheet {
            return NauticalActionBottomSheet().apply {
                arguments = Bundle().apply {
                    putDouble(LAT_KEY, lat)
                    putDouble(LON_KEY, lon)
                }
            }
        }

        @JvmStatic
        fun addNauticalLayer(mapView: net.osmand.plus.views.OsmandMapTileView, layer: NauticalMapLayer) {
            mapView.addLayer(layer, 5.0f)
        }

        @JvmStatic
        fun removeNauticalLayer(mapView: net.osmand.plus.views.OsmandMapTileView, layer: NauticalMapLayer) {
            mapView.removeLayer(layer)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_nautical_pilot, container, false)

        val targetLat = arguments?.getDouble(LAT_KEY) ?: 0.0
        val targetLon = arguments?.getDouble(LON_KEY) ?: 0.0

        view.findViewById<TextView>(R.id.tv_pilot_status)?.apply {
            text = getString(R.string.nautical_target_coordinates, targetLat, targetLon)
        }

        view.findViewById<View>(R.id.btn_auto)?.setOnClickListener {
            NauticalPlugin.autopilot?.sendActiveWaypoint(targetLat, targetLon)
            (activity?.application as? OsmandApplication)?.showToastMessage(R.string.nautical_command_sent)
            dismiss()
        }

        view.findViewById<View>(R.id.btn_emergency_stop)?.setOnClickListener {
            // Placeholder for emergency stop if needed
            (activity?.application as? OsmandApplication)?.showToastMessage(R.string.nautical_error_emergency_stop_not_implemented)
            dismiss()
        }

        return view
    }
}
