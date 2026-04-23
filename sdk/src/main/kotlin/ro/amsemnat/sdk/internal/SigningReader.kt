package ro.amsemnat.sdk.internal

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CommandAPDU
import org.bouncycastle.asn1.ASN1Encodable
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.DERSet
import org.bouncycastle.asn1.cms.Time
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.jmrtd.lds.PACEInfo
import ro.amsemnat.sdk.SignProgress
import java.io.ByteArrayOutputStream
import java.security.MessageDigest
import java.util.Date

internal data class SignResult(
    val signature: ByteArray,
    val certificate: ByteArray,
    val signedAttrsDer: ByteArray,
)

internal class SigningReader(
    isoDep: IsoDep,
    private val can: String,
    private val pin2: String,
    private val storedPaceInfo: PACEInfo,
    private val onProgress: ((SignProgress) -> Unit)? = null,
) {
    private val session = NationalAppletSession(isoDep)

    companion object {
        val SIGNING_APP_AID = byteArrayOf(
            0xE8.toByte(), 0x28, 0xBD.toByte(), 0x08, 0x0F,
            0xD2.toByte(), 0x50, 0x47, 0x65, 0x6E, 0x65, 0x72, 0x69, 0x63,
        )

        val SIGNING_CERT_FILE_ID = byteArrayOf(0xCE.toByte(), 0x8E.toByte())

        const val PIN2_REFERENCE: Byte = 0x05
        const val KEY_REFERENCE: Byte = 0x8E.toByte()
        val ECC_ALGO_REFERENCE = byteArrayOf(0xFF.toByte(), 0x20, 0x08, 0x00)

        // Max command APDU chunk for paginated READ BINARY of the signing cert.
        const val READ_CHUNK_SIZE = 0xC0

        private val OID_CONTENT_TYPE = PKCSObjectIdentifiers.pkcs_9_at_contentType
        private val OID_SIGNING_TIME = PKCSObjectIdentifiers.pkcs_9_at_signingTime
        private val OID_MESSAGE_DIGEST = PKCSObjectIdentifiers.pkcs_9_at_messageDigest
        private val OID_DATA = PKCSObjectIdentifiers.data
        private val OID_SHA384 = NISTObjectIdentifiers.id_sha384
        private val OID_SIGNING_CERT_V2 = PKCSObjectIdentifiers.id_aa_signingCertificateV2
    }

    // PACE_ESTABLISHING is emitted by the caller (CardSigner) before invoking sign(),
    // so we don't re-emit it here.
    fun sign(pdfHash: ByteArray, signingTime: Date): SignResult {
        session.open(can, storedPaceInfo)
        selectSigningApp()

        onProgress?.invoke(SignProgress.VERIFYING_PIN)
        verifyPIN2()

        onProgress?.invoke(SignProgress.READING_CERTIFICATE)
        val certificate = readSigningCertificate()

        val signedAttrsDer = buildSignedAttributes(pdfHash, certificate, signingTime)
        val hashToSign = MessageDigest.getInstance("SHA-384").digest(signedAttrsDer)

        onProgress?.invoke(SignProgress.SIGNING)
        mseSetSigningKey()
        val signature = computeSignature(hashToSign)

        return SignResult(signature, certificate, signedAttrsDer)
    }

    private fun buildSignedAttributes(
        pdfHash: ByteArray,
        certDer: ByteArray,
        signingTime: Date,
    ): ByteArray {
        val contentTypeAttr = DERSequence(arrayOf(OID_CONTENT_TYPE, DERSet(OID_DATA)))

        val signingTimeAttr = DERSequence(
            arrayOf(OID_SIGNING_TIME, DERSet(Time(signingTime)))
        )

        val messageDigestAttr = DERSequence(
            arrayOf(OID_MESSAGE_DIGEST, DERSet(DEROctetString(pdfHash)))
        )

        val certHash = MessageDigest.getInstance("SHA-384").digest(certDer)
        val essCertIdV2 = DERSequence(
            arrayOf(
                DERSequence(arrayOf<ASN1Encodable>(OID_SHA384, DERNull.INSTANCE)),
                DEROctetString(certHash),
            )
        )
        val signingCertV2 = DERSequence(
            arrayOf<ASN1Encodable>(DERSequence(arrayOf<ASN1Encodable>(essCertIdV2)))
        )
        val signingCertV2Attr = DERSequence(arrayOf(OID_SIGNING_CERT_V2, DERSet(signingCertV2)))

        val attrSet = DERSet(
            arrayOf(contentTypeAttr, signingTimeAttr, messageDigestAttr, signingCertV2Attr)
        )
        return attrSet.encoded
    }

    private fun selectSigningApp() {
        val response = session.send(CommandAPDU(0x00, 0xA4, 0x04, 0x0C, SIGNING_APP_AID))
        if (response.sw != 0x9000) {
            throw SigningException("SELECT signing app failed: SW=${"%04X".format(response.sw)}")
        }
    }

    private fun mseSetSigningKey() {
        val data = byteArrayOf(0x80.toByte(), 0x04) + ECC_ALGO_REFERENCE + byteArrayOf(
            0x84.toByte(), 0x01, KEY_REFERENCE,
        )
        val response = session.send(CommandAPDU(0x00, 0x22, 0x41, 0xA4.toInt(), data))
        if (response.sw != 0x9000) {
            throw SigningException("MSE SET failed: SW=${"%04X".format(response.sw)}")
        }
    }

    private fun verifyPIN2() {
        when (val status = session.verifyPin(PIN2_REFERENCE, pin2)) {
            is PinStatus.Ok -> {}
            is PinStatus.VerifyFailed -> throw SigningPinException(status.retriesRemaining)
            is PinStatus.Blocked -> throw SigningPinBlockedException()
            is PinStatus.Other -> throw SigningException(
                "PIN2 verification failed: SW=${"%04X".format(status.statusWord)}"
            )
        }
    }

    private fun computeSignature(hash: ByteArray): ByteArray {
        val response = session.send(CommandAPDU(0x00, 0x88, 0x00, 0x00, hash, 256))
        if (response.sw != 0x9000) {
            throw SigningException(
                "INTERNAL AUTHENTICATE failed: SW=${"%04X".format(response.sw)}"
            )
        }
        return response.data
    }

    private fun readSigningCertificate(): ByteArray {
        val selectResp = session.send(CommandAPDU(0x00, 0xA4, 0x02, 0x0C, SIGNING_CERT_FILE_ID))
        if (selectResp.sw != 0x9000) {
            throw SigningException("SELECT signing cert failed: SW=${"%04X".format(selectResp.sw)}")
        }

        val firstResp = session.send(CommandAPDU(0x00, 0xB0, 0x00, 0x00, READ_CHUNK_SIZE))
        if (firstResp.sw != 0x9000 || firstResp.data.isEmpty()) {
            throw SigningException("READ BINARY cert failed: SW=${"%04X".format(firstResp.sw)}")
        }
        val certStream = ByteArrayOutputStream()
        certStream.write(firstResp.data)

        // X.509 DER begins with SEQUENCE (0x30) + long-form length 0x82 + 2 length bytes;
        // if we can parse total length from the first chunk, page through the rest.
        val firstData = firstResp.data
        val totalLen = if (
            firstData.size >= 4 &&
            firstData[0] == 0x30.toByte() &&
            firstData[1] == 0x82.toByte()
        ) {
            4 + ((firstData[2].toInt() and 0xFF) shl 8 or (firstData[3].toInt() and 0xFF))
        } else {
            return certStream.toByteArray()
        }

        while (certStream.size() < totalLen) {
            val offset = certStream.size()
            val remaining = totalLen - offset
            val chunkLen = minOf(remaining, READ_CHUNK_SIZE)
            val readResp = session.send(
                CommandAPDU(0x00, 0xB0, (offset shr 8) and 0xFF, offset and 0xFF, chunkLen)
            )
            if (readResp.sw != 0x9000 || readResp.data.isEmpty()) break
            certStream.write(readResp.data)
        }

        return certStream.toByteArray()
    }
}
