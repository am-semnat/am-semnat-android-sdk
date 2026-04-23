package ro.amsemnat.sdk.internal

internal sealed class PinStatus {
    data object Ok : PinStatus()
    data class VerifyFailed(val retriesRemaining: Int) : PinStatus()
    data object Blocked : PinStatus()
    data class Other(val statusWord: Int) : PinStatus()
}

internal fun mapPinStatus(statusWord: Int): PinStatus = when {
    statusWord == 0x9000 -> PinStatus.Ok
    statusWord == 0x6983 -> PinStatus.Blocked
    (statusWord and 0xFFF0) == 0x63C0 -> PinStatus.VerifyFailed(statusWord and 0x0F)
    else -> PinStatus.Other(statusWord)
}
