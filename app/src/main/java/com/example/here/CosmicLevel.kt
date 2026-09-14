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
        subtitle = "One human life.",
        exponent = 0,
        sizeFact = "~1.7 metres tall"
    ),
    CosmicLevel(
        index = 1,
        title = "Your world",
        subtitle = "Everything you can reach in a day.",
        exponent = 4,
        sizeFact = null
    ),
    CosmicLevel(
        index = 2,
        title = "Earth",
        subtitle = "One planet. 8 billion+ lives.",
        exponent = 7,
        sizeFact = "12,742 km across"
    ),
    CosmicLevel(
        index = 3,
        title = "The Moon",
        subtitle = "Our nearest world beyond Earth.",
        exponent = 9,
        sizeFact = "384,400 km away"
    ),
    CosmicLevel(
        index = 4,
        title = "The Sun",
        subtitle = "The star that holds our days together.",
        exponent = 9,
        sizeFact = "1.39 million km across"
    ),
    CosmicLevel(
        index = 5,
        title = "Our Solar System",
        subtitle = "Eight planets, one star.",
        exponent = 13,
        sizeFact = "~9 billion km across"
    ),
    CosmicLevel(
        index = 6,
        title = "The Milky Way",
        subtitle = "Hundreds of billions of stars.",
        exponent = 21,
        sizeFact = "~100,000 light-years across"
    ),
    CosmicLevel(
        index = 7,
        title = "Our galactic neighborhood",
        subtitle = "The Local Group of galaxies.",
        exponent = 23,
        sizeFact = "~10 million light-years across"
    ),
    CosmicLevel(
        index = 8,
        title = "The cosmic web",
        subtitle = "Galaxies form filaments, not scatter randomly.",
        exponent = 25,
        sizeFact = "~1 billion light-years across"
    ),
    CosmicLevel(
        index = 9,
        title = "The observable universe",
        subtitle = "Everything visible to us. No further to go.",
        exponent = 27,
        sizeFact = "~93 billion light-years across"
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
