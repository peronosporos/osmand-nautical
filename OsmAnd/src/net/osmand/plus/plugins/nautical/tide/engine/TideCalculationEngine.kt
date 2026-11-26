package net.osmand.plus.plugins.nautical.tide.engine

import net.osmand.PlatformUtil
import net.osmand.plus.plugins.nautical.tide.model.TidePrediction
import net.osmand.plus.plugins.nautical.tide.model.TideStation
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Harmonic tide prediction: h(t) = datum + SUM( Hc * f * cos( V(t) + u - g ) )
 *
 * V(t) is the constituent's Doodson equilibrium argument evaluated at time t from the
 * mean astronomical elements (Moon/Sun longitudes, lunar perigee, ascending node and
 * solar perigee, Meeus polynomial fits in Julian centuries from J2000); u and f are the
 * nodal corrections (Schureman). This replaces the previous implementation which added
 * the constituent's speed*t evolution TWICE (once explicitly in the angle and once
 * inside getV0), so every tide curve oscillated at double frequency: an M2-only
 * station cycled at ~6.2 h instead of 12.42 h and every displayed HW/LW time was wrong.
 *
 * The constituent registry below (Doodson coefficient vectors [tau, s, h, p, N, pp, 90]
 * plus u/f nodal formulas) is transcribed from pytides (MIT license, github.com/
 * sam-cox/pytides: astro.py, constituent.py, nodal_corrections.py, following Schureman)
 * and was numerically verified against the canonical constituent speeds:
 * M2 28.9841042 / S2 30.0 / N2 28.4397295 / K1 15.0410686 / O1 13.9430356 /
 * P1 14.9589314 / K2 30.0821373 / M4 57.9682084 / Q1 13.3986609 / MU2 27.9682084 /
 * NU2 28.5125831 / L2 29.5284789 / T2 29.9589333 degrees/hour (agreement 1e-7).
 */
class TideCalculationEngine {

    private val log = PlatformUtil.getLog(TideCalculationEngine::class.java)

    /** Constituent names seen without a registry entry; each logged only once. */
    private val unsupportedNames = ConcurrentHashMap.newKeySet<String>()

    // ---------------------------------------------------------------------------------------
    // Astronomy. Mean-element polynomials (degrees vs T = Julian centuries from J2000.0),
    // transcribed from pytides astro.py (Meeus, Astronomical Algorithms).
    // ---------------------------------------------------------------------------------------

    private class AstroArgs(ms: Long) {
        /** Julian centuries from J2000.0 at this instant. */
        val t: Double
        val s: Double      // Moon mean longitude (deg)
        val h: Double      // Sun mean longitude (deg)
        val p: Double      // Lunar perigee mean longitude (deg)
        private val nRaw: Double // Moon ascending node longitude (deg), unmodded for xi/nu
        val nMod: Double
        val pp: Double     // Solar perigee mean longitude (deg)
        val omega: Double  // Terrestrial obliquity (deg)
        val tau: Double    // "T+h-s": mean lunar time spanning argument (deg)
        val I: Double      // Lunar inclination to the equator (deg)
        val xi: Double     // Nodal correction angle xi (deg)
        val nu: Double     // Nodal correction angle nu (deg)
        val nup: Double    // Schureman nu' (deg)
        val nupp: Double   // Schureman nu'' (deg)
        // Kotlin note: @get:JvmName avoids a JVM signature clash with the
        // lowercase lunar perigee p (both would map to getP()).
        @get:JvmName("getSchuremanP")
        val P: Double      // Schureman P = p - xi (deg)

        init {
            val jd = ms / 86400000.0 + 2440587.5
            t = (jd - 2451545.0) / 36525.0
            s = mod360(polynomial(S_COEF, t))
            h = mod360(polynomial(H_COEF, t))
            p = mod360(polynomial(P_COEF, t))
            nRaw = polynomial(N_COEF, t)
            nMod = mod360(nRaw)
            pp = mod360(polynomial(PP_COEF, t))
            omega = mod360(polynomial(OMEGA_COEF, t))
            val hour = (jd - Math.floor(jd)) * 360.0
            tau = hour + h - s

            // Nodal geometry (N, i, omega -> I, xi, nu; Schureman Table 6 notes).
            val n = Math.toRadians(nRaw)
            val i = Math.toRadians(LUNAR_INCLINATION_DEG)
            val om = Math.toRadians(omega)
            val cosI = cos(i) * cos(om) - sin(i) * sin(om) * cos(n)
            val iRad = acos(cosI)
            I = Math.toDegrees(iRad)
            var e1 = atan(cos(0.5 * (om - i)) / cos(0.5 * (om + i)) * tan(0.5 * n))
            var e2 = atan(sin(0.5 * (om - i)) / sin(0.5 * (om + i)) * tan(0.5 * n))
            e1 -= 0.5 * n
            e2 -= 0.5 * n
            xi = -Math.toDegrees(e1 + e2)
            nu = Math.toDegrees(e1 - e2)

            val nuRad = Math.toRadians(nu)
            nup = Math.toDegrees(
                atan(sin(2 * iRad) * sin(nuRad) / (sin(2 * iRad) * cos(nuRad) + 0.3347))
            )
            nupp = 0.5 * Math.toDegrees(
                atan(sin(iRad).pow(2) * sin(2 * nuRad) / (sin(iRad).pow(2) * cos(2 * nuRad) + 0.0727))
            )
            P = mod360(p - xi)
        }
    }

    companion object {
        private fun mod360(x: Double): Double {
            var v = x % 360.0
            if (v < 0.0) v += 360.0
            return v
        }

        private fun polynomial(coefficients: DoubleArray, t: Double): Double {
            var result = 0.0
            var power = 1.0
            for (c in coefficients) {
                result += c * power
                power *= t
            }
            return result
        }

        /** First derivative of [polynomial] (degree per unit t). */
        private fun polynomialDerivative(coefficients: DoubleArray, t: Double): Double {
            var result = 0.0
            var power = 1.0 // holds t^(i-1)
            for (i in coefficients.indices) {
                if (i > 0) result += coefficients[i] * i * power
                power *= t
            }
            return result
        }

        private val S_COEF = doubleArrayOf(218.3164591, 481267.88134236, -0.0013268, 1.0 / 538841.0, -1.0 / 65194000.0)
        private val H_COEF = doubleArrayOf(280.46645, 36000.76983, 0.0003032)
        private val P_COEF = doubleArrayOf(83.3532430, 4069.0137111, -0.0103238, -1.0 / 80053.0, 1.0 / 18999000.0)
        private val N_COEF = doubleArrayOf(125.0445550, -1934.1361849, 0.0020762, 1.0 / 467410.0, -1.0 / 60616000.0)
        private val PP_COEF = doubleArrayOf(
            280.46645 - 357.52910,
            36000.76932 - 35999.05030,
            0.0003032 + 0.0001559,
            0.00000048
        )
        // Obliquity coefficients already adjusted for the T parameter
        // (see pytides astro.py: c_i * 0.01^i on the arcsecond-based Meeus terms).
        private val OMEGA_COEF = doubleArrayOf(23.4392911111, -0.0130025833, -4.3055556e-8, 5.5534722e-7)
        private const val LUNAR_INCLINATION_DEG = 5.145

        /** Doodson spanning arguments in registry order. */
        private val ARG_90 = doubleArrayOf(0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 90.0)
    }

    // ---------------------------------------------------------------------------------------
    // Node factors (Schureman, transcribed from pytides nodal_corrections.py)
    // ---------------------------------------------------------------------------------------

    private fun fM2(a: AstroArgs): Double {
        val om = Math.toRadians(a.omega)
        val i = Math.toRadians(LUNAR_INCLINATION_DEG)
        val iR = Math.toRadians(a.I)
        val mean = cos(0.5 * om).pow(4) * cos(0.5 * i).pow(4)
        return cos(0.5 * iR).pow(4) / mean
    }

    private fun fO1(a: AstroArgs): Double {
        val om = Math.toRadians(a.omega)
        val i = Math.toRadians(LUNAR_INCLINATION_DEG)
        val iR = Math.toRadians(a.I)
        val mean = sin(om) * cos(0.5 * om).pow(2) * cos(0.5 * i).pow(4)
        return (sin(iR) * cos(0.5 * iR).pow(2)) / mean
    }

    private fun fK1(a: AstroArgs): Double {
        val om = Math.toRadians(a.omega)
        val i = Math.toRadians(LUNAR_INCLINATION_DEG)
        val iR = Math.toRadians(a.I)
        val nu = Math.toRadians(a.nu)
        val mean = 0.5023 * sin(2 * om) * (1 - 1.5 * sin(i).pow(2)) + 0.1681
        val num = 0.2523 * sin(2 * iR).pow(2) + 0.1689 * sin(2 * iR) * cos(nu) + 0.0283
        return sqrt(num) / mean
    }

    /** Schureman equations 77, 69. */
    private fun fJ1(a: AstroArgs): Double {
        val om = Math.toRadians(a.omega)
        val i = Math.toRadians(LUNAR_INCLINATION_DEG)
        val iR = Math.toRadians(a.I)
        val mean = sin(2 * om) * (1 - 1.5 * sin(i).pow(2))
        return sin(2 * iR) / mean
    }

    private fun fK2(a: AstroArgs): Double {
        val om = Math.toRadians(a.omega)
        val i = Math.toRadians(LUNAR_INCLINATION_DEG)
        val iR = Math.toRadians(a.I)
        val nu = Math.toRadians(a.nu)
        val mean = 0.5023 * sin(om).pow(2) * (1 - 1.5 * sin(i).pow(2)) + 0.0365
        val num = 0.2523 * sin(iR).pow(4) + 0.0367 * sin(iR).pow(2) * cos(2 * nu) + 0.0013
        return sqrt(num) / mean
    }

    private fun fL2(a: AstroArgs): Double {
        val pR = Math.toRadians(a.P)
        val iR = Math.toRadians(a.I)
        val raInv = sqrt(1 - 12 * tan(0.5 * iR).pow(2) * cos(2 * pR) + 36 * tan(0.5 * iR).pow(4))
        return fM2(a) * raInv
    }

    private fun uM2(a: AstroArgs): Double = 2 * a.xi - 2 * a.nu
    private fun uO1(a: AstroArgs): Double = 2 * a.xi - a.nu
    private fun uK1(a: AstroArgs): Double = -a.nup
    private fun uK2(a: AstroArgs): Double = -2 * a.nupp

    /** Schureman 214: L2 has its own phase correction R. */
    private fun uL2(a: AstroArgs): Double {
        val iR = Math.toRadians(a.I)
        val pR = Math.toRadians(a.P)
        val r = Math.toDegrees(atan(sin(2 * pR) / (1.0 / 6.0 * tan(0.5 * iR).pow(-2) - cos(2 * pR))))
        return 2 * a.xi - 2 * a.nu - r
    }

    // ---------------------------------------------------------------------------------------
    // Constituent registry: Doodson coefficient vectors [tau, s, h, p, N, pp, 90-offset].
    // ---------------------------------------------------------------------------------------

    private class ConstituentDef(
        val coefficients: IntArray,
        val u: (AstroArgs) -> Double,
        val f: (AstroArgs) -> Double
    )

    private val registry: Map<String, ConstituentDef> = run {
        val m = HashMap<String, ConstituentDef>()
        val unity: (AstroArgs) -> Double = { 1.0 }
        val zero: (AstroArgs) -> Double = { 0.0 }
        fun put(name: String, c: IntArray, u: (AstroArgs) -> Double, f: (AstroArgs) -> Double) {
            m[name] = ConstituentDef(c, u, f)
        }
        // --- NOAA harmonic constituent set (pytides "noaa" order, base + compounds) ---
        put("M2", intArrayOf(2, 0, 0, 0, 0, 0, 0), { a -> uM2(a) }, { a -> fM2(a) })
        put("S2", intArrayOf(2, 2, -2, 0, 0, 0, 0), zero, unity)
        put("N2", intArrayOf(2, -1, 0, 1, 0, 0, 0), { a -> uM2(a) }, { a -> fM2(a) })
        put("K1", intArrayOf(1, 1, 0, 0, 0, 0, -1), { a -> uK1(a) }, { a -> fK1(a) })
        put("M4", intArrayOf(4, 0, 0, 0, 0, 0, 0), { a -> 2 * uM2(a) }, { a -> fM2(a).let { it * it } })
        put("O1", intArrayOf(1, -1, 0, 0, 0, 0, 1), { a -> uO1(a) }, { a -> fO1(a) })
        put("M6", intArrayOf(6, 0, 0, 0, 0, 0, 0), { a -> 3 * uM2(a) }, { a -> fM2(a).pow(3) })
        put("MK3", intArrayOf(3, 1, 0, 0, 0, 0, -1), { a -> uM2(a) + uK1(a) }, { a -> fM2(a) * fK1(a) })
        put("S4", intArrayOf(4, 4, -4, 0, 0, 0, 0), zero, unity)
        put("MN4", intArrayOf(4, -1, 0, 1, 0, 0, 0), { a -> 2 * uM2(a) }, { a -> fM2(a).let { it * it } })
        put("NU2", intArrayOf(2, -1, 2, -1, 0, 0, 0), { a -> uM2(a) }, { a -> fM2(a) })
        put("S6", intArrayOf(6, 6, -6, 0, 0, 0, 0), zero, unity)
        put("MU2", intArrayOf(2, -2, 2, 0, 0, 0, 0), { a -> 2 * uM2(a) }, { a -> fM2(a).let { it * it } })
        put("2N2", intArrayOf(2, -2, 0, 2, 0, 0, 0), { a -> uM2(a) }, { a -> fM2(a) })
        put("OO1", intArrayOf(1, 3, 0, 0, 0, 0, -1), { a -> -2 * a.xi - a.nu },
            { a ->
                val om = Math.toRadians(a.omega)
                val i = Math.toRadians(LUNAR_INCLINATION_DEG)
                val iR = Math.toRadians(a.I)
                val mean = sin(om) * sin(0.5 * om).pow(2) * cos(0.5 * i).pow(4)
                sin(iR) * sin(0.5 * iR).pow(2) / mean
            })
        put("LAMBDA2", intArrayOf(2, 1, -2, 1, 0, 0, 2), { a -> uM2(a) }, { a -> fM2(a) })
        put("S1", intArrayOf(1, 1, -1, 0, 0, 0, 0), zero, unity)
        put("M1", intArrayOf(1, 0, 0, 0, 0, 0, 1),
            { a -> uM1(a) },
            { a ->
                val iR = Math.toRadians(a.I)
                val pR = Math.toRadians(a.P)
                fO1(a) * sqrt(
                    0.25 + 1.5 * cos(iR) * cos(2 * pR) * cos(0.5 * iR).pow(-0.5) +
                        2.25 * cos(iR).pow(2) * cos(0.5 * iR).pow(-4)
                )
            })
        put("J1", intArrayOf(1, 2, 0, -1, 0, 0, -1), { a -> -a.nu }, { a -> fJ1(a) })
        put("MM", intArrayOf(0, 1, 0, -1, 0, 0, 0), zero,
            { a ->
                val om = Math.toRadians(a.omega)
                val i = Math.toRadians(LUNAR_INCLINATION_DEG)
                val iR = Math.toRadians(a.I)
                val mean = (2.0 / 3.0 - sin(om).pow(2)) * (1 - 1.5 * sin(i).pow(2))
                (2.0 / 3.0 - sin(iR).pow(2)) / mean
            })
        put("SSA", intArrayOf(0, 0, 2, 0, 0, 0, 0), zero, unity)
        put("SA", intArrayOf(0, 0, 1, 0, 0, 0, 0), zero, unity)
        put("MSF", intArrayOf(0, 2, -2, 0, 0, 0, 0), { a -> -uM2(a) }, { a -> fM2(a) })
        put("MF", intArrayOf(0, 2, 0, 0, 0, 0, 0), { a -> -2 * a.xi },
            { a ->
                val om = Math.toRadians(a.omega)
                val i = Math.toRadians(LUNAR_INCLINATION_DEG)
                val iR = Math.toRadians(a.I)
                val mean = sin(om).pow(2) * cos(0.5 * i).pow(4)
                sin(iR).pow(2) / mean
            })
        put("RHO1", intArrayOf(1, -2, 2, -1, 0, 0, 1), { a -> uM2(a) - uK1(a) }, { a -> fM2(a) * fK1(a) })
        put("Q1", intArrayOf(1, -2, 0, 1, 0, 0, 1), { a -> uO1(a) }, { a -> fO1(a) })
        put("T2", intArrayOf(2, 2, -3, 0, 0, 1, 0), zero, unity)
        put("R2", intArrayOf(2, 2, -1, 0, 0, -1, 2), zero, unity)
        put("2Q1", intArrayOf(1, -3, 0, 2, 0, 0, 1), { a -> uM2(a) + a.nu }, { a -> fM2(a) * fJ1(a) })
        put("P1", intArrayOf(1, 1, -2, 0, 0, 0, 1), zero, unity)
        put("2SM2", intArrayOf(2, 4, -4, 0, 0, 0, 0), { a -> -uM2(a) }, { a -> fM2(a) })
        put("M3", intArrayOf(3, 0, 0, 0, 0, 0, 0), { a -> 1.5 * uM2(a) }, { a -> fM2(a).pow(1.5) })
        put("L2", intArrayOf(2, 1, 0, -1, 0, 0, 2), { a -> uL2(a) }, { a -> fL2(a) })
        put("2MK3", intArrayOf(3, -1, 0, 0, 0, 0, 1), { a -> uM2(a) + uO1(a) }, { a -> fM2(a) * fO1(a) })
        put("K2", intArrayOf(2, 2, 0, 0, 0, 0, 0), { a -> uK2(a) }, { a -> fK2(a) })
        put("M8", intArrayOf(8, 0, 0, 0, 0, 0, 0), { a -> 4 * uM2(a) }, { a -> fM2(a).pow(4) })
        put("MS4", intArrayOf(4, 2, -2, 0, 0, 0, 0), { a -> uM2(a) }, { a -> fM2(a) })
        put("Z0", intArrayOf(0, 0, 0, 0, 0, 0, 0), zero, unity)
        m
    }

    /** Schureman 202: M1 phase correction Q. */
    private fun uM1(a: AstroArgs): Double {
        val iR = Math.toRadians(a.I)
        val pR = Math.toRadians(a.P)
        val q = Math.toDegrees(atan((5 * cos(iR) - 1) / (7 * cos(iR) + 1) * tan(pR)))
        return a.xi - a.nu + q
    }

    // ---------------------------------------------------------------------------------------
    // Height prediction
    // ---------------------------------------------------------------------------------------

    /**
     * Canonical equilibrium speed of a constituent in degrees per hour, derived
     * from the Doodson registry and the mean-element rates at [timestampMs].
     * Returns null for constituent names unknown to the registry.
     */
    fun constituentSpeedDegPerHour(name: String, timestampMs: Long): Double? {
        val def = registry[name.uppercase(java.util.Locale.US)] ?: return null
        val astro = AstroArgs(timestampMs)
        val dTh = 1.0 / (36525.0 * 24.0) // Julian centuries per hour
        val sDot = polynomialDerivative(S_COEF, astro.t) * dTh
        val hDot = polynomialDerivative(H_COEF, astro.t) * dTh
        val pDot = polynomialDerivative(P_COEF, astro.t) * dTh
        val ppDot = polynomialDerivative(PP_COEF, astro.t) * dTh
        val tauDot = 15.0 + hDot - sDot
        // The lunar-node rate (~-0.002 deg/h) is not included: no registry vector
        // uses the N slot, and the quarter-cycle slot is a constant phase offset.
        val rates = doubleArrayOf(tauDot, sDot, hDot, pDot, 0.0, ppDot, 0.0)
        var v = 0.0
        for (j in def.coefficients.indices) {
            v += def.coefficients[j] * rates[j]
        }
        return v
    }

    /**
     * Calculates the predicted water height for a given station at a specific timestamp.
     *
     * @param station The TideStation containing harmonic constituents.
     * @param timestampMs Unix timestamp in milliseconds (UTC).
     * @return Predicted height in meters (station datum included as base level).
     */
    fun calculateHeight(station: TideStation, timestampMs: Long): Double {
        if (station.constituents.isEmpty()) return station.datum

        val astro = AstroArgs(timestampMs)
        val args = doubleArrayOf(astro.tau, astro.s, astro.h, astro.p, astro.nMod, astro.pp, 90.0)

        var height = station.datum
        for (constituent in station.constituents) {
            val def = registry[constituent.name.uppercase(java.util.Locale.US)]
            if (def == null) {
                if (unsupportedNames.add(constituent.name.uppercase(java.util.Locale.US))) {
                    log.warn("Tide engine: no Doodson definition for constituent '${constituent.name}' (station '${station.name}'); it is excluded from the prediction.")
                }
                continue
            }
            var v = 0.0
            for (j in def.coefficients.indices) {
                v += def.coefficients[j] * args[j]
            }
            val angle = Math.toRadians(mod360(v + def.u(astro) - constituent.epoch))
            height += constituent.amplitude * def.f(astro) * cos(angle)
        }
        return height
    }

    /**
     * Finds high and low tides within a 24-hour window starting from [startTimeMs].
     */
    fun predictTides(station: TideStation, startTimeMs: Long): List<TidePrediction> {
        if (station.constituents.isEmpty()) return emptyList()

        val predictions = mutableListOf<TidePrediction>()
        val stepMs = TimeUnit.MINUTES.toMillis(10) // 10-minute resolution
        val windowMs = TimeUnit.HOURS.toMillis(24)

        var prevHeight = calculateHeight(station, startTimeMs - stepMs)
        var currentHeight = calculateHeight(station, startTimeMs)

        for (t in (startTimeMs..(startTimeMs + windowMs)) step stepMs) {
            val nextHeight = calculateHeight(station, t + stepMs)

            // Peak/Valley Detection
            if (currentHeight > prevHeight && currentHeight > nextHeight) {
                // Local Maximum (High Tide)
                val exactTime = findExactPeak(station, t - stepMs, t + stepMs, isHigh = true)
                predictions.add(
                    TidePrediction(
                        timestamp = exactTime,
                        heightMeters = calculateHeight(station, exactTime),
                        heightAboveDatum = calculateHeight(station, exactTime),
                        meanSeaLevel = station.datum,
                        isHighTide = true
                    )
                )
            } else if (currentHeight < prevHeight && currentHeight < nextHeight) {
                // Local Minimum (Low Tide)
                val exactTime = findExactPeak(station, t - stepMs, t + stepMs, isHigh = false)
                predictions.add(
                    TidePrediction(
                        timestamp = exactTime,
                        heightMeters = calculateHeight(station, exactTime),
                        heightAboveDatum = calculateHeight(station, exactTime),
                        meanSeaLevel = station.datum,
                        isHighTide = false
                    )
                )
            }

            prevHeight = currentHeight
            currentHeight = nextHeight
        }

        return predictions
    }

    private fun findExactPeak(station: TideStation, tStart: Long, tEnd: Long, isHigh: Boolean): Long {
        var low = tStart
        var high = tEnd
        // Ternary search for local extremum
        repeat(8) {
            val m1 = low + (high - low) / 3
            val m2 = high - (high - low) / 3
            val h1 = calculateHeight(station, m1)
            val h2 = calculateHeight(station, m2)
            if (isHigh) {
                if (h1 < h2) low = m1 else high = m2
            } else {
                if (h1 > h2) low = m1 else high = m2
            }
        }
        return (low + high) / 2
    }
}
