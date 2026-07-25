package net.osmand.plus.plugins.nautical.maneuvers

import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
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

    private var twsValues: DoubleArray = doubleArrayOf() // m/s
    private var twaValues: DoubleArray = doubleArrayOf() // Degrees
    // 2D grid: speedTable[twaIndex][twsIndex] = target speed (m/s)
    private var speedTable: Array<DoubleArray> = arrayOf()

    var isLoaded: Boolean = false
        get() = lock.read { field }
        private set(value) {
            lock.write { field = value }
        }

    private val MS_TO_KNOTS = 1.94384
    private val KNOTS_TO_MS = 0.514444

    /**
     * Parses standard CSV polar diagrams.
     * Assumes speeds in Knots, converts to m/s internally.
     */
    fun loadFromCsv(inputStream: InputStream): Boolean {
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
                twsValues = parsedTws.toDoubleArray()
                twaValues = parsedTwa.toDoubleArray()
                speedTable = parsedRows.toTypedArray()
                isLoaded = true
            }
            return true
        } catch (_: Exception) {
            return false
        }
    }

    /**
     * Parses Signal K Resources API JSON schema for polars.
     * Assumes SI units (m/s, Radians).
     */
    fun loadFromSignalKJson(jsonString: String): Boolean {
        try {
            val json = JSONObject(jsonString)
            val root = if (json.has("value")) json.getJSONObject("value") else json

            val twsArr = root.optJSONArray("tws") ?: root.optJSONArray("windSpeeds") ?: return false
            val twaArr = root.optJSONArray("twa") ?: root.optJSONArray("windAngles") ?: return false
            val speedsArr = root.optJSONArray("speeds") ?: root.optJSONArray("polarTable") ?: return false

            val parsedTws = DoubleArray(twsArr.length()) { i -> twsArr.getDouble(i) }
            // If Signal K sends Radians, convert to Degrees for internal table consistency
            val parsedTwa = DoubleArray(twaArr.length()) { i -> Math.toDegrees(twaArr.getDouble(i)) }

            val parsedRows = Array(twaArr.length()) { i ->
                val rowJson = speedsArr.getJSONArray(i)
                DoubleArray(twsArr.length()) { j -> rowJson.getDouble(j) }
            }

            if (parsedTws.isEmpty() || parsedTwa.isEmpty() || parsedRows.isEmpty()) {
                return false
            }

            lock.write {
                twsValues = parsedTws
                twaValues = parsedTwa
                speedTable = parsedRows
                isLoaded = true
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
        val loaded = lock.read { isLoaded }
        val absTwa = abs(twaDeg)
        if (!loaded) {
            return defaultTargetSpeed(absTwa)
        }

        lock.read {
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

        var bestTwa = (minTwa + maxTwa) / 2.0
        var maxVmg = -1.0

        var angle = minTwa
        while (angle <= maxTwa) {
            val speed = getTargetSpeedDeg(twsMs, angle)
            val vmg = speed * abs(cos(Math.toRadians(angle)))
            if (vmg > maxVmg) {
                maxVmg = vmg
                bestTwa = angle
            }
            angle += 0.5
        }
        return bestTwa
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
