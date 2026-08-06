# Implementation Plan - Phase 8.0J: Audio, Lifecycle & Settings Integrity

Fix audio focus collisions, background battery drain, profile leakage, and phantom settings.

## User Review Required

> [!IMPORTANT]
> - **Audio Hierarchy**: TTS maneuver instructions will be routed through `NauticalAudioArbiter`. If a Man Overboard (MOB) siren is active, TTS will be queued and only played once the emergency is cleared or downgraded.
> - **Battery Optimization**: Background engines (`Logbook`, `NmeaPlayback`) will now suspend when the app is backgrounded unless a critical nautical task (Anchor Watch, Navigation) is active.
> - **Settings Scoping**: Nautical settings will be strictly isolated to the `BOAT` profile. Switching to `CAR` or `PEDESTRIAN` will prevent these settings from affecting the UI or background services.

## Proposed Changes

### 1. Audio Arbitration & Priority Queue
Implement a centralized priority system in `NauticalAudioArbiter`.

#### [MODIFY] [NauticalAudioArbiter.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/audio/NauticalAudioArbiter.kt)
- Add `TTS_INSTRUCTION` to `AlarmType` with priority between `MOB` and `ANCHOR_DRIFT`.
- Implement `dispatchTts()` with queueing logic.
- Ensure `MOB` siren immediately mutes/suspends active TTS via `app.player`.

#### [MODIFY] [MobAudioAlertManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/viewmodel/MobAudioAlertManager.kt)
- Update to handle emergency ducking signals for the arbiter.

#### [MODIFY] [ManeuverSpeechHelper.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/maneuvers/ManeuverSpeechHelper.kt)
- Route all voice feedback through `NauticalAudioArbiter.dispatchTts()`.

### 2. Lifecycle-Aware Background Engines
Prevent battery drain by suspending non-critical loops in the background.

#### [MODIFY] [AnchorDriftWatchdog.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/AnchorDriftWatchdog.kt)
- Implement `onAppBackgrounded()`: Suspend location processing unless `NAUTICAL_ANCHOR_LAT` is set (Armed).
- Add `cleanupLegacyPreferences()` to wipe lat/lon on disarm.

#### [MODIFY] [AutomatedLogbookEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/logbook/engine/AutomatedLogbookEngine.kt)
- Suspend periodic logging in background unless actively navigating or anchor watching.

#### [MODIFY] [NmeaPlaybackEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/replay/NmeaPlaybackEngine.kt)
- Automatically pause playback when the app is backgrounded to save CPU/Battery.

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- Dispatch background/foreground signals to all engines.

### 3. Settings Integrity & Reactive Map Invalidation
Scope preferences and ensure immediate UI updates.

#### [MODIFY] [OsmandSettings.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/settings/backend/OsmandSettings.java)
- Explicitly scope `NAUTICAL_` preferences to `ApplicationMode.BOAT`.
- Prevent pollution of other profiles.

#### [MODIFY] [nautical_settings.xml](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/res/xml/nautical_settings.xml)
- Expose `NAUTICAL_LEEWAY_COEFFICIENT`, `NAUTICAL_CORRIDOR_WIDTH`, and `NAUTICAL_SAFETY_CORRIDOR_BUFFER` with unit validation.

#### [MODIFY] [NauticalMapLayer.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalMapLayer.kt)
- Ensure safety contours redraw instantly on draft/margin change (linked via `NauticalPlugin` listener).

## Verification Plan

### Automated Tests
- `NauticalAudioArbiterTest`: Verify priority preemption (MOB > TTS).
- `AnchorWatchdogLifecycleTest`: Verify WakeLock release and suspension in background when disarmed.

### Manual Verification
1.  **Audio**: Trigger MOB while a maneuver instruction is playing. Confirm TTS stops immediately.
2.  **Battery**: Use Android Studio Profiler to observe CPU usage drop for `AutomatedLogbookEngine` when app moves to background (no active anchor).
3.  **Settings**: Change `Draft` in Boat profile, switch to Car, verify Draft is not visible/effective, switch back, verify map refreshes immediately.
