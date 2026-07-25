# Nautical Plugin Fixes Task List

- `[x]` 1. Resources & Strings
    - `[x]` Add `nautical_steer_here` and `nautical_undo` to `strings.xml`.
- `[x]` 2. Connectivity & Stability
    - `[x]` Fix WebSocket leak logic in `NauticalPlugin.kt` (verified `startEngine` cleanup).
    - `[x]` Implement SafeUnmute in `NauticalLocationProvider`.
- `[x]` 3. Performance Optimizations
    - `[x]` Implement Path caching in `NauticalMapLayer`.
    - `[x]` Move JSON parsing to `Dispatchers.Default` in `SignalKEngine`.
    - `[x]` Consolidate history buffer storage in `SignalKEngine` into `nautical_history.dat`.
    - `[x]` Optimize `NauticalGraphView` to reduce GC pressure (removed `.map {}` in widgets).
- `[x]` 4. UX & Engineering Improvements
    - `[x]` Remove fast-action from `NauticalMapLayer.onLongPressEvent`.
    - `[x]` Use theme-aware colors in `NauticalPilotWidget` layout.
    - `[x]` Replace reflection with `getEnabledPluginsPreference()` in `NauticalPlugin`.
- `[ ]` 5. Verification
    - `[ ]` Verify "Steer here" menu option.
    - `[ ]` Verify GPS restoration on plugin disable.
    - `[ ]` Verify Night Vision colors.
