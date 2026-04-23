package ro.amsemnat.sdk.internal

import android.nfc.TagLostException
import android.nfc.tech.IsoDep
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.amsemnat.sdk.AmSemnatError
import ro.amsemnat.sdk.AmSemnatLogger
import ro.amsemnat.sdk.RomanianSignature
import ro.amsemnat.sdk.SignProgress
import java.time.Instant
import java.util.Date

internal suspend fun signWithIsoDep(
    isoDep: IsoDep,
    can: String,
    pin2: String,
    pdfHash: ByteArray,
    signingTime: Instant,
    onProgress: ((SignProgress) -> Unit)?,
    logger: AmSemnatLogger?,
): RomanianSignature = withContext(Dispatchers.IO) {
    try {
        val passportService = openPassportService(isoDep)

        onProgress?.invoke(SignProgress.PACE_ESTABLISHING)
        val paceInfo = try {
            passportService.readPaceInfo()
        } catch (e: AmSemnatError) {
            throw e
        } catch (e: Exception) {
            logger?.debug("CardAccess read failed: ${e.message}")
            throw AmSemnatError.PaceAuthFailed
        }

        val signer = SigningReader(isoDep, can, pin2, paceInfo, onProgress)

        val result = try {
            signer.sign(pdfHash, Date.from(signingTime))
        } catch (e: SigningPinException) {
            throw AmSemnatError.PinVerifyFailed(e.retriesRemaining)
        } catch (e: SigningPinBlockedException) {
            throw AmSemnatError.PinBlocked
        } catch (e: SigningException) {
            throw AmSemnatError.SigningFailed(e.message ?: "unknown error")
        }

        onProgress?.invoke(SignProgress.COMPLETE)

        RomanianSignature(
            signature = result.signature,
            certificate = result.certificate,
            signedAttributes = result.signedAttrsDer,
        )
    } catch (e: CancellationException) {
        throw e
    } catch (e: AmSemnatError) {
        throw e
    } catch (e: TagLostException) {
        throw AmSemnatError.TagLost
    } catch (e: Exception) {
        logger?.error("Signing failed", e)
        throw AmSemnatError.SigningFailed(e.message ?: "unknown error")
    }
}
