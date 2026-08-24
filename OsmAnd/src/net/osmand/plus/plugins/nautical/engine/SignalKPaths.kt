package net.osmand.plus.plugins.nautical.engine

object SignalKPaths {
    // Navigation
    const val NAV_POSITION = "navigation.position"
    const val NAV_HEADING_TRUE = "navigation.headingTrue"
    const val NAV_HEADING_MAG = "navigation.headingMagnetic"
    const val NAV_MAG_VARIATION = "navigation.magneticVariation"
    const val NAV_SPEED_OVER_GROUND = "navigation.speedOverGround"
    const val NAV_SPEED_THROUGH_WATER = "navigation.speedThroughWater"
    const val NAV_COURSE_OVER_GROUND = "navigation.courseOverGroundTrue"
    const val NAV_RATE_OF_TURN = "navigation.rateOfTurn"
    const val NAV_ATTITUDE = "navigation.attitude"
    const val NAV_CLOSEST_APPROACH = "navigation.closestApproach"
    const val NAV_LOG = "navigation.log"
    const val NAV_TRIP_LOG = "navigation.trip.log"
    const val NAV_DRIFT = "navigation.drift"
    const val NAV_SET_TRUE = "navigation.setTrue"
    const val NAV_XTE = "navigation.crossTrackError"
    const val NAV_XTE_RHUMB = "navigation.courseRhumbline.crossTrackError"
    const val NAV_XTE_GC = "navigation.courseGreatCircle.crossTrackError"
    const val NAV_COURSE_NEXT_POINT = "navigation.courseGreatCircle.nextPoint"
    const val AIS_THREAT_LEVEL = "sensors.ais.threatLevel"
    const val NAV_TTW = "navigation.timeToWaypoint"
    const val NAV_DTW = "navigation.distanceToWaypoint"
    const val NAV_TWD = "navigation.trueWindDirection"
    const val NAV_STATE = "navigation.state"
    const val NAV_FLAGS = "navigation.state.flags"
    const val NAV_DESTINATION = "navigation.destination.commonName"
    const val NAV_COURSE_RHUMB_LINE_NEXT_POINT_BEARING = "navigation.courseRhumbline.nextPoint.bearingTrue"
    const val NAV_COURSE_RHUMB_LINE_NEXT_POINT_DISTANCE = "navigation.courseRhumbline.nextPoint.distance"
    const val NAV_GNSS_PREFIX = "navigation.gnss."
    const val NAV_ANCHOR_PREFIX = "navigation.anchor."
    const val NAV_ANCHOR_RODE_DEPLOYED = "navigation.anchor.rodeDeployed"
    const val NAV_ANCHOR_MAX_RADIUS = "navigation.anchor.maxRadius"
    const val NAV_ANCHOR_RODE_LENGTH = "navigation.anchor.rodeLength"
    const val NAV_DATETIME_MOON_PHASE = "navigation.datetime.moonPhase"
    const val NAV_DATETIME_MOON_ILLUMINATION = "navigation.datetime.moonIllumination"
    const val NAV_DATETIME_SUNRISE = "navigation.datetime.sunrise"
    const val NAV_DATETIME_SUNSET = "navigation.datetime.sunset"
    const val NAV_CALLSIGN = "navigation.callsign"
    const val NAV_AIS_BUDDIES = "communication.aisBuddies"
    const val NAME = "name"
    const val FLAG = "flag"
    const val PORT = "port"
    const val UUID = "uuid"

    // Environment
    const val ENV_DEPTH_BELOW_TRANSDUCER = "environment.depth.belowTransducer"
    const val ENV_DEPTH_BELOW_KEEL = "environment.depth.belowKeel"
    const val ENV_DEPTH_SURFACE_TO_TRANSDUCER = "environment.depth.surfaceToTransducer"
    const val ENV_WATER_TEMP = "environment.water.temperature"
    const val ENV_OUTSIDE_TEMP = "environment.outside.temperature"
    const val ENV_OUTSIDE_PRESSURE = "environment.outside.pressure"
    const val ENV_OUTSIDE_HUMIDITY = "environment.outside.relativeHumidity"
    const val ENV_OUTSIDE_ILLUMINANCE = "environment.outside.illuminance"
    const val ENV_WIND_SPEED_TRUE = "environment.wind.speedTrue"
    const val ENV_WIND_DIRECTION_TRUE = "environment.wind.directionTrue"
    const val ENV_WIND_ANGLE_APPARENT = "environment.wind.angleApparent"
    const val ENV_WIND_SPEED_APPARENT = "environment.wind.speedApparent"
    const val ENV_WIND_ANGLE_TRUE = "environment.wind.angleTrue"
    const val ENV_MOON_PHASE = "environment.moon.phase"
    const val ENV_WATER_SALINITY = "environment.water.salinity"
    const val ENV_AIR_DEW_POINT = "environment.air.dewPoint"
    const val ENV_SUNLIGHT_MODE = "environment.sunlight.mode"
    const val FORWARD_WATCH_DETECTIONS = "environment.forwardWatch.detections"
    const val ENV_TIDE_PREFIX = "environment.tide."
    const val ENV_TIDE_HEIGHT = "environment.tide.heightNow"
    const val ENV_CURRENT_PREFIX = "environment.current."

    // Performance
    const val PERF_VMG = "performance.velocityMadeGood"
    const val PERF_POLAR_RATIO = "performance.polarSpeedRatio"
    const val PERF_TARGET_SPEED = "performance.targetSpeed"
    const val PERF_TARGET_ANGLE = "performance.targetAngle"
    const val PERF_TACK_ANGLE = "performance.tackAngle"
    const val PERF_WIND_SHIFT = "performance.windShift"
    const val PERF_LAYLINES = "performance.laylines"
    const val PERF_RACING_TIMER = "performance.racing.timer"

    // Steering & Autopilot
    const val STEERING_AUTOPILOT_STATE = "steering.autopilot.state"
    const val STEERING_AUTOPILOT_SEA_STATE = "steering.autopilot.seaState"
    const val STEERING_AUTOPILOT_TARGET_HDG_TRUE = "steering.autopilot.target.headingTrue"
    const val STEERING_AUTOPILOT_TARGET_HDG_MAG = "steering.autopilot.target.headingMagnetic"
    const val STEERING_AUTOPILOT_TARGET_AWA = "steering.autopilot.target.windAngleApparent"
    const val STEERING_AUTOPILOT_DUTY_CYCLE = "steering.autopilot.actions.dutyCycle"
    const val STEERING_ACTUATOR_CURRENT = "steering.actuator.current"
    const val STEERING_RUDDER_ANGLE = "steering.rudderAngle"

    // Pypilot Specific
    const val STEERING_AUTOPILOT_CONFIG_PREFIX = "steering.autopilot.config."
    const val STEERING_AUTOPILOT_SERVO_PREFIX = "steering.autopilot.servo."
    const val STEERING_AUTOPILOT_CALIBRATION_PREFIX = "steering.autopilot.calibration."

    // Propulsion
    const val PROPULSION_PREFIX = "propulsion."

    // Rigging
    const val RIGGING_LOAD_PREFIX = "rigging.loads."

    // Electrical
    const val ELECTRICAL_PREFIX = "electrical."
    const val BATTERIES_PREFIX = "electrical.batteries."
    const val ELECTRICAL_AC_PREFIX = "electrical.ac."
    const val INVERTERS_PREFIX = "electrical.inverters."
    const val CHARGERS_PREFIX = "electrical.chargers."

    // Notifications
    const val NOTIFICATIONS_PREFIX = "notifications."
    const val NOTIFICATIONS_MOB = "notifications.security.mob"
    const val NOTIFICATIONS_WATCHDOG = "notifications.safety.watchdog"
    const val NOTIFICATIONS_COLLISION_RISK = "notifications.navigation.collisionRisk"
    const val NOTIFICATIONS_LOW_BATTERY = "notifications.electrical.lowBattery"

    // Media
    const val MEDIA_FUSION_PREFIX = "entertainment.device.fusion."
    const val MEDIA_TITLE = "entertainment.device.fusion.title"
    const val MEDIA_ARTIST = "entertainment.device.fusion.artist"
    const val MEDIA_PLAYBACK_STATE = "entertainment.device.fusion.state"
    const val MEDIA_SOURCE = "entertainment.device.fusion.source"
    const val MEDIA_VOLUME = "entertainment.device.fusion.volume"

    // Resources
    const val RESOURCES_CHECKLISTS = "resources.checklists"

    // Tanks
    const val TANKS_PREFIX = "tanks."

    // Design
    const val DESIGN_TYPE = "design.type"
    const val DESIGN_LENGTH_OVERALL = "design.length.overall"
    const val DESIGN_BEAM = "design.beam"
    const val DESIGN_AIR_DRAFT = "design.airDraft"
    const val DESIGN_DISPLACEMENT = "design.displacement"

    // Communication
    const val COMMUNICATION_VHF_CHANNEL = "communication.vhf.channel"
    const val COMMUNICATION_CREW_NAMES = "communication.crewNames"

    // Sails
    const val SAILS_INVENTORY = "sails.inventory"
    const val SAILS_REEFS = "sails.reefs"
    const val SAILS_ACTIVE_PLAN = "sails.activeSailPlan"
}
