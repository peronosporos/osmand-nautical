package net.osmand.plus.settings.fragments;

import net.osmand.plus.R;
import net.osmand.plus.keyevent.fragments.MainExternalInputDevicesFragment;
import net.osmand.plus.plugins.accessibility.AccessibilitySettingsFragment;
import net.osmand.plus.plugins.aistracker.AisTrackerSettingsFragment;
import net.osmand.plus.plugins.audionotes.MultimediaNotesFragment;
import net.osmand.plus.plugins.development.DevelopmentSettingsFragment;
import net.osmand.plus.plugins.externalsensors.ExternalSettingsWriteToTrackSettingsFragment;
import net.osmand.plus.plugins.externalsensors.dialogs.ExternalDevicesListFragment;
import net.osmand.plus.plugins.monitoring.MonitoringSettingsFragment;
import net.osmand.plus.plugins.odb.dialogs.OBDDevicesListFragment;
import net.osmand.plus.plugins.odb.dialogs.OBDMainFragment;
import net.osmand.plus.plugins.osmedit.fragments.OsmEditingFragment;
import net.osmand.plus.plugins.weather.dialogs.WeatherSettingsFragment;
import net.osmand.plus.settings.datastorage.DataStorageFragment;
import net.osmand.plus.settings.mediastorage.MediaStorageFragment;
import net.osmand.plus.settings.fragments.profileappearance.ProfileAppearanceFragment;
import net.osmand.plus.settings.fragments.voice.VoiceAnnouncesFragment;

public enum SettingsScreenType {

	MAIN_SETTINGS(MainSettingsFragment.class.getName(), false, null, R.xml.settings_main_screen, R.layout.global_preference_toolbar),
	GLOBAL_SETTINGS(GlobalSettingsFragment.class.getName(), false, null, R.xml.global_settings, R.layout.global_preference_toolbar),
	CONFIGURE_PROFILE(ConfigureProfileFragment.class.getName(), true, null, R.xml.configure_profile, R.layout.profile_preference_toolbar_with_switch),
	PROXY_SETTINGS(ProxySettingsFragment.class.getName(), false, null, R.xml.proxy_preferences, R.layout.global_preferences_toolbar_with_switch),
	SEND_UUID(SendUniqueIdentifiersFragment.class.getName(), false, null, R.xml.send_uuid_preferences, R.layout.global_preference_toolbar),
	GENERAL_PROFILE(GeneralProfileSettingsFragment.class.getName(), true, ApplyQueryType.BOTTOM_SHEET, R.xml.general_profile_settings, R.layout.profile_preference_toolbar),
	NAVIGATION(NavigationFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.navigation_settings_new, R.layout.profile_preference_toolbar),
	COORDINATES_FORMAT(CoordinatesFormatFragment.class.getName(), true, ApplyQueryType.BOTTOM_SHEET, R.xml.coordinates_format, R.layout.profile_preference_toolbar),
	ROUTE_PARAMETERS(RouteParametersFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.route_parameters, R.layout.profile_preference_toolbar),
	SCREEN_ALERTS(ScreenAlertsFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.screen_alerts, R.layout.profile_preference_toolbar_with_switch),
	VOICE_ANNOUNCES(VoiceAnnouncesFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.voice_announces, R.layout.profile_preference_toolbar_with_switch),
	VEHICLE_PARAMETERS(VehicleParametersFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.vehicle_parameters, R.layout.profile_preference_toolbar),
	MAP_DURING_NAVIGATION(MapDuringNavigationFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.map_during_navigation, R.layout.profile_preference_toolbar),
	TURN_SCREEN_ON(TurnScreenOnFragment.class.getName(), true, ApplyQueryType.BOTTOM_SHEET, R.xml.turn_screen_on, R.layout.profile_preference_toolbar),
	DATA_STORAGE(DataStorageFragment.class.getName(), false, null, R.xml.data_storage, R.layout.global_preference_toolbar),
	MEDIA_STORAGE(MediaStorageFragment.class.getName(), false, null, R.xml.media_storage, R.layout.global_preference_toolbar),
	DIALOGS_AND_NOTIFICATIONS_SETTINGS(DialogsAndNotificationsSettingsFragment.class.getName(), false, null, R.xml.dialogs_and_notifications_preferences, R.layout.global_preference_toolbar),
	HISTORY_SETTINGS(HistorySettingsFragment.class.getName(), false, null, R.xml.history_preferences, R.layout.global_preference_toolbar),
	PROFILE_APPEARANCE(ProfileAppearanceFragment.TAG, true, null, R.xml.profile_appearance_screen, R.layout.profile_preference_toolbar),
	OPEN_STREET_MAP_EDITING(OsmEditingFragment.class.getName(), false, null, R.xml.osm_editing, R.layout.global_preference_toolbar),
	MULTIMEDIA_NOTES(MultimediaNotesFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.multimedia_notes, R.layout.profile_preference_toolbar),
	MONITORING_SETTINGS(MonitoringSettingsFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.monitoring_settings, R.layout.profile_preference_toolbar),
	LIVE_MONITORING(LiveMonitoringFragment.class.getName(), false, ApplyQueryType.SNACK_BAR, R.xml.live_monitoring, R.layout.global_preferences_toolbar_with_switch),
	ACCESSIBILITY_SETTINGS(AccessibilitySettingsFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.accessibility_settings, R.layout.profile_preference_toolbar),
	DEVELOPMENT_SETTINGS(DevelopmentSettingsFragment.class.getName(), false, null, R.xml.development_settings, R.layout.global_preference_toolbar),
	SIMULATION_NAVIGATION(SimulationNavigationSettingFragment.class.getName(), true, ApplyQueryType.NONE, R.xml.simulation_navigation_setting, R.layout.profile_preference_toolbar_with_switch),
	ANT_PLUS_SETTINGS(ExternalDevicesListFragment.class.getName(), false, null, R.xml.antplus_settings, R.layout.global_preference_toolbar),
	VEHICLE_METRICS_SETTINGS(OBDDevicesListFragment.class.getName(), false, null, R.xml.antplus_settings, R.layout.global_preference_toolbar),
	VEHICLE_CONNECTED_METRICS_SETTINGS(OBDMainFragment.class.getName(), false, null, R.xml.antplus_settings, R.layout.global_preference_toolbar),
	WEATHER_SETTINGS(WeatherSettingsFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.weather_settings, R.layout.profile_preference_toolbar),
	EXTERNAL_SETTINGS_WRITE_TO_TRACK_SETTINGS(ExternalSettingsWriteToTrackSettingsFragment.class.getName(), true, ApplyQueryType.BOTTOM_SHEET, R.xml.external_sensors_write_to_track_settings, R.layout.profile_preference_toolbar),
	DANGEROUS_GOODS(DangerousGoodsFragment.class.getName(), true, ApplyQueryType.NONE, R.xml.dangerous_goods_parameters, R.layout.global_preference_toolbar),
	EXTERNAL_INPUT_DEVICE(MainExternalInputDevicesFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.external_input_device_settings, R.layout.profile_preference_toolbar_with_switch),
	AIS_SETTINGS(AisTrackerSettingsFragment.class.getName(), true, ApplyQueryType.SNACK_BAR, R.xml.ais_settings, R.layout.profile_preference_toolbar),
	POSITION_ANIMATION(PositionAnimationFragment.class.getName(), true, ApplyQueryType.NONE, R.xml.position_animation_settings, R.layout.profile_preference_toolbar_with_switch),
	NAUTICAL_SETTINGS("net.osmand.plus.plugins.nautical.NauticalSettingsFragment", false, ApplyQueryType.SNACK_BAR, R.xml.nautical_settings, R.layout.global_preference_toolbar),
	NAUTICAL_POLAR_WIZARD("net.osmand.plus.plugins.nautical.ui.wizard.PolarWizardFragment", false, null, -1, R.layout.global_preference_toolbar),
	SAILING_PERFORMANCE_SETTINGS("net.osmand.plus.plugins.nautical.ui.editor.PolarEditorFragment", false, null, -1, R.layout.global_preference_toolbar),
	MARINE_LOGBOOK("net.osmand.plus.plugins.nautical.ui.logbook.MarineLogbookFragment", false, null, -1, R.layout.global_preference_toolbar),
	TIDE_DATA_MANAGER("net.osmand.plus.plugins.nautical.tide.import.TideDataManagerFragment", false, null, -1, R.layout.global_preference_toolbar),
	MARINE_RASTER_MANAGER("net.osmand.plus.plugins.nautical.raster.MarineRasterManagerFragment", false, null, -1, R.layout.global_preference_toolbar),
	S63_PERMIT_MANAGER("net.osmand.plus.plugins.nautical.s63.ui.S63PermitManagerFragment", false, null, R.xml.s63_permit_manager_settings, R.layout.global_preference_toolbar),
	NAUTICAL_HARDWARE_STATS("net.osmand.plus.plugins.nautical.ui.NauticalTechnicalStatsFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_SWITCH_PANEL("net.osmand.plus.plugins.nautical.ui.NauticalSwitchPanelFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_PASSAGE_PLAN("net.osmand.plus.plugins.nautical.routing.ui.NauticalRouteSummaryFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_MASTER_TELEMETRY("net.osmand.plus.views.mapwidgets.configure.settings.NauticalMasterTelemetrySettingsFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_TELEMETRY_CONFIG("net.osmand.plus.plugins.nautical.telemetry.TelemetryWidgetSettingsFragment", false, null, -1, -1),
	ENC_CHART_MANAGER("net.osmand.plus.plugins.nautical.s57.ui.S57ChartManagerFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_CHECKLISTS("net.osmand.plus.plugins.nautical.ui.checklist.NauticalChecklistFragment", false, null, -1, R.layout.global_preference_toolbar),
	SAIL_INVENTORY("net.osmand.plus.plugins.nautical.ui.sail.SailInventoryFragment", false, null, -1, R.layout.global_preference_toolbar),
	BOAT_AI("net.osmand.plus.plugins.nautical.ui.ai.BoatAiFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_NOTIFICATIONS("net.osmand.plus.plugins.nautical.ui.NauticalNotificationsFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_SAFETY_REGIONS("net.osmand.plus.plugins.nautical.ui.NauticalSafetyRegionsFragment", false, null, -1, R.layout.global_preference_toolbar),
	SIGNALK_SERVER_ROUTES("net.osmand.plus.plugins.nautical.ui.SignalKServerRoutesFragment", false, null, -1, R.layout.global_preference_toolbar),
	SIGNALK_SERVER_CHARTS("net.osmand.plus.plugins.nautical.ui.SignalKServerChartsFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_GNSS_STATUS("net.osmand.plus.plugins.nautical.ui.NauticalGnssStatusFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_TIDE_TABLE("net.osmand.plus.plugins.nautical.ui.SignalKTideTableFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_AIS_BUDDIES("net.osmand.plus.plugins.nautical.ui.NauticalBuddyListFragment", false, null, -1, R.layout.global_preference_toolbar),
	NAUTICAL_ADVANCED_SETTINGS("net.osmand.plus.plugins.nautical.ui.NauticalAdvancedSettingsFragment", false, null, R.xml.nautical_advanced_settings, R.layout.global_preference_toolbar),
	NAUTICAL_SIGNALK_DIAGNOSTICS("net.osmand.plus.plugins.nautical.ui.SignalKDiagnosticsFragment", false, null, -1, R.layout.global_preference_toolbar),
	SIGNALK_DIAGNOSTICS("net.osmand.plus.plugins.nautical.ui.SignalKDiagnosticsFragment", false, null, -1, R.layout.global_preference_toolbar);
	public final String fragmentName;
	public final boolean profileDependent;
	public final ApplyQueryType applyQueryType;
	public final int preferencesResId;
	public final int toolbarResId;

	SettingsScreenType(String fragmentName, boolean profileDependent, ApplyQueryType applyQueryType, int preferencesResId, int toolbarResId) {
		this.fragmentName = fragmentName;
		this.profileDependent = profileDependent;
		this.applyQueryType = applyQueryType;
		this.preferencesResId = preferencesResId;
		this.toolbarResId = toolbarResId;
	}
}
