package dev.cluvex.zedsecure.desktop.core

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.fail

class ProtocolSupportTest {
    private val work = Files.createTempDirectory("protocols").toFile()

    @AfterTest
    fun cleanUp() {
        work.deleteRecursively()
    }

    private val config: String =
        javaClass.getResourceAsStream("/protocols/all-protocols.json")!!.bufferedReader().use { it.readText() }

    private fun check(xray: File, json: String): Pair<Boolean, String> {
        val file = File(work, "check-${System.nanoTime()}.json").apply { writeText(json) }
        val process = ProcessBuilder(xray.absolutePath, "singbox", "-test", "-c", file.absolutePath)
            .directory(work)
            .redirectErrorStream(true)
            .start()
        val output = StringBuilder()
        val reader = Thread { output.append(process.inputStream.bufferedReader().readText()) }.apply { start() }
        if (!process.waitFor(60, TimeUnit.SECONDS)) {
            process.destroyForcibly()
            return false to "timed out"
        }
        reader.join(5_000)
        return (process.exitValue() == 0) to output.toString().trim()
    }

    private fun alone(kind: String, item: JsonObject): String = buildJsonObject {
        put("log", buildJsonObject { put("level", "error") })
        val direct = buildJsonObject { put("type", "direct"); put("tag", "direct") }
        put("outbounds", JsonArray(if (kind == "outbounds") listOf(item, direct) else listOf(direct)))
        put("endpoints", JsonArray(if (kind == "endpoints") listOf(item) else emptyList()))
    }.toString()

    @Test
    fun `every protocol the desktop offers is in its Xray, cronet included`() {
        val xray = XrayBinary.extract(work) ?: run {
            if (System.getenv("CI") == "true") fail("xray is not bundled for ${Os.current}")
            return
        }
        val (ok, output) = check(xray, config)
        if (ok) return

        val root = Json.parseToJsonElement(config).jsonObject
        val missing = listOf("outbounds", "endpoints").flatMap { kind ->
            (root[kind] as? JsonArray).orEmpty().map { it.jsonObject }
                .filter { it["type"]?.jsonPrimitive?.content != "direct" }
                .mapNotNull { item ->
                    val (itemOk, itemOutput) = check(xray, alone(kind, item))
                    if (itemOk) null else "${item["type"]?.jsonPrimitive?.content}: ${itemOutput.lines().lastOrNull()}"
                }
        }
        fail("the ${Os.current} Xray rejects part of the protocol set:\n" + missing.ifEmpty { listOf(output) }.joinToString("\n"))
    }
}
