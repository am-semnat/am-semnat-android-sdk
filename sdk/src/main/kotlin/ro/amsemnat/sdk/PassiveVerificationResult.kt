package ro.amsemnat.sdk

import java.time.Instant

/**
 * Outcome of [AmSemnat.verifyPassiveOffline]. Passive authentication runs three checks against the
 * card's Security Object (SOD): the SOD's CMS signature, the signer certificate's chain to a
 * caller-supplied trust anchor, and the hashes of each supplied data group against the values
 * embedded in the SOD.
 *
 * @property valid `true` iff all three checks passed. If `false`, see [errors] for the breakdown.
 * @property errors Human-readable English strings describing each failure. Empty when [valid] is `true`.
 * @property signerCommonName The `CN` of the SOD signer certificate, best-effort. May be `null` if the SOD is malformed.
 * @property signedAt The CMS `signingTime` attribute on the SOD, best-effort. May be `null` if absent or unparseable.
 */
data class PassiveVerificationResult(
    val valid: Boolean,
    val errors: List<String>,
    val signerCommonName: String?,
    val signedAt: Instant?,
)
