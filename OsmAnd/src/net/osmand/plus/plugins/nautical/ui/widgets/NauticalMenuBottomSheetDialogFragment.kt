package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.StringRes
import net.osmand.plus.base.MenuBottomSheetDialogFragment
import net.osmand.plus.base.bottomsheetmenu.simpleitems.TitleItem
import net.osmand.plus.plugins.nautical.ui.NauticalNightWindowHelper

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

    override fun onStart() {
        super.onStart()
        // Window-level night treatment (red filter + dimmed brightness), applied
        // to the dialog decor view so the whole sheet, including its window
        // background, follows night vision mode. Previously only the content
        // view was filtered and screen brightness jumped back to full.
        NauticalNightWindowHelper.applyToDialog(dialog)
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
