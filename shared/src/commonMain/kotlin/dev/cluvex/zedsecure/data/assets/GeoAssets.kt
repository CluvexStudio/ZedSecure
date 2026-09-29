package dev.cluvex.zedsecure.data.assets

import dev.cluvex.zedsecure.domain.model.GeoFilesSource

data class GeoAsset(
    val name: String,
    val present: Boolean,
    val sizeBytes: Long,
    val updatedAt: Long,
)

interface GeoAssets {
    fun state(): List<GeoAsset>

    fun allPresent(): Boolean

    suspend fun downloadAll(source: GeoFilesSource = GeoFilesSource.Loyalsoldier): Boolean

    suspend fun importBytes(bytes: ByteArray, name: String): Boolean

    companion object {
        const val GEOIP = "geoip.dat"
        const val GEOSITE = "geosite.dat"
        val FILES = listOf(GEOIP, GEOSITE)
    }
}
