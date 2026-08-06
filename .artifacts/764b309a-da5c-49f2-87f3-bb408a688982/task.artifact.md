# Task List: Z-Order, Raster I/O, Closed-Loop Validation, and Safety Preflight

- [x] 1. Update Map Layer Z-Ordering in `NauticalPlugin.kt` (`SailingLaylinesMapLayer` and `WeatherRoutingMapLayer` to 3.0f)
- [x] 2. Offload synchronous disk I/O from `MarineRasterMapLayer.onDraw()` to asynchronous coroutine loading (`Dispatchers.IO`)
- [x] 3. Implement closed-loop command validation & 3000ms timeout in `AutopilotController.kt` and UI widgets
- [x] 4. Surface safety preflight failure reasons via high-priority UI alerts (red Snackbar/Toast/TTS) in `SafetyPreflightController.kt`
- [x] 5. Verify build and code correctness
