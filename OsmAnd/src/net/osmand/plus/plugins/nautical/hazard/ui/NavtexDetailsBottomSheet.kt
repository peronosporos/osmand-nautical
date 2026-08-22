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
        const val TAG = "navtex_details"
        private const val KEY_MESSAGE = "key_message"

        fun newInstance(message: NavtexMessage): NavtexDetailsBottomSheet {
            val fragment = NavtexDetailsBottomSheet()
            val args = Bundle()
            args.putSerializable(KEY_MESSAGE, message)
            fragment.arguments = args
            return fragment
        }

        fun show(manager: androidx.fragment.app.FragmentManager, message: NavtexMessage) {
            if (manager.isStateSaved) return
            if (manager.findFragmentByTag(TAG) == null) {
                newInstance(message).show(manager, TAG)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = inflater.inflate(R.layout.bottom_sheet_navtex_details, container, false)
        
        message = if (android.os.Build.VERSION.SDK_INT >= 33) {
            arguments?.getSerializable(KEY_MESSAGE, NavtexMessage::class.java)
        } else {
            @Suppress("DEPRECATION")
            arguments?.getSerializable(KEY_MESSAGE) as? NavtexMessage
        }

        // Trap focus for D-pad navigation but allow system keys
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    if (event.action == KeyEvent.ACTION_UP) dismiss()
                    true
                }
                KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_DPAD_CENTER -> {
                    // Consume navigation keys to prevent map movement
                    true
                }
                else -> false
            }
        }
        
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        val msg = message ?: return
        
        view.findViewById<TextView>(R.id.navtex_detail_id)?.text = getString(R.string.navtex_detail_id_label, msg.id)
        
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        view.findViewById<TextView>(R.id.navtex_detail_time)?.text = sdf.format(Date(msg.timestamp))
        
        view.findViewById<TextView>(R.id.navtex_detail_body)?.text = msg.body
    }
}
