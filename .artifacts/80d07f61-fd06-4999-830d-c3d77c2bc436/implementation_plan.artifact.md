# UI & Domain Optimization for Nautical Pilot Bottom Sheet

This plan addresses several UI and domain alignment issues in the Nautical Pilot Bottom Sheet, including authentication warning logic, telemetry alignment, and reorganization of tuning/switching controls.

## User Review Required

> [!IMPORTANT]
> The "Sea State" control and all PID tuning sliders (Rudder Gain, Counter Rudder, Auto Trim) will be moved from the primary pilot sheet to the **Advanced Settings** sheet. This is a deliberate safety design to prevent accidental adjustments during active steering.

> [!NOTE]
> The "Digital Switching" section will be removed from the Pilot sheet as it pertains to house electrical systems rather than helm flight controls.

## Proposed Changes

### Core Engine & Connection (Auth Logic)

#### [MODIFY] [MarineState.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/MarineState.kt)
- Add `UNAUTHORIZED` to `ConnectionStatus` enum.

#### [MODIFY] [SignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKConnection.kt)
- Update `connect` method signature to include `onAuthError: () -> Unit`.

#### [MODIFY] [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- Update `onFailure` in `WebSocketListener` to check for `response?.code() == 401` and invoke `onAuthError`.

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Update `startEngine` (indirectly via `NauticalPlugin`) to handle the new `onAuthError` callback.
- In `onAuthError`, update the `MarineState` with `ConnectionStatus.UNAUTHORIZED`.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `startEngine` to pass `onAuthError` to the connection.

---

### UI Components & Layouts

#### [MODIFY] [nautical_pilot_bottom_sheet.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/nautical_pilot_bottom_sheet.xml)
- Add `android:gravity="center"` to all telemetry value TextViews (`txt_value_1_1` through `txt_value_2_3`).
- [DELETE] Remove `tuning_title` and `tuning_container`.
- [DELETE] Remove `switching_title` and `switches_recycler`.

#### [MODIFY] [bottom_sheet_nautical_advanced.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/bottom_sheet_nautical_advanced.xml)
- [NEW] Add a new section for "Sea State" response and "Auto Sea State" toggle under the "Vessel Dynamics" section.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Update `authWarning` visibility logic: show only if `state.connectionStatus == ConnectionStatus.UNAUTHORIZED`.
- Remove pre-emptive `authWarning` visibility check from `onViewCreated`.
- Remove all code related to `switchesRecycler`, `switchesAdapter`, and tuning sliders (`rudderGainSlider`, `seaStateSlider`, etc.).

#### [MODIFY] [NauticalAdvancedSettingsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalAdvancedSettingsBottomSheet.kt)
- [NEW] Add bindings and logic for the "Sea State" slider and "Auto Sea State" switch.
- Ensure these new controls respect the `safetyLock` state.

## Verification Plan

### Automated Tests
- N/A (UI layout and internal state flow)

### Manual Verification
- Deploy to device/emulator.
- **Auth Warning:** Verify that the red warning DOES NOT appear when connecting to a secure server without a token until an actual 401 is received (if possible to simulate) or at least verify it's not pre-emptive.
- **Alignment:** Check the Pilot sheet and verify telemetry values are centered under their icons/labels.
- **Reorganization:**
    - Verify tuning sliders are GONE from the main sheet.
    - Verify the "Digital Switching" section is GONE.
    - Open Advanced Settings (gear icon) and verify the new Sea State controls are present and functional.
    - Verify that the safety lock in Advanced Settings correctly disables the new Sea State controls.
