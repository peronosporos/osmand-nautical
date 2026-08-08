package net.osmand.plus.settings.enums;

import net.osmand.plus.R;

public enum BatterySystemVoltage implements EnumWithTitleId {
    VOLTS_12(12, R.string.nautical_battery_12v),
    VOLTS_24(24, R.string.nautical_battery_24v),
    VOLTS_48(48, R.string.nautical_battery_48v);

    private final int voltage;
    private final int titleId;

    BatterySystemVoltage(int voltage, int titleId) {
        this.voltage = voltage;
        this.titleId = titleId;
    }

    public int getVoltage() {
        return voltage;
    }

    @Override
    public int getTitleId() {
        return titleId;
    }
}
