package ro.amsemnat.sdk.internal.passive

import org.bouncycastle.cms.jcajce.JcaSimpleSignerInfoVerifierBuilder
import ro.amsemnat.sdk.DataGroup
import ro.amsemnat.sdk.PassiveVerificationResult

internal fun verifyPassiveOfflineImpl(
    rawSod: ByteArray,
    dataGroups: Map<DataGroup, ByteArray>,
    trustAnchors: List<ByteArray>,
): PassiveVerificationResult {
    val errors = mutableListOf<String>()
    var signatureValid = false
    var hashesValid = false

    val sodContents = try {
        SodParser.parse(rawSod)
    } catch (e: Exception) {
        errors.add("SOD parse failed: ${e.message}")
        return PassiveVerificationResult(
            valid = false,
            errors = errors,
            signerCommonName = null,
            signedAt = null,
        )
    }

    try {
        val signerInfo = sodContents.signedData.signerInfos.signers.firstOrNull()
        if (signerInfo == null) {
            errors.add("SOD has no signerInfos")
        } else {
            val verifier = JcaSimpleSignerInfoVerifierBuilder().build(sodContents.dsc)
            signatureValid = signerInfo.verify(verifier)
            if (!signatureValid) errors.add("SOD CMS signature verification failed")
        }
    } catch (e: Exception) {
        errors.add("Signature verification error: ${e.message}")
    }

    val chainResult = ChainValidator.validate(sodContents.dsc, trustAnchors)
    if (!chainResult.valid) {
        errors.add(chainResult.error ?: "Certificate chain validation failed")
    }

    try {
        val algorithm = DgHasher.algorithmFromOid(sodContents.hashAlgorithmOid)
        var allMatched = dataGroups.isNotEmpty()

        for ((dg, raw) in dataGroups) {
            val dgNumber = dgToNumber(dg)
            val expected = sodContents.dataGroupHashes[dgNumber]
            if (expected == null) {
                errors.add("DG$dgNumber hash not found in SOD")
                allMatched = false
                continue
            }

            val computed = DgHasher.hash(raw, algorithm)
            if (!DgHasher.constantTimeEquals(computed, expected)) {
                errors.add("DG$dgNumber hash mismatch")
                allMatched = false
            }
        }
        hashesValid = allMatched
    } catch (e: Exception) {
        errors.add("Hash verification error: ${e.message}")
    }

    val valid = signatureValid && hashesValid && chainResult.valid
    return PassiveVerificationResult(
        valid = valid,
        errors = errors,
        signerCommonName = chainResult.signerCommonName,
        signedAt = sodContents.signingTime,
    )
}

private fun dgToNumber(dg: DataGroup): Int = when (dg) {
    DataGroup.DG1 -> 1
    DataGroup.DG2 -> 2
    DataGroup.DG7 -> 7
    DataGroup.DG14 -> 14
}
