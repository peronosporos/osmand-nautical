# Implementation Plan - Finalizing Build Fixes for Nautical Features

The project currently has several compilation issues in `OsmandSettings.java` and possibly missing preferences that were identified in recent build attempts. Although some fixes were previously applied, the build still fails with 13 errors (according to the user-provided log) and `analyze_file` identifies additional generic type issues.

## User Review Required

> [!IMPORTANT]
> I am adding back `NAUTICAL_DR_SPEED` which appears to have been removed during previous refactoring, as it is referenced in the build failure logs.
> I will also address generic type errors in `OsmandSettings.java` that were detected by static analysis, even though they were not explicitly in the truncated build log.

## Proposed Changes

### [OsmAnd]

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Add back `NAUTICAL_DR_SPEED` with correct Java method references (`Float::doubleValue`, `Double::floatValue`).
- Fix generic type bounds for `METRIC_SYSTEM`, `ALTITUDE_METRIC`, `ANGULAR_UNITS`, and `SPEED_SYSTEM` by removing redundant/problematic explicit type arguments and casts.
- Fix `CURRENT_GRADIENT_PALETTE` initialization by ensuring correct string type handling for `PaletteConstants.DEFAULT_NAME`.

#### [MODIFY] [PluginsHelper.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/PluginsHelper.java)
- Ensure `SailingIntegrationPlugin` is correctly initialized and any trace of the non-existent `ManeuverPlugin` is removed. (This appears already done, but I will double-verify).

## Verification Plan

### Automated Tests
- Run `analyze_file` on `OsmandSettings.java` and `PluginsHelper.java` to ensure no errors remain.
- Run `analyze_file` on `CommonPreference.java` to verify the `map` method is still valid.

### Manual Verification
- Review the modified lines to ensure they follow Java syntax (avoiding Kotlin-isms like `toDouble()`).
