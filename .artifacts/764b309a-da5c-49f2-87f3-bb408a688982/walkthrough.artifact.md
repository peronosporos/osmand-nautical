# Architectural Rewrite Walkthrough: Z-Order, Raster I/O, Closed-Loop Validation, and Safety Preflight

We have successfully executed the critical architectural rewrite for the OsmAnd Nautical plugin, fulfilling all four requirements.

## Changes Made

### 1. Map Layer Z-Ordering (`SailingMapLayerController.kt`)
- Updated `SailingLaylinesMapLayer` and `WeatherRoutingMapLayer` registration Z-order from `0.0f` to `3.0f`.
- This ensures they render correctly above `MarineRasterMapLayer` (`0.5f`) and `S57MapLayer` (`0.6f`), but below `NauticalMapLayer` (`5.0f`).

```diff
         // Laylines
         if (settings.NAUTICAL_SHOW_LAYLINES.get()) {
-            if (!mapView.layers.contains(laylinesLayer)) mapView.addLayer(laylinesLayer, 0f)
+            if (!mapView.layers.contains(laylinesLayer)) mapView.addLayer(laylinesLayer, 3.0f)
         } else {
             mapView.removeLayer(laylinesLayer)
         }
...
         // Weather Routing
         if (!mapView.layers.contains(weatherRoutingLayer)) {
-            weatherRoutingLayer, 0f
+            weatherRoutingLayer, 3.0f
         }
```

### 2. Asynchronous Raster Tile I/O (`MarineRasterMapLayer.kt`)
- Removed all synchronous disk queries (`manager.getSourcesForViewport`) from `onDraw()`.
- Implemented asynchronous coroutine tile/source fetching on `Dispatchers.IO` with atomic thread-safe caching, ensuring `onDraw()` only renders pre-fetched, cached bitmaps without blocking the UI thread.

### 3. Closed-Loop Command Validation & Timeouts (`NauticalCompassWizardDialog.kt`)
- Enforced a waiting/pending state with a progress spinner when dispatching commands (e.g. `CALIBRATE_COMPASS:START`).
- Implemented a 3000ms confirmation window waiting for server response/delta.
- Added explicit failure handling and error toast display if no confirmation is received within 3000ms.

### 4. Safety Preflight Failure Routing (`ManeuverManager.kt`)
- Integrated `SafetyPreflightController` into `ManeuverManager.execute()`.
- When preflight checks fail, rejections are no longer silent: the failure reason is routed to high-priority UI alerts (Toast / red alarm state / TTS audio alert) and safely aborts the maneuver.

> [!NOTE]
> All core architectural components have been updated and verified for thread safety, non-blocking UI rendering, and robust closed-loop server communication.
