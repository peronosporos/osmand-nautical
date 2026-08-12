# Walkthrough - Compass Wizard Robustness & Architecture Refactor

I have completed the refactor of the Nautical Compass Wizard. The feature is now more robust, follows modern Android architecture patterns, and handles various edge cases like network timeouts and screen rotations.

## Key Changes

### 1. Architectural Refactor (`ViewModel`)
- Introduced `NauticalCompassWizardViewModel` to manage calibration state independently of the Dialog's lifecycle.
- **Persistence**: The wizard now survives screen rotations and configuration changes without losing calibration progress.
- **Closed-Loop Logic**: The wizard now automatically detects when the server has finished calibration (100% progress + `isCalibrating = false`) and transitions to the completion screen.

### 2. Backend & Communication Fixes
- **Unified Messaging**: Switched from unreliable WebSocket deltas to direct REST API calls via `AutopilotController` for starting/stopping calibration. This provides immediate HTTP feedback.
- **Robust Parsing**: Updated `SignalKEngine` to correctly handle various Signal K truthy values (`"true"`, `"on"`, `"1"`) for the `isCalibrating` state.
- **Calibration Command Fix**: Corrected the `stop` command payload to ensure the server receives a clear termination signal (`{ "value": false }`).

### 3. UI/UX Enhancements
- **Progress Scaling**: Fixed the progress bar scaling. It now correctly maps Signal K's `0.0-1.0` ratio to `0-100%`.
- **Layout Polishing**:
    - Fixed overlap between the compass icon and the circular progress indicator.
    - Added a "Retry" button for failed attempts.
    - Localized all previously hardcoded strings.
- **Closed-Loop Safety**: The "Finish" button is only enabled when the server confirms calibration is successful, preventing users from prematurely ending the process.
- **Timeout Protection**: Added a 12-second timeout for the initial connection phase to avoid the UI hanging indefinitely on network failure.

## Verification Results

### Logic & State
- Verified that `isCalibrating` state parsing in `SignalKEngine` is now case-insensitive and handles multiple formats.
- Verified that `NauticalCompassWizardViewModel` correctly calculates progress by multiplying the server ratio by 100.

### UI Layout
- The `dialog_nautical_compass_wizard.xml` now uses properly spaced components:
    - Circular Indicator: 160dp
    - Compass Icon: 100dp (centered within indicator)

### Strings & Localization
- Added `nautical_compass_wizard_retry`, `nautical_compass_calibration_timeout`, and `nautical_error` to `strings.xml`.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWizardDialog.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/NauticalCompassWizardViewModel.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
