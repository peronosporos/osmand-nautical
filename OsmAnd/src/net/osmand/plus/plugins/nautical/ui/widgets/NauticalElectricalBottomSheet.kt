package net.osmand.plus.plugins.nautical.ui.widgets

import androidx.fragment.app.FragmentManager

class NauticalElectricalBottomSheet : NauticalElectricalDashboardBottomSheet() {

    companion object {
        const val TAG = "NauticalElectricalBottomSheet"

        fun show(fragmentManager: FragmentManager) {
            if (fragmentManager.findFragmentByTag(TAG) == null &&
                fragmentManager.findFragmentByTag(NauticalElectricalDashboardBottomSheet.TAG) == null) {
                NauticalElectricalBottomSheet().show(fragmentManager, TAG)
            }
        }
    }
}
