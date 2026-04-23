package ro.amsemnat.sdk

/**
 * Values passed to the `onProgress` callback of [AmSemnat.readIdentity], emitted in order as the
 * NFC session advances. A single read produces a subset of these — variants for data groups the
 * caller didn't request are skipped.
 */
enum class ReadProgress {
    PACE_ESTABLISHING,
    READING_DG1,
    READING_DG2,
    READING_DG7,
    READING_DG11,
    READING_DG14,
    CHIP_AUTHENTICATING,
    READING_EDATA,
    COMPLETE,
}
