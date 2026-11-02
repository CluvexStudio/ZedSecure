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
    val colorful: Boolean = false,
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
        dock = Color(0xFF1E1B27),
        dockLine = Color(0x24FFFFFF),
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
        dock = Color(0xFFFFFFFF),
        dockLine = Color(0x1A16161C),
        danger = Color(0xFFFFDAD6),
        onDanger = Color(0xFF410002),
    )

    val CitrusDark = BentoColors(
        dark = true,
        ground = Color(0xFF0C0D07),
        surface = Color(0xFF181A0F),
        surface2 = Color(0xFF23261A),
        line = Color(0xFF3A3D2A),
        ink = Color(0xFFF1F2E4),
        muted = Color(0xFFB0B39A),
        stage = Color(0xFF2D3A0B),
        onStage = Color(0xFFEEF8CF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFE2F53A),
        onLime = Color(0xFF1F2400),
        blue = BentoTone(Color(0xFF3B3608), Color(0xFFFFF3A1)),
        violet = BentoTone(Color(0xFF123A2A), Color(0xFFB9F5D7)),
        cyan = BentoTone(Color(0xFF22370F), Color(0xFFD3F5A7)),
        pink = BentoTone(Color(0xFF40280C), Color(0xFFFFD6A6)),
        coral = BentoTone(Color(0xFF43201A), Color(0xFFFFC8BC)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF3A4A12),
        dock = Color(0xFF23261A),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val CitrusLight = BentoColors(
        dark = false,
        ground = Color(0xFFF7F8EC),
        surface = Color(0xFFEBEDDB),
        surface2 = Color(0xFFDEE1C9),
        line = Color(0xFFC6C9AE),
        ink = Color(0xFF17180F),
        muted = Color(0xFF565A44),
        stage = Color(0xFF2D3A0B),
        onStage = Color(0xFFF2FBD6),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFD8EE2C),
        onLime = Color(0xFF1F2400),
        blue = BentoTone(Color(0xFFFFF3B0), Color(0xFF5A4A00)),
        violet = BentoTone(Color(0xFFCFF5E2), Color(0xFF0E4A33)),
        cyan = BentoTone(Color(0xFFE0F5C0), Color(0xFF2C4A0C)),
        pink = BentoTone(Color(0xFFFFE0BE), Color(0xFF6A3A06)),
        coral = BentoTone(Color(0xFFFFD9D0), Color(0xFF6E2414)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFE5F0B8),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val MatchaDark = BentoColors(
        dark = true,
        ground = Color(0xFF0A0D0A),
        surface = Color(0xFF151A15),
        surface2 = Color(0xFF20271F),
        line = Color(0xFF364034),
        ink = Color(0xFFE8F0E6),
        muted = Color(0xFFA3B09F),
        stage = Color(0xFF1F3D27),
        onStage = Color(0xFFDDF3E2),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFB4E36B),
        onLime = Color(0xFF172600),
        blue = BentoTone(Color(0xFF1E3326), Color(0xFFCDEDD6)),
        violet = BentoTone(Color(0xFF2C3416), Color(0xFFE1EFB6)),
        cyan = BentoTone(Color(0xFF0F393A), Color(0xFFB2EDEE)),
        pink = BentoTone(Color(0xFF3D2619), Color(0xFFF4CDB6)),
        coral = BentoTone(Color(0xFF36301E), Color(0xFFF2E6C2)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF28452F),
        dock = Color(0xFF20271F),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val MatchaLight = BentoColors(
        dark = false,
        ground = Color(0xFFF4F7F2),
        surface = Color(0xFFE7EDE4),
        surface2 = Color(0xFFD9E1D5),
        line = Color(0xFFBFC9BB),
        ink = Color(0xFF141A13),
        muted = Color(0xFF4E594B),
        stage = Color(0xFF1F3D27),
        onStage = Color(0xFFE2F6E7),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFA6D65A),
        onLime = Color(0xFF172600),
        blue = BentoTone(Color(0xFFD3EEDB), Color(0xFF1C4A2B)),
        violet = BentoTone(Color(0xFFE6F0C2), Color(0xFF3F4A0E)),
        cyan = BentoTone(Color(0xFFC6EEEE), Color(0xFF0B4748)),
        pink = BentoTone(Color(0xFFF7DCCB), Color(0xFF6A3415)),
        coral = BentoTone(Color(0xFFF3EAC9), Color(0xFF5A4A14)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFD6EAD9),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val NeonDark = BentoColors(
        dark = true,
        ground = Color(0xFF08070C),
        surface = Color(0xFF15131C),
        surface2 = Color(0xFF201D2A),
        line = Color(0xFF383346),
        ink = Color(0xFFF2EEFA),
        muted = Color(0xFFABA3BF),
        stage = Color(0xFF3D0B4F),
        onStage = Color(0xFFFBDDFF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFB9FF3B),
        onLime = Color(0xFF142400),
        blue = BentoTone(Color(0xFF4A0E3B), Color(0xFFFFC1EA)),
        violet = BentoTone(Color(0xFF07344A), Color(0xFFA3EAFF)),
        cyan = BentoTone(Color(0xFF2B1657), Color(0xFFE1D0FF)),
        pink = BentoTone(Color(0xFF373A08), Color(0xFFF8FFA0)),
        coral = BentoTone(Color(0xFF4A1810), Color(0xFFFFC5B5)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF4A1060),
        dock = Color(0xFF201D2A),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val NeonLight = BentoColors(
        dark = false,
        ground = Color(0xFFF8F6FB),
        surface = Color(0xFFECE8F2),
        surface2 = Color(0xFFDED8E8),
        line = Color(0xFFC5BDD3),
        ink = Color(0xFF15121C),
        muted = Color(0xFF554C66),
        stage = Color(0xFF3D0B4F),
        onStage = Color(0xFFFCE2FF),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFA8F21E),
        onLime = Color(0xFF142400),
        blue = BentoTone(Color(0xFFFFD6F1), Color(0xFF6E0F55)),
        violet = BentoTone(Color(0xFFC8F1FF), Color(0xFF00475E)),
        cyan = BentoTone(Color(0xFFE7DBFF), Color(0xFF3D1F80)),
        pink = BentoTone(Color(0xFFF6FAB4), Color(0xFF4F5200)),
        coral = BentoTone(Color(0xFFFFDCD2), Color(0xFF741D0A)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF1DDF7),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val PistachioDark = BentoColors(
        dark = true,
        ground = Color(0xFF0B0C0A),
        surface = Color(0xFF171915),
        surface2 = Color(0xFF22251F),
        line = Color(0xFF393D34),
        ink = Color(0xFFEEF1E9),
        muted = Color(0xFFA8AE9F),
        stage = Color(0xFF2B2348),
        onStage = Color(0xFFE9E2FF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFD3F2A2),
        onLime = Color(0xFF1E2A08),
        blue = BentoTone(Color(0xFF2A3420), Color(0xFFDDEFC7)),
        violet = BentoTone(Color(0xFF2C2547), Color(0xFFE2DAFF)),
        cyan = BentoTone(Color(0xFF14363A), Color(0xFFC2EDF0)),
        pink = BentoTone(Color(0xFF43242E), Color(0xFFFFD6E1)),
        coral = BentoTone(Color(0xFF3A3420), Color(0xFFF7EBC4)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF352C57),
        dock = Color(0xFF22251F),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val PistachioLight = BentoColors(
        dark = false,
        ground = Color(0xFFF6F7F2),
        surface = Color(0xFFEAECE3),
        surface2 = Color(0xFFDDE0D4),
        line = Color(0xFFC4C8BA),
        ink = Color(0xFF151713),
        muted = Color(0xFF535849),
        stage = Color(0xFF2B2348),
        onStage = Color(0xFFEEE8FF),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFC6EB8A),
        onLime = Color(0xFF1E2A08),
        blue = BentoTone(Color(0xFFE4F0D2), Color(0xFF344A1A)),
        violet = BentoTone(Color(0xFFE7E0FF), Color(0xFF3A2C7A)),
        cyan = BentoTone(Color(0xFFCFEFF1), Color(0xFF134A50)),
        pink = BentoTone(Color(0xFFFFDCE6), Color(0xFF6E2338)),
        coral = BentoTone(Color(0xFFF7EDCC), Color(0xFF5A4614)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFE3DDF7),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val OceanDark = BentoColors(
        dark = true,
        ground = Color(0xFF070B10),
        surface = Color(0xFF121923),
        surface2 = Color(0xFF1C2431),
        line = Color(0xFF313D4F),
        ink = Color(0xFFE7EEF8),
        muted = Color(0xFF9FAABD),
        stage = Color(0xFF0E2F57),
        onStage = Color(0xFFD8E9FF),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFF5CF2C8),
        onLime = Color(0xFF00281D),
        blue = BentoTone(Color(0xFF10304F), Color(0xFFC4E2FF)),
        violet = BentoTone(Color(0xFF1E2357), Color(0xFFD6D9FF)),
        cyan = BentoTone(Color(0xFF0A3838), Color(0xFFA8F0EC)),
        pink = BentoTone(Color(0xFF0A3340), Color(0xFFA6E8FF)),
        coral = BentoTone(Color(0xFF38301F), Color(0xFFF2E2BA)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF123A6A),
        dock = Color(0xFF1C2431),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val OceanLight = BentoColors(
        dark = false,
        ground = Color(0xFFF3F7FB),
        surface = Color(0xFFE4EBF3),
        surface2 = Color(0xFFD5DEE9),
        line = Color(0xFFB9C5D4),
        ink = Color(0xFF0F1620),
        muted = Color(0xFF4A5768),
        stage = Color(0xFF0E2F57),
        onStage = Color(0xFFDDEBFF),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFF3BE0B4),
        onLime = Color(0xFF00281D),
        blue = BentoTone(Color(0xFFD2E6FF), Color(0xFF0F3B6E)),
        violet = BentoTone(Color(0xFFDCDEFF), Color(0xFF262E80)),
        cyan = BentoTone(Color(0xFFC4F1EC), Color(0xFF0A4A45)),
        pink = BentoTone(Color(0xFFC6ECF7), Color(0xFF064559)),
        coral = BentoTone(Color(0xFFF3E8CC), Color(0xFF5A4614)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFD6E5F7),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val SunsetDark = BentoColors(
        dark = true,
        ground = Color(0xFF0E0A09),
        surface = Color(0xFF1C1513),
        surface2 = Color(0xFF28201D),
        line = Color(0xFF43362F),
        ink = Color(0xFFF8EDE8),
        muted = Color(0xFFBBA79E),
        stage = Color(0xFF4A1F12),
        onStage = Color(0xFFFFE3D6),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFFFB547),
        onLime = Color(0xFF2E1800),
        blue = BentoTone(Color(0xFF4A2A16), Color(0xFFFFD8B8)),
        violet = BentoTone(Color(0xFF2E1A40), Color(0xFFE6D2FF)),
        cyan = BentoTone(Color(0xFF3F3110), Color(0xFFFFE7A3)),
        pink = BentoTone(Color(0xFF4A1A2A), Color(0xFFFFC6D9)),
        coral = BentoTone(Color(0xFF4A1F1A), Color(0xFFFFC4B8)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF5A2A18),
        dock = Color(0xFF28201D),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val SunsetLight = BentoColors(
        dark = false,
        ground = Color(0xFFFBF6F3),
        surface = Color(0xFFF1E8E3),
        surface2 = Color(0xFFE5D9D2),
        line = Color(0xFFCDBDB4),
        ink = Color(0xFF1C1310),
        muted = Color(0xFF64514A),
        stage = Color(0xFF4A1F12),
        onStage = Color(0xFFFFE6DA),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFFFA62B),
        onLime = Color(0xFF2E1800),
        blue = BentoTone(Color(0xFFFFE2C9), Color(0xFF6A3A10)),
        violet = BentoTone(Color(0xFFEBDDFF), Color(0xFF4A2A70)),
        cyan = BentoTone(Color(0xFFFFEDB8), Color(0xFF5A4300)),
        pink = BentoTone(Color(0xFFFFD9E5), Color(0xFF6E1A38)),
        coral = BentoTone(Color(0xFFFFDAD0), Color(0xFF742214)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF5DCD0),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    val BerryDark = BentoColors(
        dark = true,
        ground = Color(0xFF0D080C),
        surface = Color(0xFF1B1219),
        surface2 = Color(0xFF271B24),
        line = Color(0xFF42303D),
        ink = Color(0xFFF7EAF2),
        muted = Color(0xFFB8A2B0),
        stage = Color(0xFF4A0E3A),
        onStage = Color(0xFFFFD9F2),
        chipOnStage = BrandDark.chipOnStage,
        goodOnStage = BrandDark.goodOnStage,
        lime = Color(0xFFD7F59A),
        onLime = Color(0xFF1E2A06),
        blue = BentoTone(Color(0xFF4A1230), Color(0xFFFFC6E0)),
        violet = BentoTone(Color(0xFF341B4A), Color(0xFFEBD3FF)),
        cyan = BentoTone(Color(0xFF123A2C), Color(0xFFB8F2D8)),
        pink = BentoTone(Color(0xFF4A1C24), Color(0xFFFFCCD5)),
        coral = BentoTone(Color(0xFF46281A), Color(0xFFFFD9C2)),
        good = BrandDark.good,
        mid = BrandDark.mid,
        bad = BrandDark.bad,
        selected = Color(0xFF5A1548),
        dock = Color(0xFF271B24),
        dockLine = BrandDark.dockLine,
        danger = BrandDark.danger,
        onDanger = BrandDark.onDanger,
        colorful = true,
    )

    val BerryLight = BentoColors(
        dark = false,
        ground = Color(0xFFFBF5F8),
        surface = Color(0xFFF1E6EC),
        surface2 = Color(0xFFE5D6DF),
        line = Color(0xFFCDB9C5),
        ink = Color(0xFF1C1018),
        muted = Color(0xFF634C5A),
        stage = Color(0xFF4A0E3A),
        onStage = Color(0xFFFFDDF3),
        chipOnStage = BrandLight.chipOnStage,
        goodOnStage = BrandLight.goodOnStage,
        lime = Color(0xFFC4EC7C),
        onLime = Color(0xFF1E2A06),
        blue = BentoTone(Color(0xFFFFD7E9), Color(0xFF6E1240)),
        violet = BentoTone(Color(0xFFEEDDFF), Color(0xFF4F2A72)),
        cyan = BentoTone(Color(0xFFC9F2DF), Color(0xFF0F4A34)),
        pink = BentoTone(Color(0xFFFFD9E0), Color(0xFF6E1C2C)),
        coral = BentoTone(Color(0xFFFFE0CE), Color(0xFF6A3414)),
        good = BrandLight.good,
        mid = BrandLight.mid,
        bad = BrandLight.bad,
        selected = Color(0xFFF5D8E9),
        dock = Color(0xFFFFFFFF),
        dockLine = BrandLight.dockLine,
        danger = BrandLight.danger,
        onDanger = BrandLight.onDanger,
        colorful = true,
    )

    fun of(palette: BentoPalette, dark: Boolean, seed: Color, amoled: Boolean): BentoColors {
        val base = when (palette) {
            BentoPalette.Brand -> if (dark) BrandDark else BrandLight
            BentoPalette.Accent -> tonal(seed, dark, Kind.Accent)
            BentoPalette.Pastel -> tonal(seed, dark, Kind.Pastel)
            BentoPalette.Vivid -> tonal(seed, dark, Kind.Vivid)
            BentoPalette.Mono -> mono(seed, dark)
            BentoPalette.Citrus -> if (dark) CitrusDark else CitrusLight
            BentoPalette.Matcha -> if (dark) MatchaDark else MatchaLight
            BentoPalette.Neon -> if (dark) NeonDark else NeonLight
            BentoPalette.Pistachio -> if (dark) PistachioDark else PistachioLight
            BentoPalette.Ocean -> if (dark) OceanDark else OceanLight
            BentoPalette.Sunset -> if (dark) SunsetDark else SunsetLight
            BentoPalette.Berry -> if (dark) BerryDark else BerryLight
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
            dock = neutral.toneColor(if (dark) 13 else 99),
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
            dock = n.toneColor(if (dark) 13 else 99),
            dockLine = (if (dark) Color.White else Color.Black).copy(alpha = if (dark) 0.08f else 0.10f),
            danger = badPalette.toneColor(if (dark) 80 else 90),
            onDanger = badPalette.toneColor(if (dark) 20 else 10),
        )
    }

    private fun BentoColors.amoled(): BentoColors = copy(
        ground = Color.Black,
        surface = Color(0xFF111015),
        surface2 = Color(0xFF1B1A21),
        dock = Color(0xFF141319),
    )
}
