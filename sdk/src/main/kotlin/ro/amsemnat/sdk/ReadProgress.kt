package ro.amsemnat.sdk

/**
 * Values passed to the `onProgress` callback of [AmSemnat.readIdentity], emitted in order as the
 * NFC session advances. A single read produces a subset of these — variants are skipped when the
 * caller didn't request the corresponding [DataGroup] or when the card doesn't advertise it.
 */
enum class ReadProgress {
    PACE_ESTABLISHING,
    READING_DG1,
    READING_DG2,
    READING_DG7,
    READING_DG14,
    CHIP_AUTHENTICATING,
    READING_EDATA,
    COMPLETE,
}
