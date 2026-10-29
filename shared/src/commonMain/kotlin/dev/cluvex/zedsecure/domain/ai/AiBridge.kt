package dev.cluvex.zedsecure.domain.ai

interface AiAppBridge {
    fun settingsJson(): String

    fun serversJson(): String

    fun statusJson(): String

    fun logsText(lines: Int): String

    fun coreReport(): String

    fun networkDiagnostics(): String

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

    /** Platform speech recognition. Implementations must not expose arbitrary system commands. */
    suspend fun listenOnce(): String = AI_UNSUPPORTED

    /** Speaks assistant output through the platform TTS engine when available. */
    suspend fun speak(text: String): String = AI_UNSUPPORTED

    fun stopSpeaking() = Unit

    /** Searches public repository metadata only; returned endpoints are not assumed safe or working. */
    suspend fun searchPublicRepositories(query: String, limit: Int = 5): String = AI_UNSUPPORTED
}

const val AI_UNSUPPORTED = "not available on this platform"
