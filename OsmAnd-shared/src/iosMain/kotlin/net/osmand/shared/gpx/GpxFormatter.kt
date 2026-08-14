package net.osmand.shared.gpx

import platform.Foundation.NSLocale
import platform.Foundation.NSNumber
import platform.Foundation.NSNumberFormatter
import platform.Foundation.NSNumberFormatterDecimalStyle

object IosGpxFormatter : IGpxFormatter {

    private val LAT_LON_FORMAT: NSNumberFormatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        locale = NSLocale(localeIdentifier = "en_US")
        // Set a minimum of 2 fraction digits and a maximum of 7 fraction digits.
        usesGroupingSeparator = false
        minimumFractionDigits = 2u
        maximumFractionDigits = 7u
    }

    private val DECIMAL_FORMAT: NSNumberFormatter = NSNumberFormatter().apply {
        numberStyle = NSNumberFormatterDecimalStyle
        locale = NSLocale(localeIdentifier = "en_US")
        usesGroupingSeparator = false
        minimumFractionDigits = 0u
        maximumFractionDigits = 1u
    }

    // 0.00#####
    override fun formatLatLon(value: Double): String =
        LAT_LON_FORMAT.stringFromNumber(NSNumber(value)) ?: ""

    // #.#
    override fun formatDecimal(value: Double): String =
        DECIMAL_FORMAT.stringFromNumber(NSNumber(value)) ?: ""
}

actual val GpxFormatter: IGpxFormatter = IosGpxFormatter
