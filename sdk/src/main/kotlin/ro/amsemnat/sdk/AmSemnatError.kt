package ro.amsemnat.sdk

/**
 * Sealed taxonomy of every error thrown by public [AmSemnat] methods. Exhaustive `when` is
 * supported — if a new variant is added, the Kotlin compiler will flag every unhandled site.
 *
 * Variants are grouped by concern: setup (NFC availability), session lifecycle (tag handling),
 * authentication (PACE/PIN), operation failures (read/sign), caller bugs (input validation),
 * and a fallback for anything that slips through.
 */
sealed class AmSemnatError(message: String) : Exception(message) {
    // ----- Setup -----

    /** The device has no NFC hardware. Shown when [AmSemnat.isNfcAvailable] returns `false`. */
    data object NfcUnavailable : AmSemnatError("NFC not available") {
        private fun readResolve(): Any = NfcUnavailable
    }

    /** NFC hardware is present but currently turned off in system settings. */
    data object NfcDisabled : AmSemnatError("NFC disabled") {
        private fun readResolve(): Any = NfcDisabled
    }

    // ----- Session lifecycle -----

    /** The coroutine was cancelled (e.g. `Activity` destroyed) before the session completed. */
    data object SessionCancelled : AmSemnatError("Session cancelled") {
        private fun readResolve(): Any = SessionCancelled
    }

    /** The NFC session hit its timeout without the user presenting a card. */
    data object SessionTimeout : AmSemnatError("NFC session timed out") {
        private fun readResolve(): Any = SessionTimeout
    }

    /** The tag was removed from the field before the protocol finished. */
    data object TagLost : AmSemnatError("NFC tag lost") {
        private fun readResolve(): Any = TagLost
    }

    /** The tag is not a recognized ISO-DEP eID card (wrong applet or chip type). */
    data object TagNotValid : AmSemnatError("Tag is not a valid ISO-DEP eID card") {
        private fun readResolve(): Any = TagNotValid
    }

    /** More than one NFC tag was detected in the field at once. */
    data object MultipleTagsFound : AmSemnatError("More than one NFC tag detected") {
        private fun readResolve(): Any = MultipleTagsFound
    }

    // ----- Authentication -----

    /** PACE key agreement failed. Usually means the supplied CAN is wrong, occasionally a malformed `CardAccess`. */
    data object PaceAuthFailed : AmSemnatError("PACE failed — wrong CAN or malformed CardAccess") {
        private fun readResolve(): Any = PaceAuthFailed
    }

    /**
     * VERIFY PIN rejected the supplied PIN. Use [retriesRemaining] to drive the UX (prompt again
     * vs. warn that the PIN is about to block).
     *
     * @property retriesRemaining Tries the card will accept before blocking. `0` implies the next
     *   failed attempt will transition to [PinBlocked].
     */
    data class PinVerifyFailed(val retriesRemaining: Int) :
        AmSemnatError("PIN verify failed: $retriesRemaining retries remaining")

    /** PIN counter reached zero. The card must be unblocked by the issuer; no further attempts are accepted. */
    data object PinBlocked : AmSemnatError("PIN blocked — contact issuer") {
        private fun readResolve(): Any = PinBlocked
    }

    // ----- Operation failures -----

    /**
     * Card read failed for a reason not already covered above (e.g. unexpected status word,
     * parse error).
     *
     * @property detail Diagnostic message in English. Don't pattern-match on it; log or surface as a generic failure.
     */
    data class ReadFailed(val detail: String) : AmSemnatError("Read failed: $detail")

    /**
     * Signing pipeline failed after PACE + PIN2 succeeded (e.g. INTERNAL AUTHENTICATE returned an
     * error, certificate parsing failed).
     *
     * @property detail Diagnostic message in English.
     */
    data class SigningFailed(val detail: String) : AmSemnatError("Signing failed: $detail")

    // ----- Caller bugs -----

    /**
     * A parameter violated its shape contract (wrong length, wrong byte count, non-numeric CAN).
     * These indicate consumer code that should be fixed, not a card problem.
     *
     * @property parameter Name of the offending parameter (`"can"`, `"pin2"`, `"pdfHash"`, …).
     * @property detail What specifically was wrong (`"must be 6 digits"`, `"expected 48 bytes"`, …).
     */
    data class InvalidInput(val parameter: String, val detail: String) :
        AmSemnatError("Invalid $parameter: $detail")

    // ----- Fallback -----

    /**
     * Anything the SDK couldn't classify. Report the [detail] to support and treat as transient
     * until proven otherwise.
     *
     * @property detail Diagnostic message.
     */
    data class Unknown(val detail: String) : AmSemnatError(detail)
}
