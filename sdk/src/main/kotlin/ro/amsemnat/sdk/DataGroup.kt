package ro.amsemnat.sdk

/**
 * LDS data groups that can be requested from a Romanian CEI eID card.
 *
 * The set passed to [AmSemnat.readIdentity] controls which files are read from the chip. Iteration
 * order follows declaration order here (`DG1 → DG2 → DG7 → DG11 → DG14`), which matters because
 * `DG14` must be read before chip authentication.
 */
enum class DataGroup {
    /** Machine-readable zone: document number, name, date of birth, sex, expiry, nationality. */
    DG1,

    /** Encoded face image (JPEG/JP2). Populates [RomanianIdentity.faceImage]. */
    DG2,

    /** Displayed signature image. Populates [RomanianIdentity.signatureImage]. */
    DG7,

    /** Additional personal details (place of birth, address). Populates the matching fields on [RomanianIdentity]. */
    DG11,

    /** Chip authentication public keys. Required to set [RomanianIdentity.chipAuthenticated] to `true`. */
    DG14;

    companion object {
        /**
         * Minimal set covering identity + biometric + chip authentication: `DG1`, `DG2`, `DG14`.
         * Used as the default for [AmSemnat.readIdentity] when no `dataGroups` argument is provided.
         */
        val DEFAULT: Set<DataGroup> = setOf(DG1, DG2, DG14)
    }
}
