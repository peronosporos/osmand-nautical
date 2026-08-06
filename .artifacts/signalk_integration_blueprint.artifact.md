# Signal K Integration Blueprint for OsmAnd Nautical

This document outlines the master integration plan for Signal K plugins into OsmAnd Nautical. Items have been filtered and prioritized based on their relevance to core navigation, instrument support, and safety.

## Table 1: High Priority (Core Integration)
Focus: Performance, routing, weather, charts, and autopilot.

| Name | Description | Integration Method |
| :--- | :--- | :--- |
| @signalk/charts-plugin | Core chart management service. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| @signalk/course-provider | Active navigation path API. | `SignalKRestService` API call / `SignalKEngine` path subscription |
| @signalk/open-meteo-provider | Open-Meteo weather data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| @signalk/sailsconfiguration | Sail inventory and plan management. | `SignalKRestService` API call |
| @signalk/signalk-autopilot | Core autopilot control logic. | `CapabilityManager` detection / `SignalKRestService` API call |
| @sailingnaturali/signalk-currents | Tidal current predictions and mapping. | `NauticalMapLayer` extension / `SignalKEngine` path subscription |
| @yachteye/signalk-weather-plugin | General weather data integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| @jwallinder/windshift | Tactical wind shift tracking. | `SignalKEngine` path subscription / New `WidgetType` |
| advancedwind | Advanced wind speed and angle calculations. | `SignalKEngine` path subscription / `MarineTextWidget` |
| current-impact | Navigational impact of tides/currents. | `SignalKEngine` path subscription |
| openweather-signalk | OpenWeatherMap integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| pypilot-autopilot-provider | Pypilot open autopilot integration. | `SignalKRestService` API call |
| sailracer-signalk-plugin | SailRacer tactical data integration. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-ac42-autopilot | Simrad AC42 autopilot integration. | `SignalKRestService` API call |
| signalk-autopilot-furuno | Furuno AP integration bridge. | `SignalKRestService` API call |
| signalk-autopilot-garmin | Garmin autopilot integration. | `SignalKRestService` API call |
| signalk-autopilot_route | Steer-to-route autopilot control. | `SignalKRestService` API call |
| signalk-bandg-hydra-nmea0183 | B&G Hydra hardware bridge. | `SignalKEngine` path subscription |
| signalk-chart-locker | Chart download manager. | `SignalKRestService` API call |
| signalk-charts-provider-simple | Simple chart provider for Signal K server. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-course-autoadvance | Automated route point navigation. | `SignalKRestService` API call |
| signalk-derived-data | Derived nautical calculations. | `SignalKEngine` path subscription |
| signalk-garmin-race-timer-plugin | Tactical regatta timer. | New `WidgetType` |
| signalk-gps-heading | Calculated heading from COG. | `SignalKEngine` path subscription |
| signalk-grib-downloader | GRIB file fetching and management. | `SignalKRestService` API call |
| signalk-grib-weather-provider | GRIB weather data provider for Signal K. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-h5000-websocket | B&G H5000 hardware integration. | `SignalKEngine` path subscription |
| signalk-meteoblue | Meteoblue weather data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-meteogalicia-... | MeteoGalicia weather data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-navico-autopilot-bridge | Simrad/B&G autopilot control bridge. | `CapabilityManager` detection / `SignalKRestService` API call |
| signalk-navico-routes | Navico/B&G route sync. | `SignalKRestService` API call |
| signalk-net-weather-finland | Regional Finnish weather source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-noaa-sonar-charts | Bathymetric sonar chart source. | `NauticalMapLayer` extension |
| signalk-noaa-weather | NOAA weather data integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-noaa-weather-report | NOAA weather report integration. | `SignalKRestService` API call |
| signalk-open-wind-plugin | OpenWind sensor integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-openweather-provider | OpenWeather data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-orca-core | Orca smart navigation integration. | `CapabilityManager` detection / `SignalKRestService` API call |
| signalk-performance-monitor | Real-time performance tracking. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-pmtiles-plugin | Support for PMTiles vector charts. | `NauticalMapLayer` extension |
| signalk-polar-performance | Integrated vessel polar analysis. | `SignalKRestService` API call / New `WidgetType` |
| signalk-polar-performance-plugin | Polar performance calculation engine. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-racer | Regatta and racing telemetry. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-rainviewer-charts | Real-time precipitation radar overlay. | `NauticalMapLayer` extension |
| signalk-resources-provider | Support for Waypoints/Routes API. | `SignalKRestService` API call |
| signalk-routeiq | Advanced routing optimization. | `SignalKRestService` API call |
| signalk-sailsconfig | Active sail plan tracking. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-smhi-weather-provider | SMHI weather data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-speed-wind-averaging | Data smoothing and averaging for UI. | `SignalKEngine` path subscription |
| signalk-speed-wind-averaging-sliding | Telemetry smoothing and filtering. | `SignalKEngine` path subscription |
| signalk-tidal-currents | Tidal current vector visualization. | `NauticalMapLayer` extension / `SignalKEngine` path subscription |
| signalk-vector-weather | Vector-based weather layers. | `NauticalMapLayer` extension |
| signalk-viva | Weather data integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-viva-weather-provider | Viva weather data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-weather-map | Dynamic weather layer overlay. | `NauticalMapLayer` extension |
| signalk-windjs-plugin | Fluid wind particle visualization. | `NauticalMapLayer` extension |
| signalk-windy-apiv2 | Windy.com API integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-windy-plugin | Windy.com weather integration. | `NauticalMapLayer` extension |
| speedandcurrent | STW and Current vector calculations. | `SignalKEngine` path subscription / `MarineTextWidget` |
| squid-sailing-signalk | Advanced sailing routing/weather. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| tack-now | Tactical racing countdown and tools. | New `WidgetType` |

## Table 2: Instrument Support (UI/Widgets)
Focus: Specific sensors, electrical, engines, and specialized hardware.

| Name | Description | Integration Method |
| :--- | :--- | :--- |
| @meri-imperiumi/signalk-alternator-engine-on | Alternator/Engine status monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| @meri-imperiumi/signalk-aprs | Ham radio tracking integration. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| @meri-imperiumi/signalk-meshtastic | LoRa-based telemetry and messaging. | `SignalKEngine` path subscription |
| @rhizomatics/signalk-bluetti-plugin | Battery and Solar monitor integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| @signalk/vedirect-serial-usb | Victron battery/solar data. | `SignalKEngine` path subscription / `MarineTextWidget` |
| @syseajade/signalk-tides-forked | Tidal monitoring and predictions. | `SignalKRestService` API call |
| @yachteye/signalk-engineroom-plugin | Critical engine telemetry monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| @yachteye/signalk-moon-plugin | Lunar phase and telemetry integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| @yachteye/signalk-timezone-plugin | Vessel-local timezone management. | `SignalKEngine` path subscription |
| fuel-usage-calculator | Fuel consumption and range monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| jbd-overkill-bms-plugin | JBD/Overkill BMS monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| pico2signalk | Simarine Pico telemetry integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| sk-battery-supervisor | Battery voltage/SOC monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| sk-depth-gauge | Water depth telemetry and HUD. | `SignalKEngine` path subscription / `MarineTextWidget` |
| sk-video | Live camera feed integration. | New `WidgetType` |
| signalk-activecaptain | ActiveCaptain POI integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-ai-bridge | AI assistant and LLM integration. | `SignalKRestService` API call |
| signalk-airmar-plugin | Airmar ultrasonic sensor data support. | `SignalKEngine` path subscription |
| signalk-alpicool | Fridge/Cooler monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-bandg-displaydaynight | Synchronized night-mode dimming. | `CapabilityManager` detection |
| signalk-bandg-zc-plugin | B&G remote control hardware support. | `CapabilityManager` detection |
| signalk-barometer | Barometric pressure telemetry. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-barometer-trend | Barometric pressure trend analysis. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-basic-tide-widgets | Simple tide telemetry widgets. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-bms-ble | Bluetooth Battery Monitor. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-brineomatic-plugin | Specialized watermaker sensor integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-chain-plugin | Anchor chain counter telemetry. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-cyclops-gateway | Cyclops rigging load monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-daly-bms | Daly BMS hardware integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-ecowitt | Ecowitt weather sensor data. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-ecowitt-gw2000 | Ecowitt weather station gateway. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-electrodacus | Electrodacus BMS integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-empirbusnxt-plugin | EmpirBus digital switching control. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-engine-hours | Tracks engine runtime metrics. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-flags | Visual nautical signal flag guide. | New `WidgetType` |
| signalk-fusion-stereo | Fusion multimedia control. | New `WidgetType` / `SignalKRestService` API call |
| signalk-instrument-widgets | Telemetry and dashboard widgets. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-magonis-wave-cangateway | Magonis electric boat integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-mareas-ihm | Tide data source integration. | `SignalKRestService` API call |
| signalk-mqtt-sensors | Wireless sensor network integration. | `SignalKEngine` path subscription |
| signalk-n2k-switching | Digital switch bank control. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-n2k-virtual-switch | Digital switching control. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-netgear-lte-status | LTE router health monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-onvif-camera | CCTV camera overlay support. | New `WidgetType` / `SignalKRestService` API call |
| signalk-opentide | OpenTide data source integration. | `SignalKRestService` API call |
| signalk-poi-search | Search for nautical points of interest. | `SignalKRestService` API call |
| signalk-rec-bms | REC BMS monitoring system. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-relay-windlass | Windlass/Anchor control bridge. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-rudder-n2k | Rudder position telemetry. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-sailorwind-plugin | SailorWind wireless sensor support. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-sailsense | Wireless sail sensor integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-set-gps-timezone | Automated timezone management via GPS. | `SignalKEngine` path subscription |
| signalk-shelly | Shelly smart switch and power control. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-shelly2 | Shelly electrical switch integration. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-ships-bells | Traditional nautical time bells. | `NauticalAudioArbiter` |
| signalk-simple-notifications | Notification display and management. | `NauticalNotificationManager` |
| signalk-spectra-plugin | Spectra watermaker status monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-sun-moon | Sun and Moon phase telemetry. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-telltale-plugin | Electronic telltale sensor data. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-tempest | Tempest weather station integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-tides | Tide station monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-tides-api | Tidal data and prediction API. | `SignalKRestService` API call |
| signalk-trim-plugin | Vessel pitch and heel monitoring. | `SignalKEngine` path subscription / `MarineTextWidget` |
| signalk-units-preference | Global units management. | `CapabilityManager` detection |
| signalk-virtual-weather-sensors | Virtual environment sensors. | `SignalKEngine` path subscription |
| signalk-voice-llm | AI Voice Assistant integration. | `NauticalAudioArbiter` / `SignalKRestService` API call |
| signalk-watch-schedule | Crew watchkeeping management. | New `WidgetType` |
| signalk-weatherflow | WeatherFlow sensor integration. | `SignalKEngine` path subscription / `MarineTextWidget` |
| winga-instrument-widgets | Marine telemetry widgets. | `SignalKEngine` path subscription / New `WidgetType` |

## Table 3: Safety & Alarms
Focus: MOB, AIS SART, collision avoidance, and navigational warnings.

| Name | Description | Integration Method |
| :--- | :--- | :--- |
| @meri-imperiumi/signalk-logbook | Automated marine logbook system. | `SignalKRestService` API call |
| @meri-imperiumi/signalk-mob-notifier | Man Overboard notification system. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| @meri-imperiumi/signalk-triplogger | Voyage and track logging system. | `SignalKRestService` API call |
| @meri-imperiumi/signalk-voice-alerts-safety | Voice alerts for safety events. | `NauticalAudioArbiter` |
| @marineyachtradar/signalk-plugin | Marine radar data and overlay support. | `NauticalMapLayer` extension |
| @marineyachtradar/... | Historical voyage playback. | `SignalKRestService` API call |
| @sailingnaturali/signalk-ais-distress | AIS SART and distress alerts. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| @sailingnaturali/signalk-dsc | VHF/AIS DSC message support. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| @sailingnaturali/signalk-journey-replay | NMEA/Signal K voyage playback. | `SignalKRestService` API call |
| @signalk/tracks-plugin | Automated vessel track management. | `SignalKRestService` API call |
| @yachteye/signalk-coastline-plugin | Proximity-to-shore safety monitoring. | `SignalKEngine` path subscription / `NauticalNotificationManager` |
| aisfleet | Commercial fleet tracking. | `NauticalMapLayer` extension |
| collision-detector | Collision avoidance and risk analysis. | `SignalKEngine` path subscription / `NauticalNotificationManager` |
| crowd-depth | Crowdsourced bathymetry data. | `NauticalMapLayer` extension |
| hoekens-anchor-alarm | Anchor drift monitoring and alerts. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| naivegpxlogger | Integrated GPX track recording. | `SignalKRestService` API call |
| netais | Internet-based AIS target source. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| noaa-storms | Severe storm and weather alerts. | `NauticalNotificationManager` / `NauticalMapLayer` extension |
| sk-ais-status-plugin | AIS hardware diagnostics. | `SignalKEngine` path subscription / `MarineTextWidget` |
| sk-nmea0183-vdr | Voyage Data Recorder (Blackbox). | `SignalKRestService` API call |
| signalk-aishub-ws | Global AIS data integration. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| signalk-ais-sart-... | AIS SART and MOB alert system. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| signalk-ais-target-prioritizer | Collision avoidance priority logic. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| signalk-aisstream | AIS data stream integration. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| signalk-alarm-silencer | Safety alarm management. | `NauticalNotificationManager` |
| signalk-alert-manager | Integrated nautical alarm management. | `NauticalNotificationManager` |
| signalk-anchoralarm-headless-plugin | Headless anchor watch logic. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| signalk-anchoralarm-plugin | Anchor drift watch system. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| signalk-avurnav | French nautical warning overlay. | `NauticalMapLayer` extension |
| signalk-buddylist-plugin | Social AIS buddy tracking. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| signalk-checklist | Interactive safety checklists. | New `WidgetType` |
| signalk-daily-gpx-plugin | Automated track logging. | `SignalKRestService` API call |
| signalk-data-age-watchdog | Data freshness and latency monitoring. | `NauticalNotificationManager` |
| signalk-dead-mans-switch | Solo sailing safety watchdog. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
| signalk-distance-to-shore | Navigational distance to coastline. | `SignalKEngine` path subscription / `NauticalNotificationManager` |
| signalk-entropy-saillog | Nautical logbook integration. | `SignalKRestService` API call |
| signalk-forward-watch | Forward-looking hazard detection. | `NauticalMapLayer` extension / `NauticalNotificationManager` |
| signalk-gusts | Real-time wind gust detection. | `SignalKEngine` path subscription / `NauticalNotificationManager` |
| signalk-log-player | Voyage data playback tool. | `SignalKRestService` API call |
| signalk-marinetraffic-api | MarineTraffic AIS integration. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-marinetraffic-public | Public MarineTraffic data source. | `SignalKRestService` API call / `NauticalMapLayer` extension |
| signalk-mob-course | MOB navigational assistance. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| signalk-navtex-plugin | Integrated NAVTEX message viewer. | New `WidgetType` / `SignalKRestService` API call |
| signalk-net-ais-plugin | Internet-based AIS source. | `SignalKEngine` path subscription / `NauticalMapLayer` extension |
| signalk-nightswimming-... | Battery protection and guard logic. | `NauticalNotificationManager` |
| signalk-nmea-action-log | Event-based tactical nautical log. | `SignalKRestService` API call |
| signalk-nmea-data-recorder | Raw data recording and logging. | `SignalKRestService` API call |
| signalk-nmea0183-logger | Raw NMEA data recording. | `SignalKRestService` API call |
| signalk-noon-log | Daily vessel log entries. | `SignalKRestService` API call |
| signalk-notification-player | Audio alarm playback system. | `NauticalAudioArbiter` |
| signalk-nws-alerts | US National Weather Service alerts. | `NauticalNotificationManager` / `NauticalMapLayer` extension |
| signalk-racing-calculator | Advanced tactical racing metrics. | `SignalKEngine` path subscription / New `WidgetType` |
| signalk-restricted-areas | Marine restricted zone alerts. | `NauticalMapLayer` extension / `NauticalNotificationManager` |
| signalk-sailing-logbook | Marine logbook integration. | `SignalKRestService` API call |
| signalk-vaarweginformatie-blocked | Waterway closure and hazard alerts. | `NauticalMapLayer` extension / `NauticalNotificationManager` |
| signalk-weatherdock-ais-diagnostics | AIS hardware health monitoring. | `SignalKEngine` path subscription |
| signalk-yd-alarm-button | Yacht Devices alarm button integration. | `CapabilityManager` detection / `NauticalNotificationManager` |
| y2k-anchor-alarm | Anchor drift watch system. | `NauticalNotificationManager` / `NauticalAudioArbiter` |
