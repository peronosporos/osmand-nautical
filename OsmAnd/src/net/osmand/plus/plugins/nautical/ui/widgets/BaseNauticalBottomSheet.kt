package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.View
import net.osmand.plus.base.BaseMaterialBottomSheetDialogFragment
import net.osmand.plus.plugins.nautical.NauticalPlugin

/**
 * Base bottom sheet for Nautical plugin components, handling theme filters and common setup.
 */
open class BaseNauticalBottomSheet : BaseMaterialBottomSheetDialogFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        // Red filter is applied to the window decorView in BaseMaterialBottomSheetDialogFragment
    }
}
