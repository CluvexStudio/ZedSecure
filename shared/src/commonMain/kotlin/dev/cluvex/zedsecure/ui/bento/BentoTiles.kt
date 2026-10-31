package dev.cluvex.zedsecure.ui.bento

enum class BentoTileSize(val code: Char, val cols: Int, val rows: Int) {
    Small('s', 1, 1),
    Wide('w', 2, 1),
    Tall('t', 1, 2);

    fun next(): BentoTileSize = when (this) {
        Small -> Wide
        Wide -> Tall
        Tall -> Small
    }

    companion object {
        fun of(code: Char): BentoTileSize? = entries.firstOrNull { it.code == code }
    }
}

data class BentoTileSlot(val key: String, val size: BentoTileSize)

data class BentoTileLayout(val visible: List<BentoTileSlot>, val hidden: List<String>) {
    fun without(key: String): BentoTileLayout =
        BentoTileLayout(visible.filterNot { it.key == key }, (hidden + key).distinct())

    fun with(key: String, size: BentoTileSize = BentoTiles.defaultSize(key)): BentoTileLayout =
        BentoTileLayout(visible.filterNot { it.key == key } + BentoTileSlot(key, size), hidden - key)

    fun resized(key: String): BentoTileLayout =
        copy(visible = visible.map { if (it.key == key) it.copy(size = it.size.next()) else it })

    fun moved(key: String, toIndex: Int): BentoTileLayout {
        val from = visible.indexOfFirst { it.key == key }
        if (from < 0) return this
        val list = visible.toMutableList()
        val slot = list.removeAt(from)
        list.add(toIndex.coerceIn(0, list.size), slot)
        return copy(visible = list)
    }
}

data class BentoPlaced(val slot: BentoTileSlot, val col: Int, val row: Int, val cols: Int, val rows: Int)

object BentoTiles {
    val DEFAULT: List<BentoTileSlot> = listOf(
        BentoTileSlot("appearance", BentoTileSize.Wide),
        BentoTileSlot("vpn", BentoTileSize.Tall),
        BentoTileSlot("dns", BentoTileSize.Small),
        BentoTileSlot("routing", BentoTileSize.Small),
        BentoTileSlot("tor", BentoTileSize.Wide),
        BentoTileSlot("speed-test", BentoTileSize.Small),
        BentoTileSlot("monitor", BentoTileSize.Small),
        BentoTileSlot("per-app", BentoTileSize.Wide),
        BentoTileSlot("core", BentoTileSize.Small),
        BentoTileSlot("mode", BentoTileSize.Small),
        BentoTileSlot("sing-box", BentoTileSize.Small),
        BentoTileSlot("ssh", BentoTileSize.Small),
        BentoTileSlot("map", BentoTileSize.Wide),
        BentoTileSlot("mux", BentoTileSize.Small),
        BentoTileSlot("fragment", BentoTileSize.Small),
        BentoTileSlot("observatory", BentoTileSize.Small),
        BentoTileSlot("advanced", BentoTileSize.Small),
        BentoTileSlot("assets", BentoTileSize.Wide),
        BentoTileSlot("support", BentoTileSize.Small),
        BentoTileSlot("ai", BentoTileSize.Small),
        BentoTileSlot("tour", BentoTileSize.Small),
        BentoTileSlot("update", BentoTileSize.Small),
        BentoTileSlot("rate", BentoTileSize.Small),
        BentoTileSlot("privacy", BentoTileSize.Small),
        BentoTileSlot("about", BentoTileSize.Wide),
    )

    private val PROMINENT = setOf("appearance")

    private val TONES: Map<String, BentoToneKind> = mapOf(
        "appearance" to BentoToneKind.Stage,
        "vpn" to BentoToneKind.Lime,
        "dns" to BentoToneKind.Cyan,
        "routing" to BentoToneKind.Pink,
        "tor" to BentoToneKind.Violet,
        "speed-test" to BentoToneKind.Blue,
        "monitor" to BentoToneKind.Coral,
        "per-app" to BentoToneKind.Surface,
        "core" to BentoToneKind.Violet,
        "mode" to BentoToneKind.Cyan,
        "sing-box" to BentoToneKind.Blue,
        "ssh" to BentoToneKind.Coral,
        "map" to BentoToneKind.Cyan,
        "mux" to BentoToneKind.Pink,
        "fragment" to BentoToneKind.Violet,
        "observatory" to BentoToneKind.Blue,
        "advanced" to BentoToneKind.Surface,
        "assets" to BentoToneKind.Pink,
        "support" to BentoToneKind.Coral,
        "ai" to BentoToneKind.Violet,
        "tour" to BentoToneKind.Cyan,
        "update" to BentoToneKind.Blue,
        "rate" to BentoToneKind.Lime,
        "privacy" to BentoToneKind.Surface,
        "about" to BentoToneKind.Stage,
    )

    fun isProminent(key: String): Boolean = key in PROMINENT

    fun toneOf(key: String): BentoToneKind = TONES[key] ?: BentoToneKind.Surface

    fun defaultSize(key: String): BentoTileSize = DEFAULT.firstOrNull { it.key == key }?.size ?: BentoTileSize.Small

    fun ordinalOf(key: String): Int = DEFAULT.indexOfFirst { it.key == key }.let { if (it < 0) DEFAULT.size + 1 else it + 1 }

    fun decode(raw: String, available: Collection<String>): BentoTileLayout {
        val allowed = available.toSet()
        val text = raw.trim()
        if (text.isEmpty()) return BentoTileLayout(DEFAULT.filter { it.key in allowed }, emptyList())
        val visiblePart = text.substringBefore('|')
        val hiddenPart = if ('|' in text) text.substringAfter('|') else ""
        val seen = HashSet<String>()
        val visible = visiblePart.split(',').mapNotNull { item ->
            val key = item.substringBefore(':').trim()
            if (key.isEmpty() || key !in allowed || !seen.add(key)) return@mapNotNull null
            val size = item.substringAfter(':', "").trim().firstOrNull()?.let(BentoTileSize::of) ?: defaultSize(key)
            BentoTileSlot(key, size)
        }
        val hidden = hiddenPart.split(',').map { it.trim() }.filter { it.isNotEmpty() && it in allowed && seen.add(it) }
        val missing = DEFAULT.map { it.key }.filter { it in allowed && it !in seen } +
            available.filter { it !in seen && DEFAULT.none { slot -> slot.key == it } }
        return BentoTileLayout(visible + missing.distinct().map { BentoTileSlot(it, defaultSize(it)) }, hidden)
    }

    fun encode(layout: BentoTileLayout): String =
        layout.visible.joinToString(",") { "${it.key}:${it.size.code}" } + "|" + layout.hidden.joinToString(",")

    fun pack(slots: List<BentoTileSlot>, columns: Int): List<BentoPlaced> {
        val width = columns.coerceAtLeast(1)
        val taken = ArrayList<BooleanArray>()
        fun free(row: Int, col: Int): Boolean = row >= taken.size || !taken[row][col]
        fun take(row: Int, col: Int) {
            while (taken.size <= row) taken += BooleanArray(width)
            taken[row][col] = true
        }
        val placed = ArrayList<BentoPlaced>(slots.size)
        for (slot in slots) {
            val w = slot.size.cols.coerceAtMost(width)
            val h = slot.size.rows
            var row = 0
            var done = false
            while (!done) {
                for (col in 0..width - w) {
                    val fits = (row until row + h).all { r -> (col until col + w).all { c -> free(r, c) } }
                    if (fits) {
                        for (r in row until row + h) for (c in col until col + w) take(r, c)
                        placed += BentoPlaced(slot, col, row, w, h)
                        done = true
                        break
                    }
                }
                row++
            }
        }
        return placed
    }

    fun rowsOf(placed: List<BentoPlaced>): Int = placed.maxOfOrNull { it.row + it.rows } ?: 0
}
