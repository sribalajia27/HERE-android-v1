package com.northloom.apeiron

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
    val sizeFact: String?,
    val facts: List<String>
)

val COSMIC_LEVELS = listOf(
    CosmicLevel(
        index = 0,
        title = "You",
        subtitle = "The center of your entire universe.",
        exponent = 0,
        sizeFact = "1.7 meters of stardust",
        facts = listOf(
            "Atom Count: Your body is composed of approximately 7 octillion atoms (mostly hydrogen, oxygen, carbon, and nitrogen).",
            "Stellar Origin: Every heavier atom in your body (iron, calcium, phosphorus) was forged inside ancient dying stars billions of years ago.",
            "Bio-Electricity: Your nervous system generates about 20 watts of electrical power—enough to dimly power a small LED light bulb.",
            "Cellular Turnover: Every day, your body produces around 300 billion new cells to replace dead or damaged tissue."
        )
    ),
    CosmicLevel(
        index = 1,
        title = "Your World",
        subtitle = "Your entire existence, cradled in blue.",
        exponent = 4,
        sizeFact = "Your daily horizon",
        facts = listOf(
            "Immediate Horizon: At 10 kilometers across, this represents your daily physical footprint and immediate atmospheric sphere.",
            "Troposphere: Within this scale lies the troposphere, containing 75% of Earth's atmospheric mass and virtually all weather systems.",
            "Human Perspective: Throughout 99% of human history, an individual's entire known world fit entirely within this single scale stop."
        )
    ),
    CosmicLevel(
        index = 2,
        title = "Earth",
        subtitle = "A pale blue speck suspended in a sunbeam.",
        exponent = 7,
        sizeFact = "Sunlight takes 8 min 20 s to arrive",
        facts = listOf(
            "Orbital Velocity: Earth races around the Sun at an astonishing 107,000 km/h while spinning at 1,670 km/h at the equator.",
            "Water vs Mass: 71% of Earth's surface is covered in water, yet water accounts for less than 0.02% of Earth's total planetary mass.",
            "Protective Shield: Earth's molten iron outer core generates a powerful magnetosphere that deflects lethal solar wind and cosmic radiation.",
            "Atmospheric Thinness: If Earth were the size of a basketball, its entire life-sustaining atmosphere would be thinner than a single sheet of paper."
        )
    ),
    CosmicLevel(
        index = 3,
        title = "The Moon",
        subtitle = "Our silent neighbor, watching over our brief history.",
        exponent = 9,
        sizeFact = "384,400 km away · Light takes 1.3 s",
        facts = listOf(
            "Tidal Locking: The Moon is tidally locked to Earth, rotating on its axis in the exact same time it takes to orbit Earth, keeping one face permanently hidden.",
            "Drifting Away: Due to tidal friction, the Moon is slowly spiraling away from Earth at a rate of 3.8 centimeters every single year.",
            "Impact Archive: The Moon has no atmosphere or tectonic activity, preserving impact craters from asteroid strikes spanning 4.5 billion years.",
            "Stabilizing Axis: The Moon's gravitational pull stabilizes Earth's axial tilt, preventing extreme, chaotic climate swings."
        )
    ),
    CosmicLevel(
        index = 4,
        title = "The Sun",
        subtitle = "Our raging nuclear furnace fueling every breath you take.",
        exponent = 9,
        sizeFact = "1.39 million km across",
        facts = listOf(
            "Solar Mass: The Sun accounts for 99.86% of all physical mass in the entire solar system.",
            "Nuclear Fusion: Every second, the core fuses 600 million tons of hydrogen into helium, converting 4 million tons of matter directly into pure radiant energy.",
            "Blazing Core: Temperatures at the solar core reach a staggering 15 million°C, while the visible surface (photosphere) is a relatively cool 5,500°C.",
            "Light Travel: A photon generated at the solar core takes upwards of 100,000 years to reach the surface, but only 8 minutes to travel from the surface to Earth."
        )
    ),
    CosmicLevel(
        index = 5,
        title = "Our Solar System",
        subtitle = "8 worlds dancing in the void. V1 & V2 blink back from the dark.",
        exponent = 13,
        sizeFact = "Voyager 1 & 2: 23 light-hours deep in interstellar space",
        facts = listOf(
            "Oort Cloud: The solar system's gravitational boundary extends outward into the Oort Cloud, nearly 2 light-years (halfway to Proxima Centauri).",
            "Voyager Legacy: Launched in 1977, Voyager 1 is humanity's most distant emissary, transmitting data from beyond the heliosphere over 24 billion km away.",
            "Orbital Plane: All eight planets orbit the Sun in nearly the exact same flat plane, a relic of the spinning protoplanetary disk formed 4.6 billion years ago.",
            "Gas Giants: Jupiter and Saturn contain more than 90% of the planetary mass orbiting the Sun."
        )
    ),
    CosmicLevel(
        index = 6,
        title = "The Milky Way",
        subtitle = "400 billion stars. You are invisible from here.",
        exponent = 21,
        sizeFact = "100,000 light-years across",
        facts = listOf(
            "Stellar Empire: The Milky Way contains between 100 to 400 billion stars and at least 100 billion planets.",
            "Supermassive Heart: At the galactic center lies Sagittarius A*, a supermassive black hole weighing 4 million times the mass of our Sun.",
            "Galactic Year: Our solar system takes roughly 230 million years to complete one single orbit around the galactic center (a 'galactic year').",
            "Dark Matter Halo: Over 90% of the Milky Way's mass is invisible dark matter holding the spinning disc together against centrifugal disruption."
        )
    ),
    CosmicLevel(
        index = 7,
        title = "Our Galactic Neighborhood",
        subtitle = "A tiny cluster of island universes drifting in nothingness.",
        exponent = 23,
        sizeFact = "10 million light-years across",
        facts = listOf(
            "Local Group: Our galaxy is part of the Local Group—a gravitational club of over 80 galaxies spanning 10 million light-years.",
            "Andromeda Collision: The Milky Way and the neighboring Andromeda Galaxy are hurtling toward each other at 110 km/s, scheduled to merge in ~4.5 billion years.",
            "Dwarf Companions: The Local Group is dominated by giant spirals (Milky Way and Andromeda) accompanied by dozens of tiny dwarf spheroidal galaxies."
        )
    ),
    CosmicLevel(
        index = 8,
        title = "The Cosmic Web",
        subtitle = "Galaxies strung together like fragile glowing webs.",
        exponent = 25,
        sizeFact = "1 billion light-years across",
        facts = listOf(
            "Cosmic Neural Network: Galaxies are distributed in vast intersecting filaments and clusters, resembling a colossal neural network across space.",
            "Cosmic Voids: Spanning between these glowing filaments are immense dark voids—empty regions hundreds of millions of light-years across devoid of galaxies.",
            "Dark Matter Scaffolding: The visible glowing web of galaxies is merely the frosting on an invisible scaffolding of primordial dark matter."
        )
    ),
    CosmicLevel(
        index = 9,
        title = "The Observable Universe",
        subtitle = "~2 trillion galaxies (conservative estimate). Beyond lies the incomprehensible.",
        exponent = 27,
        sizeFact = "We only see what light has reached. Beyond, realms exist beyond human comprehension.",
        facts = listOf(
            "Galaxy Count: Deep field observations by Hubble and James Webb estimate there are over 2 trillion galaxies in the observable universe.",
            "Cosmic Microwave Background: The oldest light in existence—a relic glow from the Big Bang 13.8 billion years ago—blankets every inch of the sky.",
            "Superluminal Expansion: Space itself is expanding faster than the speed of light, meaning the most distant galaxies are permanently receding beyond our horizon."
        )
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
