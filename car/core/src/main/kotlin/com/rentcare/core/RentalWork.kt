package com.rentcare.core

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * Everything done for one rental. [reset] at rental end cancels the work and invalidates
 * tokens, so a late server reply or SDK callback cannot write the previous renter's data back.
 */
class RentalWork(private val parent: CoroutineScope) {
    @JvmInline value class Token(val generation: Long)

    private val generation = AtomicLong(0)
    @Volatile private var job = SupervisorJob(parent.coroutineContext[Job])

    fun token() = Token(generation.get())

    fun launch(block: suspend CoroutineScope.(Token) -> Unit): Job {
        val t = token()
        return CoroutineScope(parent.coroutineContext + job).launch { block(t) }
    }

    /** Run [action] only if [token] still belongs to the current rental. */
    inline fun ifCurrent(token: Token, action: () -> Unit) {
        if (isCurrent(token)) action()
    }

    fun isCurrent(token: Token) = token.generation == generation.get()

    fun reset() {
        generation.incrementAndGet()
        job.cancel()
        job = SupervisorJob(parent.coroutineContext[Job])
    }
}
