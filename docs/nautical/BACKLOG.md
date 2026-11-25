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

## Dead code audit (2026-11-25, completed)

Reachability audit of all 346 nautical source files against live entry points
(plugin API, quick actions, settings framework FQCN registrations, manifest,
widget framework). Findings and resolution, per user decision:

- Deleted 7 zero-reference files (WebSocket client, network setup fragment,
  sparkline/mini-rose views, WindManeuverHelper, DriftVectorCalculator) plus
  3 duplicates (SignalKOrchestratorFragment, NauticalConfirmDialog,
  ConfigurePolarsDialogFragment) — ~950 lines removed. SQLiteExtensions
  restored after CI caught that NavtexRepository uses its extension function:
  audit scans must include extension-function imports, not only type symbols.
- Wired up as reachable features: Radar/Depth-profile/Polar map layers
  (settings toggles, NauticalLayerManager), mode auto-switch cluster
  (NauticalModeAutoSwitchManager + NauticalCockpitHudView as the Cockpit
  context strip, fed by engine state), Nautical Tool Center (new quick action
  id 106; also revives NauticalSystemsBottomSheet and cross-links to Cockpit),
  NauticalRemoteServer (lazy-start via the strip's QR button).
- Kept dormant: NauticalSetupWizardDialog (onboarding design pending).
- Caveat: layer toggles apply at layer registration (map restart); reactive
  re-registration on preference change is a follow-up.

## UI/UX audit (2026-11-25)

Scope: all 61 nautical layouts, 159 UI-bearing Kotlin files, quick actions,
settings XML, cockpit shell, and OsmAnd-convention conformance.

### Fixed immediately (batch 1+2)
- Dark-theme blindness in the full-screen Cockpit (shell + 5 pages keyed palette
  only off night vision). New `CockpitScreenTheme.isDark()` also honors the app
  theme via `DayNightHelper.isNightMode(ThemeUsageContext.APP)`.
- i18n: radar control sheet (9 literals), Tool Center items (Sail Inventory,
  Polar Manager) moved to resources; a11y strings added.
- Accessibility: contentDescription added to interactive icon-only controls in
  master telemetry, telemetry grid/item, reorder item, settings item, anchor
  HUD, buddy list (drag handles, delete, settings, close, anchor icon).
- System-bar insets: Cockpit shell now fits system windows (gesture nav,
  notches).
- 3 orphan layouts removed (network setup, data bottom sheet, reefs control
  item). Pilot telemetry grid was initially misdetected as orphan (resource
  includes were outside the scan corpus, since fixed) and restored.

### Remaining debt (queued, not fixed this pass)
- i18n breadth: RESOLVED in the 2026-11-25 breadth pass (extended into
  2026-11-25 follow-up session): ~180 user-visible literals externalized across
  24 Kotlin files + 2 layouts (server charts/routes, sensor health, VHF,
  diagnostics, polar wizard, checklist, alarm banner, AIS lists/details, VHF
  picker, notifications, route summary, passage plan, anchor HUD, start-line
  header, tactical manager (structured FavoredEndAdvantage + test update),
  buddy list, technical stats, electrical dashboard, polar recorder, S57/S63,
  GRIB, BoatAI, depth profile layer, context menu). Known remaining:
  gauge placeholder texts in
  nautical_cockpit_hud_strip.xml and electrical layout (runtime-bound, left as-is),
  plus assorted Toast/ASCII fragments: ~15 toast strings (PolarEditorFragment,
  AIS details, waypoint layer, network setup). SignalK path constants in engine
  files are NOT user-visible and must not be "fixed".
- Accessibility breadth: RESOLVED in the 2026-11-25 a11y sweep: 11 decorative
  ImageViews marked contentDescription="@null" (sensor health, compass/setup
  wizards, telemetry settings, steering HUD, touch lock) and 24 hardcoded
  contentDescription labels across 12 layouts moved to nautical_a11y_*
  resources (GRIB scrubber, AIS details, SAR config, logbook, buddy list,
  route summary, AIS target card, VHF transmission, BoatAI voice input,
  cockpit strip). Icon-only interactive controls had been covered by earlier
  batches; a full rescan found no remaining unlabeled icon widgets.
- Conventions: several sheets build titles/bodies programmatically with hard
  colors instead of themed styles; `getThemedContext` used in only 12 of 63 ui/
  files. HUD layouts carry a handful of hard-coded colors (start-line,
  MFD, cockpit strip, AIS details) — safe under night vision but not under
  pure app dark theme.
- Layer toggles need map restart (registration-time gating); follow-up:
  reactive re-registration on preference change.
- OsmAnd-native widget/hud deep review (widget ids vs WidgetRegistry labels,
  quick action categories) still to be done systematically.

## Session log 2026-11-25 (afternoon): i18n breadth pass

- Four commits: d60835c (batch 1, 8 files, incl. removal of the fake "42 ms"
  server-latency value shown while connected, replaced with "--" until real
  round-trip telemetry is wired), d93e9c9 (compile fix: charts ViewHolder
  getString scope + batch 2 alarm banner/CPA/VHF/notifications), 10e087f
  (batch 3 routing/anchor/start-line/engines/GRIB/S57/S63/BoatAI/context menu
  + TacticalStartManager structured refactor), 258d832 (batch 4 layouts).
- All validated by green Nautical Unit Tests; Remote Marine Compiler green on
  d93e9c9 (first fully-built APK of this session), later runs in progress at
  time of writing.

## Session log 2026-11-25 (evening): i18n tail + a11y sweep

- 01ac2e5: i18n tail — NauticalSettingsFragment (8 literals: summaries,
  wave-bias/logbook-interval entries), NauticalSafetyEvaluator hazard and
  advisory messages incl. TTS-spoken TSS/MARCUL/MIPARE/submarine-cable alerts,
  helm-arbitrator rejection reason, weighing-anchor abort reason, metric graph
  empty state; S57SpatialIndex indexingStatus became a structured
  S57IndexStatus with localized() (S57 manager + settings consumers updated).
- 5e5d564: a11y sweep — decorative icons nulled, a11y labels resourced.
- CI: unit tests and full APK (Remote Marine Compiler) green on 01ac2e5;
  5e5d564 unit tests green at time of writing, APK in flight.
