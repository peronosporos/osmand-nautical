package net.osmand.plus.plugins.nautical.hazard.ui

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import java.text.SimpleDateFormat
import java.util.*

class NavtexDetailsBottomSheet : BottomSheetDialogFragment() {

    private var message: NavtexMessage? = null

    companion object {
        fun newInstance(message: NavtexMessage): NavtexDetailsBottomSheet {
            val fragment = NavtexDetailsBottomSheet()
            fragment.message = message
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_navtex_details, container, false)
        
        // Trap focus for D-pad
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                dismiss()
                true
            } else {
                // Consume keys to prevent map pan/zoom
                true
            }
        }
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val msg = message ?: return
        
        view.findViewById<TextView>(R.id.navtex_detail_id).text = getString(R.string.navtex_detail_id_label, msg.id)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        view.findViewById<TextView>(R.id.navtex_detail_time).text = sdf.format(Date(msg.timestamp))
        
        view.findViewById<TextView>(R.id.navtex_detail_body).text = msg.body
    }
}
