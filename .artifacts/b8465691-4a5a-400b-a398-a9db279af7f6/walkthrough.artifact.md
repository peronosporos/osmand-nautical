# Walkthrough - Phase 7: Server Intelligence & Steering Patterns

This batch implements professional Search & Rescue (SAR) steering patterns, vessel shunting logic for multihulls, and a steering actuator health monitor with high-priority alarms.

## Key Changes

### 1. SAR & Steering Pattern Engine
- **Expanding Square Search**: A spiral search pattern with leg lengths increasing every 90° turn.
- **Sector Search (Williamson Turn)**: A 3-sector sweep with 120° turns, ideal for covering a large area around a center point.
- **Archimedian Spiral**: A smooth expanding circular spiral for high-density area coverage.
- **Execution**: Patterns generate dynamic waypoint routes that are dispatched to the `AutopilotController` for closed-loop steering.

### 2. Proa & Multihull Shunting
- **Transformation Logic**: New `MultihullShuntManager` that handles bow/stern role swaps.
- **Telemetry Flip**: When shunted, the vessel's Heading and COG are atomically flipped by 180°, and relative wind angles (AWA/TWA) are inverted.
- **Transparency**: The transformation happens at the engine level, so all UI widgets reflect the correct "forward" orientation automatically.

### 3. Actuator Health & Duty-Cycle Monitor
- **Real-time Monitoring**: Parses `steering.autopilot.actions.dutyCycle` and `steering.actuator.current` from Signal K.
- **Overload Alarm**: Implements a moving-average monitor (30s window). If duty cycle exceeds 85%, a high-priority "Autopilot Actuator Overload!" voice alert is triggered.
- **Visual Feedback**: A new `ActuatorLoadWidget` provides a real-time progress bar and pulses red during overload states.

## Verification Results

### Automated Pattern Generation
- **Expanding Square**: Verified waypoint generation starting at 0.25NM spacing with 4 iterations.
- **Sector Search**: Verified 9-leg generation with 0.5NM radius.
- **Spiral**: Verified Archimedian geometry with 1.0NM max radius.

### Shunting Transformation
- **Heading**: Raw 045° -> Shunted 225°.
- **AWA**: Raw 30° -> Shunted -150°.
- **State Propagation**: Verified `MarineState.isShunted` toggles via the Pilot widget.

### Actuator Load Handling
- **Simulated Load**: Signal K delta `0.9` duty cycle for >30s triggers `ACTUATOR_OVERLOAD` in `NauticalAudioArbiter`.
- **UI State**: `ActuatorLoadWidget` displays `90%` in red during simulation.
