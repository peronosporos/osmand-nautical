# Implementation Plan - Interactive Polar Curve Canvas Editor & Smoothing Algorithms

Implement the Interactive Polar Curve Canvas Editor and smoothing algorithms under `net.osmand.plus.plugins.nautical.ui.editor` and `viewmodel`.

## User Review Required

> [!IMPORTANT]
> All new user-visible strings will be added to the beginning of `OsmAnd/res/values/strings.xml` per project standards.

## Open Questions

- None.

## Proposed Changes

### Strings (`OsmAnd/res/values/strings.xml`)
- Add editor localized strings at the beginning of `strings.xml`:
  - `editor_title`: "Interactive Polar Curve Editor"
  - `editor_smooth_slider`: "Smoothing Intensity"
  - `editor_save_server`: "Save to Server"
  - `editor_reset`: "Reset Edits"

### ViewModel Component (`net.osmand.plus.plugins.nautical.viewmodel`)

#### [NEW] [PolarEditorViewModel.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/viewmodel/PolarEditorViewModel.kt)
- Manages active TWS selection, raw scatter points, smoothed Catmull-Rom spline / moving average curve points, and smoothing slider value.
- Implements `savePolarsToServer()` invoking PUT request via `SailingPerformanceRepository`.

### UI & Canvas Components (`net.osmand.plus.plugins.nautical.ui.editor`)

#### [NEW] [PolarCurveCanvasView.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/editor/PolarCurveCanvasView.kt)
- Custom View rendering a half-radial coordinate system (0 to 180° TWA).
- Displays faint raw scatter plot background and prominent smoothed spline path.
- Handles touch `MotionEvent`s to drag control points interactively.

#### [NEW] [PolarEditorFragment.kt](file:///home/administrator/AndroidStudioProjects/osmand-nautical/OsmAnd/src/net/osmand/plus/plugins/nautical/ui/editor/PolarEditorFragment.kt)
- Hosting fragment containing the `PolarCurveCanvasView`, TWS selector, smoothing slider, and save pipeline.

## Verification Plan

### Automated Tests
- Build and compilation verification.

### Manual Verification
- Verify interactive curve dragging, smoothing slider responsiveness, and save action.
