package net.osmand.shared.gpx

import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object AndroidGpxFormatter : IGpxFormatter {

    private val LAT_LON_FORMAT = DecimalFormat("0.00#####", DecimalFormatSymbols(Locale.US))
    private val DECIMAL_FORMAT = DecimalFormat("#.#", DecimalFormatSymbols(Locale.US))

    override fun formatLatLon(value: Double): String = LAT_LON_FORMAT.format(value)

    override fun formatDecimal(value: Double): String = DECIMAL_FORMAT.format(value)
}

actual val GpxFormatter: IGpxFormatter = AndroidGpxFormatter
