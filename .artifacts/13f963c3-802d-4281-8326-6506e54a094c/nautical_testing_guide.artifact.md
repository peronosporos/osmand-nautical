# Nautical Plugin - Master Exhaustive Testing Guide

This guide covers 100% of the Nautical plugin logic, including pro features and Signal K synergy.

## 1. Safety & Emergency Workflows

### 1.1 Man Overboard (MOB)
- **Test:** Long-press Volume UP (2s).
- **Expectation:** Audio: "Man Overboard". HUD: Red Banner. Map: MOB Pin + Active Route.
- **Synergy:** Verify Signal K server shows `notifications.mob` as `emergency`.

### 1.2 Anchor Watch Synergy
- **Test:** Set an anchor locally. Verify it appears on server dashboard. (CURRENT GAP)
- **Fallback Test:** Walk phone 50m. Verify "DRAGGING SUSPECTED" alert triggers on phone speaker.

### 1.3 Solo Watchdog
- **Setup:** Enable "Solo Watchdog" in Settings.
- **Action:** Wait for timeout.
- **Expectation:** TTS: "Solo Watchdog Timeout! Respond Immediately!". Verify HUD shows response button.

---

## 2. Advanced Steering (Helm)

### 2.1 3D Motion Wind Correction
- **Action:** Rotate the phone quickly while viewing Apparent Wind widget.
- **Expectation:** Angle should remain stable (masthead swing subtracted).

### 2.2 SAR Pattern Nav
- **Action:** Create "Expanding Square" pattern.
- **Expectation:** Map generates square route. Engage Autopilot TRACK. Verify completion drops AP to STANDBY.

### 2.3 Shadow Drive
- **Action:** AP in AUTO mode. Move physical rudder.
- **Expectation:** AP drops to STANDBY immediately. Phone vibrates.

---

## 3. Charts & Data Management

### 3.1 ENC S-63 Licensing
- **Action:** Import `.031` and `.pmt` files.
- **Expectation:** "ENC Manager" shows license validity. Chart layers appear at zoom 12+.

### 3.2 Forensic Logbook Recovery
- **Action:** Active navigation -> Kill app.
- **Expectation:** New entry in logbook: "CRASH RECOVERY: [MODE]".

### 3.3 Two-Way Sync
- **Waypoints:** Add Map Marker -> Verify server `/resources/waypoints`.
- **Checklists:** Check item -> Verify server `/resources/checklists`.

---

## 4. Hardware & Interface

### 4.1 MFD Presentation
- **Action:** Connect external monitor.
- **Expectation:** monitor shows 3-dial HUD (SOG, HDG, VMG) + Heading Arc.

### 4.2 Display Filters
- **Night Vision:** Entire UI must be Red. Confirm Status Bar visibility.
- **Sunlight Mode:** Colors shift to Yellow/Black. Confirm map icons contrast.
