package dev.cluvex.zedsecure.domain.ai

interface AiAppBridge {
    fun settingsJson(): String

    fun serversJson(): String

    fun statusJson(): String

    fun logsText(lines: Int): String

    fun coreReport(): String

    fun appMapJson(): String

    fun applySettings(patchJson: String): String

    fun setActiveServer(id: String): String

    suspend fun connect(): String

    suspend fun disconnect(): String

    suspend fun importConfig(text: String): String

    fun deleteServer(id: String): String

    suspend fun createServer(kind: String, name: String, optionsJson: String): String

    fun protocolsJson(): String

    suspend fun ping(id: String?): String

    suspend fun speedTest(): String

    suspend fun testDns(server: String, mode: String, host: String): String

    suspend fun probeMtu(reconnect: Boolean = false, apply: Boolean = false): String

    suspend fun exitInfo(): String
}

const val AI_UNSUPPORTED = "not available on this platform"
