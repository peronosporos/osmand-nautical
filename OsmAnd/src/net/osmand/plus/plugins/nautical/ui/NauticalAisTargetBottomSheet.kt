package net.osmand.plus.plugins.nautical.ui

import net.osmand.shared.aistracker.AisObject

object NauticalAisTargetBottomSheet {
    fun newInstance(aisObject: AisObject): AisTargetBottomSheet {
        return AisTargetBottomSheet.newInstance(aisObject)
    }
}
