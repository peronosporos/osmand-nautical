package net.osmand.plus.plugins.nautical.ui

import android.graphics.Color
import android.os.Bundle
import android.view.*
import android.widget.Button
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import net.osmand.plus.R
import androidx.core.graphics.drawable.toDrawable

class NauticalConfirmDialog : DialogFragment() {

    private var title: String? = null
    private var message: String? = null
    private var confirmLabel: String? = null
    private var onConfirm: (() -> Unit)? = null
    private var onCancel: (() -> Unit)? = null

    companion object {
        fun show(
            parent: androidx.fragment.app.FragmentManager,
            title: String,
            message: String,
            confirmLabel: String? = null,
            onConfirm: () -> Unit,
            onCancel: (() -> Unit)? = null
        ) {
            val dialog = NauticalConfirmDialog()
            dialog.title = title
            dialog.message = message
            dialog.confirmLabel = confirmLabel
            dialog.onConfirm = onConfirm
            dialog.onCancel = onCancel
            dialog.show(parent, "NauticalConfirmDialog")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.OsmandLightTheme_NoActionbar)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val themedCtx = androidx.appcompat.view.ContextThemeWrapper(requireContext(), R.style.OsmandLightTheme_NoActionbar)
        val view = LayoutInflater.from(themedCtx).inflate(R.layout.nautical_confirm_popup, container, false)
        
        view.findViewById<TextView>(R.id.confirm_title).text = title
        view.findViewById<TextView>(R.id.confirm_message).text = message
        
        val slider = view.findViewById<SlideToConfirmView>(R.id.confirm_slider)
        confirmLabel?.let { slider.label = it }
        slider.onConfirm = {
            onConfirm?.invoke()
            dismiss()
        }
        
        view.findViewById<Button>(R.id.btn_cancel).setOnClickListener {
            onCancel?.invoke()
            dismiss()
        }

        // Trap focus and prevent background interaction
        view.isFocusableInTouchMode = true
        view.requestFocus()
        view.setOnKeyListener { _, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                onCancel?.invoke()
                dismiss()
                true
            } else {
                // Consume all other keys to prevent them from reaching MapActivity
                true
            }
        }

        return view
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.apply {
            setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
            setGravity(Gravity.CENTER)
            
            // Enforce strict focus trapping
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            clearFlags(WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
            setDimAmount(0.6f)
        }
    }
}
