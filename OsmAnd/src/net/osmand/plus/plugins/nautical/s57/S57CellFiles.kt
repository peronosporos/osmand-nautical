package net.osmand.plus.plugins.nautical.s57

import java.io.File

/**
 * S-57 cell file classification.
 *
 * An S-57 chart consists of a base cell (GB100001.000, or an S-63 encrypted
 * container named *.ENC) and zero or more sequential update files
 * (GB100001.001 ... GB100001.999) that must be applied to their base in
 * ascending numeric order (IHO S-57 Part 3 section 2.6). S-63 exchange sets
 * keep the same file naming and extensions (S-63 3.1); update cells carry
 * the same cell key as their base (verified against the IHO S-64 test data
 * set, see S63IhoTestVectorTest).
 *
 * The S-57 catalogue file CATALOG.031 shares the ENC_ROOT directory and its
 * .031 extension lies inside the update numbering range, so it is recognized
 * by name and kept separate from cell updates.
 */
object S57CellFiles {

    private const val BASE_EXTENSION = "000"
    private const val ENCRYPTED_EXTENSION = "ENC"
    private const val CATALOG_CELL_NAME = "CATALOG"
    private const val CATALOG_EXTENSION = "031"

    fun extension(name: String): String =
            name.substringAfterLast('.', "").uppercase()

    /** The cell name shared by a base cell and its updates ("GB100001"). */
    fun cellName(name: String): String =
            name.substringBeforeLast('.').uppercase()

    fun isBaseFile(name: String): Boolean {
        // A base cell needs the 8-character cell name before the extension;
        // a dotfile like ".000" has an empty stem and is not a chart.
        if (cellName(name).isEmpty()) {
            return false
        }
        val ext = extension(name)
        return ext == BASE_EXTENSION || ext == ENCRYPTED_EXTENSION
    }

    fun isCatalogFile(name: String): Boolean =
            extension(name) == CATALOG_EXTENSION && cellName(name) == CATALOG_CELL_NAME

    /**
     * True for S-63 ENC signature files (S-63 5.3.2): the cell name with the
     * navigational purpose digit in the third position replaced by the band
     * letters I to N (GB100001.000 -> GBI00001.000). Signature files keep
     * the cell extension, so by extension alone they are indistinguishable
     * from base cells and must be excluded from the chart pipeline.
     */
    fun isSignatureFileName(name: String): Boolean {
        if (name.length < 4) {
            return false
        }
        val band = name[2].uppercaseChar()
        return band in 'I'..'N'
    }

    /** True for update files .001 ... .999 (the catalogue is not an update). */
    fun isUpdateFile(name: String): Boolean {
        if (isCatalogFile(name) || cellName(name).isEmpty()) {
            return false
        }
        val ext = extension(name)
        if (ext.length != 3 || !ext.all { it.isDigit() }) {
            return false
        }
        val number = ext.toIntOrNull() ?: return false
        return number in 1..999
    }

    /** True for every file the chart pipeline should consider. */
    fun isChartFile(name: String): Boolean =
            isBaseFile(name) || isCatalogFile(name) || isUpdateFile(name)

    /** 0 for base and catalogue files, the update number (1..999) otherwise. */
    fun updateNumber(name: String): Int =
            if (isUpdateFile(name)) extension(name).toInt() else 0

    /**
     * Orders the files of one cell group: base (or catalogue) first, then
     * updates by ascending number, so update instructions are applied in
     * the order the data server issued them. The first key must sort the
     * base/catalogue (isUpdateFile == false) before the updates (true);
     * the historic !isUpdateFile key inverted the group and applied
     * updates before their base cell.
     */
    fun sortGroup(files: List<File>): List<File> =
            files.sortedWith(
                    compareBy({ isUpdateFile(it.name) }, { updateNumber(it.name) }, { it.name.uppercase() })
            )
}
