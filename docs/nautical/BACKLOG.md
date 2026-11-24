# Nautical Plugin Hardening Backlog

Ranked defect & debt backlog produced by the Phase 0 audit (2026-11-24).
Triage rule: safety/data-corruption → fix now; robustness debt → next phase; polish → later.

## Decision log (owner, 2026-11-24)

- North-star vision: transition from MVP/proof-of-concept to a **full replacement for all
  cockpit screens** (autopilot, instruments, VHF, chartplotter). Bold structural shifts are
  welcome where justified, not just small fixes.
- UX architecture: dual-track kept for now; the owner asked for a proposal — see
  `docs/nautical/` plan. Decision pending.
- Wear OS: maintain, no expansion.
- Upstream PR: deliberately deferred; do not optimise for it yet.
- Helm commands over plaintext HTTP: **usability wins**. Commands are always sent; a
  once-per-session warning banner informs the user instead of a refused command.
  (Implemented: insecureCommandWarningShown in AutopilotController.)

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
- Phase 1.1 connection review: fixed zombie reconnect (old `OkHttpSignalKConnection`
  objects could keep their internal backoff loop alive after `startEngine` replaced them,
  producing duplicate parallel WebSockets); reset manager retry counter when a retry finds
  the connection already re-established; watchdog loop no longer logs normal shutdown as
  an error (CancellationException rethrown before generic catch).
  Open (backlog): `OkHttpSignalKConnection.WATCHDOG_TIMEOUT_MS` (5 s) is hard-coded while the
  app-level watchdog timeout is a setting — should follow the same setting in Phase 3.

## Autopilot & safety review (Phase 1 continued)

- 2026-11-24: commandTack used `autopilotHeadingSet` (radians) as degrees when a target
  heading was already set, sending the helm to a nonsense heading on tack — fixed with a
  rad->deg conversion.
- 2026-11-24: executePut's plaintext-guard was self-defeating: `isLocal` was also true
  whenever TLS was disabled or trust-all enabled, so autopilot commands and credentials
  were silently sent over plaintext HTTP to public hosts. Guard now keys on genuine
  local/private addresses only.
- Reviewed without action: helm arbitration priority scheme (standby using emergency
  priority blocks accidental standby during MOB — intentional), command throttling,
  reconciliation timeout with NonCancellable lock release, gybe-prevention confirm flow,
  insecure-command refusal toast, anchor watchdog debounce (3 fixes + 5 s), median filter,
  windlass overload monitor, anchor sync-from-server logic.

## MOB & AIS review (Phase 1 continued)

- Reviewed `MobStateMachine`, `MobVectorEngine`, `NauticalAisManager`: overall sound.
  Notes / open items:
  - MobStateMachine persists inside the StateFlow CAS transform (idempotent upsert, accepted,
    but a pure-transform refactor is preferable).
  - Verify sector-search and expanding-square waypoint generation against IAMSAR Vol. III
    reference geometry before release (Phase 5 checklist item).
  - AIS buddy list lives in an ad-hoc `SharedPreferences ("nautical_buddies_pref")` instead
    of OsmandSettings — migrate in Phase 2/3 settings consolidation.
  - Class-B AIS prune timeout (18 min) is hard-coded next to a configurable base timeout —
    expose as setting in Phase 3.
  - `resolveMmsiFromContext` maps UUID contexts to a 31-bit hash — theoretical collision
    with real MMSIs; acceptable for beta, document it.
  - CPA/TCPA logic, divergence filter, anchorage filter, out-of-order timestamp guard and
    evidence-based threat model all verified correct.

## i18n pass (Phase 2, completed 2026-11-24)

- All 39 hard-coded user-facing strings (toasts, HUD banners, spoken prompts incl. tactical
  start countdown, gybe warning, windlass overload voice) moved to `nautical_*` keys in
  `strings.xml` with positional format args. Remaining raw `Toast.makeText` in
  VhfChannelPickerDialog uses a proper resource already — acceptable.
  Known follow-up: full sweep of dialog titles built with literals (e.g. "Select Active
  Polar") during the Phase 2 conventions pass.
