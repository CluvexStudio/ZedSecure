package dev.cluvex.zedsecure.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class GlassMaterial { Clear, Tinted }

@Serializable
enum class GlassWallpaper { Live, Lagoon, Dusk, Graphite, Plain }

@Serializable
enum class GlassTint { Blue, Indigo, Purple, Pink, Red, Orange, Green, Mint, Teal, Cyan, Brown, Graphite }

@Serializable
enum class GlassConnect { Z, Button, Switch, Slider }

@Serializable
data class GlassPrefs(
    val material: GlassMaterial = GlassMaterial.Clear,
    val wallpaper: GlassWallpaper = GlassWallpaper.Live,
    val tint: GlassTint = GlassTint.Blue,
    val connect: GlassConnect = GlassConnect.Z,
    val glassLists: Boolean = true,
    val shrinkTabBar: Boolean = true,
    val motionHighlights: Boolean = false,
    val reduceTransparency: Boolean = false,
    val homeSpeed: Boolean = true,
    val homeTimer: Boolean = true,
    val homeCard: Boolean = true,
)
