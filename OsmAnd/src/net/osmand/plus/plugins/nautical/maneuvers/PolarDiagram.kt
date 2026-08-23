package net.osmand.plus.plugins.nautical.maneuvers

import net.osmand.plus.plugins.nautical.network.PolarProfile
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
     * Assumes speeds in Knots, converts to m/s internally unless # Unit: ms is present.
     */
    fun loadFromCsv(inputStream: InputStream, polarId: String = "default"): Boolean {
        try {
            val reader = BufferedReader(InputStreamReader(inputStream))
            val lines = reader.readLines()
            if (lines.isEmpty()) return false

            val parsedTws = mutableListOf<Double>()
            val parsedTwa = mutableListOf<Double>()
            val parsedRows = mutableListOf<DoubleArray>()

            var speedMultiplier = KNOTS_TO_MS
            var headerParsed = false
            for (lineRaw in lines) {
                val line = lineRaw.trim()
                if (line.isEmpty()) continue
                if (line.startsWith("#")) {
                    val lower = line.lowercase(Locale.US)
                    if (lower.contains("unit") || lower.contains("speed")) {
                        if (lower.contains("ms") || lower.contains("m/s")) {
                            speedMultiplier = 1.0
                        } else if (lower.contains("kn") || lower.contains("kt")) {
                            speedMultiplier = KNOTS_TO_MS
                        }
                    }
                    continue
                }

                val tokens = line.split(",").map { it.trim() }
                if (!headerParsed) {
                    if (tokens.size < 2) return false
                    for (i in 1 until tokens.size) {
                        tokens[i].toDoubleOrNull()?.let { parsedTws.add(it * speedMultiplier) }
                    }
                    if (parsedTws.isEmpty()) return false
                    headerParsed = true
                } else {
                    if (tokens.size < parsedTws.size + 1) continue
                    val twa = tokens[0].toDoubleOrNull() ?: continue
                    parsedTwa.add(twa)

                    val speeds = DoubleArray(parsedTws.size)
                    for (j in 0 until parsedTws.size) {
                        speeds[j] = (tokens[j + 1].toDoubleOrNull() ?: 0.0) * speedMultiplier
                    }
                    parsedRows.add(speeds)
                }
            }

            if (parsedTwa.isEmpty() || parsedTws.isEmpty() || parsedRows.isEmpty()) {
                return false
            }

            // TASK-003: Standardize matrix storage to [TWS][TWA]
            val nTws = parsedTws.size
            val nTwa = parsedTwa.size
            val transposedRows = Array(nTws) { j ->
                DoubleArray(nTwa) { i ->
                    parsedRows[i][j]
                }
            }

            lock.write {
                polars[polarId] = PolarData(
                    parsedTws.toDoubleArray(),
                    parsedTwa.toDoubleArray(),
                    transposedRows
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
     * Directly loads a PolarProfile model.
     */
    fun loadFromProfile(profile: PolarProfile, polarId: String = "default"): Boolean {
        val tws = profile.tws?.toDoubleArray() ?: return false
        val twa = profile.twa?.toDoubleArray() ?: return false
        val speeds = profile.speeds ?: return false

        val nTws = tws.size
        val nTwa = twa.size
        if (nTws == 0 || nTwa == 0 || speeds.size < nTws) return false

        val matrix = Array(nTws) { j ->
            val row = speeds[j]
            if (row.size < nTwa) return false // Item 9: Dimension validation
            DoubleArray(nTwa) { i ->
                row[i]
            }
        }

        lock.write {
            polars[polarId] = PolarData(tws, twa, matrix)
            if (activePolarId == "default" || activePolarId == polarId) {
                activePolarId = polarId
            }
        }
        return true
    }

    /**
     * Parses Signal K Resources API JSON schema for polars.
     * Supports both SI units (m/s, Radians) and legacy/common metadata (Knots, Degrees).
     */
    fun loadFromSignalKJson(jsonString: String, polarId: String = "default"): Boolean {
        try {
            val json = JSONObject(jsonString)
            val root = if (json.has("value")) json.getJSONObject("value") else json
            
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
            if (nTws == 0 || nTwa == 0) return false

            val twsMultiplier = if (speedUnit.lowercase(Locale.US) == "knots") KNOTS_TO_MS else 1.0
            val twaIsDeg = angleUnit.lowercase(Locale.US) == "deg"

            val parsedTws = DoubleArray(nTws) { i -> twsArr.optDouble(i, 0.0) * twsMultiplier }
            val parsedTwa = DoubleArray(nTwa) { i -> 
                val raw = twaArr.optDouble(i, 0.0)
                if (twaIsDeg) raw else Math.toDegrees(raw)
            }

            // Item 7: Robust matrix order detection
            val isTwsFirst = speedsArr.length() == nTws && (speedsArr.optJSONArray(0)?.length() ?: 0) == nTwa
            val isTwaFirst = speedsArr.length() == nTwa && (speedsArr.optJSONArray(0)?.length() ?: 0) == nTws

            val parsedRows = Array(nTws) { j ->
                DoubleArray(nTwa) { i ->
                    when {
                        isTwsFirst -> speedsArr.optJSONArray(j).optDouble(i, 0.0) * twsMultiplier
                        isTwaFirst -> speedsArr.optJSONArray(i).optDouble(j, 0.0) * twsMultiplier
                        else -> 0.0
                    }
                }
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

    var activeSailEfficiency: Double = 1.0
        set(value) {
            lock.write { field = value.coerceIn(0.1, 2.0) }
        }

    /**
     * Get target boat speed in m/s for a given TWS (m/s) and TWA (Radians).
     */
    fun getTargetSpeedRad(twsMs: Double, twaRad: Double, sailEfficiency: Double? = null): Double {
        return getTargetSpeedDeg(twsMs, abs(Math.toDegrees(twaRad)), sailEfficiency)
    }

    /**
     * Get target boat speed in m/s for a given TWS (m/s) and TWA (Degrees).
     */
    fun getTargetSpeedDeg(twsMs: Double, twaDeg: Double, sailEfficiency: Double? = null): Double {
        val absTwa = abs(twaDeg)
        val eff = (sailEfficiency ?: lock.read { activeSailEfficiency }).coerceIn(0.1, 2.0)
        
        lock.read {
            val data = polars[activePolarId] ?: return defaultTargetSpeed(absTwa) * eff
            
            val twsValues = data.twsValues
            val twaValues = data.twaValues
            val speedTable = data.speedTable

            if (twsValues.isEmpty() || twaValues.isEmpty() || speedTable.isEmpty()) {
                return defaultTargetSpeed(absTwa) * eff
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

            val q11 = speedTable[twsIdx1][twaIdx1]
            val q21 = speedTable[twsIdx2][twaIdx1]
            val q12 = speedTable[twsIdx1][twaIdx2]
            val q22 = speedTable[twsIdx2][twaIdx2]

            // Item 2: Bilinear Interpolation divide-by-zero protection
            val denomX = if (abs(x2 - x1) < 1e-9) 1.0 else (x2 - x1)
            val denomY = if (abs(y2 - y1) < 1e-9) 1.0 else (y2 - y1)

            val r1 = ((x2 - clampedTws) / denomX) * q11 + ((clampedTws - x1) / denomX) * q21
            val r2 = ((x2 - clampedTws) / denomX) * q12 + ((clampedTws - x1) / denomX) * q22

            val baseSpeed = ((y2 - clampedTwa) / denomY) * r1 + ((clampedTwa - y1) / denomY) * r2
            return baseSpeed * eff
        }
    }

    /**
     * Data class representing optimal target angle and performance metrics.
     */
    data class OptimalVmgTarget(
        val targetTwaDeg: Double,
        val targetSpeedMs: Double,
        val vmgMs: Double
    )

    /**
     * Resolves the optimum Upwind Target (TWA in [30°, 65°] maximizing STW * cos(TWA)).
     */
    fun getOptimalUpwindTarget(twsMs: Double, polarId: String = activePolarId): OptimalVmgTarget {
        val twaDeg = findOptimalTwaDeg(twsMs, 30.0, 65.0)
        val speedMs = getTargetSpeedDeg(twsMs, twaDeg)
        val vmgMs = speedMs * cos(Math.toRadians(twaDeg))
        return OptimalVmgTarget(targetTwaDeg = twaDeg, targetSpeedMs = speedMs, vmgMs = vmgMs)
    }

    /**
     * Resolves the optimum Downwind Gybe Target (TWA in [120°, 175°] maximizing STW * cos(180° - TWA)).
     */
    fun getOptimalDownwindTarget(twsMs: Double, polarId: String = activePolarId): OptimalVmgTarget {
        val twaDeg = findOptimalTwaDeg(twsMs, 120.0, 175.0)
        val speedMs = getTargetSpeedDeg(twsMs, twaDeg)
        val vmgMs = speedMs * cos(Math.toRadians(180.0 - twaDeg))
        return OptimalVmgTarget(targetTwaDeg = twaDeg, targetSpeedMs = speedMs, vmgMs = vmgMs)
    }

    /**
     * Calculates polar efficiency comparing live Speed Through Water against theoretical polar target speed.
     * @return Efficiency percentage (0.0 to 200.0%).
     */
    fun calculatePolarEfficiency(stwMs: Double, twsMs: Double, twaRad: Double, polarId: String = activePolarId): Double {
        if (stwMs <= 0.0 || twsMs <= 0.0) return 0.0
        val targetSpeed = getTargetSpeedRad(twsMs, twaRad)
        if (targetSpeed <= 0.001) return 0.0
        val efficiency = (stwMs / targetSpeed) * 100.0
        return efficiency.coerceIn(0.0, 200.0)
    }

    /**
     * Finds optimal upwind True Wind Angle (Radians) that maximizes VMG.
     */
    fun getOptimalUpwindTwaRad(twsMs: Double, sailEfficiency: Double? = null): Double {
        return Math.toRadians(findOptimalTwaDeg(twsMs, 30.0, 65.0, sailEfficiency))
    }

    /**
     * Finds optimal downwind True Wind Angle (Radians) that maximizes VMG.
     */
    fun getOptimalDownwindTwaRad(twsMs: Double, sailEfficiency: Double? = null): Double {
        return Math.toRadians(findOptimalTwaDeg(twsMs, 120.0, 175.0, sailEfficiency))
    }

    private fun findOptimalTwaDeg(twsMs: Double, minTwa: Double, maxTwa: Double, sailEfficiency: Double? = null): Double {
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
            val sampleVmg = getVmg(twsMs, sampleTwa, sailEfficiency)
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
        
        var f1 = getVmg(twsMs, x1, sailEfficiency)
        var f2 = getVmg(twsMs, x2, sailEfficiency)

        repeat(15) { // Fixed iterations for stable precision
            if (f1 < f2) {
                a = x1
                x1 = x2
                x2 = a + phi * (b - a)
                f1 = f2
                f2 = getVmg(twsMs, x2, sailEfficiency)
            } else {
                b = x2
                x2 = x1
                x1 = b - phi * (b - a)
                f2 = f1
                f1 = getVmg(twsMs, x1, sailEfficiency)
            }
        }
        return (a + b) / 2.0
    }

    private fun getVmg(twsMs: Double, twaDeg: Double, sailEfficiency: Double? = null): Double {
        val speed = getTargetSpeedDeg(twsMs, twaDeg, sailEfficiency)
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
