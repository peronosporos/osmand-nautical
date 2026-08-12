# Task: Navtex System Comprehensive Bug Fix

- `[x]` Backend: Engine & Parsing Logic
    - `[x]` Fix `NavtexSentenceParser.kt` (Checksums, Urgency, Low-Degree, Regex)
    - `[x]` Fix `NavtexMessageDecoder.kt` (Sequence parsing)
    - `[x]` Fix `NavtexViewModel.kt` (FQN, Spatial filtering, Safe filters)
- `[x]` Backend: Data & Persistence
    - `[x]` Fix `NavtexRepository.kt` (Deferred cleanup, Column mapping, Zero-expiry guard)
- `[x]` Frontend: Map Rendering & Interaction
    - `[x]` Fix `NavtexMapLayer.kt` (Anti-meridian, Clipping, Touch buffer, Localization)
- `[x]` Frontend: UI/UX & HUD
    - `[x]` Fix `NavtexHudView.kt` (MOB isolation, Ticker safety)
    - `[x]` Fix `NavtexDetailsBottomSheet.kt` (State restoration, Key filtering)
    - `[x]` Fix `NavtexListFragment.kt` (UTC time, Filter dialog)
- `[/]` Verification
    - `[x]` Run and update `NavtexSentenceParserTest.kt`
    - `[ ]` Verify UI changes manually
