package net.osmand.plus.plugins.nautical.ui

import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverManager
import net.osmand.plus.plugins.nautical.maneuvers.ManeuverOverlayWidget
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.enums.ScreenLayoutMode
import net.osmand.plus.views.mapwidgets.MapWidgetInfo
import net.osmand.plus.views.mapwidgets.WidgetType
import net.osmand.plus.views.mapwidgets.WidgetsPanel
import net.osmand.plus.views.mapwidgets.widgets.ActuatorLoadWidget
import net.osmand.plus.views.mapwidgets.widgets.MapWidget
import net.osmand.plus.views.mapwidgets.widgets.MarineTextWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalCameraWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalCompassWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalDisplayModeWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalElectricalWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalFlagsWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalMasterTelemetryWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalMediaWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalMobWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalPilotWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalTelltaleWidget
import net.osmand.plus.views.mapwidgets.widgets.NauticalVhfWidget
import net.osmand.plus.views.mapwidgets.widgets.PolarSpeedRatioWidget
import net.osmand.plus.views.mapwidgets.widgets.TargetVmgWidget

class NauticalWidgetFactory {

    fun isWidgetAllowed(type: WidgetType): Boolean {
        return type.isAllowed
    }

    fun createMapWidgetForParams(
        mapActivity: MapActivity,
        widgetType: WidgetType,
        customId: String?,
        widgetsPanel: WidgetsPanel?,
        maneuverManager: ManeuverManager?
    ): MapWidget? {
        if (!isWidgetAllowed(widgetType)) return null

        return when (widgetType) {
            WidgetType.NAUTICAL_COG,
            WidgetType.NAUTICAL_SOG,
            WidgetType.NAUTICAL_STW,
            WidgetType.NAUTICAL_SET_DRIFT,
            WidgetType.NAUTICAL_HEADING_MAGNETIC,
            WidgetType.NAUTICAL_LOG,
            WidgetType.NAUTICAL_TRIP_LOG,
            WidgetType.NAUTICAL_ROLL,
            WidgetType.NAUTICAL_PITCH,
            WidgetType.NAUTICAL_DEPTH_KEEL,
            WidgetType.NAUTICAL_DEPTH,
            WidgetType.NAUTICAL_WIND,
            WidgetType.NAUTICAL_WATER_TEMP,
            WidgetType.NAUTICAL_OUTSIDE_TEMP,
            WidgetType.NAUTICAL_PRESSURE,
            WidgetType.NAUTICAL_ENGINE_RPM,
            WidgetType.NAUTICAL_ENGINE_TEMP,
            WidgetType.NAUTICAL_BATTERY_VOLT,
            WidgetType.NAUTICAL_BATTERY_SOC,
            WidgetType.NAUTICAL_FUEL_LEVEL,
            WidgetType.NAUTICAL_FRESH_WATER_LEVEL,
            WidgetType.NAUTICAL_WASTE_WATER_LEVEL,
            WidgetType.NAUTICAL_ROT,
            WidgetType.NAUTICAL_XTE,
            WidgetType.NAUTICAL_TTW,
            WidgetType.NAUTICAL_DTW,
            WidgetType.NAUTICAL_ETA,
            WidgetType.NAUTICAL_AWA,
            WidgetType.NAUTICAL_AWS,
            WidgetType.NAUTICAL_TWA,
            WidgetType.NAUTICAL_TWD,
            WidgetType.NAUTICAL_OIL_PRESSURE,
            WidgetType.NAUTICAL_ENGINE_LOAD,
            WidgetType.NAUTICAL_BATTERY_CURRENT,
            WidgetType.NAUTICAL_SOLAR_CURRENT,
            WidgetType.NAUTICAL_ENGINE_RUNTIME,
            WidgetType.NAUTICAL_ENGINE_COOLANT,
            WidgetType.NAUTICAL_ENGINE_STATE,
            WidgetType.NAUTICAL_MAG_VARIATION,
            WidgetType.NAUTICAL_YAW,
            WidgetType.NAUTICAL_CPA,
            WidgetType.NAUTICAL_TCPA,
            WidgetType.NAUTICAL_RUDDER_ANGLE_TEXT,
            WidgetType.NAUTICAL_POLAR_TARGET_SPEED,
            WidgetType.NAUTICAL_ILLUMINANCE,
            WidgetType.NAUTICAL_RANGE,
            WidgetType.NAUTICAL_GNSS_QUALITY,
            WidgetType.NAUTICAL_HUMIDITY,
            WidgetType.NAUTICAL_MOON_PHASE,
            WidgetType.NAUTICAL_SALINITY,
            WidgetType.NAUTICAL_DEW_POINT,
            WidgetType.NAUTICAL_AC_VOLTAGE,
            WidgetType.NAUTICAL_AC_CURRENT,
            WidgetType.NAUTICAL_AC_FREQUENCY,
            WidgetType.NAUTICAL_VHF_CHANNEL,
            WidgetType.NAUTICAL_RIGGING_LOAD,
            WidgetType.NAUTICAL_WIND_SHIFT_WIDGET,
            WidgetType.NAUTICAL_RACING_TIMER,
            WidgetType.NAUTICAL_WATERMAKER,
            WidgetType.NAUTICAL_BOOST_PRESSURE,
            WidgetType.NAUTICAL_EXHAUST_TEMP,
            WidgetType.NAUTICAL_ALTERNATOR_VOLT,
            WidgetType.NAUTICAL_ALTERNATOR_CURR,
            WidgetType.NAUTICAL_TRANS_GEAR,
            WidgetType.NAUTICAL_TRANS_PRESS,
            WidgetType.NAUTICAL_TRANS_OIL_TEMP,
            WidgetType.NAUTICAL_INV_STATE,
            WidgetType.NAUTICAL_CHG_STATE,
            WidgetType.NAUTICAL_WATERMAKER_RATE,
            WidgetType.NAUTICAL_WATERMAKER_TOTAL,
            WidgetType.NAUTICAL_WATERMAKER_SALINITY,
            WidgetType.NAUTICAL_REEFS,
            WidgetType.NAUTICAL_AC_SYSTEM,
            WidgetType.NAUTICAL_NOTIFICATIONS_LIST,
            WidgetType.NAUTICAL_SUNLIGHT_MODE,
            -> MarineTextWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_PILOT -> NauticalPilotWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_COMPASS -> NauticalCompassWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_MEDIA -> NauticalMediaWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_CAMERA -> NauticalCameraWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_ELECTRICAL -> NauticalElectricalWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_MOB -> NauticalMobWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_VHF -> NauticalVhfWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_NIGHT_VISION -> NauticalDisplayModeWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_ACTUATOR -> ActuatorLoadWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.MANEUVER_OVERLAY -> {
                maneuverManager?.let {
                    ManeuverOverlayWidget(mapActivity, it, widgetType, customId, widgetsPanel)
                }
            }
            WidgetType.NAUTICAL_POLAR_RATIO -> PolarSpeedRatioWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_VMG -> TargetVmgWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_TELLTALE -> NauticalTelltaleWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_FLAGS -> NauticalFlagsWidget(mapActivity, widgetType, customId, widgetsPanel)
            WidgetType.NAUTICAL_MASTER_TELEMETRY -> NauticalMasterTelemetryWidget(mapActivity, widgetType, customId, widgetsPanel)
            else -> null
        }
    }

    fun createWidgets(
        activity: MapActivity,
        widgetInfos: MutableList<MapWidgetInfo>,
        appMode: ApplicationMode,
        layoutMode: ScreenLayoutMode?,
        maneuverManager: ManeuverManager?
    ) {
        if (!appMode.isDerivedRoutingFrom(ApplicationMode.BOAT)) return

        for (type in WidgetType.values()) {
            val isNautical = type.id.startsWith("nautical_") || type == WidgetType.MANEUVER_OVERLAY
            if (isNautical && type.isAllowed) {
                if (widgetInfos.none { it.key == type.id }) {
                    val wPanel = type.defaultPanel ?: WidgetsPanel.LEFT
                    createMapWidgetForParams(activity, type, null, wPanel, maneuverManager)?.let { widget: MapWidget ->
                        widgetInfos.add(object : MapWidgetInfo(
                            type.id, widget, type.dayIconId, type.nightIconId, type.titleId, null, 0, 0, wPanel
                        ) {
                            override fun getUpdatedPanel(appMode: ApplicationMode, layoutMode: ScreenLayoutMode?): WidgetsPanel {
                                if (type == WidgetType.MANEUVER_OVERLAY) return WidgetsPanel.BOTTOM
                                return widgetPanel
                            }
                        })
                    }
                }
            }
        }
    }
}
