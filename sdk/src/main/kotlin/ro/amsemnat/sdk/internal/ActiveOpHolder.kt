package ro.amsemnat.sdk.internal

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job

/**
 * Tracks the coroutine [Job] of the currently-running session-owned
 * read / sign so [AmSemnat.cancelCurrentOp] can cancel it from an
 * unrelated caller. The [detach] identity check prevents a prior op's
 * teardown from wiping a slot a newer op has already claimed.
 */
internal object ActiveOpHolder {

    @Volatile
    private var current: Job? = null

    fun attach(job: Job?) {
        current = job
    }

    fun detach(job: Job?) {
        if (current === job) current = null
    }

    fun cancelCurrent() {
        current?.cancel(CancellationException())
    }
}
