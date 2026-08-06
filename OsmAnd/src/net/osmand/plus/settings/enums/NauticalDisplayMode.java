package net.osmand.plus.settings.enums;

import androidx.annotation.StringRes;
import net.osmand.plus.R;

public enum NauticalDisplayMode implements EnumWithTitleId {
    NORMAL(R.string.nautical_display_mode_normal),
    SUNLIGHT(R.string.nautical_display_mode_sunlight),
    DARK(R.string.nautical_display_mode_dark);

    private final int titleId;

    NauticalDisplayMode(@StringRes int titleId) {
        this.titleId = titleId;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }
}
