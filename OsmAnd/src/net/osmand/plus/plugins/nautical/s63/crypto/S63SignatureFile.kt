package net.osmand.plus.plugins.nautical.s63.crypto

import java.math.BigInteger

/**
 * Parsed S-63 ENC signature file (S-63 5.3, 5.4).
 *
 * The file is ASCII text with four signature data strings and four public
 * key parameters, each preceded by a header line:
 *
 *     // Signature part R:   (data server signature of the ENC data)
 *     // Signature part S:
 *     // Signature part R:   (signature of the data server certificate)
 *     // Signature part S:
 *     // BIG p
 *     // BIG q
 *     // BIG g
 *     // BIG y
 *
 * Data strings are uppercase hexadecimal in blocks of four characters,
 * separated by spaces and terminated by a full stop; each data string is on
 * one line ending with CR LF (S-63 5.4.1.1). Producers differ in header
 * casing ("// Big p" and "// BIG p" both occur in the official IHO S-64
 * test data), so headers are matched case-insensitively. The public key
 * section is kept as the exact original bytes because the data server
 * certificate signature is computed over "all bytes within the file" from
 * the "// BIG p" header to the end of the file (S-63 8.5.1.1, 10.6.2 step d).
 */
class S63SignatureFile(
	/** DSA signature (R, S) of the encrypted ENC cell file, S-63 9.5.4, 10.6.3. */
	val cellSignatureR: BigInteger,
	val cellSignatureS: BigInteger,
	/** DSA signature (R, S) of the data server certificate (the public key section below). */
	val certificateSignatureR: BigInteger,
	val certificateSignatureS: BigInteger,
	/** Data server DSA parameters. */
	val p: BigInteger,
	val q: BigInteger,
	val g: BigInteger,
	val y: BigInteger,
	/** Raw bytes from the "// BIG p" header to the end of the file (the signed certificate message). */
	val publicKeySectionBytes: ByteArray
) {

	companion object {

		private val SIGNATURE_PART_HEADER = Regex(
			"//[ \\t]*Signature[ \\t]+part[ \\t]+([RS])[ \\t]*:[ \\t]*\\r?\\n", RegexOption.IGNORE_CASE)
		private val BIG_HEADER = Regex("//[ \\t]*BIG[ \\t]+([pqgy])\\b", RegexOption.IGNORE_CASE)
		private val HEX_GROUP = Regex("[0-9A-Fa-f]+")

		/**
		 * Name of the signature file that belongs to an ENC cell file:
		 * the navigational purpose digit (characters 1 to 6) in the third
		 * position of the cell name is replaced by the letters I to N
		 * (S-63 5.3.2). Example: GB100001.000 -> GBI00001.000,
		 * GB61021A.001 -> GBN1021A.001.
		 *
		 * @return The signature file name, or null when the name is not an
		 * ENC cell name with a navigational purpose digit.
		 */
		fun signatureFileNameFor(cellFileName: String): String? {
			if (cellFileName.length < 4) {
				return null
			}
			val bandDigit = cellFileName[2]
			if (bandDigit !in '1'..'6') {
				return null
			}
			val bandLetter = ('I' + (bandDigit - '1'))
			return cellFileName.substring(0, 2) + bandLetter + cellFileName.substring(3)
		}

		/**
		 * Parses an S-63 signature file.
		 *
		 * @return The parsed file, or null when the format is incorrect
		 * (expected system error SSE 24).
		 */
		fun parse(bytes: ByteArray): S63SignatureFile? {
			if (bytes.isEmpty()) {
				return null
			}
			// ISO-8859-1 preserves byte offsets, which are needed for the raw public key section.
			val text = String(bytes, Charsets.ISO_8859_1)

			val parts = SIGNATURE_PART_HEADER.findAll(text).toList()
			if (parts.size != 4) {
				return null
			}
			val signatureValues = arrayOfNulls<BigInteger>(4)
			for ((index, match) in parts.withIndex()) {
				if (match.groupValues[1].uppercase() != expectedPartLabel(index)) {
					return null
				}
				val hex = dataStringOnNextLine(text, match.range.last + 1) ?: return null
				signatureValues[index] = BigInteger(hex, 16)
			}
			val bigParams = HashMap<String, Pair<Int, BigInteger>>()
			for (match in BIG_HEADER.findAll(text)) {
				val key = match.groupValues[1].lowercase()
				if (bigParams.containsKey(key)) {
					return null
				}
				val hex = dataStringOnNextLine(text, match.range.last + 1) ?: return null
				bigParams[key] = Pair(match.range.first, BigInteger(hex, 16))
			}
			val publicKeySectionOffset = bigParams["p"]?.first ?: return null
			val p = bigParams["p"]?.second ?: return null
			val q = bigParams["q"]?.second ?: return null
			val g = bigParams["g"]?.second ?: return null
			val y = bigParams["y"]?.second ?: return null
			if (p.signum() <= 0 || q.signum() <= 0 || g.signum() <= 0 || y.signum() <= 0) {
				return null
			}
			return S63SignatureFile(
				cellSignatureR = signatureValues[0]!!,
				cellSignatureS = signatureValues[1]!!,
				certificateSignatureR = signatureValues[2]!!,
				certificateSignatureS = signatureValues[3]!!,
				p = p, q = q, g = g, y = y,
				publicKeySectionBytes = bytes.copyOfRange(publicKeySectionOffset, bytes.size))
		}

		/** Expected header sequence is R, S, R, S (S-63 5.4.2.5, 9.5.4). */
		private fun expectedPartLabel(index: Int): String = if (index % 2 == 0) "R" else "S"

		/**
		 * Extracts the hexadecimal data string that follows a header line.
		 * The data string occupies the remainder of the line after the
		 * header, with spaces and the terminating full stop ignored
		 * (S-63 5.4.1.1). Line bounded so that header text of the next
		 * element can never bleed into the value.
		 */
		private fun dataStringOnNextLine(text: String, from: Int): String? {
			var start = from
			// Skip the line break that ends the header line (the header regex
			// of the BIG parameters does not consume it).
			if (start < text.length && (text[start] == '\r' || text[start] == '\n')) {
				start += if (text[start] == '\r' && start + 1 < text.length && text[start + 1] == '\n') 2 else 1
			}
			var end = text.length
			for (lineBreak in charArrayOf('\r', '\n')) {
				val found = text.indexOf(lineBreak, start)
				if (found in start until end) {
					end = found
				}
			}
			val segment = text.substring(start, end)
			val hex = HEX_GROUP.findAll(segment).map { it.value }.toList()
			if (hex.isEmpty() || hex.any { it.length > 4 }) {
				return null
			}
			return hex.joinToString("")
		}
	}
}
