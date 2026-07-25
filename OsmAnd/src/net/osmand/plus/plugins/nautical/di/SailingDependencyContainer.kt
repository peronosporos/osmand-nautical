package net.osmand.plus.plugins.nautical.di

import net.osmand.plus.plugins.nautical.repository.SailingPerformanceRepository
import net.osmand.plus.plugins.nautical.grib.repository.GribRepository
import net.osmand.plus.plugins.nautical.tide.engine.TideCalculationEngine
import net.osmand.plus.plugins.nautical.tide.parser.HarmonicDataParser
import okhttp3.OkHttpClient

/**
 * Dependency assembly module for sailing performance features.
 */
object SailingDependencyContainer {
    private const val DEFAULT_BASE_URL = "http://127.0.0.1:3000"

    private val okHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(java.time.Duration.ofSeconds(5))
            .readTimeout(java.time.Duration.ofSeconds(5))
            .build()
    }

    val performanceRepository: SailingPerformanceRepository by lazy {
        SailingPerformanceRepository(okHttpClient, DEFAULT_BASE_URL)
    }

    val gribRepository: GribRepository by lazy {
        GribRepository()
    }

    val tideParser: HarmonicDataParser by lazy {
        HarmonicDataParser()
    }

    val tideEngine: TideCalculationEngine by lazy {
        TideCalculationEngine()
    }

    fun getNavtexRepository(app: net.osmand.plus.OsmandApplication): net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository {
        return net.osmand.plus.plugins.nautical.hazard.data.NavtexRepository(app)
    }

    private var _nmeaMultiplexer: net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer? = null
    
    fun getNmeaMultiplexer(app: net.osmand.plus.OsmandApplication): net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer {
        if (_nmeaMultiplexer == null) {
            val aggregator = net.osmand.plus.plugins.nautical.service.SailingDataAggregator()
            _nmeaMultiplexer = net.osmand.plus.plugins.nautical.nmea.multiplexer.DirectNmeaMultiplexer(aggregator)
        }
        return _nmeaMultiplexer!!
    }
}
