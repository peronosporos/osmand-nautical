package net.osmand.shared.gpx.helper

import net.osmand.shared.IndexConstants
import net.osmand.shared.gpx.GpxFile
import net.osmand.shared.gpx.GpxUtilities.loadGpxFile
import net.osmand.shared.gpx.helper.ImportGpx.errorImport
import net.osmand.shared.gpx.helper.ImportGpx.loadGPXFileFromKml
import net.osmand.shared.io.SourceInputStream
import okio.Source
import okio.source
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

object JvmImportHelper : IImportHelper {

	@Throws(IOException::class)
	override fun loadGPXFileFromArchive(source: Source): Pair<GpxFile, Long> {
		val stream = ZipInputStream(SourceInputStream(source))
		var entry: ZipEntry?
		while ((stream.nextEntry.also { entry = it }) != null) {
			val zipEntry = entry!!
			if (zipEntry.name.endsWith(IndexConstants.GPX_FILE_EXT)) {
				val fileSize = zipEntry.size
				return Pair(loadGpxFile(stream.source()), fileSize)
			}
			if (zipEntry.name.endsWith(IndexConstants.KML_SUFFIX)) {
				return loadGPXFileFromKml(stream.source())
			}
		}
		return errorImport("Archive doesn't have GPX/KLM files")
	}
}

actual val ImportHelper: IImportHelper = JvmImportHelper
