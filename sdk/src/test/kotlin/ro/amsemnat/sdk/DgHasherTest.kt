package ro.amsemnat.sdk

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.amsemnat.sdk.internal.passive.DgHasher

class DgHasherTest {

    @Test
    fun algorithmFromOid_knownOids() {
        assertEquals("SHA-1", DgHasher.algorithmFromOid("1.3.14.3.2.26"))
        assertEquals("SHA-224", DgHasher.algorithmFromOid("2.16.840.1.101.3.4.2.4"))
        assertEquals("SHA-256", DgHasher.algorithmFromOid("2.16.840.1.101.3.4.2.1"))
        assertEquals("SHA-384", DgHasher.algorithmFromOid("2.16.840.1.101.3.4.2.2"))
        assertEquals("SHA-512", DgHasher.algorithmFromOid("2.16.840.1.101.3.4.2.3"))
    }

    @Test
    fun algorithmFromOid_unknownOid_throws() {
        assertThrows(IllegalArgumentException::class.java) {
            DgHasher.algorithmFromOid("1.2.3.4.5")
        }
    }

    @Test
    fun hash_sha256_matchesKnownVector() {
        // echo -n "abc" | openssl dgst -sha256
        val abcSha256 = byteArrayOf(
            0xba.toByte(), 0x78.toByte(), 0x16.toByte(), 0xbf.toByte(),
            0x8f.toByte(), 0x01.toByte(), 0xcf.toByte(), 0xea.toByte(),
            0x41.toByte(), 0x41.toByte(), 0x40.toByte(), 0xde.toByte(),
            0x5d.toByte(), 0xae.toByte(), 0x22.toByte(), 0x23.toByte(),
            0xb0.toByte(), 0x03.toByte(), 0x61.toByte(), 0xa3.toByte(),
            0x96.toByte(), 0x17.toByte(), 0x7a.toByte(), 0x9c.toByte(),
            0xb4.toByte(), 0x10.toByte(), 0xff.toByte(), 0x61.toByte(),
            0xf2.toByte(), 0x00.toByte(), 0x15.toByte(), 0xad.toByte(),
        )
        assertArrayEquals(abcSha256, DgHasher.hash("abc".toByteArray(), "SHA-256"))
    }

    @Test
    fun hash_sha384_matchesKnownVector() {
        // echo -n "abc" | openssl dgst -sha384
        val abcSha384Hex =
            "cb00753f45a35e8bb5a03d699ac65007272c32ab0eded1631a8b605a43ff5bed" +
                "8086072ba1e7cc2358baeca134c825a7"
        val expected = hexToBytes(abcSha384Hex)
        assertArrayEquals(expected, DgHasher.hash("abc".toByteArray(), "SHA-384"))
    }

    @Test
    fun constantTimeEquals_matchingArrays_returnsTrue() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 5)
        assertTrue(DgHasher.constantTimeEquals(a, b))
    }

    @Test
    fun constantTimeEquals_oneByteOff_returnsFalse() {
        val a = byteArrayOf(1, 2, 3, 4, 5)
        val b = byteArrayOf(1, 2, 3, 4, 6)
        assertFalse(DgHasher.constantTimeEquals(a, b))
    }

    @Test
    fun constantTimeEquals_differentLength_returnsFalse() {
        val a = byteArrayOf(1, 2, 3)
        val b = byteArrayOf(1, 2, 3, 4)
        assertFalse(DgHasher.constantTimeEquals(a, b))
    }

    @Test
    fun constantTimeEquals_empty_returnsTrue() {
        assertTrue(DgHasher.constantTimeEquals(byteArrayOf(), byteArrayOf()))
    }

    private fun hexToBytes(hex: String): ByteArray =
        hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()
}
