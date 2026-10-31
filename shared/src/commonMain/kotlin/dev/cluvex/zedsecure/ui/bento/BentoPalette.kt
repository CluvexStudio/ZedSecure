package dev.cluvex.zedsecure.ui.bento

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.materialkolor.ktx.toHct
import com.materialkolor.ktx.toneColor
import com.materialkolor.palettes.TonalPalette
import dev.cluvex.zedsecure.domain.model.BentoPalette
import kotlin.math.max

@Immutable
data class BentoTone(val container: Color, val content: Color)

@Immutable
data class BentoColors(
    val dark: Boolean,
    val ground: Color,
    val surface: Color,
    val surface2: Color,
    val line: Color,
    val ink: Color,
    val muted: Color,
    val stage: Color,
    val onStage: Color,
    val chipOnStage: Color,
    val goodOnStage: Color,
    val lime: Color,
    val onLime: Color,
    val blue: BentoTone,
    val violet: BentoTone,
    val cyan: BentoTone,
    val pink: BentoTone,
    val coral: BentoTone,
    val good: Color,
    val mid: Color,
    val bad: Color,
    val selected: Color,
    val dock: Color,
    val dockLine: Color,
    val danger: Color,
    val onDanger: Color,
) {
    val stageTone: BentoTone get() = BentoTone(stage, onStage)
    val limeTone: BentoTone get() = BentoTone(lime, onLime)
    val surfaceTone: BentoTone get() = BentoTone(surface, ink)

    fun tone(kind: BentoToneKind): BentoTone = when (kind) {
        BentoToneKind.Stage -> stageTone
        BentoToneKind.Lime -> limeTone
        BentoToneKind.Blue -> blue
        BentoToneKind.Violet -> violet
        BentoToneKind.Cyan -> cyan
        BentoToneKind.Pink -> pink
        BentoToneKind.Coral -> coral
        BentoToneKind.Surface -> surfaceTone
    }

    fun pingColor(ms: Int): Color = when {
        ms <= 0 -> bad
        ms < 300 -> good
        ms < 800 -> mid
        else -> bad
    }
}

enum class BentoToneKind { Stage, Lime, Blue, Violet, Cyan, Pink, Coral, Surface }

object BentoPalettes {
    val BrandDark = BentoColors(
        dark = true,
        ground = Color(0xFF0B0A10),
        surface = Color(0xFF18161F),
        surface2 = Color(0xFF24212D),
        line = Color(0xFF3A3646),
        ink = Color(0xFFEEEAF6),
        muted = Color(0xFFA6A0B8),
        stage = Color(0xFF311B6B),
        onStage = Color(0xFFEFE8FF),
        chipOnStage = Color(0x1FFFFFFF),
        goodOnStage = Color(0xFF9BF0AE),
        lime = Color(0xFFC7F24E),
        onLime = Color(0xFF1A2200),
        blue = BentoTone(Color(0xFF15294A), Color(0xFFCFE1FF)),
        violet = BentoTone(Color(0xFF2A2352), Color(0xFFDDD5FF)),
        cyan = BentoTone(Color(0xFF103D3A), Color(0xFFB3F1EC)),
        pink = BentoTone(Color(0xFF4A1C35), Color(0xFFFFD0E5)),
        coral = BentoTone(Color(0xFF4A251D), Color(0xFFFFD3C6)),
        good = Color(0xFF7BE495),
        mid = Color(0xFFF2C14E),
        bad = Color(0xFFFF8A80),
        selected = Color(0xFF2B1D57),
        dock = Color(0xF01E1B27),
        dockLine = Color(0x14FFFFFF),
        danger = Color(0xFFFFB4AB),
        onDanger = Color(0xFF690005),
    )

    val BrandLight = BentoColors(
        dark = false,
        ground = Color(0xFFF6F6F0),
        surface = Color(0xFFEAEAE2),
        surface2 = Color(0xFFDCDCD2),
        line = Color(0xFFC5C5BA),
        ink = Color(0xFF16161C),
        muted = Color(0xFF55555F),
        stage = Color(0xFF2A1361),
        onStage = Color(0xFFF1EBFF),
        chipOnStage = Color(0x24FFFFFF),
        goodOnStage = Color(0xFF9BF0AE),
        lime = Color(0xFFC7F24E),
        onLime = Color(0xFF1A2200),
        blue = BentoTone(Color(0xFFD4E4FF), Color(0xFF123A70)),
        violet = BentoTone(Color(0xFFE3DBFF), Color(0xFF36268A)),
        cyan = BentoTone(Color(0xFFC6F1ED), Color(0xFF0B4A45)),
        pink = BentoTone(Color(0xFFFFD6E7), Color(0xFF6E1543)),
        coral = BentoTone(Color(0xFFFFDCCD), Color(0xFF6D2914)),
        good = Color(0xFF16803F),
        mid = Color(0xFF8A5A00),
        bad = Color(0xFFB3261E),
        selected = Color(0xFFE3DBFF),
        dock = Color(0xF0FFFFFF),
        dockLine = Color(0x1A16161C),
        danger = Color(0xFFFFDAD6),
        onDanger = Color(0xFF410002),
    )

    fun of(palette: BentoPalette, dark: Boolean, seed: Color, amoled: Boolean): BentoColors {
        val base = when (palette) {
            BentoPalette.Brand -> if (dark) BrandDark else BrandLight
            BentoPalette.Accent -> tonal(seed, dark, Kind.Accent)
            BentoPalette.Pastel -> tonal(seed, dark, Kind.Pastel)
            BentoPalette.Vivid -> tonal(seed, dark, Kind.Vivid)
            BentoPalette.Mono -> mono(seed, dark)
        }
        return if (amoled && dark) base.amoled() else base
    }

    private enum class Kind { Accent, Pastel, Vivid }

    private fun palette(hue: Double, chroma: Double): TonalPalette =
        TonalPalette.fromHueAndChroma(((hue % 360.0) + 360.0) % 360.0, chroma)

    private fun tonal(seed: Color, dark: Boolean, kind: Kind): BentoColors {
        val hct = seed.toHct()
        val hue = hct.hue
        val neutral = palette(hue, 6.0)
        val neutralVariant = palette(hue, 10.0)
        val accent = palette(
            hue,
            when (kind) {
                Kind.Accent -> max(hct.chroma, 56.0)
                Kind.Pastel -> 40.0
                Kind.Vivid -> 92.0
            },
        )
        val stage = palette(hue, if (kind == Kind.Pastel) 40.0 else 64.0)
        val tileChroma = when (kind) {
            Kind.Accent -> 36.0
            Kind.Pastel -> 26.0
            Kind.Vivid -> 66.0
        }
        fun tile(offset: Double): BentoTone {
            val p = palette(hue + offset, tileChroma)
            return when (kind) {
                Kind.Pastel -> BentoTone(p.toneColor(if (dark) 82 else 92), p.toneColor(if (dark) 16 else 24))
                Kind.Vivid -> BentoTone(p.toneColor(if (dark) 38 else 76), p.toneColor(if (dark) 96 else 10))
                Kind.Accent -> if (dark) BentoTone(p.toneColor(20), p.toneColor(88)) else BentoTone(p.toneColor(90), p.toneColor(24))
            }
        }
        val goodPalette = palette(145.0, 48.0)
        val midPalette = palette(80.0, 60.0)
        val badPalette = palette(25.0, 70.0)
        return BentoColors(
            dark = dark,
            ground = neutral.toneColor(if (dark) 4 else 97),
            surface = neutral.toneColor(if (dark) 10 else 92),
            surface2 = neutral.toneColor(if (dark) 15 else 87),
            line = neutralVariant.toneColor(if (dark) 26 else 78),
            ink = neutral.toneColor(if (dark) 93 else 8),
            muted = neutralVariant.toneColor(if (dark) 70 else 36),
            stage = stage.toneColor(if (dark) 22 else 18),
            onStage = stage.toneColor(if (dark) 92 else 95),
            chipOnStage = Color.White.copy(alpha = if (dark) 0.12f else 0.14f),
            goodOnStage = goodPalette.toneColor(88),
            lime = accent.toneColor(88),
            onLime = accent.toneColor(14),
            blue = tile(60.0),
            violet = tile(120.0),
            cyan = tile(180.0),
            pink = tile(240.0),
            coral = tile(300.0),
            good = goodPalette.toneColor(if (dark) 80 else 40),
            mid = midPalette.toneColor(if (dark) 80 else 42),
            bad = badPalette.toneColor(if (dark) 72 else 40),
            selected = stage.toneColor(if (dark) 20 else 90),
            dock = neutral.toneColor(if (dark) 13 else 99).copy(alpha = 0.94f),
            dockLine = (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.08f else 0.10f),
            danger = badPalette.toneColor(if (dark) 80 else 90),
            onDanger = badPalette.toneColor(if (dark) 20 else 10),
        )
    }

    private fun mono(seed: Color, dark: Boolean): BentoColors {
        val hue = seed.toHct().hue
        val n = palette(hue, 3.0)
        fun tile(dTone: Int, lTone: Int) =
            if (dark) BentoTone(n.toneColor(dTone), n.toneColor(94)) else BentoTone(n.toneColor(lTone), n.toneColor(10))
        val goodPalette = palette(145.0, 40.0)
        val midPalette = palette(80.0, 50.0)
        val badPalette = palette(25.0, 64.0)
        return BentoColors(
            dark = dark,
            ground = n.toneColor(if (dark) 4 else 97),
            surface = n.toneColor(if (dark) 10 else 92),
            surface2 = n.toneColor(if (dark) 16 else 86),
            line = n.toneColor(if (dark) 27 else 76),
            ink = n.toneColor(if (dark) 94 else 8),
            muted = n.toneColor(if (dark) 70 else 36),
            stage = n.toneColor(if (dark) 22 else 16),
            onStage = n.toneColor(if (dark) 95 else 96),
            chipOnStage = Color.White.copy(alpha = 0.12f),
            goodOnStage = goodPalette.toneColor(88),
            lime = n.toneColor(if (dark) 94 else 12),
            onLime = n.toneColor(if (dark) 8 else 96),
            blue = tile(14, 90),
            violet = tile(18, 86),
            cyan = tile(12, 88),
            pink = tile(20, 84),
            coral = tile(16, 91),
            good = goodPalette.toneColor(if (dark) 80 else 38),
            mid = midPalette.toneColor(if (dark) 80 else 40),
            bad = badPalette.toneColor(if (dark) 72 else 40),
            selected = n.toneColor(if (dark) 20 else 84),
            dock = n.toneColor(if (dark) 13 else 99).copy(alpha = 0.94f),
            dockLine = (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.08f else 0.10f),
            danger = badPalette.toneColor(if (dark) 80 else 90),
            onDanger = badPalette.toneColor(if (dark) 20 else 10),
        )
    }

    private fun BentoColors.amoled(): BentoColors = copy(
        ground = Color.Black,
        surface = Color(0xFF111015),
        surface2 = Color(0xFF1B1A21),
        dock = Color(0xF0141319),
    )
}
