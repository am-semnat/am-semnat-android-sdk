package ro.amsemnat.sdk

import org.bouncycastle.asn1.ASN1EncodableVector
import org.bouncycastle.asn1.ASN1Integer
import org.bouncycastle.asn1.ASN1ObjectIdentifier
import org.bouncycastle.asn1.DERNull
import org.bouncycastle.asn1.DEROctetString
import org.bouncycastle.asn1.DERSequence
import org.bouncycastle.asn1.nist.NISTObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.AlgorithmIdentifier
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.cert.X509v3CertificateBuilder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.cms.CMSProcessableByteArray
import org.bouncycastle.cms.CMSSignedDataGenerator
import org.bouncycastle.cms.jcajce.JcaSignerInfoGeneratorBuilder
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.operator.DigestCalculatorProvider
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.operator.jcajce.JcaDigestCalculatorProviderBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.BeforeClass
import org.junit.Test
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.Security
import java.security.cert.X509Certificate
import java.util.Date

class PassiveVerifierTest {

    companion object {
        @BeforeClass
        @JvmStatic
        fun setupProvider() {
            if (Security.getProvider("BC") == null) {
                Security.addProvider(BouncyCastleProvider())
            }
        }
    }

    @Test
    fun validSod_returnsValidResult() {
        val f = buildFixture()
        val result = AmSemnat.verifyPassiveOffline(
            rawSod = f.sodDer,
            dataGroups = mapOf(DataGroup.DG1 to f.dg1),
            trustAnchors = listOf(f.rootCertDer),
        )
        assertTrue("errors: ${result.errors}", result.valid)
        assertTrue(result.errors.isEmpty())
        assertEquals("Test DSC", result.signerCommonName)
    }

    @Test
    fun tamperedDg1_returnsInvalidWithHashMismatch() {
        val f = buildFixture()
        val tampered = f.dg1.copyOf().also { it[0] = (it[0] + 1).toByte() }
        val result = AmSemnat.verifyPassiveOffline(
            rawSod = f.sodDer,
            dataGroups = mapOf(DataGroup.DG1 to tampered),
            trustAnchors = listOf(f.rootCertDer),
        )
        assertFalse(result.valid)
        assertTrue(
            "expected hash mismatch error, got ${result.errors}",
            result.errors.any { it.contains("DG1 hash mismatch") },
        )
    }

    @Test
    fun emptyTrustAnchors_returnsChainError() {
        val f = buildFixture()
        val result = AmSemnat.verifyPassiveOffline(
            rawSod = f.sodDer,
            dataGroups = mapOf(DataGroup.DG1 to f.dg1),
            trustAnchors = emptyList(),
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("trusted CA", ignoreCase = true) })
    }

    @Test
    fun unrelatedTrustAnchor_returnsChainError() {
        val f = buildFixture()
        val other = buildFixture()
        val result = AmSemnat.verifyPassiveOffline(
            rawSod = f.sodDer,
            dataGroups = mapOf(DataGroup.DG1 to f.dg1),
            trustAnchors = listOf(other.rootCertDer),
        )
        assertFalse(result.valid)
        assertTrue(
            "expected chain failure, got ${result.errors}",
            result.errors.any { it.contains("Chain", ignoreCase = true) },
        )
    }

    @Test
    fun missingDgHash_reportsError() {
        val f = buildFixture()
        val result = AmSemnat.verifyPassiveOffline(
            rawSod = f.sodDer,
            dataGroups = mapOf(DataGroup.DG2 to byteArrayOf(1, 2, 3)),
            trustAnchors = listOf(f.rootCertDer),
        )
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("DG2 hash not found") })
    }

    @Test
    fun signingTime_exposedInResult() {
        val f = buildFixture()
        val result = AmSemnat.verifyPassiveOffline(
            rawSod = f.sodDer,
            dataGroups = mapOf(DataGroup.DG1 to f.dg1),
            trustAnchors = listOf(f.rootCertDer),
        )
        assertNotNull(result.signedAt)
    }

    private data class Fixture(
        val dg1: ByteArray,
        val sodDer: ByteArray,
        val rootCertDer: ByteArray,
    )

    private fun buildFixture(): Fixture {
        // Build a Root CA and a DSC signed by the root, then a CMS SignedData
        // containing an LDS Security Object with the DG1 hash, signed by DSC.
        val kpg = KeyPairGenerator.getInstance("RSA", "BC").apply { initialize(2048) }
        val rootKp = kpg.generateKeyPair()
        val dscKp = kpg.generateKeyPair()

        val now = System.currentTimeMillis()
        val oneDayAgo = Date(now - 24L * 3600 * 1000)
        val oneYearAhead = Date(now + 365L * 24 * 3600 * 1000)

        val rootCert = buildCert(
            subject = X500Name("CN=Test Root CA"),
            issuer = X500Name("CN=Test Root CA"),
            serial = BigInteger.ONE,
            notBefore = oneDayAgo,
            notAfter = oneYearAhead,
            subjectKey = rootKp,
            signerKey = rootKp,
            isCa = true,
        )

        val dscCert = buildCert(
            subject = X500Name("CN=Test DSC"),
            issuer = X500Name("CN=Test Root CA"),
            serial = BigInteger.valueOf(2),
            notBefore = oneDayAgo,
            notAfter = oneYearAhead,
            subjectKey = dscKp,
            signerKey = rootKp,
            isCa = false,
        )

        val dg1 = "THIS IS DG1 PAYLOAD".toByteArray()
        val dg1Hash = MessageDigest.getInstance("SHA-256").digest(dg1)

        val ldsBytes = buildLdsSecurityObject(dg1Hash)

        val sodDer = buildSod(ldsBytes, dscCert, dscKp)

        return Fixture(
            dg1 = dg1,
            sodDer = sodDer,
            rootCertDer = rootCert.encoded,
        )
    }

    private fun buildCert(
        subject: X500Name,
        issuer: X500Name,
        serial: BigInteger,
        notBefore: Date,
        notAfter: Date,
        subjectKey: KeyPair,
        signerKey: KeyPair,
        isCa: Boolean,
    ): X509Certificate {
        val builder: X509v3CertificateBuilder = JcaX509v3CertificateBuilder(
            issuer, serial, notBefore, notAfter, subject, subjectKey.public
        )
        if (isCa) {
            builder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        }
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(signerKey.private)
        val holder = builder.build(signer)
        return JcaX509CertificateConverter().setProvider("BC").getCertificate(holder)
    }

    private fun buildLdsSecurityObject(dg1Hash: ByteArray): ByteArray {
        val version = ASN1Integer(0)
        val hashAlg = AlgorithmIdentifier(NISTObjectIdentifiers.id_sha256, DERNull.INSTANCE)

        val dgHashEntry = DERSequence(
            ASN1EncodableVector().apply {
                add(ASN1Integer(1))
                add(DEROctetString(dg1Hash))
            }
        )
        val dgHashes = DERSequence(dgHashEntry)

        return DERSequence(
            ASN1EncodableVector().apply {
                add(version)
                add(hashAlg)
                add(dgHashes)
            }
        ).encoded
    }

    private fun buildSod(
        ldsBytes: ByteArray,
        dscCert: X509Certificate,
        dscKp: KeyPair,
    ): ByteArray {
        val signer = JcaContentSignerBuilder("SHA256withRSA").setProvider("BC").build(dscKp.private)
        val digestProvider: DigestCalculatorProvider =
            JcaDigestCalculatorProviderBuilder().setProvider("BC").build()

        val sigBuilder = JcaSignerInfoGeneratorBuilder(digestProvider).build(signer, dscCert)

        val gen = CMSSignedDataGenerator().apply {
            addSignerInfoGenerator(sigBuilder)
            addCertificate(org.bouncycastle.cert.X509CertificateHolder(dscCert.encoded))
        }

        // mrtd-signedData content OID: per ICAO 9303, eContentType is
        // 2.23.136.1.1.1 (ldsSecurityObject) — but pkijs / BC both accept
        // arbitrary content type for CMS verify; use id-data for simplicity.
        val content = CMSProcessableByteArray(
            ASN1ObjectIdentifier("2.23.136.1.1.1"),
            ldsBytes,
        )
        val signedData = gen.generate(content, true)
        return signedData.encoded
    }
}
