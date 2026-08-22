package net.osmand.plus.plugins.nautical.ui

import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AlertDialog
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import net.osmand.data.LatLon
import net.osmand.plus.widgets.ctxmenu.ContextMenuAdapter
import net.osmand.plus.widgets.ctxmenu.data.ContextMenuItem
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.track.GpxDialogs
import net.osmand.plus.utils.AndroidUtils
import net.osmand.shared.gpx.primitives.WptPt
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.NauticalPlugin.NauticalModule
import net.osmand.plus.plugins.nautical.di.SailingDependencyContainer
import net.osmand.plus.plugins.nautical.engine.AutopilotController
import net.osmand.plus.plugins.nautical.engine.GpxStreamer
import net.osmand.plus.plugins.nautical.engine.NauticalSafetyManager
import net.osmand.plus.plugins.nautical.engine.SignalKEngine
import net.osmand.plus.plugins.nautical.engine.TrajectoryPoint
import net.osmand.plus.plugins.nautical.grib.ui.GribManagerBottomSheet
import net.osmand.plus.plugins.nautical.maneuvers.TacticalStartManager
import net.osmand.plus.plugins.nautical.map.controller.SailingMapLayerController
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobTriggerSource
import net.osmand.plus.plugins.nautical.mob.viewmodel.MobViewModel
import net.osmand.plus.plugins.nautical.poi.ui.VhfPoiSearchLayer
import net.osmand.plus.plugins.nautical.quickaction.NauticalAnchorQuickAction
import net.osmand.plus.plugins.nautical.raster.MarineRasterSettingsControl
import net.osmand.plus.plugins.nautical.routing.model.RoutingRequest
import net.osmand.plus.plugins.nautical.routing.model.Waypoint
import net.osmand.plus.plugins.nautical.s57.S57SpatialIndex
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalElectricalDashboardBottomSheet
import net.osmand.plus.plugins.nautical.ui.widgets.NauticalManeuversBottomSheet
import net.osmand.plus.plugins.nautical.viewmodel.RoutingViewModel
import net.osmand.render.RenderingRuleProperty
import net.osmand.plus.settings.backend.ApplicationMode
import net.osmand.plus.settings.enums.ThemeUsageContext
import net.osmand.plus.settings.fragments.SettingsScreenType
import net.osmand.plus.settings.backend.preferences.CommonPreference
import net.osmand.shared.aistracker.AisObject
import java.util.Date
import java.util.Locale

class NauticalContextMenuHelper(private val app: OsmandApplication) {

    fun registerConfigureMapCategoryActions(
        adapter: ContextMenuAdapter,
        mapActivity: MapActivity,
        customRules: MutableList<RenderingRuleProperty>,
        isPluginActive: Boolean,
        isModuleEnabled: (NauticalModule) -> Boolean,
        onRequestRefresh: () -> Unit
    ) {
        if (isPluginActive && app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)) {
            adapter.addItem(
                ContextMenuItem("nautical_category").apply {
                    isCategory = true
                    setTitleId(R.string.nautical_category, mapActivity)
                    layout = R.layout.list_group_title_with_switch
                }
            )

            // Overlays Group
            adapter.addItem(
                ContextMenuItem("nautical_overlays_group").apply {
                    title = app.getString(R.string.nautical_map_overlays)
                }
            )

            if (isModuleEnabled(NauticalModule.ENC)) {
                adapter.addItem(createToggleWithGear(R.string.nautical_enc_manager, app.settings.NAUTICAL_MODULE_ENC, mapActivity, onRequestRefresh) {
                    showSettings(mapActivity, SettingsScreenType.ENC_CHART_MANAGER)
                })
            }

            if (isModuleEnabled(NauticalModule.RASTER)) {
                adapter.addItem(createToggleWithGear(R.string.raster_layer_name, app.settings.NAUTICAL_SHOW_RASTER_CHARTS, mapActivity, onRequestRefresh) {
                    MarineRasterSettingsControl.show(mapActivity.supportFragmentManager)
                })
            }

            adapter.addItem(createToggle(R.string.nautical_show_laylines, app.settings.NAUTICAL_SHOW_LAYLINES, mapActivity, onRequestRefresh))
            adapter.addItem(createToggle(R.string.nautical_show_wind_shifts, app.settings.NAUTICAL_SHOW_WIND_SHIFTS, mapActivity, onRequestRefresh))
            adapter.addItem(createToggle(R.string.nautical_show_trajectory, app.settings.NAUTICAL_SHOW_TRAJECTORY, mapActivity, onRequestRefresh))

            if (isModuleEnabled(NauticalModule.TIDES)) {
                adapter.addItem(createToggleWithGear(R.string.layer_tides_title, app.settings.NAUTICAL_SHOW_TIDES, mapActivity, onRequestRefresh) {
                    showSettings(mapActivity, SettingsScreenType.TIDE_DATA_MANAGER)
                })
            }

            if (isModuleEnabled(NauticalModule.GRIB)) {
                adapter.addItem(createToggleWithGear(R.string.grib_layer_waves, app.settings.NAUTICAL_SHOW_GRIB_WAVES, mapActivity, onRequestRefresh) {
                    GribManagerBottomSheet.show(mapActivity.supportFragmentManager)
                })
                adapter.addItem(createToggle(R.string.grib_layer_pressure, app.settings.NAUTICAL_SHOW_GRIB_PRESSURE, mapActivity, onRequestRefresh))
            }

            if (isModuleEnabled(NauticalModule.LOGBOOK)) {
                adapter.addItem(createToggleWithGear(R.string.nautical_log_entries, app.settings.NAUTICAL_SHOW_LOGBOOK_LAYER, mapActivity, onRequestRefresh) {
                    showSettings(mapActivity, SettingsScreenType.MARINE_LOGBOOK)
                })
            }

            adapter.addItem(createToggle(R.string.nautical_restricted_area, app.settings.NAUTICAL_RESTRICTED_AREAS_ENABLED, mapActivity, onRequestRefresh))

            // Vessel Projections Group
            adapter.addItem(
                ContextMenuItem("nautical_vessel_group").apply {
                    title = app.getString(R.string.nautical_vessel_indicators)
                }
            )
            adapter.addItem(createToggle(R.string.nautical_show_heading_line, app.settings.NAUTICAL_SHOW_HEADING_LINE, mapActivity, onRequestRefresh))
            adapter.addItem(createToggle(R.string.nautical_show_cog_line, app.settings.NAUTICAL_SHOW_COG_LINE, mapActivity, onRequestRefresh))
            adapter.addItem(createToggle(R.string.nautical_show_cmg_line, app.settings.NAUTICAL_SHOW_CMG_LINE, mapActivity, onRequestRefresh))
            adapter.addItem(createToggle(R.string.nautical_show_current_vector, app.settings.NAUTICAL_SHOW_CURRENT_VECTOR, mapActivity, onRequestRefresh))

            // Projection Time
            adapter.addItem(
                ContextMenuItem("nautical_look_ahead").apply {
                    title = app.getString(R.string.nautical_look_ahead_time)
                    description = "${app.settings.NAUTICAL_LOOK_AHEAD_TIME.get()} ${app.getString(R.string.shared_string_min)}"
                    icon = R.drawable.ic_action_time
                    setListener { uiAdapter, _, item, _ ->
                        val options = arrayOf<CharSequence>("2", "5", "10", "20", "30", "60")
                        val isNight = app.daynightHelper.isNightMode(app.settings.APPLICATION_MODE.get(), ThemeUsageContext.OVER_MAP)
                        AlertDialog.Builder(mapActivity, if (isNight) R.style.OsmandDarkTheme else R.style.OsmandLightTheme)
                            .setTitle(R.string.nautical_look_ahead_time)
                            .setItems(options) { _, which ->
                                val mins = options[which].toString().toInt()
                                app.settings.NAUTICAL_LOOK_AHEAD_TIME.set(mins)
                                item.description = "$mins ${app.getString(R.string.shared_string_min)}"
                                uiAdapter.onDataSetChanged()
                                onRequestRefresh()
                            }
                            .show()
                        true
                    }
                }
            )
        }
    }

    private fun createToggleWithGear(
        titleId: Int,
        pref: CommonPreference<Boolean>,
        mapActivity: MapActivity,
        onRequestRefresh: () -> Unit,
        onGearClick: () -> Unit
    ): ContextMenuItem {
        return ContextMenuItem("nautical_item_$titleId").apply {
            setTitleId(titleId, mapActivity)
            selected = pref.get()
            layout = R.layout.list_item_icon_and_menu
            icon = R.drawable.ic_action_additional_option
            secondaryIcon = R.drawable.ic_action_settings
            setListener { uiAdapter, _, item, isChecked ->
                pref.set(isChecked)
                item.selected = isChecked
                uiAdapter.onDataSetChanged()
                onRequestRefresh()
                true
            }
            setSecondaryIconClickListener { _, _, _, _ ->
                onGearClick()
                true
            }
        }
    }

    private fun showSettings(mapActivity: MapActivity, type: SettingsScreenType) {
        net.osmand.plus.settings.fragments.BaseSettingsFragment.showInstance(mapActivity, type)
    }

    private fun createToggle(
        titleId: Int,
        pref: CommonPreference<Boolean>,
        mapActivity: MapActivity,
        onRequestRefresh: () -> Unit
    ): ContextMenuItem {
        return ContextMenuItem("nautical_item_$titleId").apply {
            setTitleId(titleId, mapActivity)
            selected = pref.get()
            icon = R.drawable.ic_action_additional_option
            setListener { uiAdapter, _, item, isChecked ->
                pref.set(isChecked)
                item.selected = isChecked
                uiAdapter.onDataSetChanged()
                onRequestRefresh()
                true
            }
        }
    }

    fun registerMapContextMenuActions(
        mapActivity: MapActivity,
        lat: Double,
        lon: Double,
        adapter: ContextMenuAdapter,
        obj: Any?,
        conf: Boolean,
        tacticalStartManager: TacticalStartManager?,
        autopilot: AutopilotController?,
        engine: SignalKEngine?,
        mobViewModel: MobViewModel?,
        routingViewModel: RoutingViewModel?,
        safetyManager: NauticalSafetyManager?,
        s57SpatialIndex: S57SpatialIndex?,
        layerController: SailingMapLayerController?,
        vhfPoiLayer: VhfPoiSearchLayer?,
        skWaypointLayer: SignalKWaypointLayer?,
        pluginScope: CoroutineScope?,
        onRequestRefresh: () -> Unit
    ) {
        if (!app.settings.APPLICATION_MODE.get().isDerivedRoutingFrom(ApplicationMode.BOAT)) return

        adapter.addItem(
            ContextMenuItem("nautical_switches").apply {
                setTitleId(R.string.nautical_electrical_dashboard, mapActivity)
                icon = R.drawable.ic_action_nautical_battery_volt
                setListener { _, _, _, _ ->
                    NauticalElectricalDashboardBottomSheet.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        val isPortSet = tacticalStartManager?.isPortPinSet() == true
        adapter.addItem(
            ContextMenuItem("nautical_ping_port").apply {
                title = if (isPortSet) mapActivity.getString(R.string.nautical_clear_port_pin) else mapActivity.getString(R.string.nautical_ping_port_pin)
                icon = if (isPortSet) R.drawable.ic_action_remove else R.drawable.ic_action_flag
                setListener { _, _, _, _ ->
                    if (isPortSet) {
                        tacticalStartManager?.clearPortPin()
                        app.showToastMessage(R.string.nautical_pin_cleared)
                    } else {
                        tacticalStartManager?.setPortPin(lat, lon)
                        app.showToastMessage(R.string.nautical_pin_marked_start_line)
                    }
                    onRequestRefresh()
                    true
                }
            }
        )

        val isStbdSet = tacticalStartManager?.isStarboardPinSet() == true
        adapter.addItem(
            ContextMenuItem("nautical_ping_stbd").apply {
                title = if (isStbdSet) mapActivity.getString(R.string.nautical_clear_stbd_pin) else mapActivity.getString(R.string.nautical_ping_stbd_pin)
                icon = if (isStbdSet) R.drawable.ic_action_remove else R.drawable.ic_action_flag
                setListener { _, _, _, _ ->
                    if (isStbdSet) {
                        tacticalStartManager?.clearStarboardPin()
                        app.showToastMessage(R.string.nautical_pin_cleared)
                    } else {
                        tacticalStartManager?.setStarboardPin(lat, lon)
                        app.showToastMessage(R.string.nautical_pin_marked_start_line)
                    }
                    onRequestRefresh()
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_anchor_watch").apply {
                setTitleId(R.string.nautical_anchor_label, mapActivity)
                icon = R.drawable.ic_action_anchor
                selected = app.settings.NAUTICAL_ANCHOR_LAT.get() != 0.0
                setListener { _, _, _, _ ->
                    net.osmand.plus.plugins.nautical.ui.anchor.AnchorWatchDialogFragment.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        if (app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.get() != 0.0) {
            adapter.addItem(
                ContextMenuItem("nautical_anchor_set_preview").apply {
                    setTitleId(R.string.nautical_adjust_anchor_here, mapActivity)
                    icon = R.drawable.ic_action_anchor
                    setListener { _, _, _, _ ->
                        app.settings.NAUTICAL_ANCHOR_PREVIEW_LAT.set(lat)
                        app.settings.NAUTICAL_ANCHOR_PREVIEW_LON.set(lon)
                        app.showToastMessage(R.string.nautical_anchor_moved_to_tap)
                        app.osmandMap?.refreshMap()
                        true
                    }
                }
            )
        }

        adapter.addItem(
            ContextMenuItem("nautical_autopilot_mode").apply {
                val mode = autopilot?.let { " (${engine?.getCurrentState()?.autopilotState ?: "standby"})" } ?: ""
                title = app.getString(R.string.nautical_autopilot) + mode
                icon = R.drawable.ic_action_settings
                setListener { _, _, _, _ ->
                    val options = arrayOf<CharSequence>(
                        app.getString(R.string.nautical_autopilot_mode_standby),
                        app.getString(R.string.nautical_autopilot_mode_track),
                        app.getString(R.string.nautical_autopilot_mode_wind)
                    )
                    val values = arrayOf("standby", "track", "wind")
                    AlertDialog.Builder(mapActivity)
                        .setTitle(R.string.nautical_autopilot)
                        .setItems(options) { _, which ->
                            autopilot?.setAutopilotMode(values[which])
                        }
                        .show()
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_show_laylines_to_here").apply {
                setTitleId(R.string.nautical_show_laylines_here, mapActivity)
                icon = R.drawable.ic_action_sail_boat_dark
                setListener { _, _, _, _ ->
                    app.settings.NAUTICAL_TACTICAL_TARGET_LAT.set(lat)
                    app.settings.NAUTICAL_TACTICAL_TARGET_LON.set(lon)
                    app.settings.NAUTICAL_SHOW_LAYLINES.set(true)
                    val plugin = NauticalPlugin.getInstance()
                    plugin?.laylineViewModel?.updateTargetWaypoint(net.osmand.plus.plugins.nautical.laylines.engine.LatLon(lat, lon))
                    app.showToastMessage(R.string.nautical_laylines_rendered)
                    onRequestRefresh()
                    app.osmandMap.refreshMap()
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_maneuvers_menu").apply {
                setTitleId(R.string.nautical_maneuver_menu, mapActivity)
                icon = R.drawable.ic_action_sail_boat_dark
                setListener { _, _, _, _ ->
                    NauticalManeuversBottomSheet.show(mapActivity.supportFragmentManager, lat, lon)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("mob_maneuver").apply {
                setTitleId(R.string.nautical_mob_label, mapActivity)
                setListener { _, _, _, _ ->
                    mobViewModel?.triggerMob(
                        LatLon(lat, lon),
                        MobTriggerSource.MAP
                    )
                    true
                }
            }
        )

        val multiplexer = SailingDependencyContainer.getNmeaMultiplexer(app, pluginScope)
        val recorder = multiplexer.recorder

        adapter.addItem(
            ContextMenuItem("nautical_replay_controls").apply {
                setTitleId(R.string.nautical_replay_title, mapActivity)
                icon = R.drawable.ic_action_play_dark
                setListener { _, _, _, _ ->
                    net.osmand.plus.plugins.nautical.replay.NmeaPlaybackControlBottomSheet.show(mapActivity.supportFragmentManager)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_open_logbook_menu").apply {
                setTitleId(R.string.nautical_log_entries, mapActivity)
                icon = R.drawable.ic_action_note_dark
                setListener { _, _, _, _ ->
                    showSettings(mapActivity, SettingsScreenType.MARINE_LOGBOOK)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_boat_ai").apply {
                setTitleId(R.string.nautical_boat_ai_title, mapActivity)
                icon = R.drawable.ic_action_message
                setListener { _, _, _, _ ->
                    showSettings(mapActivity, SettingsScreenType.BOAT_AI)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_checklists_menu").apply {
                setTitleId(R.string.nautical_checklists, mapActivity)
                icon = R.drawable.ic_action_list_bullet
                setListener { _, _, _, _ ->
                    showSettings(mapActivity, SettingsScreenType.NAUTICAL_CHECKLISTS)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_sail_inventory_menu").apply {
                setTitleId(R.string.nautical_sail_inventory_title, mapActivity)
                icon = R.drawable.ic_action_sail_boat_dark
                setListener { _, _, _, _ ->
                    showSettings(mapActivity, SettingsScreenType.SAIL_INVENTORY)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_follow_gpx").apply {
                title = mapActivity.getString(R.string.nautical_follow_gpx_route)
                icon = R.drawable.ic_action_track_16
                setListener { _, _, _, _ ->
                    handleGpxSelection(mapActivity, engine)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_export_trajectory").apply {
                title = app.getString(R.string.nautical_export_trajectory)
                icon = R.drawable.ic_action_export
                setListener { _, _, _, _ ->
                    if (pluginScope != null) {
                        exportCurrentTrajectory(mapActivity, engine, pluginScope)
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_clear_trajectory").apply {
                title = app.getString(R.string.nautical_clear_breadcrumbs)
                icon = R.drawable.ic_action_remove
                setListener { _, _, _, _ ->
                    engine?.clearTrajectory()
                    app.showToastMessage(R.string.nautical_breadcrumbs_cleared)
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_steer_id").apply {
                title = mapActivity.getString(R.string.nautical_steer_here)
                icon = R.drawable.ic_action_direction_compass
                setListener { _, _, _, _ ->
                    if (autopilot?.isConnected() == true) {
                        autopilot.sendActiveWaypoint(lat, lon)
                        app.showToastMessage(R.string.nautical_command_sent)
                    } else {
                        app.showToastMessage(R.string.nautical_autopilot_not_connected)
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_sync_routes").apply {
                title = app.getString(R.string.nautical_sync_routes_from_server)
                icon = R.drawable.ic_action_import
                setListener { _, _, _, _ ->
                    pluginScope?.launch {
                        val routes = engine?.fetchRoutesFromServer()
                        if (!routes.isNullOrEmpty()) {
                            val names = routes.values.map { (it.name ?: "Unnamed Route") as CharSequence }.toTypedArray()
                            val ids = routes.keys.toTypedArray()

                            app.runInUIThread {
                                AlertDialog.Builder(mapActivity)
                                    .setTitle(R.string.nautical_select_server_route)
                                    .setItems(names) { _, which: Int ->
                                        val routeId = ids[which]
                                        val selectedRoute = routes[routeId]

                                        AlertDialog.Builder(mapActivity)
                                            .setTitle(selectedRoute?.name ?: "Route Actions")
                                            .setItems(arrayOf("Load Route", "Update with Active", "Delete from Server")) { _, actionIdx ->
                                                pluginScope.launch {
                                                    when (actionIdx) {
                                                        0 -> {
                                                            val fullRoute = engine?.getRestService()?.getRouteById(routeId)?.body()
                                                            (fullRoute ?: selectedRoute)?.feature?.geometry?.coordinates?.let { coords ->
                                                                val points = coords.map { Pair(it[1], it[0]) }
                                                                engine?.loadRoute(points)
                                                                app.showToastMessage(R.string.nautical_loaded_points, points.size)
                                                            }
                                                        }
                                                        1 -> {
                                                            val activePoints = engine?.getRoutePoints() ?: emptyList()
                                                            if (activePoints.isNotEmpty()) {
                                                                engine?.updateRouteOnServer(routeId, selectedRoute?.name ?: "Updated", activePoints)
                                                            }
                                                        }
                                                        2 -> {
                                                            engine?.deleteRouteFromServer(routeId)
                                                        }
                                                    }
                                                }
                                            }
                                            .show()
                                    }
                                    .show()
                            }
                        } else {
                            app.showToastMessage(R.string.nautical_no_routes_found_on_server)
                        }
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_sync_charts").apply {
                setTitleId(R.string.nautical_server_charts, mapActivity)
                icon = R.drawable.ic_action_world_globe
                setListener { _, _, _, _ ->
                    pluginScope?.launch {
                        val charts = engine?.getRestService()?.getCharts()?.body()
                        if (!charts.isNullOrEmpty()) {
                            val names = charts.values.map { (it.name ?: it.identifier) as CharSequence }.toTypedArray()
                            app.runInUIThread {
                                AlertDialog.Builder(mapActivity)
                                    .setTitle(R.string.nautical_server_charts)
                                    .setItems(names) { _, which ->
                                        val chart = charts.values.toList()[which]
                                        app.settings.NAUTICAL_ACTIVE_SERVER_CHART.set(chart.identifier)
                                        app.settings.NAUTICAL_SHOW_RASTER_CHARTS.set(true)
                                        app.showToastMessage(app.getString(R.string.nautical_chart_overlay_enabled, chart.name ?: chart.identifier))
                                        app.osmandMap.refreshMap()
                                    }
                                    .show()
                            }
                        } else {
                            app.showToastMessage(R.string.nautical_no_charts_on_server)
                        }
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_upload_route").apply {
                title = app.getString(R.string.nautical_upload_route_to_server)
                icon = R.drawable.ic_action_export
                setListener { _, _, _, _ ->
                    val name = "Route-${System.currentTimeMillis()}"
                    pluginScope?.launch {
                        engine?.uploadActiveRouteToSignalK(name)
                    }
                    true
                }
            }
        )

        adapter.addItem(
            ContextMenuItem("nautical_weather_route").apply {
                title = mapActivity.getString(R.string.nautical_calculate_weather_route)
                icon = R.drawable.ic_action_wind
                setListener { _, _, _, _ ->
                    if (pluginScope != null) {
                        calculateWeatherRouteTo(lat, lon, mapActivity, routingViewModel, safetyManager, s57SpatialIndex, layerController, pluginScope)
                    }
                    true
                }
            }
        )

        val activeRoute = engine?.getRoutePoints() ?: emptyList()
        if (activeRoute.isNotEmpty()) {
            adapter.addItem(
                ContextMenuItem("nautical_export_route_active").apply {
                    setTitleId(R.string.nautical_export_route_gpx, mapActivity)
                    icon = R.drawable.ic_action_export
                    setListener { _, _, _, _ ->
                        pluginScope?.launch {
                            val file = GpxStreamer(app).exportRoute(activeRoute)
                            if (file != null) {
                                app.showToastMessage(R.string.nautical_export_route_success, file.name)
                            } else {
                                app.showToastMessage(R.string.nautical_export_route_failed)
                            }
                        }
                        true
                    }
                }
            )
        }

        routingViewModel?.optimalRoute?.value?.let { weatherRoute ->
            adapter.addItem(
                ContextMenuItem("nautical_export_weather_route").apply {
                    title = app.getString(R.string.nautical_export_route_gpx) + " (Weather)"
                    icon = R.drawable.ic_action_export
                    setListener { _, _, _, _ ->
                        pluginScope?.launch {
                            val file = GpxStreamer(app).exportRouteGpx(weatherRoute)
                            if (file != null) {
                                app.showToastMessage(R.string.nautical_export_route_success, file.name)
                            } else {
                                app.showToastMessage(R.string.nautical_export_route_failed)
                            }
                        }
                        true
                    }
                }
            )
        }

        vhfPoiLayer?.registerContextMenuActions(adapter, obj)
        skWaypointLayer?.registerContextMenuActions(adapter, obj)

        if (obj is AisObject) {
            adapter.addItem(
                ContextMenuItem("nautical_ais_details").apply {
                    setTitleId(R.string.nautical_vessel_details, mapActivity)
                    icon = R.drawable.ic_action_info
                    setListener { _, _, _, _ ->
                        NauticalAisDetailsDialog.show(mapActivity.supportFragmentManager, obj.mmsi)
                        true
                    }
                }
            )

            val isBuddy = engine?.getCurrentState()?.aisBuddies?.contains(obj.mmsi) ?: false
            adapter.addItem(
                ContextMenuItem("nautical_toggle_buddy").apply {
                    title = if (isBuddy) mapActivity.getString(R.string.nautical_remove_from_buddies) else mapActivity.getString(R.string.nautical_add_to_buddies)
                    icon = if (isBuddy) R.drawable.ic_action_remove else R.drawable.ic_action_add
                    setListener { _, _, _, _ ->
                        val current = engine?.getCurrentState()?.aisBuddies?.toMutableSet() ?: mutableSetOf()
                        if (isBuddy) current.remove(obj.mmsi) else current.add(obj.mmsi)
                        engine?.sendDelta("navigation.aisBuddies", current.toList())
                        true
                    }
                }
            )
        }
    }

    private fun calculateWeatherRouteTo(
        lat: Double,
        lon: Double,
        activity: MapActivity,
        routingViewModel: RoutingViewModel?,
        safetyManager: NauticalSafetyManager?,
        s57SpatialIndex: S57SpatialIndex?,
        layerController: SailingMapLayerController?,
        pluginScope: CoroutineScope
    ) {
        val vm = routingViewModel ?: return
        val gribRepo = SailingDependencyContainer.gribRepository
        val gridData = gribRepo?.gridData
        if (gridData == null) {
            app.showToastMessage(R.string.grib_parse_error)
            return
        }

        val lastLoc = app.locationProvider.lastKnownLocation
        if (lastLoc == null) {
            app.showToastMessage(R.string.nautical_error_no_gps)
            return
        }

        val polarProfile = SailingDependencyContainer.performanceRepository?.activePolarProfile?.value
        if (polarProfile == null) {
            app.showToastMessage(R.string.nautical_error_no_polar)
            return
        }

        val request = RoutingRequest(
            start = Waypoint(lastLoc.latitude, lastLoc.longitude),
            destination = Waypoint(lat, lon),
            departureTime = System.currentTimeMillis(),
            polarProfile = polarProfile
        )

        val index = s57SpatialIndex ?: S57SpatialIndex(app)
        val sm = safetyManager ?: NauticalSafetyManager.getInstance(app)
        vm.calculateWeatherRoute(request, gridData, index, sm)

        vm.routingStatus.onEach { status ->
            app.runInUIThread {
                if (status != "Idle") {
                    app.showToastMessage(status)
                }
            }
        }.launchIn(pluginScope)

        vm.optimalRoute.onEach { result ->
            app.runInUIThread {
                layerController?.setWeatherRoute(result)
            }
        }.launchIn(pluginScope)
    }

    fun handleGpxSelection(mapActivity: MapActivity, engine: SignalKEngine?) {
        GpxDialogs.selectGPXFile(
            mapActivity, false, false,
            { result ->
                if (!result.isNullOrEmpty()) {
                    val gpx = result[0]
                    mapActivity.lifecycleScope.launch {
                        val items = withContext(Dispatchers.IO) {
                            val extracted = mutableListOf<Pair<String, List<WptPt>>>()
                            gpx.routes.forEach { rte ->
                                if (rte.points.isNotEmpty()) extracted.add((rte.name ?: "Route") to rte.points)
                            }
                            gpx.tracks.forEach { trk ->
                                val pts = trk.segments.flatMap { it.points }
                                if (pts.isNotEmpty()) extracted.add((trk.name ?: "Track") to pts)
                            }
                            extracted
                        }

                        fun loadSelection(points: List<WptPt>) {
                            engine?.loadRoute(points.map { it.lat to it.lon })
                            app.showToastMessage(R.string.nautical_loaded_points, points.size)

                            points.firstOrNull { it.getExtensionsToRead().containsKey("arrival_radius") }?.let { pt ->
                                val radius = pt.getExtensionsToRead()["arrival_radius"]?.toDoubleOrNull()
                                radius?.let { engine?.arrivalRadiusMeters = it }
                            }
                        }

                        if (items.size == 1) {
                            loadSelection(items[0].second)
                        } else if (items.size > 1) {
                            val names = items.map { it.first as CharSequence }.toTypedArray()
                            AlertDialog.Builder(mapActivity)
                                .setTitle(R.string.nautical_select_route_from_gpx)
                                .setItems(names) { _, which: Int ->
                                    loadSelection(items[which].second)
                                }
                                .show()
                        } else {
                            val waypoints = gpx.getPointsList()
                            if (waypoints.isNotEmpty()) {
                                loadSelection(waypoints)
                            }
                        }
                    }
                }
                true
            },
            NauticalPlugin.isNightVision(app),
        )
    }

    fun exportCurrentTrajectory(activity: MapActivity, engine: SignalKEngine?, pluginScope: CoroutineScope) {
        val points = mutableListOf<TrajectoryPoint>()
        engine?.copyTrajectoryTo(points)
        if (points.isEmpty()) {
            app.showToastMessage(R.string.nautical_no_trajectory_data)
            return
        }

        pluginScope.launch {
            val file = GpxStreamer(app).exportTrajectory(points)
            if (file != null) {
                withContext(Dispatchers.Main) {
                    val uri = AndroidUtils.getUriForFile(app, file)
                    if (uri != null) {
                        val intent = Intent(Intent.ACTION_SEND)
                        intent.type = "application/gpx+xml"
                        intent.putExtra(Intent.EXTRA_STREAM, uri as android.os.Parcelable)
                        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        activity.startActivity(Intent.createChooser(intent, app.getString(R.string.nautical_export_trajectory)))
                    } else {
                        app.showToastMessage(R.string.nautical_trajectory_exported, file.name)
                    }
                }
            } else {
                app.showToastMessage(R.string.nautical_export_trajectory_failed)
            }
        }
    }
}
