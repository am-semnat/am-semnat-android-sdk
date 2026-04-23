package ro.amsemnat.sdk

/**
 * LDS data groups that can be requested from a Romanian CEI eID card.
 *
 * The set passed to [AmSemnat.readIdentity] controls which files are read from the chip. Iteration
 * order follows declaration order here (`DG1 → DG2 → DG7 → DG14`), which matters because
 * `DG14` must be read before chip authentication.
 *
 * [comTag] is the ICAO LDS tag byte the DG advertises in the card's COM file; used internally to
 * filter requested DGs against what the card actually ships.
 */
enum class DataGroup(internal val comTag: Int) {
    /** Machine-readable zone: document number, name, date of birth, sex, expiry, nationality. */
    DG1(0x61),

    /** Encoded face image (JPEG/JP2). Populates [RomanianIdentity.faceImage]. */
    DG2(0x75),

    /** Displayed signature image. Populates [RomanianIdentity.signatureImage]. */
    DG7(0x67),

    /** Chip authentication public keys. Required to set [RomanianIdentity.chipAuthenticated] to `true`. */
    DG14(0x6E);

    companion object {
        /**
         * Minimal set covering identity + biometric + chip authentication: `DG1`, `DG2`, `DG14`.
         * Used as the default for [AmSemnat.readIdentity] when no `dataGroups` argument is provided.
         */
        val DEFAULT: Set<DataGroup> = setOf(DG1, DG2, DG14)
    }
}
