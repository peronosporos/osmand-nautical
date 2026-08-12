# UI Complexity & Clutter Analysis: Current vs. Commit 3b6ba78

The current marine telemetry UI has transitioned from a minimalist display to a high-density, interactive telemetry system. Below is an analysis of where the "visual clutter" and "overcomplication" originate.

## 1. HUD Widget Footprint (Density)

In commit `3b6ba78`, widgets were almost exclusively text-based. The current implementation introduces several complex HUD elements:

- **[NauticalPilotWidget](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalPilotWidget.kt)**:
    - **Old Way**: Simple text indicating autopilot mode.
    - **Current Way**: Adds a rudder indicator bar, a status icon, and **5 interactive nudge buttons** (-10, -1, +1, +10, Predictive) directly on the map HUD when engaged. This significantly increases map occlusion.
- **[ActuatorLoadWidget](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/layout/map_hud_actuator_widget.xml)**: Introduces a vertical layout with a progress bar and multiple rows of text (Duty Cycle + Current), which breaks the horizontal rhythm of the standard widget panel.

## 2. Graphical "Layering" in Telemetry

The new telemetry grid introduces multiple visual layers within a single small item:

- **Sparklines**: Real-time history graphs are rendered *behind* the text values in the grid.
- **Mini-Roses**: Directional icons (like wind) are replaced with active compass-like roses.
- **Stale Badges**: Data integrity issues are flagged with a floating "STALE" badge on top of the widget content.

While these provide more data, they deviate from the "clean way" where an icon and a number were sufficient.

## 3. Aggressive Status Signaling

The [MarineTextWidget](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt) now uses highly intrusive visual states for data integrity:

- **ALARM State**:
    - Flashing red backgrounds.
    - **Strike-through text** on the telemetry value.
    - Bold styling.
    - Replacing the value with the word "ALARM".
- **STALE State**: Yellowing of text and reduced opacity.

In the old version, data was either there or not. The current "loud" signaling of connectivity issues adds significant visual stress during sensor drops.

## 4. Interactive Overlap

The addition of the **Telemetry Grid Bottom Sheet** creates redundancy. The user now has:
1. Standard Map HUD widgets.
2. A 3x3 Grid in a bottom sheet.
3. Master Telemetry Widget to trigger the grid.

This multi-entry approach can feel overcomplicated compared to having a single, well-defined place for marine data.

---

> [!TIP]
> To restore the "clean way", we could consider:
> 1.  Moving the interactive Pilot Nudge buttons into the Bottom Sheet rather than the map HUD.
> 2.  Simplifying the Alarm/Stale states to use only icon color changes instead of strike-through/background flashing.
> 3.  Standardizing the Actuator widget to match the horizontal text-info layout.
