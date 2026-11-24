# Cockpit Surface & Pane Contract (implementation plan)

Engineering plan for bold-shift #1 from ARCHITECTURE.md. Target: replace the ~12
parallel `initXSystem` wirings in `NauticalUiOverlayManager` with one pane contract
mounted in a managed shell, starting with a pilot pane (Helm).

## Inventory of what becomes a pane

Current `initXSystem(...)` wirings (each constructs a view, collect-observes a
ViewModel flow, attaches via `NauticalHudManager.addHeader(view, priority)`):

| Pane (proposed)   | Source wiring              | Data source                    | Priority |
|-------------------|----------------------------|--------------------------------|----------|
| MobEmergency      | initMobSystem              | MobViewModel                   | EMERGENCY (top) |
| DrWarning         | initDrSystem               | DeadReckoningViewModel         | ALARM |
| Navtex            | initNavtexSystem           | NavtexViewModel                | WARNING |
| Tactical          | initTacticalHudSystem      | engine flows + tacticalProcessor | NORMAL |
| ForwardWatch      | initForwardWatchSystem     | watch schedule + engine        | NORMAL |
| Environment       | initEnvironmentSystem      | broker marineState             | NORMAL |
| WatchSchedule     | initWatchScheduleSystem    | workflow manager               | LOW |
| Workflow          | initWorkflowSystem         | SailingWorkflowEngine          | LOW |
| AlarmBanner       | initMarineAlarmBannerSystem| notifications                 | EMERGENCY |
| GribScrubber      | initGribTimeScrubberSystem | grib repository                | LOW |
| AnchorWatch       | anchor/AnchorWatchHudView | anchor watchdog driftStage     | ALARM |

`NauticalHudManager` already arbitrates by priority and owns the banner queue — it
becomes the **shell**. The gap is that each wiring re-implements its own lifecycle.

## The pane contract

```kotlin
interface CockpitPane {
    val paneId: String
    val priority: Int                    // reuses NauticalHudManager header priorities
    val requiredWhen: (MarineState) -> Boolean   // pane wants to be mounted
    val dataDependencies: List<StateFlow<*>>     // for lifecycle-aware restart
    fun buildView(activity: MapActivity): View
    fun bind(view: View, scope: CoroutineScope)   // observe flows, called on (re)mount
    fun unbind(view: View)                       // cancel pane-scoped jobs
}
```

Shell responsibilities (extend `NauticalHudManager`, no new container):
1. Mount/unmount panes as their `requiredWhen` flips (MOB takeover stays unconditional).
2. Single `CoroutineScope` per pane mount; auto-cancel on activity destroy.
3. Day/night + night-vision theming applied centrally (used ad hoc today).
4. Touch lock and safe-area insets applied by the shell, not per pane.

## Pilot refactor (Helm pane)

Phase into this order, one commit per step, tests green at each:
1. Introduce `CockpitPane` + shell mount logic alongside existing wiring (additive, zero behavior change).
2. Convert **AlarmBanner** and **MobEmergency** (smallest, highest value).
3. Convert the rest mechanically; delete per-system lifecycle code as each goes.
4. Then evaluate the full-screen Cockpit activity on top of the same pane set.

## Groundwork already done

- Replay-first harness v1 (RecordedSignalSessions/SignalKSessionReplayTest) protects
  the engine data path while the UI layer is restructured.
