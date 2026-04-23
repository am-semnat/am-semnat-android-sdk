package ro.amsemnat.sdk

/**
 * Values passed to the `onProgress` callback of [AmSemnat.sign], emitted in order as the NFC
 * session advances.
 */
enum class SignProgress {
    PACE_ESTABLISHING,
    VERIFYING_PIN,
    READING_CERTIFICATE,
    SIGNING,
    COMPLETE,
}
