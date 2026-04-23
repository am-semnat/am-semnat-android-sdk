package ro.amsemnat.sdk.internal

import java.util.Calendar

/// Normalizers for the two date shapes we see on CEI cards:
/// - MRZ: `YYMMDD` (6 digits, century inferred from context)
/// - eDATA: `DDMMYYYY` (8 digits, full year)
///
/// Both produce ISO-8601 `YYYY-MM-DD`. Mirrors the iOS helpers in
/// `IdentityAssembler.swift` so output matches across platforms.

internal enum class DateWindow { BIRTH, FUTURE }

/// MRZ dates are `YYMMDD`. Without a century, pick birth-mode or future-mode
/// heuristics. Returns null if the input isn't 6 digits or date fields are
/// out of range.
internal fun isoDateFromMrzSixDigit(input: String?, window: DateWindow): String? {
    if (input == null) return null
    val digits = input.filter { it.isDigit() }
    if (digits.length != 6) return null
    val yy = digits.substring(0, 2).toIntOrNull() ?: return null
    val mm = digits.substring(2, 4).toIntOrNull() ?: return null
    val dd = digits.substring(4, 6).toIntOrNull() ?: return null
    if (mm !in 1..12 || dd !in 1..31) return null

    val currentYY = Calendar.getInstance().get(Calendar.YEAR) % 100
    val century = when (window) {
        DateWindow.BIRTH -> if (yy <= currentYY) 2000 else 1900
        DateWindow.FUTURE -> 2000
    }
    return "%04d-%02d-%02d".format(century + yy, mm, dd)
}

/// eDATA applet dates are `DDMMYYYY` (8 digits). Returns null if the input
/// isn't 8 digits or date fields are out of range.
internal fun isoDateFromEDataDDMMYYYY(input: String?): String? {
    if (input == null) return null
    val digits = input.filter { it.isDigit() }
    if (digits.length != 8) return null
    val dd = digits.substring(0, 2).toIntOrNull() ?: return null
    val mm = digits.substring(2, 4).toIntOrNull() ?: return null
    val yyyy = digits.substring(4, 8).toIntOrNull() ?: return null
    if (mm !in 1..12 || dd !in 1..31 || yyyy < 1900) return null
    return "%04d-%02d-%02d".format(yyyy, mm, dd)
}
