package ro.amsemnat.sdk

import android.app.Activity
import android.content.Context
import android.nfc.NfcAdapter
import android.nfc.tech.IsoDep
import ro.amsemnat.sdk.internal.ActiveOpHolder
import ro.amsemnat.sdk.internal.passive.verifyPassiveOfflineImpl
import ro.amsemnat.sdk.internal.readIdentityFromIsoDep
import ro.amsemnat.sdk.internal.signWithIsoDep
import ro.amsemnat.sdk.internal.validateCan
import ro.amsemnat.sdk.internal.validatePdfHash
import ro.amsemnat.sdk.internal.validatePin
import ro.amsemnat.sdk.internal.withNfcSession
import java.time.Instant

/**
 * Singleton entry point for the am-semnat Android SDK.
 *
 * Exposes three capabilities against Romanian CEI eID cards over NFC:
 * - [readIdentity] — PACE with the CAN, optionally VERIFY PIN1, read selected LDS data groups, and
 *   return a flat [RomanianIdentity].
 * - [sign] — PACE, VERIFY PIN2, and ECDSA-P384 signing of a pre-computed PDF byte-range hash,
 *   returning a [RomanianSignature] ready for PAdES B-B assembly.
 * - [verifyPassiveOffline] — pure-JVM passive authentication of a previously captured SOD + DG set
 *   against a caller-supplied trust anchor list.
 *
 * There are two flavors of [readIdentity] and [sign]: the `Activity` overloads manage the NFC
 * reader-mode session themselves, while the `IsoDep` overloads let callers own the tag lifecycle
 * (useful when wrapping existing dispatch code).
 *
 * Attach a [logger] to receive diagnostics. All public methods are safe to call from any
 * coroutine; the `suspend` functions do their I/O on the caller's dispatcher.
 */
object AmSemnat {

    /**
     * Optional diagnostic sink. Volatile so writes from any thread are visible to the NFC reader
     * thread. Defaults to `null` (silent). See [AmSemnatLogger] for the redaction contract.
     */
    @Volatile
    var logger: AmSemnatLogger? = null

    /**
     * Cancels the in-flight session-owned [readIdentity] / [sign], if any. The awaiting suspend
     * function raises [AmSemnatError.SessionCancelled]. No-op when nothing is in flight, and for
     * the `IsoDep` overloads — those don't go through the internal session helper; callers own
     * the tag lifecycle and can cancel their own [Job][kotlinx.coroutines.Job] directly.
     */
    fun cancelCurrentOp() {
        ActiveOpHolder.cancelCurrent()
    }

    /**
     * Returns `true` if the device exposes an [NfcAdapter] at all. A `false` result is a hardware
     * limitation — no runtime change can flip it.
     *
     * @param context Any [Context]; the application context is used internally.
     */
    fun isNfcAvailable(context: Context): Boolean =
        NfcAdapter.getDefaultAdapter(context.applicationContext) != null

    /**
     * Returns `true` if NFC is present **and** currently enabled in system settings. Callers
     * typically direct the user to NFC settings when this is `false` but [isNfcAvailable] is `true`.
     *
     * @param context Any [Context]; the application context is used internally.
     */
    fun isNfcEnabled(context: Context): Boolean {
        val adapter = NfcAdapter.getDefaultAdapter(context.applicationContext) ?: return false
        return adapter.isEnabled
    }

    /**
     * Acquires an NFC reader-mode session bound to [activity], waits for a card, and reads the
     * requested [dataGroups]. Suspends until the read completes or fails.
     *
     * @param activity The foreground activity; reader mode is scoped to its lifecycle.
     * @param can 6-digit Card Access Number printed on the document. Used for PACE.
     * @param pin1 4-digit PIN1 for the national eDATA applet. Pass `""` to skip eDATA entirely —
     *   in that case [RomanianIdentity.cnp], [RomanianIdentity.issuingAuthority] and related fields
     *   will be `null`.
     * @param dataGroups LDS data groups to read. Defaults to [DataGroup.DEFAULT]
     *   (`DG1`, `DG2`, `DG14`).
     * @param onProgress Optional callback invoked on the reader thread as the session advances.
     *   See [ReadProgress] for the value space.
     * @return A [RomanianIdentity] populated from the requested groups.
     * @throws AmSemnatError One of: [AmSemnatError.NfcUnavailable], [AmSemnatError.NfcDisabled],
     *   [AmSemnatError.SessionCancelled], [AmSemnatError.SessionTimeout], [AmSemnatError.TagLost],
     *   [AmSemnatError.TagNotValid], [AmSemnatError.MultipleTagsFound],
     *   [AmSemnatError.PaceAuthFailed], [AmSemnatError.PinVerifyFailed],
     *   [AmSemnatError.PinBlocked], [AmSemnatError.ReadFailed], [AmSemnatError.InvalidInput].
     */
    suspend fun readIdentity(
        activity: Activity,
        can: String,
        pin1: String,
        dataGroups: Set<DataGroup> = DataGroup.DEFAULT,
        onProgress: ((ReadProgress) -> Unit)? = null,
    ): RomanianIdentity {
        validateCan(can)
        if (pin1.isNotEmpty()) validatePin(pin1, "pin1")
        return withNfcSession(activity) { isoDep ->
            readIdentityFromIsoDep(isoDep, can, pin1, dataGroups, onProgress, logger)
        }
    }

    /**
     * Signs a pre-computed PDF byte-range hash using the card's signing certificate. Produces the
     * three artifacts needed to assemble a PAdES B-B `SignedData` structure and embed it into the
     * PDF placeholder.
     *
     * @param activity The foreground activity; reader mode is scoped to its lifecycle.
     * @param can 6-digit Card Access Number. Used for PACE.
     * @param pin2 6-digit PIN2 required for the signing operation. PIN1 is **not** required here.
     * @param pdfHash SHA-384 digest of the PDF byte range — exactly 48 bytes.
     * @param signingTime Timestamp embedded verbatim into the CMS `signingTime` attribute.
     *   Usually `Instant.now()` on the caller side.
     * @param onProgress Optional callback invoked on the reader thread. See [SignProgress].
     * @return A [RomanianSignature] containing the raw signature, certificate, and built
     *   `SignedAttributes` bytes.
     * @throws AmSemnatError Same error space as [readIdentity], plus [AmSemnatError.SigningFailed]
     *   for failures past VERIFY PIN2.
     */
    suspend fun sign(
        activity: Activity,
        can: String,
        pin2: String,
        pdfHash: ByteArray,
        signingTime: Instant,
        onProgress: ((SignProgress) -> Unit)? = null,
    ): RomanianSignature {
        validateCan(can)
        validatePin(pin2, "pin2")
        validatePdfHash(pdfHash)
        return withNfcSession(activity) { isoDep ->
            signWithIsoDep(isoDep, can, pin2, pdfHash, signingTime, onProgress, logger)
        }
    }

    /**
     * Variant of [readIdentity] that takes an already-acquired [IsoDep] tag. The caller owns the
     * tag lifecycle — the SDK calls `connect()` if the tag isn't already connected and sets a
     * 10-second transceive timeout, but never closes the tag.
     *
     * @see readIdentity for parameter semantics.
     */
    suspend fun readIdentity(
        isoDep: IsoDep,
        can: String,
        pin1: String,
        dataGroups: Set<DataGroup> = DataGroup.DEFAULT,
        onProgress: ((ReadProgress) -> Unit)? = null,
    ): RomanianIdentity {
        validateCan(can)
        if (pin1.isNotEmpty()) validatePin(pin1, "pin1")
        return readIdentityFromIsoDep(isoDep, can, pin1, dataGroups, onProgress, logger)
    }

    /**
     * Variant of [sign] that takes an already-acquired [IsoDep] tag. The caller owns the tag
     * lifecycle — the SDK calls `connect()` if needed and sets a 10-second transceive timeout,
     * but never closes the tag.
     *
     * @see sign for parameter semantics.
     */
    suspend fun sign(
        isoDep: IsoDep,
        can: String,
        pin2: String,
        pdfHash: ByteArray,
        signingTime: Instant,
        onProgress: ((SignProgress) -> Unit)? = null,
    ): RomanianSignature {
        validateCan(can)
        validatePin(pin2, "pin2")
        validatePdfHash(pdfHash)
        return signWithIsoDep(isoDep, can, pin2, pdfHash, signingTime, onProgress, logger)
    }

    /**
     * Runs offline passive authentication against data previously captured from a card. No NFC is
     * required — all inputs are byte arrays and the call is synchronous and pure.
     *
     * Three checks are performed:
     * 1. The SOD's CMS `SignedData` signature.
     * 2. The signer certificate chains to one of [trustAnchors] under PKIX.
     * 3. The hash of each supplied DG matches the corresponding hash inside the SOD.
     *
     * @param rawSod DER bytes of the LDS Security Object (e.g. [RomanianIdentity.rawSod]).
     * @param dataGroups The data groups to re-hash, keyed by [DataGroup]. Typically `DG1`, `DG2`,
     *   `DG14` — pass the same `raw*` bytes returned by [readIdentity].
     * @param trustAnchors DER-encoded CSCA or document-signer certificates trusted as roots. An
     *   empty list causes the chain check to fail.
     * @return A [PassiveVerificationResult] with `valid` plus per-check errors.
     */
    fun verifyPassiveOffline(
        rawSod: ByteArray,
        dataGroups: Map<DataGroup, ByteArray>,
        trustAnchors: List<ByteArray>,
    ): PassiveVerificationResult = verifyPassiveOfflineImpl(rawSod, dataGroups, trustAnchors)
}
