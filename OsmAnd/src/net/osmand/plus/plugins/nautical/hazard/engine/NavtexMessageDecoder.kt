package net.osmand.plus.plugins.nautical.hazard.engine

/**
 * Advanced decoder for standard NAVTEX ASCII format.
 * Format: ZCZC [Station ID][Subject Indicator][Message Sequence Number] ... NNNN
 */
object NavtexMessageDecoder {

    private const val MESSAGE_START = "ZCZC"
    private const val MESSAGE_END = "NNNN"
    // B1 (A-Z) B2 (A-Z) B3B4 (00-99) directly after ZCZC
    private val HEADER_ID = Regex("ZCZC\\s*([A-Z][A-Z]\\d{1,2})")

    /**
     * Decodes a raw NAVTEX message block.
     */
    fun decode(rawBlock: String): NavtexMessage? {
        val trimmed = rawBlock.trim()
        if (!trimmed.startsWith(MESSAGE_START) || !trimmed.endsWith(MESSAGE_END)) {
            // Check if it contains the markers even if not at start/end
            val start = trimmed.indexOf(MESSAGE_START)
            val end = trimmed.lastIndexOf(MESSAGE_END)
            if (start == -1 || end == -1 || end <= start) return null
        }

        val lines = trimmed.lines().filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        // Line 1 usually contains ZCZC [B1B2B3B4]: B1 = station letter,
        // B2 = subject indicator, B3B4 = serial number. Some feeds omit
        // the space after ZCZC, so match the group directly.
        val headerLine = lines.firstOrNull { it.contains(MESSAGE_START) } ?: return null
        val idPart = HEADER_ID.find(headerLine)?.groupValues?.get(1) ?: return null
        if (idPart.length != 4) return null

        val stationLetter = idPart[0]
        val subjectChar = idPart[1]
        val sequenceNumber = idPart.substring(2).toIntOrNull() ?: 0
        val subject = NavtexSubject.fromCode(subjectChar)

        // The rest is the body
        val bodyLines = lines.filter { !it.contains(MESSAGE_START) && !it.contains(MESSAGE_END) }
        val body = bodyLines.joinToString("\n")

        return NavtexMessage(
            id = idPart,
            stationLetter = stationLetter,
            subject = subject,
            sequenceNumber = sequenceNumber,
            timestamp = System.currentTimeMillis(),
            body = body,
            points = NavtexSentenceParser.extractCoordinates(body),
            isUrgent = isUrgent(subject)
        )
    }

    private fun isUrgent(subject: NavtexSubject): Boolean {
        // Non-rejectable categories (subject indicators A, B, D, plus L
        // which is additional to A) are always treated as urgent per the
        // NAVTEX receiver requirements.
        return subject == NavtexSubject.NAVIGATIONAL_WARNING ||
               subject == NavtexSubject.METEOROLOGICAL_WARNING ||
               subject == NavtexSubject.SEARCH_AND_RESCUE ||
               subject == NavtexSubject.NAVIGATIONAL_WARNING_ADDITIONAL
    }
}
