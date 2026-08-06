# Signal K Plugins List - Part 1 (1-130)

| Name | Description | Integration | Role/Reason |
| :--- | :--- | :--- | :--- |
| @signalk/freeboard-sk | Web-based chartplotter interface for Signal K. | No | OsmAnd is a standalone chartplotter UI |
| signalk-edge-link | Bridge for connecting Signal K to Edge devices. | No | Protocol/bridge utility |
| signalk-charts-provider-simple | Simple chart provider for Signal K server. | Yes | OsmAnd handles RASTER/ENC charts |
| advancedwind | Advanced wind speed and angle calculations. | Yes | OsmAnd handles AWA/AWS/TWA/TWS/TWD |
| speedandcurrent | STW and Current vector calculations. | Yes | OsmAnd handles STW/SOG/Set/Drift |
| @noforeignland/signalk-to-noforeignland | Social sailing service synchronization. | No | External social service sync |
| signalk-attitude-calibrator | IMU and attitude calibration tool. | No | Server-side calibration tool |
| @meri-imperiumi/signalk-mob-notifier | Man Overboard notification system. | Yes | OsmAnd has a dedicated MOB system |
| signalk-brineomatic-plugin | Specialized watermaker sensor integration. | Maybe | Specialized watermaker sensor |
| @marineyachtradar/signalk-plugin | Marine radar data and overlay support. | Yes | Radar overlay support |
| @meri-imperiumi/signalk-adsb | Aviation ADSB traffic data integration. | No | Aviation data |
| @meri-imperiumi/signalk-value-combiner | Server-side data processing logic. | No | Server-side data processing |
| signalk-polar-performance-plugin | Polar performance calculation engine. | Yes | OsmAnd has internal Polar performance engine |
| @meri-imperiumi/signalk-autostate | Automated vessel state logic. | No | Server-side state logic |
| @meri-imperiumi/signalk-logbook | Automated marine logbook system. | Yes | OsmAnd has an automated Marine Logbook |
| @meri-imperiumi/signalk-meshtastic | LoRa-based telemetry and messaging. | Maybe | Long-range LoRa telemetry |
| hoekens-anchor-alarm | Anchor drift monitoring and alerts. | Yes | OsmAnd has Anchor Drift Watchdog |
| signalk-prometheus-exporter-macjl | IT infrastructure metrics exporter. | No | IT monitoring metrics |
| signalk-container | Docker/Host infrastructure management. | No | Host infrastructure |
| signalk-questdb | High-performance database storage. | No | Database storage |
| signalk-grafana | External data visualization bridge. | No | External visualization |
| signalk-backup | Server configuration backup utility. | No | Maintenance utility |
| signalk-fallback | Server-side data fallback logic. | No | Server logic |
| @sailingnaturali/signalk-currents | Tidal current predictions and mapping. | Yes | OsmAnd handles Tidal Currents |
| signalk-grib-weather-provider | GRIB weather data provider for Signal K. | Yes | OsmAnd has GRIB weather module |
| @sailingnaturali/signalk-depth-offsets | Transducer depth calibration utility. | No | Server-side calibration |
| signalk-grib-downloader | GRIB file fetching and management. | Yes | OsmAnd has built-in GRIB downloader |
| @sailingnaturali/signalk-dsc | VHF/AIS DSC message support. | Yes | VHF/AIS DSC support |
| @rhizomatics/signalk-bluetti-plugin | Battery and Solar monitor integration. | Yes | OsmAnd monitors Batteries/Solar |
| @sailingnaturali/signalk-journey-replay | NMEA/Signal K voyage playback. | Yes | OsmAnd has NMEA/Signal K Replay |
| @sailingnaturali/signalk-ntfy-relay | External notification relay service. | Maybe | External notifications |
| signalk-symbol-manager | UI icon and symbol management. | No | Server-side UI utility |
| signalk-instrument-widgets | Telemetry and dashboard widgets. | Yes | OsmAnd has extensive Marine Widgets |
| signalk-poi-search | Search for nautical points of interest. | Yes | Specific Nautical POI search |
| signalk-navico-autopilot-bridge | Simrad/B&G autopilot control bridge. | Yes | OsmAnd has Autopilot controller |
| @sailingnaturali/signalk-equipment-registry | Vessel equipment inventory management. | No | Inventory management |
| signalk-navico-embedder | NMEA 2000 hardware utility. | No | NMEA 2000 hardware utility |
| signalk-rainviewer-charts | Real-time precipitation radar overlay. | Yes | Real-time precipitation radar overlay |
| signalk-sailsense | Wireless sail sensor integration. | Maybe | Wireless sail sensors |
| @rhizomatics/signalk-einklabel-plugin | External E-Ink display driver. | No | External display hardware |
| signalk-watch-schedule | Crew watchkeeping management. | Maybe | Watchkeeping management |
| @sailingnaturali/signalk-ais-distress | AIS SART and distress alerts. | Yes | AIS SART/Distress alerts |
| signalk-basic-tide-widgets | Simple tide telemetry widgets. | Yes | Tide telemetry widgets |
| signalk-sun-moon | Sun and Moon phase telemetry. | Yes | Handles environmental sun/moon phase |
| sk-image | Signal K image server bridge. | No | Signal K image server |
| signalk-frothfet-plugin | Specific battery hardware driver. | No | Specific battery hardware |
| signalk-stowage-mgmt | Onboard inventory and stowage. | No | Inventory management |
| signalk-maintenance-tracker | Vessel maintenance logistics. | No | Vessel logistics |
| caveman-chartplotter | Web-based alternative UI. | No | Alternative web-based UI |
| signalk-web-tracker | External web-based tracking. | No | External web tracking |
| signalk-sailing-logbook | Marine logbook integration. | Yes | Marine Logbook |
| signalk-dead-mans-switch | Solo sailing safety watchdog. | Maybe | Safety/Solo sailing |
| @meri-imperiumi/signalk-reticulum | Networking protocol bridge. | No | Networking protocol |
| signalk-routeiq | Advanced routing optimization. | Yes | Routing optimization |
| signalk-performance-monitor | Real-time performance tracking. | Yes | Real-time performance tracking |
| @rhizomatics/signalk-einklabel-genai-plugin | Experimental AI/E-Ink utility. | No | AI/Hardware niche |
| @meri-imperiumi/signalk-alternator-engine-on | Alternator/Engine status monitoring. | Yes | Engine/Alternator status |
| signalk-openwrt | OpenWrt router health metrics. | No | Router health |
| signalk-noon-log | Daily vessel log entries. | Yes | Integrated Logbook |
| @meri-imperiumi/signalk-maidenhead | Ham radio grid system support. | No | Ham radio grid system |
| signalk-ecowitt-gw2000 | Ecowitt weather station gateway. | Yes | Weather station data |
| signalk-doctor | Server diagnostics and health. | No | Server diagnostics |
| signalk-updater | Software update management. | No | Software infrastructure |
| signalk-distance-to-shore | Navigational distance to coastline. | Yes | Navigation safety |
| signalk-restricted-areas | Marine restricted zone alerts. | Yes | OsmAnd handles marine restricted zones |
| signalk-vector-weather | Vector-based weather layers. | Yes | Vector weather layers |
| signalk-ships-bells | Traditional nautical time bells. | Maybe | Nautical bells/time |
| signalk-tailscale | VPN/Networking bridge. | No | VPN/Networking |
| signalk-piper | Text-to-speech engine bridge. | No | Text-to-speech engine |
| signalk-whisper | Speech-to-text engine bridge. | No | Speech-to-text engine |
| signalk-wyoming | Voice automation protocol. | No | Voice automation protocol |
| signalk-openwakeword | Voice trigger detection. | No | Voice trigger engine |
| signalk-checklist | Interactive safety checklists. | Yes | Interactive safety checklists |
| signalk-voice-llm | AI Voice Assistant integration. | Maybe | AI Voice Assistant |
| @rhizomatics/signalk-delta-squelch-plugin | Data rate optimization. | No | Data rate optimization |
| @mxtommy/kip | Alternative dashboard UI. | No | Alternative dashboard UI |
| signalk-usage | Server resource statistics. | No | Server statistics |
| @signalk/course-provider | Active navigation path API. | Yes | Active navigation paths |
| signalk-derived-data | Derived nautical calculations. | Yes | Server-side calculations like True Wind |
| signalk-mareas-ihm | Tide data source integration. | Yes | Tide data source |
| @signalk/charts-plugin | Core chart management service. | Yes | Chart management |
| @signalk/signalk-to-nmea0183 | Hardware bridge to NMEA 0183. | No | Hardware bridge |
| signalk-nmea2000-emitter-cannon | Hardware bridge to NMEA 2000. | No | Hardware bridge |
| signalk-virtual-weather-sensors | Virtual environment sensors. | Yes | Calculated environmental sensors |
| signalk-noaa-space-weather | Space weather and aurora data. | Maybe | Aurora/Radio propagation |
| signalk-ais-target-prioritizer | Collision avoidance priority logic. | Yes | Collision avoidance priority |
| @signalk/aisreporter | External AIS relay service. | No | External AIS relay |
| @signalk/vedirect-serial-usb | Victron battery/solar data. | Yes | Victron battery/solar data |
| @signalk/app-dock | Server application UI manager. | No | Server UI |
| signalk-beluga-core | Specialized marine protocol. | No | Niche protocol |
| signalk-openrouter-companion | Networking router utility. | No | Networking |
| signalk-crows-nest | Server-side monitor utility. | No | Server monitor |
| signalk-ssl | Server security management. | No | Server security |
| signalk-noaa-sonar-charts | Bathymetric sonar chart source. | Yes | Bathymetric charts |
| signalk-compass-calibrator | Heading sensor calibration tool. | No | Server-side calibration |
| signalk-synthetic-values | Advanced data synthesis logic. | No | Server-side processing |
| signalk-chart-locker | Chart download manager. | Yes | Chart downloading utility |
| winga-instrument-widgets | Marine telemetry widgets. | Yes | Telemetry widgets |
| signalk-navico-routes | Navico/B&G route sync. | Yes | Route synchronization |
| signalk-bms-ble | Bluetooth Battery Monitor. | Yes | Bluetooth Battery Monitor |
| signalk-rec-bms | REC BMS monitoring system. | Yes | REC BMS monitoring |
| @signalk/signalk-autopilot | Core autopilot control logic. | Yes | Autopilot control |
| signalk-n2kais-to-nmea0183 | AIS data bridge to NMEA 0183. | No | Hardware bridge |
| signalk-to-stalk | SeaTalk hardware bridge. | No | Hardware bridge |
| crowd-depth | Crowdsourced bathymetry data. | Yes | Crowdsourced bathymetry |
| signalk-logviewer | Server log access utility. | No | Server log access |
| signalk-notification-player | Audio alarm playback system. | Yes | Audio alarm playback |
| signalk-daily-gpx-plugin | Automated track logging. | Yes | Track logging |
| signalk-relay-windlass | Windlass/Anchor control bridge. | Yes | Windlass/Anchor control |
| signalk-gnx-display-preset-plugin | Hardware display presets. | No | Hardware config |
| signalk-garmin-race-timer-plugin | Tactical regatta timer. | Yes | Tactical regatta timer |
| signalk-database | Data storage infrastructure. | No | Infrastructure |
| signalk-autopilot-furuno | Furuno AP integration bridge. | Yes | Furuno AP integration |
| signalk-alpicool | Fridge/Cooler monitoring. | Yes | Fridge/Cooler monitoring |
| @halos-org/skip | Alternative dashboard UI. | No | Alternative UI |
| sailkick-boat | Social networking for sailors. | No | Social networking |
| sk-battery-supervisor | Battery voltage/SOC monitoring. | Yes | Voltage/SOC monitoring |
| signalk-dmi | Server dashboard UI. | No | Server dashboard |
| signalk-aisstream | AIS data stream integration. | Yes | AIS data |
| signalk-onvif-camera | CCTV camera overlay support. | Yes | CCTV/Security camera overlay |
| signalk-net-ais-plugin | Internet-based AIS source. | Yes | Internet-based AIS |
| signalk-halpi | Hardware-specific integration. | No | Specific hardware |
| signalk-engine-hours | Tracks engine runtime metrics. | Yes | Tracks engine runtime |
| signalk-raspberry-pi-sx1262-rx | Hardware driver for LoRa. | No | Hardware driver |
| signalk-net-weather-finland | Regional Finnish weather source. | Yes | Regional weather |
| signalk-slack-notify | External Slack notifications. | No | External alerts |
| signalk-raspberry-pi-rockblock9603 | Satellite hardware driver. | No | Satellite hardware driver |
| signalk-vessels-to-ais | Server-side vessel bridge. | No | Server bridge |
| signalk-raspberry-pi-sx1262-tx | Hardware driver for LoRa. | No | Hardware driver |
| signalk-log-player | Voyage data playback tool. | Yes | NMEA/Signal K Playback |
