package ro.amsemnat.sdk.internal

import android.app.Activity
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import kotlinx.coroutines.Job
import kotlinx.coroutines.suspendCancellableCoroutine
import ro.amsemnat.sdk.AmSemnatError
import kotlin.coroutines.coroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

private const val NFC_PRESENCE_CHECK_DELAY = 1000

internal suspend fun <T> withNfcSession(
    activity: Activity,
    block: suspend (IsoDep) -> T,
): T {
    val adapter = NfcAdapter.getDefaultAdapter(activity)
        ?: throw AmSemnatError.NfcUnavailable
    if (!adapter.isEnabled) throw AmSemnatError.NfcDisabled

    val job = coroutineContext[Job]
    ActiveOpHolder.attach(job)
    try {
        val tag = awaitTag(activity, adapter)
        val isoDep = IsoDep.get(tag) ?: run {
            runCatching { adapter.disableReaderMode(activity) }
            throw AmSemnatError.TagNotValid
        }

        return try {
            block(isoDep)
        } finally {
            runCatching { adapter.disableReaderMode(activity) }
        }
    } finally {
        ActiveOpHolder.detach(job)
    }
}

private suspend fun awaitTag(activity: Activity, adapter: NfcAdapter): Tag =
    suspendCancellableCoroutine { cont ->
        val flags = NfcAdapter.FLAG_READER_NFC_A or
            NfcAdapter.FLAG_READER_NFC_B or
            NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

        val options = Bundle().apply {
            putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY, NFC_PRESENCE_CHECK_DELAY)
        }

        val callback = NfcAdapter.ReaderCallback { tag ->
            if (cont.isActive) cont.resume(tag)
        }

        try {
            adapter.enableReaderMode(activity, callback, flags, options)
        } catch (e: Exception) {
            cont.resumeWithException(AmSemnatError.Unknown(e.message ?: "enableReaderMode failed"))
            return@suspendCancellableCoroutine
        }

        cont.invokeOnCancellation {
            runCatching { adapter.disableReaderMode(activity) }
        }
    }
