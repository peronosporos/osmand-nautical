package net.osmand.plus.settings.enums;

import androidx.annotation.StringRes;
import net.osmand.plus.R;

public enum VesselType implements EnumWithTitleId {
	CONVENTIONAL(R.string.nautical_vessel_conventional),
	PROA(R.string.nautical_vessel_proa);

	private final int titleId;

	VesselType(@StringRes int titleId) {
		this.titleId = titleId;
	}

	@Override
	public int getTitleId() {
		return titleId;
	}
}
