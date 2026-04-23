package ro.amsemnat.sdk

/**
 * Output of [AmSemnat.sign]. The three fields are the primitives a caller needs to assemble a
 * PAdES B-B CMS SignedData structure and embed it into a PDF.
 *
 * @property signature Raw ECDSA P-384 signature as `r‖s`, 96 bytes. Convert to DER before putting
 *   it into a CMS `SignerInfo.signature` field.
 * @property certificate DER-encoded X.509 signer certificate read from the card (~995 bytes).
 * @property signedAttributes DER-encoded CMS `SignedAttributes` SET that was hashed (SHA-384) and
 *   signed on-chip. Re-tag from `0x31` to `0xA0` and embed in `SignerInfo.signedAttrs`; the
 *   `messageDigest`, `signingTime`, and `signingCertificateV2` attributes are already populated.
 */
data class RomanianSignature(
    val signature: ByteArray,
    val certificate: ByteArray,
    val signedAttributes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is RomanianSignature) return false
        return signature.contentEquals(other.signature) &&
            certificate.contentEquals(other.certificate) &&
            signedAttributes.contentEquals(other.signedAttributes)
    }

    override fun hashCode(): Int {
        var result = signature.contentHashCode()
        result = 31 * result + certificate.contentHashCode()
        result = 31 * result + signedAttributes.contentHashCode()
        return result
    }
}
