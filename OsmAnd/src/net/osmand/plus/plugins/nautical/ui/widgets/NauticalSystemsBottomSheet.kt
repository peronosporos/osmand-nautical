package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.*
import android.widget.Button
import androidx.fragment.app.FragmentManager
import net.osmand.plus.R
import net.osmand.plus.base.bottomsheetmenu.BaseBottomSheetItem
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * Control hub for vessel systems: Windlass, Checklists, etc.
 */
class NauticalSystemsBottomSheet : BaseNauticalBottomSheet() {

    companion object {
        fun show(fragmentManager: FragmentManager) {
            NauticalSystemsBottomSheet().show(fragmentManager, "nautical_systems")
        }
    }

    override fun createMenuItems(savedInstanceState: Bundle?) {
        addTitleItem(getString(R.string.nautical_systems_group))

        val customView = LayoutInflater.from(requireContext()).inflate(R.layout.bottom_sheet_nautical_systems, null)

        val btnUp = customView.findViewById<Button>(R.id.btn_windlass_up)
        val btnDown = customView.findViewById<Button>(R.id.btn_windlass_down)
        val btnChecklists = customView.findViewById<Button>(R.id.btn_open_checklists)
        val btnLighting = customView.findViewById<Button>(R.id.btn_lighting_control)
        val btnPumps = customView.findViewById<Button>(R.id.btn_pumps_status)
        
        setupWindlassButton(btnUp, "electrical.switches.windlass.up")
        setupWindlassButton(btnDown, "electrical.switches.windlass.down")
        
        btnChecklists.setOnClickListener {
            net.osmand.plus.plugins.nautical.ui.checklist.NauticalChecklistFragment.show(parentFragmentManager)
            dismiss()
        }

        btnLighting.setOnClickListener {
            NauticalElectricalDashboardBottomSheet.show(parentFragmentManager)
            dismiss()
        }

        btnPumps.setOnClickListener {
            NauticalElectricalDashboardBottomSheet.show(parentFragmentManager)
            dismiss()
        }

        items.add(BaseBottomSheetItem.Builder().setCustomView(customView).create())
    }

    private fun setupWindlassButton(button: Button, path: String) {
        button.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    NauticalPlugin.engine?.setSwitch(path, true)
                    v.isPressed = true
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    NauticalPlugin.engine?.setSwitch(path, false)
                    v.isPressed = false
                    true
                }
                else -> false
            }
        }
    }
}
