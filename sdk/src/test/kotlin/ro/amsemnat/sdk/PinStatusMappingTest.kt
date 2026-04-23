package ro.amsemnat.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.amsemnat.sdk.internal.PinStatus
import ro.amsemnat.sdk.internal.mapPinStatus

class PinStatusMappingTest {

    @Test
    fun sw9000_mapsToOk() {
        assertTrue(mapPinStatus(0x9000) is PinStatus.Ok)
    }

    @Test
    fun sw63C1_mapsToVerifyFailedWithOneRetry() {
        val result = mapPinStatus(0x63C1)
        assertTrue(result is PinStatus.VerifyFailed)
        assertEquals(1, (result as PinStatus.VerifyFailed).retriesRemaining)
    }

    @Test
    fun sw63C3_mapsToVerifyFailedWithThreeRetries() {
        val result = mapPinStatus(0x63C3) as PinStatus.VerifyFailed
        assertEquals(3, result.retriesRemaining)
    }

    @Test
    fun sw63C0_mapsToVerifyFailedWithZeroRetries() {
        // Distinct from 6983 — card reports retries remaining as 0, not yet
        // hard-blocked. Taxonomy: PinVerifyFailed(0), not PinBlocked.
        val result = mapPinStatus(0x63C0) as PinStatus.VerifyFailed
        assertEquals(0, result.retriesRemaining)
    }

    @Test
    fun sw6983_mapsToBlocked() {
        assertTrue(mapPinStatus(0x6983) is PinStatus.Blocked)
    }

    @Test
    fun sw6A86_mapsToOther() {
        val result = mapPinStatus(0x6A86) as PinStatus.Other
        assertEquals(0x6A86, result.statusWord)
    }

    @Test
    fun sw6D00_mapsToOther() {
        val result = mapPinStatus(0x6D00) as PinStatus.Other
        assertEquals(0x6D00, result.statusWord)
    }
}
