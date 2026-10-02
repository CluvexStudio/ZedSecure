package dev.cluvex.zedsecure.desktop.platform

import dev.cluvex.zedsecure.domain.model.AppSettings
import dev.cluvex.zedsecure.domain.model.RenderingMode
import dev.cluvex.zedsecure.domain.model.RunMode
import dev.cluvex.zedsecure.platform.KeyValueStore
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class DesktopSettingsModeTest {
    private class MemoryStore : KeyValueStore {
        val values = mutableMapOf<String, Any?>()
        override fun getString(key: String) = values[key] as? String
        override fun putString(key: String, value: String?) { values[key] = value }
        override fun getInt(key: String, default: Int) = values[key] as? Int ?: default
        override fun putInt(key: String, value: Int) { values[key] = value }
        override fun getBoolean(key: String, default: Boolean) = values[key] as? Boolean ?: default
        override fun putBoolean(key: String, value: Boolean) { values[key] = value }
        override fun remove(key: String) { values.remove(key) }
    }

    private val json = Json { encodeDefaults = true }

    @Test
    fun `a fresh install starts in system proxy mode`() {
        assertEquals(RunMode.SystemProxy, DesktopSettings(MemoryStore()).settings.value.runMode)
    }

    @Test
    fun `settings saved before the three modes move to system proxy once`() {
        val store = MemoryStore()
        store.putString("settings", json.encodeToString(AppSettings.serializer(), AppSettings(runMode = RunMode.Vpn)))
        assertEquals(RunMode.SystemProxy, DesktopSettings(store).settings.value.runMode)
    }

    @Test
    fun `the old software rendering switch carries over to the new setting`() {
        val store = MemoryStore()
        store.putString("run_mode_version", "2")
        store.putString("settings", """{"runMode":"Vpn","softwareRendering":true}""")
        assertEquals(RenderingMode.Software, DesktopSettings(store).settings.value.renderingMode)

        val untouched = MemoryStore()
        untouched.putString("run_mode_version", "2")
        untouched.putString("settings", """{"softwareRendering":false}""")
        assertEquals(RenderingMode.Auto, DesktopSettings(untouched).settings.value.renderingMode)
    }

    @Test
    fun `a mode chosen after that is kept`() {
        val store = MemoryStore()
        DesktopSettings(store).update { it.copy(runMode = RunMode.Vpn) }
        assertEquals(RunMode.Vpn, DesktopSettings(store).settings.value.runMode)
        DesktopSettings(store).update { it.copy(runMode = RunMode.ProxyOnly) }
        assertEquals(RunMode.ProxyOnly, DesktopSettings(store).settings.value.runMode)
    }
}
