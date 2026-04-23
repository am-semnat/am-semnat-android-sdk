package ro.amsemnat.sdk.internal

import android.nfc.tech.IsoDep
import net.sf.scuba.smartcards.CommandAPDU
import net.sf.scuba.smartcards.IsoDepCardService
import net.sf.scuba.smartcards.ResponseAPDU
import org.jmrtd.PACEKeySpec
import org.jmrtd.PassportService
import org.jmrtd.lds.PACEInfo
import org.jmrtd.protocol.PACEAPDUSender
import org.jmrtd.protocol.PACEProtocol
import org.jmrtd.protocol.SecureMessagingWrapper
import java.math.BigInteger

private val NATIONAL_APPLET_AID = byteArrayOf(
    0xA0.toByte(), 0x00, 0x00, 0x00, 0x77, 0x03, 0x0C, 0x60,
    0x00, 0x00, 0x00, 0xFE.toByte(), 0x00, 0x00, 0x05, 0x00,
)

internal class AppletSessionException(message: String) : Exception(message)

internal class NationalAppletSession(private val isoDep: IsoDep) {
    private var wrapper: SecureMessagingWrapper? = null
    private var cardService: IsoDepCardService? = null

    fun open(can: String, paceInfo: PACEInfo) {
        selectNationalApplet()
        doPace(can, paceInfo)
    }

    fun send(apdu: CommandAPDU): ResponseAPDU {
        val w = wrapper ?: throw AppletSessionException("No SM wrapper — PACE not completed")
        val cs = cardService ?: throw AppletSessionException("No card service")
        return w.unwrap(cs.transmit(w.wrap(apdu)))
    }

    fun verifyPin(pinRef: Byte, pin: String): PinStatus {
        val padded = padPinTo12(pin)
        try {
            val response = send(CommandAPDU(0x00, 0x20, 0x00, pinRef.toInt(), padded))
            return mapPinStatus(response.sw)
        } finally {
            padded.fill(0)
        }
    }

    private fun selectNationalApplet() {
        val rawApdu = byteArrayOf(
            0x00, 0xA4.toByte(), 0x04, 0x00, NATIONAL_APPLET_AID.size.toByte(),
        ) + NATIONAL_APPLET_AID
        val response = isoDep.transceive(rawApdu)
        if (response.size < 2) throw AppletSessionException("SELECT national applet: no response")
        val sw = ResponseAPDU(response).sw
        if (sw != 0x9000) {
            throw AppletSessionException("SELECT national applet failed: SW=${"%04X".format(sw)}")
        }
    }

    private fun doPace(can: String, paceInfo: PACEInfo) {
        val cs = IsoDepCardService(isoDep)
        val paceProtocol = PACEProtocol(
            PACEAPDUSender(cs), null, PassportService.NORMAL_MAX_TRANCEIVE_LENGTH, true,
        )
        val paceResult = paceProtocol.doPACE(
            PACEKeySpec.createCANKey(can),
            paceInfo.objectIdentifier,
            PACEInfo.toParameterSpec(paceInfo.parameterId),
            BigInteger.valueOf(paceInfo.parameterId.toLong()),
        )
        wrapper = paceResult.wrapper
        cardService = cs
    }
}

private fun padPinTo12(pin: String): ByteArray {
    val padded = ByteArray(12) { 0xFF.toByte() }
    val bytes = pin.toByteArray(Charsets.UTF_8)
    bytes.copyInto(padded)
    bytes.fill(0)
    return padded
}
