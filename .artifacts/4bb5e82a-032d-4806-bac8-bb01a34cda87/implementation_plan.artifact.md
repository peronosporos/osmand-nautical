# Implementation Plan - Final Nautical Refinements & Safety

This plan addresses minor unit inconsistencies in AIS encoding, adds connection status voice alerts, and ensures the PID preview chart remains legible in all modes.

## User Review Required

> [!NOTE]
> I am adding voice announcements for connection status: "Signal K connected" and "Signal K lost".

## Proposed Changes

### 1. AIS Unit Corrections

#### [MODIFY] [AisEncoder.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AisEncoder.kt)
- **Fix SOG Conversion**: Convert `speedOverGround` from m/s to knots before applying the AIS scale (knots * 10).
- **Signed Integer Handling**: Ensure `BitBuffer.appendSigned` correctly handles negative values for high-precision longitude/latitude encoding.

### 2. Connection Awareness (Voice & Safety)

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Voice Alerts**:
    - Announce "Signal K connected" when the WebSocket successfully opens.
    - Announce "Signal K lost" when the connection times out or fails (with debouncing to avoid repeating alerts during rapid retries).
- **Cleanup**: Ensure `retryHandler` callbacks are cleared in more lifecycle paths.

### 3. UI Legibility & UX

#### [MODIFY] [NauticalAdvancedSettingsBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalAdvancedSettingsBottomSheet.kt)
- **Chart Colors**: Use theme-aware colors for the PID preview chart. In night mode, use high-contrast variants of Cyan and Red.
- **Labeling**: Clarify that the "Safety Lock" prevents accidental changes to autopilot tuning parameters.

#### [MODIFY] [res/values/strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing strings for connection status voice alerts.
- Ensure all newly added strings have clear, nautical-standard terminology.

### 4. Engine Optimization

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- **Throttling**: Add a lightweight throttle to the position update logic to prevent excessive calculation during high-frequency (10Hz+) NMEA/Signal K updates.

## Verification Plan

### Manual Verification
- **AIS Encoding**: Monitor the AIS UDP stream and verify that SOG is reported correctly in knots (e.g., using an AIS decoder or by checking the value on another device).
- **Voice Test**: Disable Wi-Fi/Data to simulate connection loss and verify the "Signal K lost" announcement. Re-enable to hear "Signal K connected".
- **Chart Test**: Switch to Night Mode and verify that the PID preview lines are clearly visible against the dark background.
- **Throttling**: Verify that UI responsiveness remains high even when the boat is sending updates at the maximum possible rate.
