package net.osmand.plus.plugins.nautical.engine

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.plugins.nautical.NauticalPlugin
import net.osmand.plus.plugins.nautical.audio.AlarmType
import net.osmand.plus.plugins.nautical.audio.NauticalAudioArbiter
import net.osmand.plus.plugins.nautical.logbook.data.MarineLogbookRepository
import java.util.concurrent.ConcurrentHashMap

class NauticalNotificationManager(
    private val app: OsmandApplication,
    private val repository: MarineLogbookRepository? = null
) {
    private val log = PlatformUtil.getLog(NauticalNotificationManager::class.java)
    private val arbiter = NauticalAudioArbiter.getInstance(app)
    private val processedNotifications = ConcurrentHashMap<String, SignalKNotification>()
    private val lastTriggerTimes = ConcurrentHashMap<String, Long>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val initTime = System.currentTimeMillis()

    companion object {
        const val CHANNEL_CRITICAL = "osmand_marine_critical"
        const val ALERTS_STATE_KEY = "tactical.active_alerts"
        const val ALERT_COOLDOWN_MS = 60000L // 1 minute suppression for same path
        const val STARTUP_SILENCE_MS = 5000L // 5 seconds silence on startup
    }

    init {
        createNotificationChannels()
        restorePersistedAlerts()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = app.getString(R.string.nautical_critical_notifications)
            val importance = NotificationManager.IMPORTANCE_HIGH
            val channel = NotificationChannel(CHANNEL_CRITICAL, name, importance).apply {
                description = app.getString(R.string.nautical_critical_notifications_descr)
                enableLights(true)
                lightColor = android.graphics.Color.RED
                enableVibration(true)
                setBypassDnd(true)
                lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
            }
            val notificationManager = app.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun processNotifications(notifications: Map<String, SignalKNotification>) {
        if (processedNotifications == notifications) {
            return
        }
        var changed = false
        val now = System.currentTimeMillis()
        notifications.forEach { (path, notification) ->
            val last = processedNotifications[path]
            val lastTrigger = lastTriggerTimes[path] ?: 0L
            
            if (last == null || last.state != notification.state) {
                // State changed or new notification
                if (notification.state == NotificationState.ALARM || notification.state == NotificationState.EMERGENCY) {
                    if (now - lastTrigger > ALERT_COOLDOWN_MS) {
                        triggerAlert(path, notification)
                        lastTriggerTimes[path] = now
                    }
                }
                processedNotifications[path] = notification
                changed = true
            }
        }

        // Clean up cleared notifications
        val iterator = processedNotifications.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (!notifications.containsKey(entry.key)) {
                log.info("Notification cleared: ${entry.key}")
                val notificationId = entry.key.hashCode()
                scope.launch(Dispatchers.IO) {
                    try {
                        NotificationManagerCompat.from(app).cancel(notificationId)
                    } catch (_: Exception) {}
                }
                
                // Item 9 Fix: Only stop audio if no other notification of this alarm type is active
                val alarmType = getAlarmTypeForPath(entry.key)
                val otherActiveOfSameType = notifications.any { (otherPath, otherNotif) ->
                    otherPath != entry.key && 
                    getAlarmTypeForPath(otherPath) == alarmType && 
                    (otherNotif.state == NotificationState.ALARM || otherNotif.state == NotificationState.EMERGENCY)
                }
                
                if (!otherActiveOfSameType) {
                    arbiter.stopAlarm(alarmType)
                }
                
                iterator.remove()
                changed = true
            }
        }
        
        if (changed) {
            persistAlerts()
        }
    }

    private fun triggerAlert(path: String, notification: SignalKNotification) {
        val now = System.currentTimeMillis()
        val isHydration = (now - initTime < STARTUP_SILENCE_MS)
        
        log.error("SIGNAL K ALERT: [$path] ${notification.message} (State: ${notification.state}) ${if (isHydration) "[HYDRATION]" else ""}")
        
        val isCritical = notification.state == NotificationState.ALARM || notification.state == NotificationState.EMERGENCY
        val plugin = NauticalPlugin.getInstance()
        val onPassage = plugin?.isVesselOnPassage() == true

        app.runInUIThread {
            val priorityPrefix = when (notification.state) {
                NotificationState.EMERGENCY -> app.getString(R.string.nautical_notification_state_emergency)
                NotificationState.ALARM -> app.getString(R.string.nautical_notification_state_alarm)
                else -> app.getString(R.string.nautical_notification_state_warning)
            }
            val message = "$priorityPrefix: ${notification.message}"
            
            val alarmType = getAlarmTypeForPath(path)

            // Audio alerting: Suppress if in hydration mode OR not on passage AND it's just a general navigation alert
            val shouldSilenceAudio = isHydration || (!onPassage && (alarmType == AlarmType.XTE_NAVIGATION || path.contains("watchdog")))
            
            if (!shouldSilenceAudio) {
                arbiter.dispatchAlarm(
                    type = alarmType,
                    voiceText = message,
                    loop = isCritical,
                    playTone = isCritical
                )
            }
            
            // Post distinct Android Notification for critical safety events
            if (isCritical) {
                postCriticalNotification(path, priorityPrefix, notification.message)
            }

            // Visual alert: Always show banner for critical alarms even during hydration
            if (isCritical) {
                val isMob = alarmType == AlarmType.MOB
                val isMuted = isMob && (plugin?.mobViewModel?.uiState?.value?.muteUntil ?: 0L) > System.currentTimeMillis()

                val bannerLabel = when {
                    alarmType == AlarmType.DSC_DISTRESS || alarmType == AlarmType.AIS_SART -> app.getString(R.string.nautical_locate_vessel)
                    isMuted -> app.getString(R.string.nautical_unmute_alarm)
                    else -> app.getString(R.string.nautical_silence_alarm)
                }

                NauticalPlugin.hudManager?.get()?.showBanner(
                    message,
                    durationMs = if (isHydration) 10000L else 30000L,
                    label = bannerLabel,
                    isWarning = true,
                    onConfirm = {
                        if (alarmType == AlarmType.DSC_DISTRESS || alarmType == AlarmType.AIS_SART) {
                            handleRescueLocate(notification)
                        } else if (isMuted) {
                            plugin?.mobViewModel?.unmuteAlarm()
                        } else {
                            NauticalPlugin.engine?.acknowledgeNotification(path)
                        }
                    }
                )
            } else if (!isHydration) {
                app.showToastMessage(message)
            }
        }
    }

    private fun getAlarmTypeForPath(path: String): AlarmType {
        // Aligned with AlarmPriorityManager.mapKeyToAlarmType so the same Signal K alert
        // produces consistent audio semantics in both managers (previously watchdog fell
        // through to XTE navigation audio here).
        return when {
            path.startsWith("notifications.communication.dsc") -> AlarmType.DSC_DISTRESS
            path == "notifications.navigation.ais.sart" -> AlarmType.AIS_SART
            path == SignalKPaths.NOTIFICATIONS_MOB -> AlarmType.MOB
            path == "notifications.safety.alarm.gybe" -> AlarmType.TACTICAL_GYBE
            path == "notifications.navigation.offCourse" -> AlarmType.XTE_NAVIGATION
            path == SignalKPaths.NOTIFICATIONS_WATCHDOG -> AlarmType.SOLO_WATCHDOG
            path.contains("collision") -> AlarmType.COLLISION_DANGER
            path.contains("depth") || path.contains("shallow") -> AlarmType.SHALLOW_WATER
            path.contains("anchor") -> AlarmType.ANCHOR_DRIFT
            // Unknown categories previously reused XTE navigation audio; a generic
            // safety-hazard tone is the honest neutral default for unspecified alerts.
            else -> AlarmType.MAP_HAZARD
        }
    }

    private fun handleRescueLocate(notification: SignalKNotification) {
        val message = notification.message
        val source = notification.source
        
        // 1. Try to extract MMSI from structured source if available
        var mmsi: Int? = null
        if (source != null) {
            if (source.startsWith("vessels.urn:mrn:imo:mmsi:")) {
                mmsi = source.substringAfterLast(":").toIntOrNull()
            } else if (source.all { it.isDigit() } && source.length == 9) {
                mmsi = source.toIntOrNull()
            }
        }
        
        // 2. Fallback to regex on message
        if (mmsi == null) {
            val mmsiMatch = Regex("""MMSI:\s*(\d+)""").find(message)
            if (mmsiMatch != null) {
                mmsi = mmsiMatch.groupValues[1].toIntOrNull()
            }
        }

        if (mmsi != null) {
            val aisObj = NauticalPlugin.getAisObject(mmsi)
            if (aisObj != null && aisObj.position != null) {
                app.runInUIThread {
                    app.osmandMap.mapView.setLatLon(aisObj.position!!.latitude, aisObj.position!!.longitude)
                    app.osmandMap.mapView.setIntZoom(15)
                }
                return
            }
        }
        app.showToastMessage(R.string.nautical_vessel_not_found_on_ais)
    }


    private val lastCriticalNotifyTimes = ConcurrentHashMap<String, Long>()
    private val NOTIFY_THROTTLE_MS = 2500L

    fun postCriticalNotification(id: String, title: String, message: String) {
        val now = System.currentTimeMillis()
        // Throttle per alert id: a global throttle previously dropped a NEW critical
        // alert (e.g. MOB) because an unrelated one had been posted seconds before.
        val last = lastCriticalNotifyTimes[id] ?: 0L
        if (now - last < NOTIFY_THROTTLE_MS) {
            return
        }
        lastCriticalNotifyTimes[id] = now
        val builder = NotificationCompat.Builder(app, CHANNEL_CRITICAL)
            .setSmallIcon(R.drawable.ic_action_sail_boat_dark)
            .setContentTitle(title)
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(false)
            .setOngoing(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(android.graphics.Color.RED)

        scope.launch(Dispatchers.IO) {
            try {
                NotificationManagerCompat.from(app).notify(id.hashCode(), builder.build())
            } catch (e: SecurityException) {
                log.error("Missing notification permission for critical alert", e)
            } catch (e: Exception) {
                log.error("Failed to post critical notification", e)
            }
        }
    }

    private fun persistAlerts() {
        val repo = repository ?: return
        val activeAlerts = processedNotifications.filter { 
            it.value.state == NotificationState.ALARM || it.value.state == NotificationState.EMERGENCY 
        }
        
        scope.launch {
            try {
                if (activeAlerts.isEmpty()) {
                    repo.deleteTacticalState(ALERTS_STATE_KEY)
                } else {
                    val json = Json.encodeToString(activeAlerts)
                    repo.upsertTacticalState(ALERTS_STATE_KEY, json)
                }
            } catch (e: Exception) {
                log.error("Failed to persist alerts", e)
            }
        }
    }

    private fun restorePersistedAlerts() {
        val repo = repository ?: return
        scope.launch {
            try {
                val json = repo.getTacticalState(ALERTS_STATE_KEY)
                if (!json.isNullOrEmpty()) {
                    val alerts = Json.decodeFromString<Map<String, SignalKNotification>>(json)
                    alerts.forEach { (path, notification) ->
                        processedNotifications[path] = notification
                        // Hydrate silently: Post Android notification if critical, but skip audio/TTS
                        if (notification.state == NotificationState.ALARM || notification.state == NotificationState.EMERGENCY) {
                            app.runInUIThread {
                                val priorityPrefix = when (notification.state) {
                                    NotificationState.EMERGENCY -> app.getString(R.string.nautical_notification_state_emergency)
                                    NotificationState.ALARM -> app.getString(R.string.nautical_notification_state_alarm)
                                    else -> app.getString(R.string.nautical_notification_state_warning)
                                }
                                postCriticalNotification(path, priorityPrefix, notification.message)
                            }
                        }
                    }
                    // A restored alarm whose state never changes would otherwise never be
                    // voiced or bannered again after a restart (identical state == no trigger).
                    // Re-announce still-active critical alerts once the startup silence ends.
                    val stillCritical = alerts.filter {
                        it.value.state == NotificationState.ALARM || it.value.state == NotificationState.EMERGENCY
                    }
                    if (stillCritical.isNotEmpty()) {
                        delay(STARTUP_SILENCE_MS)
                        stillCritical.forEach { (path, notification) ->
                            processedNotifications[path]?.let { current ->
                                if (current.state == notification.state &&
                                    (current.state == NotificationState.ALARM || current.state == NotificationState.EMERGENCY)
                                ) {
                                    triggerAlert(path, current)
                                    lastTriggerTimes[path] = System.currentTimeMillis()
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                log.error("Failed to restore alerts", e)
            }
        }
    }
}
