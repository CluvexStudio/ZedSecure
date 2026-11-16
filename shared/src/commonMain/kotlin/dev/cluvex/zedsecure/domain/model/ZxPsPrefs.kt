package dev.cluvex.zedsecure.domain.model

import kotlinx.serialization.Serializable

enum class ZxPsPalette { Aurora, Graphite, Sakura, Mint, Ember }

enum class ZxPsTile { Soft, Squircle, Petal }

/** Customisation for the ZX PS design. [glass] runs from 0 (solid) to 100 (most transparent). */
@Serializable
data class ZxPsPrefs(
    val palette: ZxPsPalette = ZxPsPalette.Aurora,
    val tile: ZxPsTile = ZxPsTile.Soft,
    val glass: Int = 70,
    val navLabels: Boolean = true,
    val sheen: Boolean = true,
    val compact: Boolean = false,
)

enum class NotifAccent { Theme, Cyan, Magenta, Amber, Mint }
