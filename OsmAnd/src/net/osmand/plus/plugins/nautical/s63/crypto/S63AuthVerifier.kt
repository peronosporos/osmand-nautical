package net.osmand.plus.plugins.nautical.s63.crypto

import java.io.InputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.security.cert.CertificateFactory
import java.util.Locale

/**
 * S-63 data authentication engine (S-63 10.6.2 and 10.6.3).
 *
 * Verification model, confirmed byte for byte against the official IHO
 * S-64 ENC Test Data Set (S-63 TDS v1.2.1, Authentication tests 4a to 4f
 * and 5a to 5c):
 *
 * 1. The data server certificate stored in the signature file is the
 *    signature (second R, S pair) over the raw bytes of the public key
 *    section (from the "// BIG p" header to the end of the file) verified
 *    with the Scheme Administrator (SA) public key. Success means the ENC
 *    is authenticated by the IHO acting as the SA.
 * 2. When the certificate does not verify against the SA key, S-63
 *    10.6.2.1 allows authentication against another certificate or public
 *    key stored on the data client. If it verifies with one of those keys,
 *    processing continues after warning SSE 26 ("This ENC is not
 *    authenticated by the IHO acting as the Scheme Administrator"). If it
 *    verifies with no installed key, processing must terminate with
 *    expected system error SSE 06.
 * 3. The ENC signature (first R, S pair) is the DSA/SHA-1 signature of the
 *    data server over the entire encrypted ENC file, verified with the
 *    data server public key from the signature file. Failure means
 *    expected system error SSE 09 and the cell must not be decrypted.
 */
object S63AuthVerifier {

	/** A DSA public key with its parameters. */
	data class DsaKey(val p: BigInteger, val q: BigInteger, val g: BigInteger, val y: BigInteger) {

		fun toHexString(): String =
			"%s|%s|%s|%s".format(Locale.US, p.toString(16), q.toString(16), g.toString(16), y.toString(16))

		companion object {
			fun fromHexString(value: String?): DsaKey? {
				if (value == null) {
					return null
				}
				val parts = value.split("|")
				if (parts.size != 4) {
					return null
				}
				return try {
					DsaKey(BigInteger(parts[0], 16), BigInteger(parts[1], 16),
						BigInteger(parts[2], 16), BigInteger(parts[3], 16))
				} catch (_: NumberFormatException) {
					null
				}
			}
		}
	}

	/** Result of authenticating one ENC signature file. */
	sealed class Outcome {
		/** The data server certificate is signed by the Scheme Administrator. */
		object SaAuthenticated : Outcome()

		/** The certificate verifies with another installed key: proceed with the SSE 26 warning. */
		class ThirdPartyAuthenticated(val key: DsaKey) : Outcome()

		/** The certificate verifies with no installed key: expected system error SSE 06. */
		object CertificateInvalid : Outcome()

		/** The ENC signature does not verify: expected system error SSE 09. */
		object CellSignatureInvalid : Outcome()

		/** The signature file cannot be parsed: expected system error SSE 24. */
		object SignatureFileMalformed : Outcome()
	}

	/**
	 * Genuine public key of the IHO S-63 Scheme Administrator, extracted
	 * from the IHO issued X.509 certificate that ships with the official
	 * IHO S-64 ENC Test Data Set (subject "IHO S-63 Scheme Administrator",
	 * International Hydrographic Organization, Monaco, valid 2003-09-01 to
	 * 2013-08-29). Only the public parameters are embedded, in the format
	 * of an SA public key file: a public key file carries no expiry, and
	 * the X.509 expiry check of S-63 10.6.2 step 3 applies only to an
	 * installed certificate. Users can install a newer IHO certificate or
	 * public key through the S-63 manager.
	 */
	val IHO_SCHEME_ADMINISTRATOR_KEY = DsaKey(
		BigInteger("FCA682CE8E12CABA26EFCCF7110E526DB078B05EDECBCD1EB4A208F3AE1617AE01F35B91A47E6DF63413C5E12ED0899BCD132ACD50D99151BDC43EE737592E17", 16),
		BigInteger("962EDDCC369CBA8EBB260EE6B6A126D9346E38C5", 16),
		BigInteger("678471B27A9CF44EE91A49C5147DB1A9AAF244F05A434D6486931D2D14271B9E35030B71FD73DA179069B32E2935630E1C2062354D0DA20A6C416E50BE794CA4", 16),
		BigInteger("963F14E32BA5372928F24F15B0730C49D31B28E5C7641002564DB95995B15CF8800ED54E354867B82BB9597B158269E079F0C4F4926B17761CC89EB77C9B7EF8", 16))

	/**
	 * Authenticates a signature file against an encrypted ENC stream.
	 *
	 * @param signatureFileBytes Raw content of the signature file.
	 * @param cellContent The entire encrypted ENC cell file. The stream is
	 * consumed and must be re-readable by the caller afterwards.
	 * @param schemeAdministratorKey Installed SA public key.
	 * @param trustedKeys Additional installed certificates/public keys used
	 * for the S-63 10.6.2.1 third party path.
	 */
	fun authenticate(
		signatureFileBytes: ByteArray,
		cellContent: InputStream,
		schemeAdministratorKey: DsaKey,
		trustedKeys: List<DsaKey>): Outcome {
		val signatureFile = S63SignatureFile.parse(signatureFileBytes)
			?: return Outcome.SignatureFileMalformed
		val certificateOutcome = verifyDataServerCertificate(signatureFile, schemeAdministratorKey, trustedKeys)
		when (certificateOutcome) {
			is Outcome.SaAuthenticated, is Outcome.ThirdPartyAuthenticated -> {}
			else -> return certificateOutcome
		}
		return if (verifyCellSignature(signatureFile, cellContent)) {
			certificateOutcome
		} else {
			Outcome.CellSignatureInvalid
		}
	}

	/**
	 * Step 10.6.2: authenticate the data server certificate stored in the
	 * signature file against the installed Scheme Administrator key and,
	 * per 10.6.2.1, against the other installed keys.
	 */
	fun verifyDataServerCertificate(
		signatureFile: S63SignatureFile,
		schemeAdministratorKey: DsaKey,
		trustedKeys: List<DsaKey>): Outcome {
		val message = signatureFile.publicKeySectionBytes
		val r = signatureFile.certificateSignatureR
		val s = signatureFile.certificateSignatureS
		if (dsaVerify(schemeAdministratorKey, message, r, s)) {
			return Outcome.SaAuthenticated
		}
		for (key in trustedKeys) {
			if (dsaVerify(key, message, r, s)) {
				return Outcome.ThirdPartyAuthenticated(key)
			}
		}
		return Outcome.CertificateInvalid
	}

	/**
	 * Step 10.6.3: authenticate the ENC signature, the DSA/SHA-1 signature
	 * of the data server over the entire encrypted ENC file.
	 */
	fun verifyCellSignature(signatureFile: S63SignatureFile, cellContent: InputStream): Boolean {
		val key = DsaKey(signatureFile.p, signatureFile.q, signatureFile.g, signatureFile.y)
		return try {
			val digest = MessageDigest.getInstance("SHA-1")
			val buffer = ByteArray(65536)
			var read: Int
			while (cellContent.read(buffer).also { read = it } != -1) {
				digest.update(buffer, 0, read)
			}
			dsaVerify(key, digest.digest(), signatureFile.cellSignatureR, signatureFile.cellSignatureS)
		} catch (_: Exception) {
			false
		}
	}

	/** Extracts the DSA public key from a DER encoded X.509 certificate. */
	fun dsaKeyFromX509(der: ByteArray): Pair<DsaKey, java.security.cert.X509Certificate>? {
		return try {
			val certificate = CertificateFactory.getInstance("X.509")
				.generateCertificate(java.io.ByteArrayInputStream(der)) as? java.security.cert.X509Certificate
				?: return null
			val dsa = certificate.publicKey as? java.security.interfaces.DSAPublicKey ?: return null
			val params = dsa.params ?: return null
			Pair(DsaKey(params.p, params.q, params.g, dsa.y), certificate)
		} catch (_: Exception) {
			null
		}
	}

	/**
	 * Parses an ASCII public key file (the S-63 5.4.2.3 "Y file" format
	 * used by IHO.PUB and the data server .PUB files).
	 */
	fun dsaKeyFromAsciiPublicKey(text: String): DsaKey? {
		val values = HashMap<String, BigInteger>()
		val header = Regex("//\\s*BIG\\s+([pqgy])\\s*\\r?\\n([0-9A-Fa-f\\s.]*)", RegexOption.IGNORE_CASE)
		for (match in header.findAll(text)) {
			if (!values.containsKey(match.groupValues[1].lowercase())) {
				val hex = match.groupValues[2].replace(Regex("[\\s.]"), "")
				if (hex.isNotEmpty()) {
					try {
						values[match.groupValues[1].lowercase()] = BigInteger(hex, 16)
					} catch (_: NumberFormatException) {
					}
				}
			}
		}
		val p = values["p"] ?: return null
		val q = values["q"] ?: return null
		val g = values["g"] ?: return null
		val y = values["y"] ?: return null
		return DsaKey(p, q, g, y)
	}

	/**
	 * DSA/SHA-1 verification of a message with a public key and a raw
	 * (r, s) signature pair, computed manually per FIPS 186-2 instead of
	 * through the JCA Signature API. The IHO Scheme Administrator and data
	 * server keys use the original FIPS 186-2 parameter size (512-bit p
	 * with 160-bit q); the SUN provider in JDK 15 and later rejects DSA
	 * keys below 1024 bits, so SHA1withDSA throws InvalidKeyException,
	 * the catch block swallowed it, and every S-63 authentication silently
	 * failed on modern runtimes. The math is provider independent and
	 * verified against the S-64 authentication vectors.
	 */
	fun dsaVerify(key: DsaKey, message: ByteArray, r: BigInteger, s: BigInteger): Boolean {
		if (r.signum() <= 0 || s.signum() <= 0 || r >= key.q || s >= key.q) {
			return false
		}
		return try {
			val digest = MessageDigest.getInstance("SHA-1").digest(message)
			// FIPS 186-2: the leftmost min(N, outlen) bits of the hash.
			val hashBits = Math.min(key.q.bitLength(), digest.size * 8)
			var h = BigInteger(1, digest)
			val excess = digest.size * 8 - hashBits
			if (excess > 0) {
				h = h.shiftRight(excess)
			}
			val w = s.modInverse(key.q)
			val u1 = h.multiply(w).mod(key.q)
			val u2 = r.multiply(w).mod(key.q)
			val v = key.g.modPow(u1, key.p).multiply(key.y.modPow(u2, key.p)).mod(key.p).mod(key.q)
			v == r
		} catch (_: Exception) {
			false
		}
	}
}
