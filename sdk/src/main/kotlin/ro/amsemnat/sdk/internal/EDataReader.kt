package ro.amsemnat.sdk.internal

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CommandAPDU
import org.bouncycastle.asn1.ASN1InputStream
import org.bouncycastle.asn1.ASN1OctetString
import org.bouncycastle.asn1.ASN1Sequence
import org.bouncycastle.asn1.ASN1TaggedObject
import org.jmrtd.lds.PACEInfo
import ro.amsemnat.sdk.AmSemnatLogger

internal data class EData(
    val cnp: String? = null,
    val firstName: String? = null,
    val lastName: String? = null,
    val sex: String? = null,
    val dateOfBirth: String? = null,
    val nationality: String? = null,
    val placeOfBirth: String? = null,
    val address: String? = null,
    val documentNumber: String? = null,
    val dateOfExpiry: String? = null,
    val issuingDate: String? = null,
    val issuingAuthority: String? = null,
)

internal class EDataReader(
    isoDep: IsoDep,
    private val can: String,
    private val pin: String,
    private val storedPaceInfo: PACEInfo,
    private val logger: AmSemnatLogger? = null,
) {
    private val session = NationalAppletSession(isoDep)

    companion object {
        val EDATA_AID = byteArrayOf(
            0xE8.toByte(), 0x28, 0xBD.toByte(), 0x08, 0x0F,
            0xA0.toByte(), 0x00, 0x00, 0x01, 0x67,
            0x45, 0x44, 0x41, 0x54, 0x41,
        )

        const val PIN1_REFERENCE: Byte = 0x03

        // eDATA applet file IDs, order matches the on-card layout.
        val FILE_PERSONAL_DATA = byteArrayOf(0x01, 0x01)
        val FILE_BIRTH_DATA = byteArrayOf(0x01, 0x02)
        val FILE_ISSUER_DATA = byteArrayOf(0x01, 0x04)
        val FILE_ADDRESS = byteArrayOf(0x01, 0x06)
    }

    fun readEData(): EData {
        session.open(can, storedPaceInfo)
        verifyPIN()
        selectEDataApp()

        var out = EData()

        readFields(FILE_PERSONAL_DATA, "0101") { fields ->
            out = out.copy(
                lastName = fields.getOrNull(0),
                firstName = fields.getOrNull(1),
                sex = fields.getOrNull(2),
                dateOfBirth = fields.getOrNull(3),
                cnp = fields.getOrNull(4),
                nationality = fields.getOrNull(5),
            )
        }

        readFields(FILE_BIRTH_DATA, "0102") { fields ->
            out = out.copy(placeOfBirth = fields.getOrNull(1))
        }

        readFields(FILE_ISSUER_DATA, "0104") { fields ->
            out = out.copy(
                documentNumber = fields.getOrNull(0),
                issuingDate = fields.getOrNull(1),
                dateOfExpiry = fields.getOrNull(2),
                issuingAuthority = fields.getOrNull(3),
            )
        }

        readFields(FILE_ADDRESS, "0106") { fields ->
            out = out.copy(address = fields.getOrNull(0))
        }

        return out
    }

    private inline fun readFields(fileId: ByteArray, label: String, onFields: (List<String>) -> Unit) {
        try {
            onFields(parseAsn1Fields(readFile(fileId)))
        } catch (e: Exception) {
            logger?.debug("Failed to read eDATA file $label: ${e.message}")
        }
    }

    private fun verifyPIN() {
        when (val status = session.verifyPin(PIN1_REFERENCE, pin)) {
            is PinStatus.Ok -> {}
            is PinStatus.VerifyFailed -> throw EDataPinException(status.retriesRemaining)
            is PinStatus.Blocked -> throw EDataPinBlockedException()
            is PinStatus.Other -> throw EDataException(
                "PIN verification failed: SW=${"%04X".format(status.statusWord)}"
            )
        }
    }

    private fun selectEDataApp() {
        val response = session.send(CommandAPDU(0x00, 0xA4, 0x04, 0x0C, EDATA_AID))
        if (response.sw != 0x9000) {
            throw EDataException("SELECT eDATA failed: SW=${"%04X".format(response.sw)}")
        }
    }

    private fun readFile(fileId: ByteArray): ByteArray {
        val selectResp = session.send(CommandAPDU(0x00, 0xA4, 0x02, 0x0C, fileId))
        if (selectResp.sw != 0x9000) {
            throw EDataException("SELECT file failed: SW=${"%04X".format(selectResp.sw)}")
        }

        val readResp = session.send(CommandAPDU(0x00, 0xB0, 0x00, 0x00, 256))
        if (readResp.sw != 0x9000) {
            throw EDataException("READ BINARY failed: SW=${"%04X".format(readResp.sw)}")
        }

        return readResp.data
    }

    private fun parseAsn1Fields(data: ByteArray): List<String> {
        return try {
            val asn1 = ASN1InputStream(data).use { it.readObject() }
            val seq = ASN1Sequence.getInstance(asn1)
            (0 until seq.size()).map { i ->
                val tagged = ASN1TaggedObject.getInstance(seq.getObjectAt(i))
                val octets = ASN1OctetString.getInstance(tagged, false).octets
                String(octets, Charsets.UTF_8)
            }
        } catch (_: Exception) {
            emptyList()
        }
    }
}
