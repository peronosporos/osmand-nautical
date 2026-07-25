# Fix errors and warnings in Nautical Plugin

This plan addresses several errors and warnings identified in the Nautical plugin files, including unresolved string references, type mismatches in history data handling, and various code style improvements.

## User Review Required

> [!IMPORTANT]
> - New strings will be added to `OsmAnd/res/values/strings.xml` to resolve unresolved references.
> - The watchdog logic in `SignalKEngine.kt` will be slightly modified to ensure `onConnectionRestored` is correctly triggered after a timeout.

## Proposed Changes

### [Component] Android Resources

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add `nautical_compass_calibration_started` and `nautical_advanced_settings_unlocked` strings.

---

### [Component] Nautical Plugin Logic

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Fix type mismatch in `drawWindShifts` when accessing history data (access `.first` of the Pair).
- Remove redundant `OsmandMapLayer` qualifier.
- Inline `tb` variable.
- Use `withIndex()` in angle sorting loop.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Update `startWatchdog` to not `break` on timeout, allowing it to detect and notify when connection is restored.
- Use `Duration` overloads for `delay()`.
- Fix unused variable/condition warnings for `previouslyDisconnected`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Add clarifying parentheses.
- Use property access syntax where appropriate.
- Fix various style warnings (trailing commas, line breaks).

#### [MODIFY] [NauticalSettingsFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalSettingsFragment.kt)
- Add clarifying parentheses in long boolean expressions.

---

### [Component] Nautical Widgets & UI

#### [MODIFY] [NauticalCompassWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWizardDialog.kt)
- The unresolved reference will be fixed by adding the string to `strings.xml`.

#### [MODIFY] [NauticalAdvancedSettingsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalAdvancedSettingsBottomSheet.kt)
- Fix unresolved string reference.
- Use `toColorInt()` for hex colors.
- Remove redundant initializer for `rudderAngle`.

#### [MODIFY] [NauticalGraphWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphWidget.kt)
- Add clarifying parentheses.

#### [MODIFY] [NauticalGraphView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphView.kt)
- Add clarifying parentheses and trailing commas.

#### [MODIFY] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Add clarifying parentheses.
- Use Kotlin `abs()` and `toDegrees()` where possible.
- Fix trailing comma and line break warnings.

#### [MODIFY] [NauticalDataBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalDataBottomSheet.kt)
- Add clarifying parentheses.

#### [MODIFY] [NauticalNightVisionWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalNightVisionWidget.kt)
- Fix trailing comma warning.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Fix trailing comma and line break warnings.

## Verification Plan

### Automated Tests
- Build the project to ensure all errors are resolved.
- Since I cannot run full Gradle builds, I will use `analyze_file` on the modified files to verify that the identified errors and warnings are gone.

### Manual Verification
- N/A (UI verification would require running the app, which I cannot do in this environment).
