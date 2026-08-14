# Walkthrough - Restore Missing Nautical Widgets

I have restored the missing Nautical plugin widgets and ensured they are properly registered and functional. This fix is strictly limited to the Nautical plugin.

## Changes

### Nautical Plugin

#### [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- **Automatic Registration**: Updated `createWidgets` to iterate through all nautical widgets defined in `WidgetType` and register them if allowed. This ensures they appear active in the "Configure map" screen.
- **Widget Creation**: Expanded `createMapWidgetForParams` to handle all nautical widget types, including the restored `NAUTICAL_PILOT` and `NAUTICAL_COMPASS`.
- **Simplification**: Simplified `isWidgetAllowed` to rely on the central logic in `WidgetType.isAllowed`.

#### [NEW] [NauticalPilotWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotWidget.kt)
- Re-implemented the Autopilot widget. It displays the current mode (Standby, Track, Wind) and the target course/angle.
- Tapping the widget opens the `NauticalPilotBottomSheet`.

#### [NEW] [NauticalCompassWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalCompassWidget.kt)
- Re-implemented the Compass widget. It displays the magnetic heading and variation.
- Tapping the widget opens the `NauticalCompassWizardDialog`.

## Reversions

As requested, I have reverted all changes made to non-nautical plugins:
- **[REVERTED]** `ExternalSensorsPlugin.java`
- **[REVERTED]** `OsmandMonitoringPlugin.java`

## Verification Results

### Manual Verification Required
1.  **Nautical Category**: Go to "Configure map" -> "Add widget" -> "Nautical Telemetry/Systems". Verify all nautical widgets (Autopilot, Compass, SOG, Depth, etc.) are available and active.
2.  **Functionality**: Add "Nautical Pilot" and "Compass" to the map and verify tapping them opens the correct control sheets/wizards.
