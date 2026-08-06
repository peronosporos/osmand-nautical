# Signal K Plugin Analysis for OsmAnd Nautical Plugin

This document provides an analysis of the first 386 items in the Signal K plugin registry, evaluating their current implementation status within the OsmAnd Nautical Plugin and whether they are suitable candidates for future implementation.

## Evaluation Criteria

*   **Implemented**:
    *   **Yes**: Direct support exists in the `NauticalPlugin` or associated engines.
    *   **Partial**: Some paths/features are supported, or the core data is handled but full plugin functionality is not replicated.
    *   **Equivalent exists**: OsmAnd has its own native implementation that provides the same or better functionality.
    *   **No**: No direct support or equivalent feature is currently present.
*   **Makes sense to implement**:
    *   **Yes**: Provides data or features that enhance navigation, safety, or tactical awareness within a mobile chartplotter.
    *   **Maybe**: Niche utility or feature that might benefit specific users but isn't core to navigation.
    *   **No**: Server-side utility, web-only UI, hardware driver, or infrastructure tool that belongs on the Signal K server itself.

---

| # | Name | Implemented | Makes Sense to Implement? |
| :--- | :--- | :--- | :--- |
| 1 | @signalk/freeboard-sk | Equivalent exists | No (OsmAnd is a standalone chartplotter UI) |
| 2 | signalk-edge-link | No | No (Protocol/bridge utility) |
| 3 | signalk-charts-provider-simple | Equivalent exists | Yes (OsmAnd handles RASTER/ENC charts) |
| 4 | advancedwind | Partial | Yes (OsmAnd handles AWA/AWS/TWA/TWS/TWD) |
| 5 | speedandcurrent | Partial | Yes (OsmAnd handles STW/SOG/Set/Drift) |
| 6 | @noforeignland/signalk-to-noforeignland | No | No (External social service sync) |
| 7 | signalk-attitude-calibrator | No | No (Server-side calibration tool) |
| 8 | @meri-imperiumi/signalk-mob-notifier | Equivalent exists | Yes (OsmAnd has a dedicated MOB system) |
| 9 | signalk-brineomatic-plugin | No | Maybe (Specialized watermaker sensor) |
| 10 | @marineyachtradar/signalk-plugin | No | Yes (Radar overlay support) |
| 11 | @meri-imperiumi/signalk-adsb | No | No (Aviation data) |
| 12 | @meri-imperiumi/signalk-value-combiner | No | No (Server-side data processing) |
| 13 | signalk-polar-performance-plugin | Yes | Yes (OsmAnd has internal Polar performance engine) |
| 14 | @meri-imperiumi/signalk-autostate | No | No (Server-side state logic) |
| 15 | @meri-imperiumi/signalk-logbook | Equivalent exists | Yes (OsmAnd has an automated Marine Logbook) |
| 16 | @meri-imperiumi/signalk-meshtastic | No | Maybe (Long-range LoRa telemetry) |
| 17 | hoekens-anchor-alarm | Equivalent exists | Yes (OsmAnd has Anchor Drift Watchdog) |
| 18 | signalk-prometheus-exporter-macjl | No | No (IT monitoring metrics) |
| 19 | signalk-container | No | No (Host infrastructure) |
| 20 | signalk-questdb | No | No (Database storage) |
| 21 | signalk-grafana | No | No (External visualization) |
| 22 | signalk-backup | No | No (Maintenance utility) |
| 23 | signalk-fallback | No | No (Server logic) |
| 24 | @sailingnaturali/signalk-currents | Equivalent exists | Yes (OsmAnd handles Tidal Currents) |
| 25 | signalk-grib-weather-provider | Equivalent exists | Yes (OsmAnd has GRIB weather module) |
| 26 | @sailingnaturali/signalk-depth-offsets | No | No (Server-side calibration) |
| 27 | signalk-grib-downloader | Equivalent exists | Yes (OsmAnd has built-in GRIB downloader) |
| 28 | @sailingnaturali/signalk-dsc | Equivalent exists | Yes (VHF/AIS DSC support) |
| 29 | @rhizomatics/signalk-bluetti-plugin | Partial | Yes (OsmAnd monitors Batteries/Solar) |
| 30 | @sailingnaturali/signalk-journey-replay | Equivalent exists | Yes (OsmAnd has NMEA/Signal K Replay) |
| 31 | @sailingnaturali/signalk-ntfy-relay | No | Maybe (External notifications) |
| 32 | signalk-symbol-manager | No | No (Server-side UI utility) |
| 33 | signalk-instrument-widgets | Equivalent exists | Yes (OsmAnd has extensive Marine Widgets) |
| 34 | signalk-poi-search | No | Yes (Specific Nautical POI search) |
| 35 | signalk-navico-autopilot-bridge | Partial | Yes (OsmAnd has Autopilot controller) |
| 36 | @sailingnaturali/signalk-equipment-registry | No | No (Inventory management) |
| 37 | signalk-navico-embedder | No | No (NMEA 2000 hardware utility) |
| 38 | signalk-rainviewer-charts | No | Yes (Real-time precipitation radar overlay) |
| 39 | signalk-sailsense | No | Maybe (Wireless sail sensors) |
| 40 | @rhizomatics/signalk-einklabel-plugin | No | No (External display hardware) |
| 41 | signalk-watch-schedule | No | Maybe (Watchkeeping management) |
| 42 | @sailingnaturali/signalk-ais-distress | Equivalent exists | Yes (AIS SART/Distress alerts) |
| 43 | signalk-basic-tide-widgets | Equivalent exists | Yes (Tide telemetry widgets) |
| 44 | signalk-sun-moon | Yes | Yes (Handles environmental sun/moon phase) |
| 45 | sk-image | No | No (Signal K image server) |
| 46 | signalk-frothfet-plugin | No | No (Specific battery hardware) |
| 47 | signalk-stowage-mgmt | No | No (Inventory management) |
| 48 | signalk-maintenance-tracker | No | No (Vessel logistics) |
| 49 | caveman-chartplotter | Equivalent exists | No (Alternative web-based UI) |
| 50 | signalk-web-tracker | No | No (External web tracking) |
| 51 | signalk-sailing-logbook | Equivalent exists | Yes (Marine Logbook) |
| 52 | signalk-dead-mans-switch | No | Maybe (Safety/Solo sailing) |
| 53 | @meri-imperiumi/signalk-reticulum | No | No (Networking protocol) |
| 54 | signalk-routeiq | No | Yes (Routing optimization) |
| 55 | signalk-performance-monitor | Partial | Yes (Real-time performance tracking) |
| 56 | @rhizomatics/signalk-einklabel-genai-plugin | No | No (AI/Hardware niche) |
| 57 | @meri-imperiumi/signalk-alternator-engine-on | Partial | Yes (Engine/Alternator status) |
| 58 | signalk-openwrt | No | No (Router health) |
| 59 | signalk-noon-log | Equivalent exists | Yes (Integrated Logbook) |
| 60 | @meri-imperiumi/signalk-maidenhead | No | No (Ham radio grid system) |
| 61 | signalk-ecowitt-gw2000 | Partial | Yes (Weather station data) |
| 62 | signalk-doctor | No | No (Server diagnostics) |
| 63 | signalk-updater | No | No (Software infrastructure) |
| 64 | signalk-distance-to-shore | No | Yes (Navigation safety) |
| 65 | signalk-restricted-areas | Yes | Yes (OsmAnd handles marine restricted zones) |
| 66 | signalk-vector-weather | No | Yes (Vector weather layers) |
| 67 | signalk-ships-bells | No | Maybe (Nautical bells/time) |
| 68 | signalk-tailscale | No | No (VPN/Networking) |
| 69 | signalk-piper | No | No (Text-to-speech engine) |
| 70 | signalk-whisper | No | No (Speech-to-text engine) |
| 71 | signalk-wyoming | No | No (Voice automation protocol) |
| 72 | signalk-openwakeword | No | No (Voice trigger engine) |
| 73 | signalk-checklist | No | Yes (Interactive safety checklists) |
| 74 | signalk-voice-llm | No | Maybe (AI Voice Assistant) |
| 75 | @rhizomatics/signalk-delta-squelch-plugin | No | No (Data rate optimization) |
| 76 | @mxtommy/kip | Equivalent exists | No (Alternative dashboard UI) |
| 77 | signalk-usage | No | No (Server statistics) |
| 78 | @signalk/course-provider | Partial | Yes (Active navigation paths) |
| 79 | signalk-derived-data | Partial | Yes (Server-side calculations like True Wind) |
| 80 | signalk-mareas-ihm | Equivalent exists | Yes (Tide data source) |
| 81 | @signalk/charts-plugin | Equivalent exists | Yes (Chart management) |
| 82 | @signalk/signalk-to-nmea0183 | No | No (Hardware bridge) |
| 83 | signalk-nmea2000-emitter-cannon | No | No (Hardware bridge) |
| 84 | signalk-virtual-weather-sensors | No | Yes (Calculated environmental sensors) |
| 85 | signalk-noaa-space-weather | No | Maybe (Aurora/Radio propagation) |
| 86 | signalk-ais-target-prioritizer | No | Yes (Collision avoidance priority) |
| 87 | @signalk/aisreporter | No | No (External AIS relay) |
| 88 | @signalk/vedirect-serial-usb | Partial | Yes (Victron battery/solar data) |
| 89 | @signalk/app-dock | No | No (Server UI) |
| 90 | signalk-beluga-core | No | No (Niche protocol) |
| 91 | signalk-openrouter-companion | No | No (Networking) |
| 92 | signalk-crows-nest | No | No (Server monitor) |
| 93 | signalk-ssl | No | No (Server security) |
| 94 | signalk-noaa-sonar-charts | Equivalent exists | Yes (Bathymetric charts) |
| 95 | signalk-compass-calibrator | No | No (Server-side calibration) |
| 96 | signalk-synthetic-values | No | No (Server-side processing) |
| 97 | signalk-chart-locker | No | Yes (Chart downloading utility) |
| 98 | winga-instrument-widgets | Equivalent exists | Yes (Telemetry widgets) |
| 99 | signalk-navico-routes | No | Yes (Route synchronization) |
| 100 | signalk-bms-ble | Partial | Yes (Bluetooth Battery Monitor) |
| 101 | signalk-rec-bms | Partial | Yes (REC BMS monitoring) |
| 102 | @signalk/signalk-autopilot | Partial | Yes (Autopilot control) |
| 103 | signalk-n2kais-to-nmea0183 | No | No (Hardware bridge) |
| 104 | signalk-to-stalk | No | No (Hardware bridge) |
| 105 | crowd-depth | No | Yes (Crowdsourced bathymetry) |
| 106 | signalk-logviewer | No | No (Server log access) |
| 107 | signalk-notification-player | No | Yes (Audio alarm playback) |
| 108 | signalk-daily-gpx-plugin | Equivalent exists | Yes (Track logging) |
| 109 | signalk-relay-windlass | Partial | Yes (Windlass/Anchor control) |
| 110 | signalk-gnx-display-preset-plugin | No | No (Hardware config) |
| 111 | signalk-garmin-race-timer-plugin | No | Yes (Tactical regatta timer) |
| 112 | signalk-database | No | No (Infrastructure) |
| 113 | signalk-autopilot-furuno | Partial | Yes (Furuno AP integration) |
| 114 | signalk-alpicool | Partial | Yes (Fridge/Cooler monitoring) |
| 115 | @halos-org/skip | Equivalent exists | No (Alternative UI) |
| 116 | sailkick-boat | No | No (Social networking) |
| 117 | sk-battery-supervisor | Partial | Yes (Voltage/SOC monitoring) |
| 118 | signalk-dmi | No | No (Server dashboard) |
| 119 | signalk-aisstream | Equivalent exists | Yes (AIS data) |
| 120 | signalk-onvif-camera | No | Yes (CCTV/Security camera overlay) |
| 121 | signalk-net-ais-plugin | Equivalent exists | Yes (Internet-based AIS) |
| 122 | signalk-halpi | No | No (Specific hardware) |
| 123 | signalk-engine-hours | Yes | Yes (Tracks engine runtime) |
| 124 | signalk-raspberry-pi-sx1262-rx | No | No (Hardware driver) |
| 125 | signalk-net-weather-finland | Equivalent exists | Yes (Regional weather) |
| 126 | signalk-slack-notify | No | No (External alerts) |
| 127 | signalk-raspberry-pi-rockblock9603 | No | No (Satellite hardware driver) |
| 128 | signalk-vessels-to-ais | No | No (Server bridge) |
| 129 | signalk-raspberry-pi-sx1262-tx | No | No (Hardware driver) |
| 130 | signalk-log-player | Yes | Yes (NMEA/Signal K Playback) |
| 131 | signalk-embedded-webapp-proxy | No | No (Server utility) |
| 132 | signalk-update | No | No (Maintenance) |
| 133 | signalk-entropy-saillog | Equivalent exists | Yes (Logbook) |
| 134 | signalk-h5000-websocket | No | Yes (B&G H5000 integration) |
| 135 | signalk-smhi-weather-provider | Equivalent exists | Yes (Weather source) |
| 136 | signalk-viva-weather-provider | Equivalent exists | Yes (Weather source) |
| 137 | signalk-electrodacus | Partial | Yes (BMS integration) |
| 138 | signalk-ac42-autopilot | Partial | Yes (Simrad AP integration) |
| 139 | signalk-skydancer | No | No (Niche protocol) |
| 140 | signalk-bandg-hydra-nmea0183 | No | Yes (B&G Hydra integration) |
| 141 | signalk-tlm100-config | No | No (Hardware config) |
| 142 | signalk-kontro | No | No (Controller utility) |
| 143 | @halos-org/skip-freeboard-panel | Equivalent exists | No (Alternative UI) |
| 144 | signalk-parquet | No | No (Data format) |
| 145 | signalk-units-preference | Yes | Yes (Global units management) |
| 146 | @meri-imperiumi/signalk-infodisplay | No | No (External hardware) |
| 147 | signalk-racer | No | Yes (Racing telemetry) |
| 148 | @meri-imperiumi/signalk-aprsfi-ais-reporter | No | No (External AIS reporting) |
| 149 | signalk-shelly2 | Partial | Yes (Electrical switches) |
| 150 | signalk-weather-map | No | Yes (Dynamic weather layers) |
| 151 | sk-video | No | Yes (Live camera feeds) |
| 152 | signalk-tidal-currents | Yes | Yes (Tidal current vectors) |
| 153 | signalk-fcm-notify | No | Maybe (Cloud push alerts) |
| 154 | nmea0183-to-nmea0183 | No | No (Hardware bridge) |
| 155 | @meri-imperiumi/signalk-teltonika-rutx11 | No | No (Router status) |
| 156 | @meri-imperiumi/signalk-triplogger | Yes | Yes (Track and voyage logging) |
| 157 | @meri-imperiumi/signalk-audio-notifications | No | Yes (Voice alerts for safety) |
| 158 | signalk-noaa-weather-report | Equivalent exists | Yes (Weather reports) |
| 159 | signalk-gps-heading | Partial | Yes (Calculated heading from COG) |
| 160 | signalk-hmi-designer | No | No (UI tool) |
| 161 | signalk-wetty | No | No (Web terminal) |
| 162 | @codekilo/signalk-trigger-event | No | No (Server logic) |
| 163 | @meri-imperiumi/signalk-aprs | No | Maybe (Ham radio tracking) |
| 164 | signalk-wind-calibration | No | No (Server-side calibration) |
| 165 | signalk-ais-sart-opencpn-mob-plugin | Equivalent exists | Yes (AIS SART / MOB alerts) |
| 166 | signalk-siparu | No | No (Niche) |
| 167 | signalk-anchoralarm-plugin | Equivalent exists | Yes (Anchor watch) |
| 168 | signalk-open-wind-plugin | No | Yes (OpenWind sensor integration) |
| 169 | signalk-nmea0183-logger | No | Yes (NMEA data recording) |
| 170 | signalk-windy-apiv2 | No | Yes (Windy.com API integration) |
| 171 | signalk-attitude-converter | No | No (Server processing) |
| 172 | signalk-postgsail | No | No (Analytics) |
| 173 | signalk-mob-course | Equivalent exists | Yes (MOB navigation) |
| 174 | sksim | No | No (Simulator) |
| 175 | @signalk/udp-nmea-plugin | No | No (Network bridge) |
| 176 | @signalk/resources-provider | No | Yes (Support for Waypoints/Routes API) |
| 177 | signalk-wilhelmsk-plugin | Equivalent exists | No (Alternative UI) |
| 178 | signalk-rpi-monitor | No | Maybe (System health) |
| 179 | signalk-n2k-switching-emulator | No | No (Simulator) |
| 180 | signalk-windy-plugin | No | Yes (Windy.com) |
| 181 | signalk-generic-pgn-parser | No | No (Server core) |
| 182 | signalk-buddylist-plugin | No | Maybe (Social/Ais Buddies) |
| 183 | sk-py-bno08x | No | No (Hardware driver) |
| 184 | @marineyachtradar/signalk-playback-plugin | Equivalent exists | Yes (Voyage playback) |
| 185 | signalk-n2k-displays | No | No (External hardware) |
| 186 | signalk-raspberry-pi-1wire | No | No (Hardware driver) |
| 187 | signalk-n2k-virtual-switch | Partial | Yes (Digital switching control) |
| 188 | signalk-sailsconfig | Partial | Yes (Active sail plan tracking) |
| 189 | signalk-noaa-weather | Equivalent exists | Yes (Weather data) |
| 190 | signalk-path-filter | No | No (Server core) |
| 191 | signalk-aishub-ws | Equivalent exists | Yes (AIS data) |
| 192 | signalk-autopilot-garmin | Partial | Yes (Garmin AP integration) |
| 193 | signalk-tides-api | Equivalent exists | Yes (Tide data) |
| 194 | signalk-vlm | No | No (Virtual racing integration) |
| 195 | signalk-marinetraffic-api | Equivalent exists | Yes (Global AIS data) |
| 196 | rest-provider-signalk | No | No (Server core) |
| 197 | signalk-path-mapper | No | No (Server core) |
| 198 | signalk-ntfy | No | Maybe (Push alerts) |
| 199 | signalk-n2k-switching | Partial | Yes (Switch bank control) |
| 200 | signalk-ais-navionics-converter | No | No (Chart bridge) |
| 201 | signalk-weatherflow | Partial | Yes (WeatherFlow sensors) |
| 202 | signalk-saillogger | No | No (External service) |
| 203 | signalk-tides | Yes | Yes (Tide station monitoring) |
| 204 | signalk-alarm-silencer | No | Yes (Safety management) |
| 205 | @signalk/open-meteo-provider | Equivalent exists | Yes (Weather data) |
| 206 | pypilot-autopilot-provider | Partial | Yes (Pypilot AP integration) |
| 207 | signalk-fusion-stereo | No | Maybe (Multimedia control) |
| 208 | openweather-signalk | Equivalent exists | Yes (Weather data) |
| 209 | signalk-n2k-switch-alias | No | No (Server config) |
| 210 | signalk-maretron-proprietary | No | No (Hardware bridge) |
| 211 | signalk-gps-filter | No | No (Server processing) |
| 212 | signalk-triangle-tank-calculator | No | No (Server processing) |
| 213 | signalk-services-to-signalk | No | No (Bridge) |
| 214 | signalk-appswitcher | No | No (Server UI) |
| 215 | signalk-tado-integration | No | No (Smart home integration) |
| 216 | squid-sailing-signalk | No | Yes (Advanced routing/weather) |
| 217 | signalk-netgear-lte-status | No | Maybe (Internet health) |
| 218 | signalk-yd-alarm-button | No | Yes (Hardware alarm integration) |
| 219 | signalk-spectra-plugin | No | Maybe (Watermaker status) |
| 220 | signalk-ecowitt | Partial | Yes (Weather sensors) |
| 221 | @jwallinder/windshift | Yes | Yes (Tactical wind shift tracking) |
| 222 | signalk-sealink-cloud-dev | No | No (Cloud sync) |
| 223 | signalk-sealink-cloud | No | No (Cloud sync) |
| 224 | signalk-garmin-keypad-plugin | No | No (Hardware driver) |
| 225 | signalk-avurnav | No | Yes (French nautical warnings overlay) |
| 226 | signalk-opentide | Equivalent exists | Yes (Tide data) |
| 227 | noaa-storms | No | Yes (Extreme weather alerts) |
| 228 | y2k-anchor-alarm | Equivalent exists | Yes (Anchor watch) |
| 229 | signalk-meshcore | No | No (Networking) |
| 230 | signalk-gpio-beeper-plugin | No | No (Hardware driver) |
| 231 | signalk-to-sealink-reporter | No | No (Data reporting) |
| 232 | signalk-to-nmea2000 | No | No (Bridge) |
| 233 | @canboat/visual-analyzer | No | No (Diagnostics) |
| 234 | signalk-autopilot_route | Partial | Yes (Steer-to-route control) |
| 235 | @signalk/tracks-plugin | Equivalent exists | Yes (Vessel tracks) |
| 236 | @yachteye/signalk-engineroom-plugin | Partial | Yes (Critical engine telemetry) |
| 237 | signalk-pmtiles-plugin | No | Yes (Vector chart tiles) |
| 238 | @signalk/set-system-time | No | No (Server utility) |
| 239 | sk-ais-status-plugin | Partial | Yes (AIS diagnostic data) |
| 240 | signalk-empirbusnxt-plugin | Partial | Yes (Digital switching control) |
| 241 | stingray-signalk | No | No (Niche hardware) |
| 242 | signalk-openweather-provider | Equivalent exists | Yes (Weather data) |
| 243 | signalk-meteoblue | Equivalent exists | Yes (Weather data) |
| 244 | signalk-cruisereport | No | No (Social blogging) |
| 245 | e-inkdashboardextended | No | No (External hardware) |
| 246 | signalk-flags | No | Yes (Visual signal flag reference) |
| 247 | @signalk/sailsconfiguration | Partial | Yes (Sail inventory management) |
| 248 | signalk-bandg-displaydaynight | Yes | Yes (Synchronized UI dimming) |
| 249 | aisfleet | No | Maybe (Fleet tracking) |
| 250 | signalk-pushover-plugin | No | Maybe (Push alerts) |
| 251 | signalk-windjs-plugin | No | Yes (Fluid wind visualization) |
| 252 | signalk-simple-notifications | Yes | Yes (Signal K notification display) |
| 253 | signalk-mqtt-bridge | No | No (Bridge) |
| 254 | @yachteye/signalk-timezone-plugin | Yes | Yes (Vessel-local time/timezone) |
| 255 | @essense/simulate-paths | No | No (Simulator) |
| 256 | signalk-mydata-plugin | No | No (Server core) |
| 257 | pluginsealab | No | No (Niche) |
| 258 | signalk-instrument-display-plugin | Equivalent exists | No (Alternative UI) |
| 259 | signalk-fixed-position | No | No (Server config) |
| 260 | signalk-nmea0183-to-serial | No | No (Bridge) |
| 261 | signalk-navtex-plugin | Yes | Yes (Integrated NAVTEX viewer) |
| 262 | @yachteye/signalk-position-fallback | No | No (Server logic) |
| 263 | signalk-meteogalicia-weather-provider | Equivalent exists | Yes (Weather data) |
| 264 | @codekilo/regexp-jexl-reader | No | No (Logic) |
| 265 | @yachteye/signalk-weather-plugin | Equivalent exists | Yes (Weather data) |
| 266 | pico2signalk | Partial | Yes (Simarine telemetry) |
| 267 | signalk-rpi-uptime | No | No (Host health) |
| 268 | signalk-ais-interceptor | No | No (Server logic) |
| 269 | signalk-set-gps-timezone | Yes | Yes (Timezone management) |
| 270 | collision-detector | Yes | Yes (Collision avoidance logic) |
| 271 | signalk-n2k-switching-translator | No | No (Server logic) |
| 272 | signalk-nws-alerts | No | Yes (US severe weather alerts) |
| 273 | sms-alarm | No | No (External alerts) |
| 274 | signalk-airmar-plugin | No | Yes (Airmar ultrasonic sensor data) |
| 275 | signalk-repl | No | No (Terminal) |
| 276 | signalk-shrpi-monitor | No | No (Hardware health) |
| 277 | @signalk/simulatorplugin | No | No (Simulator) |
| 278 | signalk-browser | No | No (Server UI) |
| 279 | e-inkdashboard | No | No (External hardware) |
| 280 | signalk-barometer-trend | Yes | Yes (Pressure trending) |
| 281 | signalk-marinetraffic-public | Equivalent exists | Yes (Global AIS data) |
| 282 | @signalk/calibration | No | No (Server calibration) |
| 283 | signalk-mqtt-export | No | No (Bridge) |
| 284 | signalk-mqtt-import | No | No (Bridge) |
| 285 | ais-forwarder-peafy | No | No (Bridge) |
| 286 | netais | Equivalent exists | Yes (Internet AIS) |
| 287 | @yachteye/signalk-moon-plugin | Yes | Yes (Moon telemetry) |
| 288 | signalk-speed-wind-averaging | No | Yes (Data smoothing for UI) |
| 289 | fuel-usage-calculator | Partial | Yes (Fuel/Range monitoring) |
| 290 | sk-nmea0183-vdr | No | Yes (Voyage Data Recorder) |
| 291 | @bluenav/signalk-definitions | No | No (Server core) |
| 292 | @yachteye/signalk-coastline-plugin | No | Yes (Shore distance safety) |
| 293 | msp-webhook | No | No (Bridge) |
| 294 | signalk-peplink-monitor | No | No (Router health) |
| 295 | signalk-gusts | Yes | Yes (Wind gust detection) |
| 296 | signalk-server-shutdown | No | No (Server utility) |
| 297 | signalk-tempest | Partial | Yes (Tempest weather station) |
| 298 | signalk-10axis-ros-imu | No | No (Hardware driver) |
| 299 | signalk-data-age-watchdog | Yes | Yes (Data freshness monitoring) |
| 300 | signalk-anchoralarm-headless-plugin | Equivalent exists | Yes (Anchor watch) |
| 301 | signalk-raspberry-pi-bmp180 | No | No (Hardware driver) |
| 302 | signalk-nmea0183-pmvbm-plugin | No | No (Niche hardware) |
| 303 | signalk-nmea-action-log | No | Yes (Tactical event log) |
| 304 | signalk-trim-plugin | Yes | Yes (Vessel pitch/heel) |
| 305 | @yachteye/signalk-vessel-tracker-plugin | No | No (External tracking) |
| 306 | @yachteye/signalk-makkah-plugin | No | Maybe (Qibla orientation) |
| 307 | @marinminds/signalk-notification-publisher | No | No (External relay) |
| 308 | signalk-barometer | Yes | Yes (Barometric pressure) |
| 309 | sk-plugin-sigbus-parser | No | No (Hardware driver) |
| 310 | signalk-rudder-n2k | Yes | Yes (Rudder position) |
| 311 | signalk-racing-calculator | No | Yes (Advanced racing tactics) |
| 312 | sk-depth-gauge | Yes | Yes (Depth telemetry) |
| 313 | signalk-data-dejitter | No | No (Server processing) |
| 314 | @yachteye/signalk-airlabs-plugin | No | No (Aviation data) |
| 315 | signalk-notification-to-alarm-delta | No | No (Server processing) |
| 316 | yachteye-ship2cloud-v2 | No | No (Cloud sync) |
| 317 | signalk-plugin-internet-speed | No | Maybe (Performance monitoring) |
| 318 | signalk-mqtt-openhasp | No | No (External hardware) |
| 319 | flatten-vessel-data | No | No (Server core) |
| 320 | signalk-hypermarket | No | No (Server UI) |
| 321 | signalk-detect-outliers | No | No (Server processing) |
| 322 | eventsource-sk | No | No (Server core) |
| 323 | jbd-overkill-bms-plugin | Partial | Yes (BMS monitoring) |
| 324 | @yachteye/signalk-radarcape-plugin | No | No (Aviation data) |
| 325 | current-impact | No | Yes (Impact of tides on navigation) |
| 326 | signalk-value-txt2num | No | No (Server core) |
| 327 | @yachteye/signalk-ship2cloud-plugin | No | No (Cloud sync) |
| 328 | signalk-nmea-data-recorder | No | Yes (Raw data logging) |
| 329 | signalk-activecaptain | No | Yes (ActiveCaptain points of interest) |
| 330 | @meri-imperiumi/signalk-stardate | No | No (Niche) |
| 331 | naivegpxlogger | Yes | Yes (Integrated track recording) |
| 332 | import-remote-data | No | No (Server utility) |
| 333 | signalk-cyclops-gateway | No | Yes (Rigging load monitoring) |
| 334 | signalk-fomofleet | No | No (Social networking) |
| 335 | signalk-from-batch-format | No | No (Server utility) |
| 336 | @yachteye/signalk-cloud2ship-plugin | No | No (Cloud sync) |
| 337 | signalk-avg-paths | No | No (Server processing) |
| 338 | dynamo-signalk-fleeter | No | No (Fleet management) |
| 339 | quartermaster-signalk-plugin | No | No (Stores/Inventory) |
| 340 | signalk-scientia-kraivio-dev | No | No (Niche) |
| 341 | signalk-scientia-kraivio | No | No (Niche) |
| 342 | signalk-webhook-publisher | No | No (Bridge) |
| 343 | signalk-chain-plugin | Partial | Yes (Anchor chain telemetry) |
| 344 | signalk-bandg-user-remote-rename | No | No (Hardware config) |
| 345 | signalk-bandg-zc-plugin | No | Yes (B&G Remote control support) |
| 346 | signalk-to-mongodb-atlas | No | No (Database) |
| 347 | posmv_input_plugin | No | No (Niche hardware) |
| 348 | e-inkdashboardjs | No | No (External hardware) |
| 349 | sailracer-signalk-plugin | No | Yes (SailRacer tactics integration) |
| 350 | signalk-magonis-wave-cangateway | No | Yes (Magonis boat integration) |
| 351 | signalk-charlotte | No | No (Niche) |
| 352 | signalk-pisugar | No | No (Hardware driver) |
| 353 | signalk-orca-core | No | Yes (Orca smart navigation integration) |
| 354 | @sailingrotevista/rotevista-dash | Equivalent exists | No (Alternative UI) |
| 355 | signalk-polar-performance | Yes | Yes (Integrated Polar analysis) |
| 356 | signalk-ai-bridge | No | Maybe (AI assistant integration) |
| 357 | signalk-alert-manager | Yes | Yes (Integrated alarm priority management) |
| 358 | signalk-walkthedock | No | No (Social networking) |
| 359 | signalk-nightswimming-battery-guard | Partial | Yes (Battery protection logic) |
| 360 | @talmnes/signalk-ais700-to-nmea2000 | No | No (Hardware bridge) |
| 361 | signalk-speed-wind-averaging-sliding | No | Yes (Telemetry smoothing) |
| 362 | signalk-plugin-suki-bridge | No | No (Bridge) |
| 363 | signalk-sailorwind-plugin | No | Yes (SailorWind sensor support) |
| 364 | @syseajade/signalk-tides-forked | Yes | Yes (Tidal monitoring) |
| 365 | signalk-daly-bms | Partial | Yes (BMS data integration) |
| 366 | signalk-lift-header | No | No (Niche hardware) |
| 367 | @rhizomatics/signalk-datalab-plugin | No | No (Research) |
| 368 | signalk-rpi-stats | No | No (Diagnostics) |
| 369 | signalk-x729 | No | No (Hardware driver) |
| 370 | signalk-telltale-plugin | No | Yes (Telltale electronic sensor data) |
| 371 | signalk-course-autoadvance | No | Yes (Automated route navigation) |
| 372 | signalk-viva | Equivalent exists | Yes (Weather data support) |
| 373 | signalk-weatherdock-ais-diagnostics | No | Yes (AIS hardware health) |
| 374 | signalk-clickhouse-history-bridge | No | No (Database) |
| 375 | tack-now | No | Yes (Racing countdown/tactics) |
| 376 | @welytics/clearship-signalk | No | No (Commercial management) |
| 377 | signalk-vaarweginformatie-blocked | No | Yes (Waterway closure alerts) |
| 378 | signalk-bluetooth-scanner | No | No (Bridge) |
| 379 | signalk-forward-watch | No | Yes (Hazard detection/look-ahead) |
| 380 | signalk-mqtt-sensors | No | Yes (Wireless sensor integration) |
| 381 | signalk-fixedstation | No | No (Server config) |
| 382 | signalk-multiplex-viewer | No | No (Diagnostics) |
| 383 | signalk-n2k-server | No | No (Server core) |
| 384 | @codekilo/signalk-iso19848 | No | No (Standardization) |
| 385 | signalk-mqtt-gw | No | No (Bridge) |
| 386 | signalk-shelly | Partial | Yes (Electrical/Smart switch control) |
