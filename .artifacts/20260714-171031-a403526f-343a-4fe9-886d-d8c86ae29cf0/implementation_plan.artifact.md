# Professional Refinement of Nautical Widgets

This plan addresses suboptimal UX, redundant logic, and bad practices identified during the audit of the Nautical Widgets.

## User Review Required

- **Removal of Status Indication**: Following the prompt, all connection status indicators (tints, dots, etc.) will be removed. The widget will display data if available, or a standard empty/off state if not, aligning with core OsmAnd widgets (e.g., speed, altitude).
- **Units & Locales**: Hardcoded units like "m", "ft", "kn" will be replaced with localized resources or calculated from OsmAnd's unit system.

## Proposed Changes

### Component: Core Assets & Metadata

#### [strings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/values/strings.xml)
- Add missing localized units if not present (e.g., knots).
- Add specific abbreviations for autopilot modes (AP, AUTO, STBY, STOP) to ensure they are localizable and consistent.

#### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Clean up unused preferences or legacy logic related to old widget status if any remains.

---

### Component: Widget Logic & UI

#### [MarineTextWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.java)
- **Remove Status Logic**: Delete `updateColors` override and any icon tinting or connection-dependent alpha.
- **Native Unit Integration**: Use `mapActivity.getApp().getSettings().METRIC_SYSTEM` properly. Instead of hardcoded "m", use `MetricsConstants`.
- **String Constants**: Replace "OFF" and "---" with `R.string.n_a` or dedicated nautical off/empty strings.
- **UX Fix**: Ensure the widget doesn't flicker by optimizing `updateInfo`.

#### [NauticalPilotWidget.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.java)
- **Remove Status Logic**: Delete `updateColors` override. The pilot widget will look consistent with the theme at all times.
- **Professional Layout**: Refine `map_hud_pilot_widget.xml` to ensure the progress bar for emergency stop is "native" (using standard `ProgressBar` styling and colors).
- **Mode Abbreviations**: Use localized strings for STBY, AUTO, STOP.

#### [NauticalGraphView.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalGraphView.java)
- **Audit Drawing**: Fix the density-dependent line widths (remove `1f * density` warning).
- **Professional Aesthetics**: Ensure the "Pulsing Dot" isn't too distracting or "un-OsmAnd-like." Use a more subtle indicator for "Live" data.

---

### Component: Architecture & Best Practices

#### [ResizableWidgetState.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgetstates/ResizableWidgetState.java)
- Ensure the `registerWidgetSizePref` is clean and follows the patterns of other `MapWidget` state registrations.

## Verification Plan

### Automated Tests
- No specific new tests required, but will run the existing widget suite to ensure no regressions in sizing/layout logic.

### Manual Verification
- **Aesthetic Audit**: Verify widgets look identical to standard OsmAnd text widgets when in normal operation (no weird tints, no dots).
- **Transparency & Sizing**: Re-verify that they still respect global settings.
- **Unit Switching**: Change app units (Metric/Imperial/Nautical) and verify nautical widgets update their labels accordingly without hardcoded strings.
- **Interaction**: Long-press on Pilot widget and verify the progress bar looks professional and executes the stop command correctly.
- **Clean Code Check**: Ensure no `PorterDuff` or `Color.RED` remains in the widget classes.
