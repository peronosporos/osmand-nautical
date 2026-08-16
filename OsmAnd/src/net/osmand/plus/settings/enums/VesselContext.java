package net.osmand.plus.settings.enums;

import androidx.annotation.StringRes;
import net.osmand.plus.R;

public enum VesselContext implements EnumWithTitleId {
    SAILING(R.string.vessel_context_sailing),
    MOTORING(R.string.vessel_context_motoring),
    ANCHORED(R.string.vessel_context_anchored),
    MOORED(R.string.vessel_context_moored),
    DOCKING(R.string.vessel_context_docking),
    EMERGENCY_HEAVE_TO(R.string.vessel_context_emergency);

    private final int titleId;

    VesselContext(@StringRes int titleId) {
        this.titleId = titleId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }
}
