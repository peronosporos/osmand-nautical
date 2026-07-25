package net.osmand.plus.plugins.nautical.raster

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.SeekBar
import androidx.appcompat.widget.SwitchCompat
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.settings.fragments.BaseSettingsFragment
import net.osmand.plus.settings.fragments.SettingsScreenType

class MarineRasterSettingsControl : BaseMaterialBottomSheetDialogFragment() {

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_marine_raster_control, container, false)
        val app = requireActivity().application as OsmandApplication
        val settings = app.settings

        val toggle = view.findViewById<SwitchCompat>(R.id.raster_toggle)
        toggle.isChecked = settings.NAUTICAL_SHOW_RASTER_CHARTS.get()
        toggle.setOnCheckedChangeListener { _, isChecked ->
            settings.NAUTICAL_SHOW_RASTER_CHARTS.set(isChecked)
            app.osmandMap.refreshMap()
        }

        val seekBar = view.findViewById<SeekBar>(R.id.opacity_seekbar)
        seekBar.progress = settings.NAUTICAL_RASTER_CHARTS_OPACITY.get()
        seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    settings.NAUTICAL_RASTER_CHARTS_OPACITY.set(progress)
                    app.osmandMap.refreshMap()
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        view.findViewById<Button>(R.id.manage_btn).setOnClickListener {
            dismiss()
            BaseSettingsFragment.showInstance(requireActivity(), SettingsScreenType.MARINE_RASTER_MANAGER)
        }

        return view
    }

    companion object {
        fun show(fragmentManager: androidx.fragment.app.FragmentManager) {
            MarineRasterSettingsControl().show(fragmentManager, "MarineRasterSettingsControl")
        }
    }
}
