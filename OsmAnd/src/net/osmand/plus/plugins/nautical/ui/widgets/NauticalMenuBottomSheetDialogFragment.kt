package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import net.osmand.plus.base.MenuBottomSheetDialogFragment
import net.osmand.plus.base.bottomsheetmenu.simpleitems.TitleItem
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * A standard OsmAnd bottom sheet for Nautical plugin with proper integration and theme support.
 */
abstract class NauticalMenuBottomSheetDialogFragment : MenuBottomSheetDialogFragment() {

    override fun getDismissButtonTextId(): Int = net.osmand.plus.R.string.shared_string_cancel
    override fun getRightBottomButtonTextId(): Int = 0
    override fun getThirdBottomButtonTextId(): Int = 0
    override fun hideButtonsContainer(): Boolean = true

    override fun createMenuItems(savedInstanceState: Bundle?) {
        // Subclasses implement this
    }

    override fun onCreateView(inflater: LayoutInflater, parent: ViewGroup?, savedInstanceState: Bundle?): View? {
        val view = super.onCreateView(inflater, parent, savedInstanceState)
        
        // Apply Nautical red filter if night vision is on
        if (NauticalPlugin.isNightVision(app)) {
            val paint = android.graphics.Paint().apply {
                colorFilter = NauticalPlugin.NIGHT_VISION_FILTER
            }
            view?.setLayerType(View.LAYER_TYPE_HARDWARE, paint)
        }
        
        return view
    }

    protected fun addTitleItem(@StringRes titleId: Int) {
        if (titleId != 0 && titleId != -1) {
            items.add(TitleItem(getString(titleId)))
        }
    }

    protected fun addTitleItem(title: String) {
        items.add(TitleItem(title))
    }
}
