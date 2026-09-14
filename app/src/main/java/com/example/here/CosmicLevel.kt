package com.example.here

/**
 * The ten orders-of-magnitude stops in the journey, from a human body
 * to the edge of the observable universe. Exponents are approximate but
 * scientifically grounded (metres, base-10), so the scale indicator is
 * never make-believe.
 */
data class CosmicLevel(
    val index: Int,
    val title: String,
    val subtitle: String,
    val exponent: Int,
    val sizeFact: String?
)

val COSMIC_LEVELS = listOf(
    CosmicLevel(
        index = 0,
        title = "You",
        subtitle = "The center of your entire universe.",
        exponent = 0,
        sizeFact = "1.7 meters of stardust"
    ),
    CosmicLevel(
        index = 1,
        title = "Your World",
        subtitle = "Your entire existence, cradled in blue.",
        exponent = 4,
        sizeFact = "Your daily horizon"
    ),
    CosmicLevel(
        index = 2,
        title = "Earth",
        subtitle = "A pale blue speck suspended in a sunbeam.",
        exponent = 7,
        sizeFact = "Sunlight takes 8 min 20 s to arrive"
    ),
    CosmicLevel(
        index = 3,
        title = "The Moon",
        subtitle = "Our silent neighbor, watching over our brief history.",
        exponent = 9,
        sizeFact = "384,400 km away · Light takes 1.3 s"
    ),
    CosmicLevel(
        index = 4,
        title = "The Sun",
        subtitle = "A raging nuclear furnace fueling every breath you take.",
        exponent = 9,
        sizeFact = "1.39 million km across"
    ),
    CosmicLevel(
        index = 5,
        title = "Our Solar System",
        subtitle = "8 worlds dancing in the void. V1 & V2 blink back from the dark.",
        exponent = 13,
        sizeFact = "Voyager 1 & 2: 23 light-hours deep in interstellar space"
    ),
    CosmicLevel(
        index = 6,
        title = "The Milky Way",
        subtitle = "400 billion stars. You are invisible from here.",
        exponent = 21,
        sizeFact = "100,000 light-years across"
    ),
    CosmicLevel(
        index = 7,
        title = "Our Galactic Neighborhood",
        subtitle = "A tiny cluster of island universes drifting in nothingness.",
        exponent = 23,
        sizeFact = "10 million light-years across"
    ),
    CosmicLevel(
        index = 8,
        title = "The Cosmic Web",
        subtitle = "Galaxies strung together like fragile glowing webs.",
        exponent = 25,
        sizeFact = "1 billion light-years across"
    ),
    CosmicLevel(
        index = 9,
        title = "The Observable Universe",
        subtitle = "~2 trillion galaxies (conservative estimate). Beyond lies the incomprehensible.",
        exponent = 27,
        sizeFact = "We only see what light has reached. Beyond, realms exist beyond human comprehension."
    )
)

const val MAX_LEVEL = 9f
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
