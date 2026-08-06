package net.osmand.plus.views.mapwidgets.widgets

import android.os.Bundle
import android.view.*
import android.widget.Button
import androidx.fragment.app.FragmentManager
import net.osmand.plus.R
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * Control hub for vessel systems: Windlass, Checklists, etc.
 */
class NauticalSystemsBottomSheet : BaseMaterialBottomSheetDialogFragment() {

    companion object {
        fun show(fragmentManager: FragmentManager) {
            NauticalSystemsBottomSheet().show(fragmentManager, "nautical_systems")
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_nautical_systems, container, false)
        
        val btnUp = view.findViewById<Button>(R.id.btn_windlass_up)
        val btnDown = view.findViewById<Button>(R.id.btn_windlass_down)
        val btnChecklists = view.findViewById<Button>(R.id.btn_open_checklists)
        
        setupWindlassButton(btnUp, "electrical.switches.windlass.up")
        setupWindlassButton(btnDown, "electrical.switches.windlass.down")
        
        btnChecklists.setOnClickListener {
            net.osmand.plus.plugins.nautical.ui.NauticalChecklistFragment.show(parentFragmentManager)
            dismiss()
        }
        
        return view
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
