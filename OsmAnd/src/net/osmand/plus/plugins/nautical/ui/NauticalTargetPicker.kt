package net.osmand.plus.plugins.nautical.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.hazard.engine.NavtexMessage
import net.osmand.plus.plugins.nautical.s57.S57Object
import net.osmand.shared.aistracker.AisObject

class NauticalTargetPicker : BottomSheetDialogFragment() {

    private var targets: List<Any> = emptyList()

    companion object {
        fun newInstance(targets: List<Any>): NauticalTargetPicker {
            val fragment = NauticalTargetPicker()
            fragment.targets = targets
            return fragment
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val context = requireContext()
        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dpToPx(context, 16f), dpToPx(context, 16f), 
                       dpToPx(context, 16f), dpToPx(context, 24f))
            layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        root.addView(TextView(context).apply {
            text = getString(R.string.nautical_target_picker_title)
            textSize = 20f
            setTypeface(null, android.graphics.Typeface.BOLD)
            setPadding(0, 0, 0, dpToPx(context, 16f))
        })

        targets.forEach { target ->
            root.addView(createTargetRow(target))
        }

        return root
    }

    private fun createTargetRow(target: Any): View {
        val context = requireContext()
        val textView = TextView(context).apply {
            val name = when (target) {
                is AisObject -> getString(R.string.nautical_ais_target, target.shipName ?: target.mmsi.toString())
                is NavtexMessage -> getString(R.string.nautical_navtex_target, target.id)
                is S57Object -> getString(R.string.nautical_s57_target, target.attributes["OBJNAM"] ?: target.acronym)
                else -> target.toString()
            }
            text = name
            textSize = 16f
            setPadding(0, dpToPx(context, 12f), 0, dpToPx(context, 12f))
            setBackgroundResource(net.osmand.plus.utils.AndroidUtils.resolveAttribute(context, android.R.attr.selectableItemBackground))
            isClickable = true
            setOnClickListener {
                dismiss()
                val mapActivity = activity as? net.osmand.plus.activities.MapActivity ?: return@setOnClickListener
                val arbitrator = NauticalTouchArbitrator(mapActivity)
                arbitrator.showTargetDetails(target)
            }
        }
        return textView
    }

    private fun dpToPx(context: android.content.Context, dp: Float): Int {
        return (dp * context.resources.displayMetrics.density).toInt()
    }
}
