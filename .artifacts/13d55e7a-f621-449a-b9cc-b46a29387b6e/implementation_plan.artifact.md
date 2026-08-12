# Implementation Plan - Sunlight Vision & Display Mode Fixes

This plan addresses the identified bugs and architectural gaps in the Nautical plugin's Sunlight and Night Vision functionality. The goal is to ensure absolute legibility in extreme lighting conditions and maintain a consistent "Glass Cockpit" experience.

## User Review Required

> [!IMPORTANT]
> **Brightness Control**: Sunlight mode will now force the device screen to maximum brightness (100%). This may impact battery life significantly if left on.
> **Theme Overrides**: Sunlight mode will now force the Map Theme to "Day" even if the global system setting is "Night," as dark themes lack the contrast required for direct sunlight.

## Proposed Changes

### Nautical Plugin Core Logic
#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Brightness Override**: Update `applyDisplayMode` to set `window.attributes.screenBrightness = 1.0f` when `SUNLIGHT` mode is active.
- **Theme Coupling**: Update `getMapTheme()` to return `DayNightMode.DAY` when in `SUNLIGHT` mode to prevent dark map rendering in bright light.
- **Signal K Automation**: Integrate `MarineState#sunlightMode` into the `marineStateListener` to allow the vessel's sensors to trigger the display mode automatically.
- **Red Filter Compensation**: Improve the logic that clears UI filters when switching from `DARK` to `SUNLIGHT`.

### Map UI & Consistency
#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- **Semantic Colors**: Replace hardcoded `Color.rgb(...)` calls in `onDraw` with `NauticalColorResolver.getColor(...)` to ensure the map matches the telemetry widgets.
- **Polarized Lens Mode**: Add a slight "High-Contrast Overlay" logic for sunlight mode to combat polarization blackout.

#### [MODIFY] [NauticalColorResolver.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/NauticalColorResolver.kt)
- **Priority Logic**: Ensure `SUNLIGHT` mode colors are used regardless of the global `isNightMode` status.

### UI Widgets
#### [MODIFY] [NauticalDisplayModeWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalDisplayModeWidget.kt)
- **Visual Feedback**: Update the widget icon/background to pulse or glow slightly when in Sunlight mode as a confirmation.

## Verification Plan

### Automated Tests
- Unit tests for `NauticalColorResolver` to verify correct color returns for all 3 modes.
- Logic check in `NauticalPluginTest` (if exists) for `getMapTheme()` state transitions.

### Manual Verification
1. **Direct Sunlight Emulation**: Set system theme to Night, then enable Sunlight mode in Nautical. Verify map switches to light theme and brightness jumps to max.
2. **Signal K Sync**: Use a test Signal K server to send `environment.sunlight.mode = "high"`. Verify the app switches mode automatically.
3. **Red Filter Continuity**: Switch from Night Vision to Sunlight and verify the red filter is fully cleared and replaced by high-contrast colors.
