# Implementation Plan - Phase 8.0Z: Reactive UI & Lifecycle Scoping

Fix optimistic UI updates, state desynchronization, and orphaned observers in the nautical autopilot system.

## User Review Required

> [!IMPORTANT]
> - **Visual Pending State**: Buttons will transition to an "Amber/Flashing" state when a command is sent, and only turn "Green/Active" once the hardware confirms the state via SignalK.
> - **Lifecycle Collection**: We are moving from custom listeners to `StateFlow` collection using `repeatOnLifecycle(Lifecycle.State.STARTED)` to ensure zero CPU/Network usage when the UI is not visible.

## Proposed Changes

### [Nautical Engine]

#### [MODIFY] [AutopilotManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotManager.kt)
- Remove any local optimistic state variables.
- Ensure `updatePendingCommand` is used exclusively for tracking "sent but not confirmed" actions.

### [Nautical UI]

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/views/mapwidgets/widgets/NauticalPilotBottomSheet.kt)
- Replace `engine.registerListener` with `viewLifecycleOwner.lifecycleScope.launch` collecting from `engine.marineStateFlow` inside `repeatOnLifecycle(Lifecycle.State.STARTED)`.
- Implement `PENDING` visual state:
    - Use amber color/animation for buttons whose mode is in `pendingAutopilotState`.
    - Only show green (checked) for buttons whose mode matches the confirmed `autopilotState`.
- Remove `selectedModeOverride`.

#### [MODIFY] [TacticalHudView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/TacticalHudView.kt)
- Refactor to support clean state updates from a `Lifecycle`-aware observer.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Update `initWorkflowSystem` to launch `StateFlow` collection for `tacticalHudView` and `healthHudView` using `activity.lifecycleScope` and `repeatOnLifecycle(Lifecycle.State.STARTED)`.

## Verification Plan

### Automated Tests
- Build verification to ensure all Kotlin coroutine dependencies are correctly resolved.

### Manual Verification
1. Open the Nautical Pilot Bottom Sheet.
2. Tap a mode button (e.g., AUTO).
3. Observe that the button flashes amber or shows a distinct "pending" state immediately.
4. Verify that the button only turns green once SignalK confirms the state change.
5. Minimize the app and verify (via logs) that `StateFlow` collection for the Bottom Sheet and HUD views stops completely.
6. Re-open the app and verify updates resume immediately.
