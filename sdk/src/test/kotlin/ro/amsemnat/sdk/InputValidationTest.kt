package ro.amsemnat.sdk

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.amsemnat.sdk.internal.validateCan
import ro.amsemnat.sdk.internal.validatePdfHash
import ro.amsemnat.sdk.internal.validatePin

class InputValidationTest {

    @Test
    fun validateCan_sixDigits_passes() {
        validateCan("123456")
    }

    @Test
    fun validateCan_tooShort_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) { validateCan("12345") }
        assertEquals("can", e.parameter)
    }

    @Test
    fun validateCan_tooLong_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) { validateCan("1234567") }
        assertEquals("can", e.parameter)
    }

    @Test
    fun validateCan_nonDigit_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) { validateCan("12345A") }
        assertEquals("can", e.parameter)
    }

    @Test
    fun validatePin_fourDigits_passes() {
        validatePin("1234", "pin1")
    }

    @Test
    fun validatePin_sixDigits_passes() {
        validatePin("123456", "pin2")
    }

    @Test
    fun validatePin_fiveDigits_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) {
            validatePin("12345", "pin1")
        }
        assertEquals("pin1", e.parameter)
    }

    @Test
    fun validatePin_nonDigit_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) {
            validatePin("12A4", "pin1")
        }
        assertEquals("pin1", e.parameter)
    }

    @Test
    fun validatePdfHash_exactly48Bytes_passes() {
        validatePdfHash(ByteArray(48))
    }

    @Test
    fun validatePdfHash_47Bytes_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) {
            validatePdfHash(ByteArray(47))
        }
        assertEquals("pdfHash", e.parameter)
        assertTrue(e.detail.contains("48"))
    }

    @Test
    fun validatePdfHash_64Bytes_throws() {
        val e = assertThrows(AmSemnatError.InvalidInput::class.java) {
            validatePdfHash(ByteArray(64))
        }
        assertEquals("pdfHash", e.parameter)
    }
}
