# Implementation Plan - Final MOB Polishing

Complete the MOB functionality by addressing the final edge cases and optimizations identified during assessment.

## Proposed Changes

### Banner Preemption (UX Priority)

#### [MODIFY] [NauticalHudManager.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalHudManager.kt)
- Update `BannerRequest` to implement `Comparable` based on `isWarning`.
- Change `bannerQueue` to a `PriorityBlockingQueue` to ensure critical safety banners (like MOB) jump to the front of the display queue.

---

### Dynamic Tactical Decisions

#### [MODIFY] [MobViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/mob/viewmodel/MobViewModel.kt)
- Replace the hardcoded `0.3 Nm` threshold in `executeRecommendedTurn` with a calculation: `max(0.2, sog * 60 / 1852)`. This accounts for the vessel's turning radius at different speeds.
- Add an observer for `marineState.isMobActive`. If a remote MOB is detected (e.g. from a networked button or physical sensor via Signal K), trigger the local `MobStateMachine` so the tactical HUD becomes visible on the phone.

---

### Signal K Enrichment

#### [MODIFY] [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- Improve MOB notification parsing to extract latitude and longitude from the message string if the server provides them (e.g. "Man Overboard at 45.1, -1.2").

---

### Safe Environment Cleanup

#### [MODIFY] [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)
- In the `isMobActive` observer, only clear `FLAG_KEEP_SCREEN_ON` if `USE_SYSTEM_SCREEN_TIMEOUT` is enabled in settings, respecting the user's preference for an "always on" screen during standard navigation.

## Verification Plan

### Automated Tests
- Test priority queue ordering for banners.
- Test threshold calculation logic with varying SOG.

### Manual Verification
- Trigger a "Wind Warning" banner and then a "MOB" banner. Verify MOB appears immediately after the current one ends, even if other non-critical banners were queued first.
- Trigger a MOB via Signal K and verify the MOB tactical HUD appears on the phone.
- Verify screen stays on after MOB clear if the user has globally set "Screen Always On".
