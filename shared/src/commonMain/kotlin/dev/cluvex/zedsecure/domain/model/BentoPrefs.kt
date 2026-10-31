package dev.cluvex.zedsecure.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class BentoPalette { Brand, Accent, Pastel, Vivid, Mono }

@Serializable
enum class BentoCorners { Leaf, Round, Soft }

@Serializable
enum class BentoTitle { Huge, Large, Compact }

@Serializable
enum class BentoDensity { Comfortable, Compact }

@Serializable
enum class BentoDockLabels { Active, Always, Never }

@Serializable
enum class BentoConnect { Slide, Button }

@Serializable
data class BentoPrefs(
    val palette: BentoPalette = BentoPalette.Brand,
    val corners: BentoCorners = BentoCorners.Leaf,
    val title: BentoTitle = BentoTitle.Large,
    val density: BentoDensity = BentoDensity.Comfortable,
    val motifs: Boolean = true,
    val playfulTouch: Boolean = true,
    val indexes: Boolean = true,
    val liveTiles: Boolean = true,
    val tiles: String = "",
    val dockLabels: BentoDockLabels = BentoDockLabels.Active,
    val dockAutoHide: Boolean = true,
    val connect: BentoConnect = BentoConnect.Slide,
    val wave: Boolean = true,
    val homeSpeed: Boolean = true,
    val homeSession: Boolean = true,
    val homeQuick: Boolean = true,
    val homeChips: Boolean = true,
)
