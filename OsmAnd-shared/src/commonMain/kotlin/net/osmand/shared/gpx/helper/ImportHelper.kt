package net.osmand.shared.gpx.helper

import net.osmand.shared.gpx.GpxFile
import okio.Source

interface IImportHelper {
	fun loadGPXFileFromArchive(source: Source): Pair<GpxFile, Long>
}

expect val ImportHelper: IImportHelper
