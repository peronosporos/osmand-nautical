# Implementation Plan - Compass Wizard Robustness & Architecture Refactor

This plan addresses all 15 identified issues in the Nautical Compass Wizard by introducing a proper `ViewModel` architecture, unifying backend command dispatching, and fixing UI/UX flaws.

## User Review Required

> [!IMPORTANT]
> The Wizard will be migrated from using Signal K WebSocket Deltas to direct REST API calls via `AutopilotController`. This ensures better reliability and immediate error feedback (e.g., authentication failures or server-side rejection).

> [!WARNING]
> This refactor involves changes to core Signal K state parsing. While designed to be backward compatible, it will enforce stricter type handling for calibration states.

## Proposed Changes

### [Nautical Plugin Core]

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Improve `updatePypilotCalibration` to handle various truthy values ("true", "on", "1") for `isCalibrating`.
- Ensure `compassCalibrationProgress` is correctly updated from server messages.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Fix `stopPypilotCalibration` payload (ensure it sends `false` or appropriate value if `true` was incorrect).
- Add specific error reporting for calibration commands.

---

### [Compass Wizard Refactor]

#### [NEW] [NauticalCompassWizardViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/NauticalCompassWizardViewModel.kt)
- **State Management**: Hold `currentStep`, `progress`, `isCalibrating`, and `error` in `StateFlow`.
- **Command Dispatching**: Use `AutopilotController` to start/stop calibration.
- **Closed-Loop Logic**: Automatically transition from Step 2 to Step 3 when `progress` reaches 100% and `isCalibrating` becomes `false`.
- **Timeout Handling**: Robust 10-second timeout for Step 1 (connection) with proper cleanup.

#### [MODIFY] [NauticalCompassWizardDialog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWizardDialog.kt)
- **Lifecycle Safety**: Use `repeatOnLifecycle` for flow collection.
- **UI Consistency**: Fix progress scaling (* 100).
- **Rotation Support**: Connect to `NauticalCompassWizardViewModel` to persist state across configuration changes.
- **Bug Fixes**: Ensure `STOP` command is sent on cancellation.

#### [MODIFY] [dialog_nautical_compass_wizard.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/dialog_nautical_compass_wizard.xml)
- **Layout Fixes**: Increase spacing and fix overlap between the compass icon and the circular progress indicator.
- **Localization**: Use `@string/nautical_compass_wizard_retry` instead of hardcoded text.

---

### [Resources]

#### [MODIFY] [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing strings for calibration errors and retry button.
- Fix any inconsistencies in existing nautical wizard strings.

## Verification Plan

### Automated Tests
- I will verify the `SignalKEngine` parsing logic for calibration states via inspection and existing unit tests if available.

### Manual Verification
- Deploy to device/emulator.
- Open Compass Wizard.
- Verify Step 1 transition with a simulated "START" success.
- Verify Step 2 progress bar scaling.
- Rotate the screen during calibration and ensure progress and step are preserved.
- Cancel calibration and verify (via logs) that the `STOP` command is sent.
- Verify that the "Finish" button is only enabled when the server reports completion.
