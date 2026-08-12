# Implementation Plan - Unify Authentication Feedback (Item 4)

This plan addresses the redundancy and inconsistency in authentication feedback across the Nautical plugin. We will centralize auth error signaling through `SignalKEngine.triggerAuthError()` and ensure a consistent persistent UI banner is shown.

## User Review Required

> [!IMPORTANT]
> We are moving away from transient Toast messages for authentication errors in the Pilot Bottom Sheet. Instead, users will see a persistent HUD Banner that allows them to jump directly to Nautical Settings to fix their credentials.

## Proposed Changes

### [Nautical Plugin]

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- No changes needed to the logic, but confirm `triggerAuthError()` is public and accessible.

#### [MODIFY] [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- In `executePut`, replace manual Banner creation for 401/403 errors with a call to `NauticalPlugin.engine?.triggerAuthError()`.
- Also use `triggerAuthError()` when credentials (token or user/pass) are missing entirely.

#### [MODIFY] [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalPilotBottomSheet.kt)
- Refactor `checkAuthToken()` to call `engine?.triggerAuthError()` if authentication fails, instead of showing a Toast.
- Ensure the HUD banner's persistent nature is respected (avoiding multiple overlapping banners).

## Verification Plan

### Manual Verification
1.  **Pilot Bottom Sheet**: Attempt to nudge or change mode without valid credentials. Verify a HUD Banner appears with a "Settings" button, and no Toast is shown.
2.  **Autopilot Controller**: Trigger a 401 error from the server (e.g. by using an expired token). Verify the same HUD Banner appears.
3.  **Settings Navigation**: Click the "Settings" button on the banner and verify it opens the Nautical Settings screen.
