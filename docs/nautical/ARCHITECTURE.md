# Nautical Plugin Architecture Direction

Owner vision: the plugin must become a full replacement for all cockpit screens —
autopilot, instruments, VHF, chartplotter — not an OsmAnd accessory. This note records
the current structure, the proposed architectural direction, and the open decision.

## Current structure (measured, 2026-11-24)

Three parallel UI systems coexist:

1. **OsmAnd-native widgets** — `NauticalWidgetFactory` implements the platform contract
   (`createMapWidgetForParams`, `createWidgets`, `WidgetType` registry). Glance data
   (SOG, depth, wind…) configured through OsmAnd's widget UI, theming and profiles for free.
2. **Bespoke HUD overlay system** — `NauticalHudManager` + `NauticalUiOverlayManager`
   manage ~20 custom HUD views (tactical, environment, anchor watch, MOB, Navtex, DR,
   touch lock…) attached to the map, each with its own init/teardown wiring.
3. **Dialogs/bottom sheets** — ~15 sheets (pilot, VHF, electrical, maneuvers, diagnostics).

Below the UI: a coherent engine layer (SignalK ingest, broker, arbiters, calculators —
mostly platform-light and already unit-tested) plus feature modules (tide, s57, grib,
hazard, maneuvers, replay…).

## Proposed direction (for owner decision)

**Two tracks with hard rules, converging on one "Cockpit" surface.**

- **Track A — Glance data stays native.** Every value a skipper wants at-a-glance on the
  map remains an OsmAnd `WidgetType`: configuration UI, profiles, day/night theming,
  ordering and placement are inherited from the platform. Rule: if it fits in a widget
  chip, it is a widget. Do not duplicate widgets in the HUD.
- **Track B — Cockpit replaces the HUD sprawl.** Introduce a single **Cockpit surface**
  (full-screen or overlay mode selectable per profile: Helm, Instruments, Nav/Chart, VHF,
  Watchkeeper) powered by a **pane contract**: each pane declares its data dependencies
  (`StateFlow`s from the engine/broker), alarm affordances and lifecycle, and is mounted
  into a shared shell that owns theming (incl. night vision), back handling, touch lock
  and safe areas. The ~20 bespoke HUD views then reduce to panes behind one shell instead
  of parallel view managers. MOB keeps its guaranteed top-priority takeover regardless of
  active pane.

**Why keep both tracks:** Track A gives OsmAnd-fit and configurability with minimal code;
Track B is the only way to reach cockpit-screen quality (map+instruments+commands in one
glance-able surface with big touch targets). The current dual system prototyped both;
formalising it removes the redundancy without throwing away working UI.

## Bold-shift candidates (ranked, not yet scheduled)

1. **Cockpit surface + pane contract** (above) — highest product value.
2. **`nautical-core` JVM module**: split pure engine (parsers, calculators, trackers,
   arbiters, state) from Android-dependent code so the whole data path is unit-testable
   without device/emulator builds and CI compile checks run in minutes, not 20.
3. **Replay-first regression harness**: promote the existing `replay/` package to the
   golden-master test (record real SignalK sessions, assert end state), giving every
   engine change a sea-trial equivalent in CI.
4. **Instrument-grade data pipeline**: single coherent staleness/fallback policy
   (external → internal GPS → DR) implemented once in the broker, consumed by all panes.

## Constraints honored

- Wear OS: maintain (no expansion).
- Upstream PR: deferred; architecture chosen for product quality, not for minimal diff.
- Usability over strictness where they conflict (e.g. plaintext command policy warns
  rather than refuses).
