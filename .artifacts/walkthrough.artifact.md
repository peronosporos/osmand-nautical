# Walkthrough - Unify Authentication Feedback (Item 4)

I have unified the authentication feedback across the Nautical plugin, centralizing the signaling through `SignalKEngine.triggerAuthError()` and replacing inconsistent Toast messages with persistent HUD Banners.

## Changes Made

### [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Changed `triggerAuthError()` from `private` to `public` to allow external components to signal authentication failures.

### [AutopilotController.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
- Refactored `executePut` to use `triggerAuthError()` when:
    - Credentials (token or username/password) are missing.
    - The server returns a `401 Unauthorized` or `403 Forbidden` response.
- Removed redundant `showPersistentError` calls for authentication issues, favoring the unified specialized banner.

### [NauticalPilotBottomSheet.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalPilotBottomSheet.kt)
- Updated `checkAuthToken()` to use `engine?.triggerAuthError()` instead of showing a transient Toast message. This ensures that when a user attempts an action without being logged in, they get a clear, persistent notification with a direct link to settings.

## Verification Results

### Manual Verification
- **Pilot Bottom Sheet**: Verified that attempting to nudge course or change pilot mode without valid credentials now triggers the HUD Banner with a "Settings" button. No Toast is shown.
- **Autopilot Controller**: Verified that simulated server auth errors (401/403) correctly trigger the same unified banner.
- **Settings Navigation**: Confirmed that the "Settings" button on the banner correctly opens the Nautical Settings screen.

render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/AutopilotController.kt)
render_diffs(file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/widgets/NauticalPilotBottomSheet.kt)
