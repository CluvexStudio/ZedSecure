package dev.cluvex.zedsecure.desktop.core

import kotlin.test.Test
import kotlin.test.assertFalse

class XrayCoreStopTest {
    @Test
    fun `a core that ignores SIGTERM is still gone after stop`() {
        if (Os.current == Os.WINDOWS) return
        val process = ProcessBuilder("sh", "-c", "trap '' TERM; sleep 30").start()
        Thread.sleep(200)
        XrayCore.stopProcess(process, graceMs = 300)
        assertFalse(process.isAlive)
    }
}
