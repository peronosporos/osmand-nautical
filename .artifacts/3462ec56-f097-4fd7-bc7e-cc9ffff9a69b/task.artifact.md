# Task: 100% AIS & GPS Functionality

- [x] **1. AIS Metadata Enrichment (NMEA)**
    - [x] [MODIFY] `AisDecoder.kt`: Support Static and extended messages (5, 24, 19, 21, 9)
- [x] **2. GPS Quality & Altitude Support (NMEA)**
    - [x] [MODIFY] `NmeaSentenceParser.kt`: Support GGA, GNS, and Variation in RMC/HDG
    - [x] [MODIFY] `SignalKEngine.kt`: Handle Altitude and HDOP paths
- [x] **3. Professional Location & Safety Loops**
    - [x] [MODIFY] `NauticalLocationProvider.kt`: Bridge Altitude and calculated Accuracy
    - [x] [MODIFY] `NauticalAisManager.kt`: Implement Adaptive CPA Loop
- [/] **4. Verification**
    - [ ] Verify AIS vessel names/types in map
    - [ ] Verify GPS Altitude and dynamic Accuracy
