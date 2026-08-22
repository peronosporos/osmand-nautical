package net.osmand.plus.plugins.nautical.ui

import net.osmand.shared.aistracker.AisObject

class NauticalAisTargetBottomSheet : AisTargetBottomSheet() {

    companion object {
        fun newInstance(aisObject: AisObject): NauticalAisTargetBottomSheet {
            val fragment = NauticalAisTargetBottomSheet()
            val field = AisTargetBottomSheet::class.java.getDeclaredField("aisObject")
            field.isAccessible = true
            field.set(fragment, aisObject)
            return fragment
        }
    }
}
