package net.osmand.shared.gpx

interface IGpxFormatter {

    // 0.00#####
    fun formatLatLon(value: Double): String

    // #.#
    fun formatDecimal(value: Double): String
}

expect val GpxFormatter: IGpxFormatter
