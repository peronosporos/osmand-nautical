# Architectural Hygiene & Performance Audit: Nautical Plugin

An audit of the `net.osmand.plus.plugins.nautical` package was performed across four key criteria. Below is the summary of findings.

## 1. Memory & Threading Hygiene (S-57 & Canvas Rendering)
**Status:** [WARNING] Potential Performance Issues Found

- **S57SpatialIndex.kt:**
  - [x] NO persistent list structures holding JTS `Geometry` objects. `S57Object` uses lightweight `S57Geometry` (POJO) and converts to JTS on-demand.
  - [x] Spatial feature fetching delegates directly to `S57SqliteHelper`.
  - [!] **Violation:** Contains an unused `viewCache` (ConcurrentHashMap). While it doesn't hold JTS Geometries, it is a leftover in-memory cache.
- **Map Canvas Rendering:**
  - [!] **Violation (`S57MapLayer.kt`):** `onDraw()` performs `String.format("%.1f", depth)` inside a feature loop, which instantiates `Formatter` and `String` objects on every frame.
  - [!] **Violation (`S57MapLayer.kt`):** `getPathFromGeometry()` is called inside `onDraw()`, which instantiates a `new Path()` object for every line/area feature on every frame.
  - [!] **Violation (`NauticalMapLayer.kt`):** `onDraw()` instantiates `SafetyCorridorChecker` and creates a list of `Waypoint` objects via `route.map { ... }` inside `drawNavigationPath()` whenever `needsRecheck` is true (which can be frequently during movement).

## 2. Coroutine & Resource Lifecycle Leaks
**Status:** [CAUTION] Resource Leaks Identified

- **SignalKEngine.kt:**
  - [x] `engineScope` is properly cancelled in `stop()`.
- **Leaking Scopes:**
  - [!] **Violation (`SignalKDataBroker.kt`):** Contains a member `scope` that is never cancelled. No `stop()` or `cleanup()` method exists.
  - [!] **Violation (`TcpNmeaClient.kt`):** `disconnect()` does not cancel the `scope` passed in or the default scope.
  - [!] **Violation (`SignalKWebSocketClient.kt`):** `disconnect()` does not cancel the member `scope`.

## 3. Reversion & Leftover Cleanup
**Status:** [PASS] Criteria Met

- [x] **S57IndexManager.kt:** Successfully verified as deleted from the project tree.
- [x] **Temporary Artifacts:** 0 leftover `.artifact.md` files found in the source tree (outside of the protected `.artifacts/` directory).

## 4. Algorithmic Bounds & Safety
**Status:** [WARNING] Sub-optimal Spatial Safety Checks

- [!] **Violation:** `SafetyCorridorChecker` and `IsochroneRoutingEngine` do **NOT** apply fine-grained JTS intersection checks on candidate features.
- **Current Behavior:** Both engines rely solely on the bounding box (Envelope) results returned from `S57SqliteHelper`.
- **Risk:** This may lead to false positives (e.g., land collision detected when a point is near a land feature's bounding box but outside its actual irregular shape). To meet the "fine-grained" requirement, `intersects()` or `contains()` checks against the actual JTS `Geometry` should be performed on the candidate list.

---

### Recommended Actions
1. Remove `viewCache` from `S57SpatialIndex`.
2. Move `Path` and `String` allocation out of `onDraw()` in `S57MapLayer`. Pre-calculate paths in `prepareFeatures`.
3. Move `SafetyCorridorChecker` instantiation and `Waypoint` mapping out of `NauticalMapLayer.onDraw()`.
4. Implement `cancel()` or `stop()` methods for `SignalKDataBroker`, `TcpNmeaClient`, and `SignalKWebSocketClient` to prevent `CoroutineScope` leaks.
5. Add `jtsGeometry.intersects(queryPoint)` checks in `IsochroneRoutingEngine` and `SafetyCorridorChecker` to ensure precise spatial safety.
