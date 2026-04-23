package ro.amsemnat.sdk.internal.passive

import java.security.MessageDigest

internal object DgHasher {
    private val HASH_OID_MAP = mapOf(
        "1.3.14.3.2.26" to "SHA-1",
        "2.16.840.1.101.3.4.2.1" to "SHA-256",
        "2.16.840.1.101.3.4.2.2" to "SHA-384",
        "2.16.840.1.101.3.4.2.3" to "SHA-512",
        "2.16.840.1.101.3.4.2.4" to "SHA-224",
    )

    fun algorithmFromOid(oid: String): String =
        HASH_OID_MAP[oid] ?: throw IllegalArgumentException("Unknown hash algorithm OID: $oid")

    fun hash(data: ByteArray, algorithm: String): ByteArray =
        MessageDigest.getInstance(algorithm).digest(data)

    fun constantTimeEquals(a: ByteArray, b: ByteArray): Boolean =
        MessageDigest.isEqual(a, b)
}
