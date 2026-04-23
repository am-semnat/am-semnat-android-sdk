package ro.amsemnat.sdk.internal

internal class EDataPinException(val retriesRemaining: Int) :
    Exception("PIN1 verify failed; retries remaining: $retriesRemaining")

internal class EDataPinBlockedException :
    Exception("PIN1 blocked")

internal class EDataException(message: String) : Exception(message)

internal class SigningPinException(val retriesRemaining: Int) :
    Exception("PIN2 verify failed; retries remaining: $retriesRemaining")

internal class SigningPinBlockedException :
    Exception("PIN2 blocked")

internal class SigningException(message: String) : Exception(message)
