# Tasks - Phase 5: Predictive Navigation & Mission Continuity

## Predictive AIS Collision Vectors (ROT-based)
- [x] Add `rot` to `AisLocation` [AisLocation.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisLocation.kt)
- [x] Update `AisObject` to pass `rot` [AisObject.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisObject.kt)
- [x] Implement `getCurvedPosition` in [AisTrackerMath.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd-shared/src/commonMain/kotlin/net/osmand/shared/aistracker/AisTrackerMath.kt)
- [x] Implement curved predictor lines in [AisObjectDrawable.java](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/aistracker/AisObjectDrawable.java)

## Mission Continuity (Offline Logging & Sync)
- [x] Implement internal location fallback in [SignalKEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/SignalKEngine.kt)
- [x] Add latency tracking to [OkHttpSignalKConnection.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/engine/OkHttpSignalKConnection.kt)
- [x] Integrate location listener in [NauticalPlugin.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/NauticalPlugin.kt)

## Tidal Correction Feedback
- [x] Calculate `confidenceFactor` in [IsochroneRoutingEngine.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/algorithm/IsochroneRoutingEngine.kt)
- [x] Update [RoutingModels.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/routing/model/RoutingModels.kt) with `confidenceFactor`

## Hardware Health Dashboard
- [x] Create `hardware_health_hud.xml`
- [x] Create [HardwareHealthHudHeader.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/HardwareHealthHudHeader.kt)
- [x] Integrate with `NauticalHudManager` in `NauticalPlugin.kt`
