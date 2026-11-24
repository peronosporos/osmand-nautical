# Nautical Plugin Hardening Backlog

Ranked defect & debt backlog produced by the Phase 0 audit (2026-11-24).
Triage rule: safety/data-corruption → fix now; robustness debt → next phase; polish → later.

## Review order (Phase 0.2, risk-ranked)

1. `engine/` data path — largely reviewed; 6 correctness bugs fixed (delta parser x3,
   broker/GPS-fallback x3). Remaining: `SignalKSessionManager`, `OkHttpSignalKConnection`,
   `AutopilotController`, `NauticalAisManager` (CPA/TCPA), watch/alarm evaluators.
2. Safety surfaces — `mob/`, `AnchorDriftWatchdog`, `NauticalSafetyManager/Evaluator`,
   autopilot authority flows (maneuvers), audio arbiter priority handling.
3. Data integrity — `tide/`, `hazard/` (Navtex), `s57/`, `s63/`, `grib/`, `polar/`, `nmea/`.
4. App integration — `NauticalPlugin`, `NauticalHudManager`, `ui/NauticalWidgetFactory`,
   layers, quick actions, settings wiring.
5. Remaining UI (~89 files) — conventions pass, strings, theming.

## Audit findings (open items)

### i18n (Phase 2)
- ~15 hardcoded English user-facing toast strings (e.g. PolarEditorFragment,
  NauticalAisDetailsDialog, SignalKWaypointLayer, NauticalNetworkSetupFragment,
  VhfChannelPickerDialog uses Toast directly instead of app.showToastMessage).
- Fix pattern: move all to `strings.xml` as `nautical_*` keys; stop using `Toast.makeText`
  directly (use `app.showToastMessage` for theming/queueing consistency).

### Concurrency (Phase 1)
- `routing/SignalKRouteProvider.kt` wraps the whole routing calculation in `runBlocking`
  with a 60 s timeout. OsmAnd invokes RouteProvider on a background thread so this is
  acceptable today, but the 60 s ceiling should be exposed as a setting (Phase 3) and the
  fallback path double-checked for leaks of the cancelled job.
- Verify: no `GlobalScope`, no `Thread.sleep`, no raw `URL()` in plugin code — confirmed clean.

### Resource lifecycle (Phase 1)
- Receivers/settings listeners in `NauticalPlugin` and `UsbNmeaClient` are registered and
  unregistered symmetrically (audited 2026-11-24 — the earlier "register vs unregister
  count" heuristic flagged false positives). Re-verify after any new listener is added.

### Tests (Phase 4)
- Current coverage: parser/engine basics only (26 test files).
- Highest-value untested pure logic: RouteTracker, SafetyEvaluator/CPA limits,
  NavtexMessageDecoder, tide tables, layline geometry, LeewayCalculator,
  SignalKUnitConverter edge cases, SignalKSecondsFormat/NMEA checksum utils.

### Repo/PR hygiene (Phase 5)
- Root scratch files (`MarineTextWidget_*.kt`, `SignalKEngine_*.kt`, ...) must be excluded
  from the PR branch (kept on master per owner request).
- `.github/workflows/nautical-ci.yml` is fork-specific — decide whether it stays in the PR.
- Plugin-only diff outside `plugins/nautical/` should be minimised and itemised
  (strings.xml, settings, widget registry, manifest).

## Fixed log

- 2026-11-24 (f50ee85): delta parser — updates dropped on `self`, timestamp pre-validation,
  STW overwriting SOG fix time (parser level).
- 2026-11-24 (c464afe): broker STW still masking SOG staleness; duplicated trajectory/history
  side effects in GPS-fallback CAS transform; missing `timeOfPositionFix` in GPS fallback.
