package com.northloom.apeiron

import android.content.Context
import org.json.JSONObject

/**
 * The twelve orders-of-magnitude stops in the journey, from a human body
 * to beyond the observable universe. Exponents are approximate but
 * scientifically grounded (metres, base-10), so the scale indicator is
 * never make-believe.
 */
data class CosmicLevel(
    val index: Int,
    val title: String,
    val subtitle: String,
    val exponent: Int,
    val sizeFact: String?,
    val facts: List<String>
)

private var cachedLevels: List<CosmicLevel>? = null

fun getCosmicLevels(context: Context? = null): List<CosmicLevel> {
    if (cachedLevels != null) return cachedLevels!!
    if (context == null) return COSMIC_LEVELS_FALLBACK
    try {
        val inputStream = context.assets.open("cosmic_facts.json")
        val jsonString = inputStream.bufferedReader().use { it.readText() }
        val jsonObject = JSONObject(jsonString)
        val jsonArray = jsonObject.getJSONArray("levels")
        val list = mutableListOf<CosmicLevel>()
        for (i in 0 until jsonArray.length()) {
            val item = jsonArray.getJSONObject(i)
            val factsArray = item.getJSONArray("facts")
            val allFacts = mutableListOf<String>()
            for (j in 0 until factsArray.length()) {
                allFacts.add(factsArray.getString(j))
            }
            list.add(
                CosmicLevel(
                    index = item.getInt("index"),
                    title = item.getString("title"),
                    subtitle = item.getString("subtitle"),
                    exponent = item.getInt("exponent"),
                    sizeFact = item.optString("sizeFact").takeIf { it.isNotBlank() },
                    facts = allFacts
                )
            )
        }
        cachedLevels = list
        return list
    } catch (e: Exception) {
        return COSMIC_LEVELS_FALLBACK
    }
}

val COSMIC_LEVELS: List<CosmicLevel>
    get() = cachedLevels ?: COSMIC_LEVELS_FALLBACK

val COSMIC_LEVELS_FALLBACK = listOf(
    CosmicLevel(
        index = 0,
        title = "You",
        subtitle = "The center of your entire universe.",
        exponent = 0,
        sizeFact = "1.7 meters of stardust",
        facts = listOf(
            "Atom Count: Your body is composed of approximately 7 octillion atoms.",
            "Stellar Origin: Every heavier atom in your body was forged inside ancient dying stars.",
            "Bio-Electricity: Your nervous system generates about 20 watts of electrical power.",
            "Cellular Turnover: Your body produces around 300 billion new cells daily."
        )
    ),
    CosmicLevel(
        index = 1,
        title = "Your World",
        subtitle = "Your entire existence, cradled in blue.",
        exponent = 4,
        sizeFact = "Your daily horizon",
        facts = listOf(
            "Immediate Horizon: At 10 kilometers across, this represents your daily physical footprint.",
            "Troposphere: Contains 75% of Earth's atmospheric mass and virtually all weather systems."
        )
    ),
    CosmicLevel(
        index = 2,
        title = "Earth",
        subtitle = "A pale blue speck suspended in a sunbeam.",
        exponent = 7,
        sizeFact = "Sunlight takes 8 min 20 s to arrive",
        facts = listOf(
            "Orbital Velocity: Earth races around the Sun at 107,000 km/h.",
            "Water vs Mass: 71% surface water, yet water is <0.02% of total planetary mass.",
            "Protective Shield: Molten iron core generates a magnetosphere shielding solar wind."
        )
    ),
    CosmicLevel(
        index = 3,
        title = "The Moon",
        subtitle = "Our silent neighbor, watching over our brief history.",
        exponent = 9,
        sizeFact = "384,400 km away · Light takes 1.3 s",
        facts = listOf(
            "Tidal Locking: Keeps one face permanently facing Earth.",
            "Drifting Away: Spirals away from Earth by 3.8 cm every year."
        )
    ),
    CosmicLevel(
        index = 4,
        title = "The Sun",
        subtitle = "Our raging nuclear furnace fueling every breath you take.",
        exponent = 9,
        sizeFact = "1.39 million km across",
        facts = listOf(
            "Solar Mass: Accounts for 99.86% of all mass in the solar system.",
            "Nuclear Fusion: Fuses 600 million tons of hydrogen into helium every second."
        )
    ),
    CosmicLevel(
        index = 5,
        title = "Our Solar System",
        subtitle = "8 worlds dancing in the void. V1 & V2 blink back from the dark.",
        exponent = 13,
        sizeFact = "Voyager 1 & 2: 23 light-hours deep in interstellar space",
        facts = listOf(
            "Oort Cloud: Gravitational boundary extending nearly 2 light-years.",
            "Voyager Legacy: Humanity's most distant emissary traveling beyond the heliosphere."
        )
    ),
    CosmicLevel(
        index = 6,
        title = "The Milky Way",
        subtitle = "400 billion stars. You are invisible from here.",
        exponent = 21,
        sizeFact = "100,000 light-years across",
        facts = listOf(
            "Stellar Empire: Contains 100 to 400 billion stars.",
            "Supermassive Heart: Sagittarius A* black hole weighing 4 million suns."
        )
    ),
    CosmicLevel(
        index = 7,
        title = "Our Galactic Neighborhood",
        subtitle = "A tiny cluster of island universes drifting in nothingness.",
        exponent = 23,
        sizeFact = "10 million light-years across",
        facts = listOf(
            "Local Group: Gravitational club of over 80 galaxies.",
            "Andromeda Collision: Set to merge with Milky Way in 4.5 billion years."
        )
    ),
    CosmicLevel(
        index = 8,
        title = "The Laniakea Supercluster",
        subtitle = "Rivers of 100,000 galaxies flowing together toward a massive gravitational basin.",
        exponent = 24,
        sizeFact = "520 million light-years across · Home to the Great Attractor",
        facts = listOf(
            "Laniakea Meaning: 'Immeasurable Heaven' in Hawaiian.",
            "The Great Attractor: All 100,000 galaxies slide along gravitational streamlines."
        )
    ),
    CosmicLevel(
        index = 9,
        title = "The Cosmic Web",
        subtitle = "Galaxies strung together like fragile glowing webs.",
        exponent = 25,
        sizeFact = "1 billion light-years across",
        facts = listOf(
            "Cosmic Neural Network: Galaxies distributed in vast intersecting filaments.",
            "Cosmic Voids: Immense dark voids hundreds of millions of light-years across."
        )
    ),
    CosmicLevel(
        index = 10,
        title = "The Observable Universe",
        subtitle = "~2 trillion galaxies (conservative estimate). Beyond lies the infinite unknown.",
        exponent = 27,
        sizeFact = "We only see what light has reached. Beyond, realms exist in eternal shadow.",
        facts = listOf(
            "Galaxy Count: Over 2 trillion galaxies in the observable universe.",
            "Cosmic Microwave Background: Relic glow from the Big Bang 13.8 billion years ago."
        )
    ),
    CosmicLevel(
        index = 11,
        title = "Beyond the Observable Universe",
        subtitle = "Where light has never touched, and space stretches into the infinite.",
        exponent = 29,
        sizeFact = "An endless tapestry beyond our cosmic light horizon",
        facts = listOf(
            "The Light Horizon: Everything beyond 46.5 billion light-years cloaked in darkness.",
            "Infinite Inflation: Space stretches infinitely into a multiverse."
        )
    )
)

const val MAX_LEVEL = 11f
const val EARTH_LEVEL = 2

/** Renders an exponent as a superscript power-of-ten string, e.g. 10²⁶. */
fun exponentLabel(exponent: Int): String {
    val superscripts = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴',
        '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹'
    )
    val supExp = exponent.toString().map { superscripts[it] ?: it }.joinToString("")
    return "10$supExp m"
}
