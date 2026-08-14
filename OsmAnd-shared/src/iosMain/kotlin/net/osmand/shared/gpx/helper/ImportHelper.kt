package net.osmand.shared.gpx.helper

import net.osmand.shared.gpx.GpxFile
import okio.Source

object IosImportHelper : IImportHelper {
	override fun loadGPXFileFromArchive(source: Source): Pair<GpxFile, Long> {
		throw NotImplementedError("loadGPXFileFromZip not supported on iOS")
	}
}

actual val ImportHelper: IImportHelper = IosImportHelper
