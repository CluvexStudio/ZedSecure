package dev.cluvex.zedsecure.desktop.core

import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class EnginesLaunchTest {
    private val work = Files.createTempDirectory("engines-launch").toFile()

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    private data class Run(val exit: Int, val output: String)

    private fun bundled(file: File?, what: String): File? {
        if (file == null && System.getenv("CI") == "true") fail("$what is not bundled for ${Os.current}")
        return file
    }

    private fun launch(binary: File, vararg args: String): Run {
        val process = ProcessBuilder(listOf(binary.absolutePath) + args)
            .directory(work)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
        if (!process.waitFor(30, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            fail("${binary.name} ${args.joinToString(" ")} did not return within 30 s")
        }
        reader.join(5_000)
        return Run(process.exitValue(), output.toString())
    }

    @Test
    fun `xray starts and reports its version`() {
        val xray = bundled(XrayBinary.extract(work), "xray") ?: return
        val run = launch(xray, "version")
        assertEquals(0, run.exit, run.output)
        assertTrue(run.output.startsWith("Xray "), "unexpected output: ${run.output.take(200)}")
    }

    @Test
    fun `hev starts, which on Windows proves its DLLs load`() {
        val hev = bundled(HevBinary.extract(work), "hev-socks5-tunnel") ?: return
        val run = launch(hev)
        assertTrue(
            "Version:" in run.output,
            "hev printed nothing it would print when it starts (exit ${run.exit}): ${run.output.take(200)}",
        )
    }

    @Test
    fun `zeddns starts and lists its flags`() {
        val zeddns = bundled(BundledBinary.extract(work, "zeddns", "zeddns.exe"), "zeddns") ?: return
        val run = launch(zeddns, "-h")
        assertTrue("-domain" in run.output, "unexpected output (exit ${run.exit}): ${run.output.take(200)}")
    }

    @Test
    fun `the libraries beside the engines are built for this OS`() {
        bundled(XrayBinary.extract(work), "xray") ?: return
        val expected = when (Os.current) {
            Os.LINUX -> mapOf("libcronet.so" to "7f454c46")
            Os.WINDOWS -> mapOf("libcronet.dll" to "4d5a") + HevBinary.WINDOWS_COMPANIONS.associateWith { "4d5a" }
            else -> emptyMap()
        }
        if (Os.current == Os.WINDOWS) bundled(HevBinary.extract(work), "hev-socks5-tunnel") ?: return
        expected.forEach { (name, magic) ->
            val file = File(work, name)
            assertTrue(file.isFile, "$name was not extracted next to the engine")
            val head = file.inputStream().use { it.readNBytes(magic.length / 2) }
            assertEquals(magic, head.joinToString("") { "%02x".format(it) }, "$name is not a native library for ${Os.current}")
        }
    }
}
