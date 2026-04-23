package ro.amsemnat.sdk.internal.passive

import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import java.security.cert.CertPathValidator
import java.security.cert.CertificateFactory
import java.security.cert.PKIXParameters
import java.security.cert.TrustAnchor
import java.security.cert.X509Certificate
import java.util.Date

internal data class ChainValidationResult(
    val valid: Boolean,
    val error: String? = null,
    val signerCommonName: String? = null,
)

internal object ChainValidator {

    fun validate(
        dsc: X509CertificateHolder,
        trustAnchorBytes: List<ByteArray>,
    ): ChainValidationResult {
        if (trustAnchorBytes.isEmpty()) {
            return ChainValidationResult(false, error = "No trusted CA certificates provided")
        }

        val converter = JcaX509CertificateConverter()
        val dscX509: X509Certificate = try {
            converter.getCertificate(dsc)
        } catch (e: Exception) {
            return ChainValidationResult(false, error = "Failed to convert DSC: ${e.message}")
        }

        val now = Date()
        if (now.before(dscX509.notBefore) || now.after(dscX509.notAfter)) {
            return ChainValidationResult(
                false,
                error = "DSC expired or not yet valid " +
                    "(${dscX509.notBefore.toInstant()} - ${dscX509.notAfter.toInstant()})",
            )
        }

        val (anchorCerts, intermediateCerts) = try {
            loadTrustAnchors(trustAnchorBytes)
        } catch (e: Exception) {
            return ChainValidationResult(false, error = "Trust anchor parse failed: ${e.message}")
        }

        if (anchorCerts.isEmpty()) {
            return ChainValidationResult(false, error = "No self-signed roots among trust anchors")
        }

        return try {
            val certFactory = CertificateFactory.getInstance("X.509")
            val certPathCerts = mutableListOf<X509Certificate>(dscX509)
            certPathCerts.addAll(intermediateCerts)
            val certPath = certFactory.generateCertPath(certPathCerts)

            val trustAnchors = anchorCerts.map { TrustAnchor(it, null) }.toSet()
            val params = PKIXParameters(trustAnchors).apply {
                isRevocationEnabled = false
            }

            val validator = CertPathValidator.getInstance("PKIX")
            validator.validate(certPath, params)

            ChainValidationResult(
                valid = true,
                signerCommonName = extractCommonName(dscX509.subjectX500Principal.name),
            )
        } catch (e: Exception) {
            ChainValidationResult(false, error = "Chain validation failed: ${e.message}")
        }
    }

    private fun loadTrustAnchors(
        anchorBytes: List<ByteArray>,
    ): Pair<List<X509Certificate>, List<X509Certificate>> {
        val certFactory = CertificateFactory.getInstance("X.509")
        val all = anchorBytes.map { bytes ->
            certFactory.generateCertificate(bytes.inputStream()) as X509Certificate
        }
        val roots = all.filter { it.subjectX500Principal == it.issuerX500Principal }
        val intermediates = all - roots.toSet()
        return roots to intermediates
    }

    // Best-effort CN extraction — does not handle RDNs with escaped commas.
    private fun extractCommonName(dn: String): String? {
        return dn.split(",")
            .map { it.trim() }
            .firstOrNull { it.startsWith("CN=") }
            ?.substringAfter("CN=")
    }
}
