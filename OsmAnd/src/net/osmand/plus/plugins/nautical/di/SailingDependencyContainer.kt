package net.osmand.plus.plugins.nautical.di

import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.tide.engine.TideCalculationEngine
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import okhttp3.OkHttpClient
import kotlinx.coroutines.*
import net.osmand.PlatformUtil
import net.osmand.plus.OsmandApplication
import net.osmand.plus.plugins.nautical.engine.SignalKDataBroker
import net.osmand.plus.plugins.nautical.engine.AutopilotController
import net.osmand.plus.plugins.nautical.engine.EnvironmentalFilterService
import net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer
import net.osmand.plus.plugins.nautical.service.SailingDataAggregator
import net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository

/**
 * Dependency assembly module for sailing performance features.
 * Enforces strict lifecycle boundaries via initialize() and teardown().
 */
object SailingDependencyContainer {
    private val log = PlatformUtil.getLog(SailingDependencyContainer::class.java)
    
    private var containerScope: CoroutineScope? = null
    
    private var _okHttpClient: OkHttpClient? = null
    val okHttpClient: OkHttpClient
        get() = _okHttpClient ?: throw IllegalStateException("Nautical dependencies not initialized")

    var performanceRepository: SailingPerformanceRepository? = null
        private set

    var gribRepository: GribRepository? = null
        private set

    var tideParser: HarmonicDataParser? = null
        private set

    var tideEngine: TideCalculationEngine? = null
        private set

    var environmentalFilterService: EnvironmentalFilterService? = null
        private set

    var recoveryEngine: net.osmand.plus.plugins.nautical.engine.AbortRecoveryEngine? = null
        private set

    private var _nmeaMultiplexer: DirectNmeaMultiplexer? = null
    val nmeaMultiplexer: DirectNmeaMultiplexer?
        get() = _nmeaMultiplexer

    fun initialize(app: OsmandApplication, broker: SignalKDataBroker, autopilot: AutopilotController, client: OkHttpClient) {
        log.info("Initializing SailingDependencyContainer")

        val scope = CoroutineScope(Dispatchers.Default + SupervisorJob() + CoroutineExceptionHandler { _, throwable ->
            log.error("Container Scope Error: ${throwable.message}", throwable)
        })
        containerScope = scope

        _okHttpClient = client

        val serverIp = app.settings.NAUTICAL_SERVER_IP.get()
        if (serverIp.isNotEmpty()) {
            val baseUrl = "http://$serverIp:${app.settings.NAUTICAL_SERVER_PORT.get()}"
            performanceRepository = SailingPerformanceRepository(broker, okHttpClient, baseUrl)
        } else {
            log.warn("Nautical server IP is not configured, skipping SailingPerformanceRepository initialization")
        }
        gribRepository = GribRepository()
        tideParser = HarmonicDataParser()
        tideEngine = TideCalculationEngine()
        environmentalFilterService = EnvironmentalFilterService(broker, autopilot)
        recoveryEngine = net.osmand.plus.plugins.nautical.engine.AbortRecoveryEngine(app, autopilot)

        val navtexRepo = NavtexRepository(app)
        val aggregator = SailingDataAggregator()
        _nmeaMultiplexer = DirectNmeaMultiplexer(app, aggregator, scope, navtexRepo = navtexRepo)
    }

    fun setOkHttpClient(client: OkHttpClient) {
        _okHttpClient = client
        // Repositories might need re-init if they hold client reference, 
        // but typically they are re-initialized in NauticalPlugin.reconnect()
    }

    fun teardown() {
        log.info("Tearing down SailingDependencyContainer")
        
        _nmeaMultiplexer?.stopAll()
        _nmeaMultiplexer = null
        
        containerScope?.cancel()
        containerScope = null
        
        performanceRepository?.disconnect()
        performanceRepository = null
        
        gribRepository?.cleanup()
        gribRepository = null
        
        tideParser = null
        tideEngine = null
        environmentalFilterService = null

        _okHttpClient = null
    }

    // Legacy support for DirectNmeaMultiplexer access if needed before full refactor
    fun getNmeaMultiplexer(app: OsmandApplication, scope: CoroutineScope? = null, navtexRepo: NavtexRepository? = null): DirectNmeaMultiplexer {
        return _nmeaMultiplexer ?: DirectNmeaMultiplexer(app, SailingDataAggregator(), scope ?: CoroutineScope(Dispatchers.Default), navtexRepo = navtexRepo)
    }
}
