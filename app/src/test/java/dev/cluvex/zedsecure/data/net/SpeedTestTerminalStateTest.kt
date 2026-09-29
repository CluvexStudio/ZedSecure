package dev.cluvex.zedsecure.data.net

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpeedTestTerminalStateTest {
    @Test(timeout = 5_000)
    fun `timeout around awaitAll does not bound the children`() = runBlocking {
        val neverCompletes = CompletableDeferred<Unit>()
        var scopeReturned = false

        val outer = async {
            coroutineScope {
                val stuck = async { neverCompletes.await(); 0L }
                withTimeoutOrNull(50) { listOf(stuck).awaitAll() }

                scopeReturned = true
            }
        }

        withTimeoutOrNull(500) { outer.await() }
        assertTrue("the block body ran", scopeReturned)
        assertTrue("but coroutineScope is still pending on its child", outer.isActive)

        neverCompletes.complete(Unit)
        outer.await()
    }

    @Test(timeout = 5_000)
    fun `cancelling the handles on timeout lets the phase end`() = runBlocking {
        val neverCompletes = CompletableDeferred<Unit>()

        val result = withTimeoutOrNull(2_000) {
            coroutineScope {
                val stuck = async { neverCompletes.await(); 0L }
                if (withTimeoutOrNull(50) { listOf(stuck).awaitAll() } == null) {
                    stuck.cancel()
                }
                "terminal"
            }
        }
        assertEquals("terminal", result)
    }
}
