package ro.amsemnat.sdk

/**
 * Optional diagnostic sink for the SDK. Set [AmSemnat.logger] to receive internal log events; the
 * default is `null` (silent).
 *
 * Messages are English-only and never contain user data — CAN, PIN, CNP, and raw card payloads are
 * redacted or omitted before logging. Implementations are called synchronously on the thread that
 * produced the event (often the NFC reader thread), so heavy work should be dispatched elsewhere.
 */
interface AmSemnatLogger {
    /** Verbose protocol-level trace (APDU commands, state transitions). Safe to drop in release builds. */
    fun debug(message: String)

    /** High-level lifecycle event (session started, DG read, signature produced). */
    fun info(message: String)

    /** A recoverable or fatal error. [throwable] is the underlying cause, if any. */
    fun error(message: String, throwable: Throwable?)
}
