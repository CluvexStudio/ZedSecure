package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.desktop.core.AdminAnswer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit

object AdminPassword {
    class Request(val retry: Boolean) {
        internal val answer = CompletableFuture<AdminAnswer?>()
    }

    private val _request = MutableStateFlow<Request?>(null)
    val request: StateFlow<Request?> = _request.asStateFlow()

    fun ask(retry: Boolean): AdminAnswer? {
        val r = Request(retry)
        _request.value = r
        return try {
            r.answer.get(WAIT_MIN, TimeUnit.MINUTES)
        } catch (e: Exception) {
            null
        } finally {
            _request.compareAndSet(r, null)
        }
    }

    fun answer(r: Request, answer: AdminAnswer?) {
        if (!r.answer.complete(answer)) answer?.password?.fill('\u0000')
        _request.compareAndSet(r, null)
    }

    private const val WAIT_MIN = 3L
}
