package net.osmand.plus.plugins.nautical.ui.widgets

import android.os.Bundle
import android.view.View

/**
 * Base bottom sheet for Nautical plugin components, handling standard OsmAnd integration.
 */
open class BaseNauticalBottomSheet : NauticalMenuBottomSheetDialogFragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
    }
}
