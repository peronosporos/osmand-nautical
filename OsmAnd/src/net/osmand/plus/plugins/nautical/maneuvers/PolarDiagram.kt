package net.osmand.plus.plugins.nautical.maneuvers

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.math.*

/**
 * Production-grade PolarDiagram engine supporting Bilinear Interpolation across
 * True Wind Speed (TWS) and True Wind Angle (TWA) axes.
 * Internally uses Degrees for lookup table (common polar format) but provides Radian interfaces.
 * TWS is in m/s (Signal K standard), speeds are in m/s.
 */
class PolarDiagram {

    private val lock = ReentrantReadWriteLock()

    private class PolarData(
        val twsValues: DoubleArray,
        val twaValues: DoubleArray,
        val speedTable: Array<DoubleArray>
    )

    private val polars = mutableMapOf<String, PolarData>()
    var activePolarId: String = "default"
        set(value) {
            lock.write { field = value }
        }

    var isLoaded: Boolean = false
        get() = lock.read { polars.containsKey(activePolarId) }
        private set

    private val MS_TO_KNOTS = 1.94384
    private val KNOTS_TO_MS = 0.514444

    /**
     * Parses standard CSV polar diagrams.
     * Assumes speeds in Knots, converts to m/s internally.
     */
    fun loadFromCsv(inputStream: InputStream, polarId: String = "default"): Boolean {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            if (lines.isEmpty()) return false

            val parsedTws = mutableListOf<Double>()
            val parsedTwa = mutableListOf<Double>()
            val parsedRows = mutableListOf<DoubleArray>()

            var headerParsed = false
            for (lineRaw in lines) {
                val line = lineRaw.trim()
                if (line.isEmpty() || line.startsWith("#")) continue

                val tokens = line.split(",").map { it.trim() }
                if (!headerParsed) {
                    if (tokens.size < 2) return false
                    for (i in 1 until tokens.size) {
                        tokens[i].toDoubleOrNull()?.let { parsedTws.add(it * KNOTS_TO_MS) }
                    }
                    if (parsedTws.isEmpty()) return false
                    headerParsed = true
                } else {
                    if (tokens.size < parsedTws.size + 1) continue
                    val twa = tokens[0].toDoubleOrNull() ?: continue
                    parsedTwa.add(twa)

                    val speeds = DoubleArray(parsedTws.size)
                    for (j in 0 until parsedTws.size) {
                        speeds[j] = (tokens[j + 1].toDoubleOrNull() ?: 0.0) * KNOTS_TO_MS
                    }
                    parsedRows.add(speeds)
                }
            }

            if (parsedTwa.isEmpty() || parsedTws.isEmpty() || parsedRows.isEmpty()) {
                return false
            }

            lock.write {
                polars[polarId] = PolarData(
                    parsedTws.toDoubleArray(),
                    parsedTwa.toDoubleArray(),
                    parsedRows.toTypedArray()
                )
                if (activePolarId == "default" || activePolarId == polarId) {
                    activePolarId = polarId
                }
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Parses Signal K Resources API JSON schema for polars.
     * Supports both SI units (m/s, Radians) and legacy/common metadata (Knots, Degrees).
     */
    fun loadFromSignalKJson(jsonString: String, polarId: String = "default"): Boolean {
        try {
            val json = JSONObject(jsonString)
            val root = if (json.has("value")) json.getJSONObject("value") else json
            
            // Metadata Unit Check (TASK-015)
            val meta = root.optJSONObject("meta")
            val angleUnit = meta?.optString("angleUnit", "rad") ?: "rad"
            val speedUnit = meta?.optString("speedUnit", "ms") ?: "ms"

            val twsArr = root.optJSONArray("tws") ?: root.optJSONArray("windSpeeds")
            val twaArr = root.optJSONArray("twa") ?: root.optJSONArray("windAngles")
            val speedsArr = root.optJSONArray("speeds") ?: root.optJSONArray("polarTable")

            if (twsArr == null || twaArr == null || speedsArr == null) {
                return false
            }

            val nTws = twsArr.length()
            val nTwa = twaArr.length()
            if (nTws == 0 || nTwa == 0 || speedsArr.length() != nTwa) {
                return false
            }

            val twsMultiplier = if (speedUnit.lowercase(Locale.US) == "knots") KNOTS_TO_MS else 1.0
            val twaIsDeg = angleUnit.lowercase(Locale.US) == "deg"

            val parsedTws = DoubleArray(nTws) { i -> twsArr.optDouble(i, 0.0) * twsMultiplier }
            val parsedTwa = DoubleArray(nTwa) { i -> 
                val raw = twaArr.optDouble(i, 0.0)
                if (twaIsDeg) raw else Math.toDegrees(raw)
            }

            val parsedRows = Array(nTwa) { i ->
                val rowJson = speedsArr.optJSONArray(i)
                if (rowJson != null && rowJson.length() >= nTws) {
                    DoubleArray(nTws) { j -> rowJson.optDouble(j, 0.0) * twsMultiplier }
                } else {
                    DoubleArray(nTws) { 0.0 }
                }
            }

            if (parsedTws.isEmpty() || parsedTwa.isEmpty() || parsedRows.isEmpty()) {
                return false
            }

            lock.write {
                polars[polarId] = PolarData(parsedTws, parsedTwa, parsedRows)
                if (activePolarId == "default" || activePolarId == polarId) {
                    activePolarId = polarId
                }
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Get target boat speed in m/s for a given TWS (m/s) and TWA (Radians).
     */
    fun getTargetSpeedRad(twsMs: Double, twaRad: Double): Double {
        return getTargetSpeedDeg(twsMs, abs(Math.toDegrees(twaRad)))
    }

    /**
     * Get target boat speed in m/s for a given TWS (m/s) and TWA (Degrees).
     */
    fun getTargetSpeedDeg(twsMs: Double, twaDeg: Double): Double {
        val absTwa = abs(twaDeg)
        
        lock.read {
            val data = polars[activePolarId] ?: return defaultTargetSpeed(absTwa)
            
            val twsValues = data.twsValues
            val twaValues = data.twaValues
            val speedTable = data.speedTable

            if (twsValues.isEmpty() || twaValues.isEmpty() || speedTable.isEmpty()) {
                return defaultTargetSpeed(absTwa)
            }

            val clampedTws = twsMs.coerceIn(twsValues.first(), twsValues.last())
            val clampedTwa = absTwa.coerceIn(twaValues.first(), twaValues.last())

            val twaIdx1 = findLowerIndex(twaValues, clampedTwa)
            val twaIdx2 = if (twaIdx1 < twaValues.size - 1) twaIdx1 + 1 else twaIdx1

            val twsIdx1 = findLowerIndex(twsValues, clampedTws)
            val twsIdx2 = if (twsIdx1 < twsValues.size - 1) twsIdx1 + 1 else twsIdx1

            val x1 = twsValues[twsIdx1]
            val x2 = twsValues[twsIdx2]
            val y1 = twaValues[twaIdx1]
            val y2 = twaValues[twaIdx2]

            val q11 = speedTable[twaIdx1][twsIdx1]
            val q21 = speedTable[twaIdx1][twsIdx2]
            val q12 = speedTable[twaIdx2][twsIdx1]
            val q22 = speedTable[twaIdx2][twsIdx2]

            if (x1 == x2 && y1 == y2) return q11

            val denomX = if (x2 == x1) 1.0 else (x2 - x1)
            val denomY = if (y2 == y1) 1.0 else (y2 - y1)

            val r1 = ((x2 - clampedTws) / denomX) * q11 + ((clampedTws - x1) / denomX) * q21
            val r2 = ((x2 - clampedTws) / denomX) * q12 + ((clampedTws - x1) / denomX) * q22

            return ((y2 - clampedTwa) / denomY) * r1 + ((clampedTwa - y1) / denomY) * r2
        }
    }

    /**
     * Finds optimal upwind True Wind Angle (Radians) that maximizes VMG.
     */
    fun getOptimalUpwindTwaRad(twsMs: Double): Double {
        return Math.toRadians(findOptimalTwaDeg(twsMs, 20.0, 85.0))
    }

    /**
     * Finds optimal downwind True Wind Angle (Radians) that maximizes VMG.
     */
    fun getOptimalDownwindTwaRad(twsMs: Double): Double {
        return Math.toRadians(findOptimalTwaDeg(twsMs, 100.0, 175.0))
    }

    private fun findOptimalTwaDeg(twsMs: Double, minTwa: Double, maxTwa: Double): Double {
        val loaded = lock.read { isLoaded }
        if (!loaded) {
            return if (minTwa < 90.0) 42.0 else 135.0
        }

        // Bimodal Refinement: Multi-start Sampled search + Golden Section refinement (TASK-012)
        // 1. Coarse sample to avoid local maxima (asymmetric polars)
        var bestSampleTwa = minTwa
        var maxSampleVmg = -1.0
        val steps = 15
        for (i in 0..steps) {
            val sampleTwa = minTwa + (maxTwa - minTwa) * (i.toDouble() / steps)
            val sampleVmg = getVmg(twsMs, sampleTwa)
            if (sampleVmg > maxSampleVmg) {
                maxSampleVmg = sampleVmg
                bestSampleTwa = sampleTwa
            }
        }

        // 2. Golden Section Refinement around the best sample
        val range = (maxTwa - minTwa) / steps
        var a = (bestSampleTwa - range).coerceAtLeast(minTwa)
        var b = (bestSampleTwa + range).coerceAtMost(maxTwa)
        
        val phi = (sqrt(5.0) - 1.0) / 2.0
        var x1 = b - phi * (b - a)
        var x2 = a + phi * (b - a)
        
        var f1 = getVmg(twsMs, x1)
        var f2 = getVmg(twsMs, x2)

        repeat(15) { // Fixed iterations for stable precision
            if (f1 < f2) {
                a = x1
                x1 = x2
                x2 = a + phi * (b - a)
                f1 = f2
                f2 = getVmg(twsMs, x2)
            } else {
                b = x2
                x2 = x1
                x1 = b - phi * (b - a)
                f2 = f1
                f1 = getVmg(twsMs, x1)
            }
        }
        return (a + b) / 2.0
    }

    private fun getVmg(twsMs: Double, twaDeg: Double): Double {
        val speed = getTargetSpeedDeg(twsMs, twaDeg)
        // VMG is the component of boat speed along the wind axis.
        // We use abs(cos) to maximize speed either directly upwind or directly downwind.
        return speed * abs(cos(Math.toRadians(twaDeg)))
    }

    private fun findLowerIndex(array: DoubleArray, value: Double): Int {
        var low = 0
        var high = array.size - 2
        while (low <= high) {
            val mid = (low + high) / 2
            if (value < array[mid]) {
                high = mid - 1
            } else if (value >= array[mid + 1]) {
                low = mid + 1
            } else {
                return mid
            }
        }
        return low.coerceIn(0, array.size - 1)
    }

    private fun defaultTargetSpeed(twaDeg: Double): Double {
        val rad = Math.toRadians(twaDeg)
        val base = (6.0 * KNOTS_TO_MS) * sin(rad)
        return if (base < KNOTS_TO_MS) KNOTS_TO_MS else base
    }
}
