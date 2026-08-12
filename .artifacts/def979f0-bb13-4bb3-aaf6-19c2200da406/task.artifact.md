# Tasks: Restoration of Nautical Map UI & Plugin Logic

- [x] Restore `NauticalMapLayer.kt` Structural Elements
    - [x] Restore architectural comments (Task 8.0, Scotopic rendering)
    - [x] Restore simple laylines drawing (Item 5)
    - [x] Restore Paint property assignments in `onDraw` (textSize, strokeWidth)
    - [x] Restore blinking connection warning with background rect and bold text
    - [x] Verify dateline-crossing logic in trajectory drawing
- [x] Restore `NauticalPlugin.kt` Documentation & Logic
    - [x] Restore "Task" based comments for connectivity and battery
    - [x] Verify `checkConnectionSafety` behavior for `SOLO_WATCHDOG`
- [x] Final Verification
    - [x] Verify Infinite Laylines fallback
    - [x] Verify Connection Loss alert (visual + audio)
    - [x] Create walkthrough artifact
