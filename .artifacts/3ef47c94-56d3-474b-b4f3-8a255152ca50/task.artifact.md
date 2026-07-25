# Tasks - Autopilot UI/UX Refinement & Functional Restoration

- [x] **OsmAnd Settings**
    - [x] Add `NAUTICAL_PILOT_SEA_STATE` to `OsmandSettings.java`
- [x] **Interaction Cleanup (`HeadingArcView`)**
    - [x] Remove circular slide logic from `onTouchEvent`
    - [x] Implement `onCenterClicked` callback
- [x] **Dashboard Layout (`bottom_sheet_nautical_pilot.xml`)**
    - [x] Add Sea State slider section below `RudderView`
- [x] **Dashboard Logic (`NauticalPilotBottomSheet`)**
    - [x] Implement "Hold Heading" via `arcView.onCenterClicked`
    - [x] Bind Sea State slider to `autopilot.setSeaState`
    - [x] Add enhanced voice announcements for course changes and maneuvers
- [x] **Resource Management**
    - [x] Update `strings.xml` to remove obsolete items and finalize restoration strings
- [x] **Verification**
    - [x] Verify interaction and voice feedback
