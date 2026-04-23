package ro.amsemnat.sdk.internal

import ro.amsemnat.sdk.AmSemnatError

internal fun validateCan(can: String) {
    if (can.length != 6 || !can.all { it.isDigit() }) {
        throw AmSemnatError.InvalidInput("can", "must be exactly 6 digits")
    }
}

internal fun validatePin(pin: String, parameter: String) {
    if ((pin.length != 4 && pin.length != 6) || !pin.all { it.isDigit() }) {
        throw AmSemnatError.InvalidInput(parameter, "must be 4 or 6 digits")
    }
}

internal fun validatePdfHash(hash: ByteArray) {
    if (hash.size != 48) {
        throw AmSemnatError.InvalidInput(
            "pdfHash",
            "must be exactly 48 bytes (SHA-384), got ${hash.size}",
        )
    }
}
