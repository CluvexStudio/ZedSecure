package dev.cluvex.zedsecure.ui.easteregg

import kotlin.random.Random

class Namer {
    fun nameSystem(rng: Random): String = buildString {
        append(constellationTable.roll(rng).pull(rng))
        if (rng.nextFloat() <= SUFFIX_PROBABILITY) {
            append(delimiters.roll(rng))
            append(suffixTable.roll(rng).pull(rng))
        }
        if (rng.nextFloat() <= LETTER_PROBABILITY) {
            append(delimiters.roll(rng))
            append('A' + rng.nextInt(0, 26))
        }
        if (rng.nextFloat() <= NUMBER_PROBABILITY) {
            append(delimiters.roll(rng))
            append(rng.nextInt(2, 5039))
        }
    }

    fun describePlanet(rng: Random): String =
        "${planetTable.roll(rng).pull(rng)} ${planetTypes.pull(rng)}"

    fun describeAtmosphere(rng: Random): String = atmosphereTable.roll(rng).pull(rng)

    fun describeLife(rng: Random): String = lifeTable.roll(rng).pull(rng)

    fun describeActivity(rng: Random, planet: Planet?): String = activities.pull(rng)
        .fill("{flora}") { "${planet?.flora ?: "some"} ${floraPlurals.pull(rng)}" }
        .fill("{fauna}") { "${planet?.fauna ?: "some"} ${faunaPlurals.pull(rng)}" }
        .fill("{atmo}") { "${planet?.atmosphere ?: "some"} ${atmospherePlurals.pull(rng)}" }
        .fill("{planet}") { planet?.description ?: "some body" }
        .uppercase()

    private val constellations = Bag(CONSTELLATIONS)
    private val rareConstellations = Bag(RARE_CONSTELLATIONS)
    private val suffixes = Bag(SUFFIXES)
    private val rareSuffixes = Bag(RARE_SUFFIXES)
    private val planetTypes = Bag(PLANET_TYPES)
    private val planetDescriptors = Bag(PLANET_DESCRIPTORS)
    private val lifeDescriptors = Bag(LIFE_DESCRIPTORS)
    private val atmosphereDescriptors = Bag(ATMOSPHERE_DESCRIPTORS)
    private val anyDescriptors = Bag(ANY_DESCRIPTORS)
    private val floraPlurals = Bag(FLORA_PLURALS)
    private val faunaPlurals = Bag(FAUNA_PLURALS)
    private val atmospherePlurals = Bag(ATMOSPHERE_PLURALS)
    private val activities = Bag(ACTIVITIES)

    private val constellationTable =
        RandomTable(RARE_PROBABILITY to rareConstellations, 1f - RARE_PROBABILITY to constellations)
    private val suffixTable =
        RandomTable(RARE_PROBABILITY to rareSuffixes, 1f - RARE_PROBABILITY to suffixes)
    private val planetTable = RandomTable(0.75f to planetDescriptors, 0.25f to anyDescriptors)
    private val lifeTable = RandomTable(0.75f to lifeDescriptors, 0.25f to anyDescriptors)
    private val atmosphereTable =
        RandomTable(0.75f to atmosphereDescriptors, 0.25f to anyDescriptors)
    private val delimiters = RandomTable(
        15f to " ", 3f to "-", 1f to "_", 1f to ".", 1f to "/", 1f to "^", 0.1f to "#!*",
    )

    private companion object {
        const val SUFFIX_PROBABILITY = 0.75f
        const val LETTER_PROBABILITY = 0.3f
        const val NUMBER_PROBABILITY = 0.3f
        const val RARE_PROBABILITY = 0.05f
    }
}

private inline fun String.fill(token: String, value: () -> String): String =
    if (contains(token)) replace(token, value()) else this

private class Bag<T>(items: List<T>) {
    private val stones = items.toMutableList()
    private var next = stones.size

    fun pull(rng: Random): T {
        if (next >= stones.size) {
            stones.shuffle(rng)
            next = 0
        }
        return stones[next++]
    }
}

private class RandomTable<T>(private vararg val entries: Pair<Float, T>) {
    private val total = entries.sumOf { it.first.toDouble() }.toFloat()

    fun roll(rng: Random): T {
        var x = rng.nextFloat() * total
        for ((weight, value) in entries) {
            x -= weight
            if (x < 0f) return value
        }
        return entries.last().second
    }
}

private val CONSTELLATIONS = listOf(
    "Andromeda", "Antlia", "Apus", "Aquarius", "Aquila", "Ara", "Aries", "Auriga", "Boötes",
    "Caelum", "Camelopardalis", "Cancer", "Canis", "Capricornus", "Carina", "Cassiopeia",
    "Centaurus", "Cepheus", "Cetus", "Chamaeleon", "Circinus", "Columba", "Corvus", "Crater",
    "Crux", "Cygnus", "Delphinus", "Dorado", "Draco", "Equuleus", "Eridanus", "Fornax",
    "Gemini", "Grus", "Hercules", "Horologium", "Hydra", "Indus", "Lacerta", "Leo", "Lepus",
    "Libra", "Lupus", "Lynx", "Lyra", "Mensa", "Microscopium", "Monoceros", "Musca", "Norma",
    "Octans", "Ophiuchus", "Orion", "Pavo", "Pegasus", "Perseus", "Phoenix", "Pictor",
    "Pisces", "Puppis", "Pyxis", "Reticulum", "Sagitta", "Sagittarius", "Scorpius", "Sculptor",
    "Scutum", "Serpens", "Sextans", "Taurus", "Telescopium", "Triangulum", "Tucana", "Ursa",
    "Vela", "Virgo", "Volans", "Vulpecula",
)

private val RARE_CONSTELLATIONS = listOf("Cluvex", "Zed", "Bugdroid", "Tunnelis", "Onionis")

private val SUFFIXES = listOf(
    "Prime", "Major", "Minor", "Australis", "Borealis", "Alpha", "Beta", "Gamma", "Delta",
    "Epsilon", "Zeta", "Eta", "Theta", "Proxima", "Ultima", "Centralis", "Nova", "Antiqua",
    "Secunda", "Tertia", "Quadrans", "Nadir", "Zenith",
)

private val RARE_SUFFIXES =
    listOf("Actual", "[REDACTED]", "(Do Not Enter)", "Beta Test", "Prime Prime")

private val PLANET_TYPES =
    listOf("planet", "world", "moon", "body", "rock", "sphere", "globe", "orb", "outpost", "terra")

private val PLANET_DESCRIPTORS = listOf(
    "barren", "verdant", "molten", "frozen", "arid", "oceanic", "cratered", "shattered",
    "ringed", "tidally locked", "silicate", "ferrous", "gaseous", "crystalline",
    "storm-wracked", "ancient", "temperate", "radioactive", "magnetic", "hollow", "terraced",
    "windswept", "salt-crusted", "basalt", "glassy", "fog-bound",
)

private val LIFE_DESCRIPTORS = listOf(
    "photosynthetic", "ambulatory", "gelatinous", "crystalline", "bioluminescent", "colonial",
    "telepathic", "silicon-based", "burrowing", "aerial", "symbiotic", "extremophile",
    "filter-feeding", "parasitic", "hibernating", "chitinous", "migratory", "iridescent",
    "eyeless", "many-legged", "slow-moving", "singing",
)

private val ATMOSPHERE_DESCRIPTORS = listOf(
    "nitrogen", "methane", "ammonia", "carbon dioxide", "helium", "chlorine", "sulfurous",
    "water vapour", "argon", "hydrogen", "ozone", "neon", "iron oxide", "hydrocarbon",
)

private val ANY_DESCRIPTORS = listOf(
    "unremarkable", "surprising", "forbidding", "welcoming", "disquieting", "magnificent",
    "tedious", "luminous", "fragrant", "indifferent", "overrated", "underrated", "familiar",
    "impossible", "well-reviewed",
)

private val FLORA_PLURALS = listOf("specimens", "growths", "spores", "vines", "blooms", "fronds")
private val FAUNA_PLURALS =
    listOf("creatures", "herds", "swarms", "grazers", "burrowers", "singers")
private val ATMOSPHERE_PLURALS = listOf("clouds", "haze", "winds", "vapours", "fronts")

private val ACTIVITIES = listOf(
    "collecting {flora} samples",
    "cataloguing {fauna}",
    "measuring {atmo} density",
    "surveying the {planet}",
    "photographing {fauna} at rest",
    "listening to the {atmo}",
    "planting a flag",
    "sitting quietly among the {flora}",
    "recording {fauna} song",
    "sketching the {planet}",
    "tasting the {atmo} (inadvisable)",
    "counting {flora}",
    "naming every {fauna}",
    "watching the sunset",
    "waiting out a storm",
    "leaving a note for the next crew",
    "checking the {atmo} for signal",
)
