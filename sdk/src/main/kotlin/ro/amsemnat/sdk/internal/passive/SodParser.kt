package ro.amsemnat.sdk.internal.passive

import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cms.CMSSignedData
import java.io.ByteArrayOutputStream
import java.time.Instant

internal data class SodContents(
    val dsc: X509CertificateHolder,
    val signedData: CMSSignedData,
    val ldsSecurityObjectBytes: ByteArray,
    val hashAlgorithmOid: String,
    val dataGroupHashes: Map<Int, ByteArray>,
    val signingTime: Instant?,
)

internal object SodParser {

    fun parse(derBytes: ByteArray): SodContents {
        val signedData = try {
            CMSSignedData(derBytes)
        } catch (e: Exception) {
            throw IllegalArgumentException("Failed to parse SOD: ${e.message}", e)
        }

        val dsc = signedData.certificates.getMatches(null).firstOrNull()
            ?: throw IllegalArgumentException("SOD contains no certificates")

        val encapContent = signedData.signedContent
            ?: throw IllegalArgumentException("SOD has no encapsulated content")
        val ldsBytes = ByteArrayOutputStream().also { encapContent.write(it) }.toByteArray()

        val (hashOid, dgHashes) = parseLdsSecurityObject(ldsBytes)

        val signingTime = extractSigningTime(signedData)

        return SodContents(
            dsc = dsc,
            signedData = signedData,
            ldsSecurityObjectBytes = ldsBytes,
            hashAlgorithmOid = hashOid,
            dataGroupHashes = dgHashes,
            signingTime = signingTime,
        )
    }

    // LDS Security Object (ICAO 9303) is SEQUENCE { version, algorithmIdentifier, dgHashes }.
    private fun parseLdsSecurityObject(bytes: ByteArray): Pair<String, Map<Int, ByteArray>> {
        val asn1 = ASN1InputStream(bytes).use { it.readObject() }
        val sequence = ASN1Sequence.getInstance(asn1)

        val algSeq = ASN1Sequence.getInstance(sequence.getObjectAt(1))
        val algOid = ASN1ObjectIdentifier.getInstance(algSeq.getObjectAt(0)).id

        val dgHashSeq = ASN1Sequence.getInstance(sequence.getObjectAt(2))
        val hashes = mutableMapOf<Int, ByteArray>()
        for (i in 0 until dgHashSeq.size()) {
            val dgSeq = ASN1Sequence.getInstance(dgHashSeq.getObjectAt(i))
            val dgNumber = ASN1Integer.getInstance(dgSeq.getObjectAt(0)).value.toInt()
            val hash = ASN1OctetString.getInstance(dgSeq.getObjectAt(1)).octets
            hashes[dgNumber] = hash
        }
        return algOid to hashes
    }

    private fun extractSigningTime(signedData: CMSSignedData): Instant? {
        val signerInfo = signedData.signerInfos.signers.firstOrNull() ?: return null
        val signedAttrs = signerInfo.signedAttributes ?: return null
        val attr = signedAttrs[PKCSObjectIdentifiers.pkcs_9_at_signingTime] ?: return null
        val value = attr.attributeValues.firstOrNull() ?: return null
        return try {
            val time = org.bouncycastle.asn1.cms.Time.getInstance(value)
            time.date.toInstant()
        } catch (_: Exception) {
            null
        }
    }
}
