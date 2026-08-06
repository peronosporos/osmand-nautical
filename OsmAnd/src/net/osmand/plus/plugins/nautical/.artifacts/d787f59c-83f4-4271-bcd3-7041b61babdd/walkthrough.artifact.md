# Nautical UI/UX Overhaul Walkthrough

I have completed the UI/UX overhaul of the Nautical Plugin, focusing on functional integration and boat-profile-specific automation.

## Key Improvements

### 1. Everything in its Right Place
- **Plugin Settings**: Now acts as a "Master Switchboard" with clear categories (Connection, Vessel, Safety, Performance).
- **Module Gatekeeping**: You can now enable/disable high-level modules (AIS, Tides, GRIB, etc.). Disabling a module hides its associated layers, widgets, and menu items across the entire app.
- **Unified Visibility**: 11 visibility toggles were removed from Settings and moved exclusively to "Configure Map".

### 2. Enhanced "Configure Map" Integration
- **Shortcut Gears**: Major nautical layers in the "Configure Map" menu now have gear icons. Tapping the gear jumps you directly to the respective manager (e.g., Tide Manager, ENC Manager).
- **Categorization**: Nautical items are grouped into "Marine Overlays" and "Vessel Indicators".

### 3. Helm & Operations UX
- **Tabbed Autopilot Tuning**: The "Advanced Settings" in the Pilot sheet is no longer a long list. It's now organized into tabs: [Tuning], [Limits], [Vessel], [Env].
- **Visual Anchor Watch**:
    - Added a "Preview & Adjust on Map" button to the Anchor dialog.
    - When active, a yellow "Swing Area" circle appears on the map.
    - You can now long-press on the map to adjust the anchor drop point manually.

### 4. Functional Data Managers
- **ENC Manager**: Now shows indexed chart count and total coverage area. Includes a shortcut to S-63 Permit management.
- **Tide Manager**: Added a Signal K station browser. Selecting a station shows its location on the map.
- **Polar Library**: Added library management to the Polar Editor. Switch between multiple polar profiles stored in Signal K.

### 5. Smart Automation
- **Master Telemetry Widget**: Now supports **Contextual Presets**. It automatically switches displayed fields based on your boat's activity (Sailing, Motoring, or Docking).

## Verification Results

- **Module Pruning**: Verified that disabling "VHF Integration" removes the VHF widget and poi layer.
- **Shortcut Logic**: Verified that tapping the gear next to "Tides" opens the Tide Manager.
- **Anchor Logic**: Validated manual anchor drop adjustment via map context menu.
- **Persistence**: All new settings (Modules, Keel Offset, Wind Alignment) persist correctly in `OsmandSettings`.

> [!TIP]
> Use the **Master Telemetry Widget** in your map layout; it will now evolve with your workflow, showing you exactly what you need when docking or on a long passage.
