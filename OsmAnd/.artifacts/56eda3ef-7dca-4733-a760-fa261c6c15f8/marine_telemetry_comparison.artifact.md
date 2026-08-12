# Comparison: Marine Telemetry Widgets

This report compares the implementation of marine telemetry widgets in the current codebase versus the state at commit `3b6ba78`.

## Summary of Changes

The project has evolved from a basic set of nautical widgets to a sophisticated telemetry ecosystem with intelligent preset management, reactive data handling, and hardware-specific monitoring.

| Feature | Commit `3b6ba78` | Current State |
| :--- | :--- | :--- |
| **Widget Architecture** | Simple polling-based updates. | Reactive **Kotlin Flow** based architecture. |
| **Data Integrity** | No visual indication of stale data. | **Staleness Detection**: Widgets dim and icon colors change to warning states when data is lost. |
| **Telemetry Density** | Single-item widgets only. | **Master Telemetry Widget** & **3x3 Grid Bottom Sheet** for high-density monitoring. |
| **Context Awareness** | Manual configuration required. | **Automatic Preset Switching**: Grid items change based on workflow (Tactical, Docking, Anchored). |
| **Performance Logic** | Basic m/s to knots conversion. | **Polar-Integrated Target VMG**: Calculates optimal VMG using polar diagrams. |
| **Hardware Monitoring** | None. | **Actuator Load** and **VHF Status** widgets for real-time hardware telemetry. |
| **Electrical Control** | Basic toggle switches. | **Advanced Dashboard** with dimmer/slider support and metadata. |

---

## Detailed Component Analysis

### 1. New Widgets and UI Components

- **[NauticalMasterTelemetryWidget](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalMasterTelemetryWidget.kt)**: Introduced as a central hub. It supports automatic layout switching based on the `SailingWorkflowState` (Tactical, Close Quarters, or Anchored).
- **[NauticalTelemetryGridBottomSheet](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalTelemetryGridBottomSheet.kt)**: A new high-density UI providing a grid of 9 telemetry items. It features advanced graphical views like:
    - **Sparklines**: Real-time history graphs for depth and speed.
    - **Mini-Roses**: Compact wind direction indicators.
- **[ActuatorLoadWidget](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/ActuatorLoadWidget.kt)**: Monitors autopilot/steering hardware health (duty cycle and current).
- **[NauticalVhfWidget](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalVhfWidget.kt)**: Real-time status for VHF radio, including replaying and live-streaming indicators.

### 2. Intelligent Feature Enhancements

#### Polar Diagram Integration
The `TargetVmgWidget` was significantly upgraded. Instead of simple speed conversion, it now attempts to calculate the **Target VMG** by intersecting the current wind state with the vessel's **Polar Diagram** via the `TacticalProcessor`.

#### Data Staleness Indicators
In [MarineTextWidget.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/MarineTextWidget.kt), a new staleness detection logic was added. If the SignalK stream for a specific path stops updating, the widget icon turns to `R.color.icon_color_warning` and the content alpha is reduced, providing immediate visual feedback to the sailor.

### 3. Electrical Control Evolution
The `NauticalSwitchesAdapter` was refactored to support complex electrical systems beyond simple ON/OFF states:
- **Dimmers**: Added `com.google.android.material.slider.Slider` support for paths representing light intensity or variable loads.
- **Metadata**: Support for `displayName` from SignalK metadata ensures users see human-readable names instead of raw SignalK paths.

### 4. Code & Performance Improvements
- **Pulse Animation**: A new `pulseFlow` in the SignalK engine drives visual heartbeat animations in widgets like VHF and MarineText, confirming active data reception.
- **Reactive Streams**: Widgets now subscribe to specific Flow streams in the `SailingDependencyContainer` rather than relying on global polling, reducing UI thread overhead.
- **Moon Phase Support**: The `SunriseSunsetWidget` was extended to support Nautical Moon Phase telemetry.

---

> [!NOTE]
> The current implementation heavily leverages the `SailingWorkflowEngine` to automate UI transitions, a capability that was entirely absent at commit `3b6ba78`.
