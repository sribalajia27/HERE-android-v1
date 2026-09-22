package com.example.here

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.graphics.drawscope.scale
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the current cosmic scale as an abstract scene with soft glow, gentle idle
 * motion (rotation/orbit/twinkle), and cross-fades into the next scale as `fraction`
 * (0f...1f) advances. `time` is continuous seconds, used purely for idle animation so
 * nothing on screen ever looks like a frozen diagram.
 */
@Composable
fun CosmicVisual(
    level: Int,
    fraction: Float,
    time: Float,
    modifier: Modifier = Modifier,
    showCityLights: Boolean = false,
    userAvatar: String? = null,
) {
    val current = COSMIC_LEVELS[level.coerceIn(0, COSMIC_LEVELS.size - 1)]
    val nextIndex = (level + 1).coerceAtMost(COSMIC_LEVELS.size - 1)
    val next = COSMIC_LEVELS[nextIndex]

    val field = remember { Random(42) }
    // Increased from 400 to 1200 points to make the observable universe look much denser
    val points = remember { List(1200) { Offset(field.nextFloat(), field.nextFloat()) } }
    val surroundingsImage = ImageBitmap.imageResource(R.drawable.surroundings_landscape)
    val earth = ImageBitmap.imageResource(R.drawable.earth_apollo)
    val moon = ImageBitmap.imageResource(R.drawable.moon)

    Canvas(modifier = modifier.fillMaxSize()) {
        if (current.index == next.index) {
            drawScene(current.index, 1f, points, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
        } else {
            // THE FIX: Cinematic Pull-Back with proper Z-Ordering.
            // A "slideshow" feel happens when a flat image fades in ON TOP of another flat image.
            // To fix this, we respect physical 3D space:
            // 1. The LARGER environment (next) is drawn in the BACKGROUND.
            // 2. The SMALLER object (current) is drawn in the FOREGROUND.
            
            // Background Layer (Incoming larger scale)
            // Starts just slightly larger (1.15x) and settles to 1.0x, 
            // enveloping the foreground smoothly.
            val inScale = 1.15f - (0.15f * fraction)
            scale(scale = inScale, pivot = center) {
                drawScene(next.index, fraction, points, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
            }

            // Foreground Layer (Outgoing smaller scale)
            // Shrinks just slightly (0.85x) as if we are physically pulling the camera away from it.
            val outScale = 1f - (0.15f * fraction)
            scale(scale = outScale, pivot = center) {
                drawScene(current.index, 1f - fraction, points, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
            }
        }
    }
}

private fun DrawScope.drawScene(
    levelIndex: Int,
    alpha: Float,
    points: List<Offset>,
    time: Float,
    showCityLights: Boolean,
    surroundingsImage: ImageBitmap,
    earth: ImageBitmap,
    moon: ImageBitmap,
    userAvatar: String?
) {
    if (alpha <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val short = min(size.width, size.height)

    when (levelIndex) {
        0 -> drawYou(c, short, alpha, time, userAvatar)
        1 -> drawSurroundings(c, short, alpha, surroundingsImage, time)
        2 -> drawEarth(c, short, alpha, time, showCityLights, earth)
        3 -> drawMoon(c, short, alpha, moon, time)
        4 -> drawSun(c, short, alpha, time)
        5 -> drawSolarSystem(c, short, alpha, time)
        6 -> drawGalaxy(c, short, alpha, points, time)
        7 -> drawLocalGroup(c, short, alpha, points, time)
        8 -> drawCosmicWeb(c, short, alpha, points, time)
        9 -> drawObservableUniverse(c, short, alpha, points, time)
    }
}

/** Soft radial glow behind a body — the single biggest thing separating "real" from "diagram". */
private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    if ((alpha <= 0f) || (radius <= 0f)) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawYou(c: Offset, short: Float, a: Float, time: Float, userAvatar: String?) {
    val r = short * 0.09f
    // Vibrant multi-color stardust aura around You
    glow(c, r * 3.0f, Color(0xFF4CC9F0), a * 0.35f)
    glow(c, r * 2.0f, Color(0xFFFFD166), a * 0.3f)
    glow(c, r * 1.5f, Color(0xFFF72585), a * 0.25f)

    if (userAvatar != null) {
        // Draw the selected emoji avatar
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                textSize = r * 2.2f 
                isAntiAlias = true
                alpha = (a * 255).toInt()
                textAlign = Paint.Align.CENTER
            }
            // Offset Y slightly to center the emoji baseline vertically
            canvas.nativeCanvas.drawText(userAvatar, c.x, c.y + r * 0.75f, paint)
        }
    } else {
        // Fallback generic body shape
        val headRadius = r * 0.28f
        drawCircle(Color.White.copy(alpha = a * 0.95f), radius = headRadius, center = Offset(c.x, c.y - r * 0.58f))
        drawOval(
            color = Color.White.copy(alpha = a * 0.95f),
            topLeft = Offset(c.x - r * 0.48f, c.y - r * 0.22f),
            size = Size(r * 0.96f, r * 1.2f)
        )
    }

    drawCircle(Color(0xFF4CC9F0).copy(alpha = a * 0.6f), radius = r * 1.08f, center = c, style = Stroke(width = 2f))

    // Twinkling stardust around You
    for (i in 0..5) {
        val angle = i * 1.047 + time * 0.5
        val dist = r * (1.3f + 0.2f * sin(time + i))
        val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        drawCircle(Color(0xFFFFD166).copy(alpha = a * 0.7f), radius = short * 0.002f, center = pos)
    }
}

private fun DrawScope.drawSurroundings(
    c: Offset,
    short: Float,
    a: Float,
    image: ImageBitmap,
    time: Float
) {
    val imageRadius = short * 0.18f
    val pulse = 1f + 0.04f * sin(time * 2.5f)
    // Vibrant sunset/aurora multi-color glow
    glow(c, imageRadius * 2.6f * pulse, Color(0xFF4361EE), a * 0.35f)
    glow(c, imageRadius * 1.9f * pulse, Color(0xFFF72585), a * 0.3f)
    glow(c, imageRadius * 1.4f * pulse, Color(0xFFFFD166), a * 0.25f)
    drawCircularImage(image, c, imageRadius, a)
    drawCircle(Color(0xFFFFEE93).copy(alpha = a * 0.6f), radius = imageRadius * 1.04f, center = c, style = Stroke(width = 2f))

    // Orbiting atmospheric / dust motes (consistent animation)
    for (i in 0..7) {
        val angle = i * 0.785 + time * 0.4
        val dist = imageRadius * (1.2f + 0.15f * sin(time * 1.5f + i))
        val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        val twinkle = 0.5f + 0.5f * sin(time * 3f + i)
        drawCircle(Color(0xFFFFD166).copy(alpha = a * 0.7f * twinkle), radius = short * 0.0025f, center = pos)
    }
}

private fun DrawScope.drawCircularImage(image: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    val diameter = (radius * 2f).toInt()
    val sourceSize = minOf(image.width, image.height)
    val sourceOffset = IntOffset(
        (image.width - sourceSize) / 2,
        (image.height - sourceSize) / 2
    )
    val clip = Path().apply {
        addOval(Rect(center.x - radius, center.y - radius, center.x + radius, center.y + radius))
    }
    clipPath(clip) {
        drawImage(
            image = image,
            srcOffset = sourceOffset,
            srcSize = IntSize(sourceSize, sourceSize),
            dstOffset = IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
            dstSize = IntSize(diameter, diameter),
            alpha = alpha
        )
    }
}

private fun DrawScope.drawEarth(
    c: Offset,
    short: Float,
    a: Float,
    time: Float,
    cityLights: Boolean,
    earth: ImageBitmap
) {
    val r = short * 0.22f
    val pulse = 1f + 0.03f * sin(time * 2f)
    // Vivid neon blue and auroral cyan atmosphere glow
    glow(c, r * 2.7f * pulse, Color(0xFF4CC9F0), a * 0.55f)
    glow(c, r * 2.0f * pulse, Color(0xFF3A7DFF), a * 0.5f)
    glow(c, r * 1.4f * pulse, Color(0xFF7209B7), a * 0.35f)

    val diameter = (r * 2f).toInt()
    val sourceSize = minOf(earth.width, earth.height)
    val sourceOffset = IntOffset(
        (earth.width - sourceSize) / 2,
        (earth.height - sourceSize) / 2
    )
    val clip = Path().apply {
        addOval(Rect(c.x - r, c.y - r, c.x + r, c.y + r))
    }
    clipPath(clip) {
        drawImage(
            image = earth,
            srcOffset = sourceOffset,
            srcSize = IntSize(sourceSize, sourceSize),
            dstOffset = IntOffset((c.x - r).toInt(), (c.y - r).toInt()),
            dstSize = IntSize(diameter, diameter),
            alpha = a
        )
    }

    val rot = time * 0.04

    // terminator shading — gives the sense of a lit sphere, not a flat disc
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = a * 0.45f)),
            center = Offset(c.x - r * 0.4f, c.y - r * 0.4f),
            radius = r * 1.4f
        ),
        radius = r,
        center = c
    )
    drawCircle(Color(0xFF4CC9F0).copy(alpha = a * 0.5f), radius = r * 1.05f, center = c, style = Stroke(width = 2f))

    if (cityLights) {
        val lightCount = 16
        for (i in 0 until lightCount) {
            val angle = (i / lightCount.toFloat()) * 2 * Math.PI + rot * 0.5
            val rad = r * 0.9f
            val pos = Offset(c.x + (cos(angle) * rad).toFloat(), c.y + (sin(angle) * rad).toFloat())
            val twinkle = (sin(time * 3f + i) * 0.5f + 0.5f)
            drawCircle(
                Color(0xFFFFE8B0).copy(alpha = a * (0.3f + twinkle * 0.5f)),
                radius = short * 0.0025f,
                center = pos
            )
        }
    }

    // Orbiting upper-atmosphere satellite / stardust particles
    for (i in 0..6) {
        val angle = i * 1.047 + time * 0.35
        val dist = r * (1.25f + 0.1f * sin(time * 2f + i))
        val satPos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        drawCircle(Color(0xFF4CC9F0).copy(alpha = a * 0.75f), radius = short * 0.002f, center = satPos)
    }
}

private fun DrawScope.drawMoon(c: Offset, short: Float, a: Float, moon: ImageBitmap, time: Float) {
    val r = short * 0.22f

    // Multi-color cosmic mineral halo behind the moon (cyan, gold, silver-blue)
    glow(c, r * 2.8f, Color(0xFF4CC9F0), a * 0.2f)
    glow(c, r * 2.0f, Color(0xFFFFD166), a * 0.15f)
    glow(c, r * 1.5f, Color(0xFFB8C4D8), a * 0.3f)

    // Draw the moon image
    drawCircularImage(moon, c, r, a)

    // Mineral astrophotography color tint overlay (subtle cyan/gold mineral hues)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF4CC9F0).copy(alpha = a * 0.12f), Color(0xFFFFD166).copy(alpha = a * 0.08f), Color.Transparent),
            center = Offset(c.x - r * 0.2f, c.y - r * 0.2f),
            radius = r * 1.2f
        ),
        radius = r,
        center = c
    )

    // Terminator shading — 3D sphere lighting
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = a * 0.5f)),
            center = Offset(c.x - r * 0.45f, c.y - r * 0.45f),
            radius = r * 1.3f
        ),
        radius = r,
        center = c
    )

    // Glowing atmospheric rim highlight
    drawCircle(Color(0xFFFFEE93).copy(alpha = a * 0.3f), radius = r * 1.02f, center = c, style = Stroke(width = 2f))

    // Twinkling stardust particles orbiting the moon
    val starCount = 8
    for (i in 0 until starCount) {
        val angle = i * (2.0 * Math.PI / starCount) + time * 0.2
        val dist = r * (1.25f + 0.15f * sin(time * 2f + i))
        val starPos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        val twinkle = 0.5f + 0.5f * sin(time * 4f + i)
        val starColor = if (i % 2 == 0) Color(0xFF4CC9F0) else Color(0xFFFFD166)
        drawCircle(starColor.copy(alpha = a * 0.8f * twinkle), radius = short * 0.003f, center = starPos)
    }
}

private fun DrawScope.drawSun(c: Offset, short: Float, a: Float, time: Float) {
    val r = short * 0.16f
    val pulse = 1f + 0.05f * sin(time * 3f)
    
    // Terrifying and majestic multi-tier solar corona & plasma glow
    glow(c, r * 4.2f * pulse, Color(0xFFD90429), a * 0.4f)
    glow(c, r * 3.2f * pulse, Color(0xFFFF6B00), a * 0.6f)
    glow(c, r * 2.2f * pulse, Color(0xFFFFB703), a * 0.8f)

    // Erupting Coronal Loops / Magnetic Arcs (curving plasma prominences)
    val loopCount = 6
    for (i in 0 until loopCount) {
        val baseAngle = i * (2.0 * Math.PI / loopCount) + time * 0.05
        val arcPath = Path().apply {
            val startAngle = baseAngle - 0.2
            val endAngle = baseAngle + 0.2
            val p1 = Offset(c.x + (cos(startAngle) * r).toFloat(), c.y + (sin(startAngle) * r).toFloat())
            val p3 = Offset(c.x + (cos(endAngle) * r).toFloat(), c.y + (sin(endAngle) * r).toFloat())
            val controlAngle = baseAngle
            val peakR = r * (1.35f + 0.25f * sin(time * 2f + i))
            val p2 = Offset(c.x + (cos(controlAngle) * peakR).toFloat(), c.y + (sin(controlAngle) * peakR).toFloat())
            moveTo(p1.x, p1.y)
            quadraticBezierTo(p2.x, p2.y, p3.x, p3.y)
        }
        drawPath(
            path = arcPath,
            color = Color(0xFFFF4500).copy(alpha = a * 0.7f),
            style = Stroke(width = 4f)
        )
    }

    // Solar Wind Particles / Coronal Mass Ejection streams radiating outward
    val rayCount = 20
    for (i in 0 until rayCount) {
        val angle = (i / rayCount.toFloat()) * 2 * Math.PI + time * 0.08
        val innerR = r * 1.02f
        val outerR = r * (1.2f + 0.3f * ((i * 37) % 10) / 10f * (0.5f + 0.5f * sin(time * 4f + i)))
        val p1 = Offset(c.x + (cos(angle) * innerR).toFloat(), c.y + (sin(angle) * innerR).toFloat())
        val p2 = Offset(c.x + (cos(angle) * outerR).toFloat(), c.y + (sin(angle) * outerR).toFloat())
        drawLine(Color(0xFFFFD166).copy(alpha = a * 0.5f), p1, p2, strokeWidth = 2.5f)
    }

    // Sun Core Disc with blazing plasma gradient
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFF3), Color(0xFFFFEE93), Color(0xFFFFB703), Color(0xFFFF4500), Color(0xFFD90429)),
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )

    // Blazing Rim Atmosphere
    drawCircle(Color(0xFFFFEE93).copy(alpha = a * 0.8f), radius = r * 1.02f, center = c, style = Stroke(width = 2.5f))
}

private fun DrawScope.drawSolarSystem(c: Offset, short: Float, a: Float, time: Float) {
    // Outer heliosphere boundary glow matching infographic style
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1E1B4B).copy(alpha = a * 0.7f), Color.Transparent),
            center = c,
            radius = short * 0.5f
        ),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f),
        size = Size(short * 1.0f, short * 0.8f)
    )

    // AU Distance rings (1 AU, 5 AU, 10 AU, 20 AU, 30 AU) with distance labels
    val ringRadii = listOf(0.10f, 0.20f, 0.30f, 0.40f, 0.48f)
    val ringLabels = listOf("1 AU", "5 AU", "10 AU", "20 AU", "30 AU (Kuiper)")
    ringRadii.forEachIndexed { index, rFrac ->
        val r = short * rFrac
        drawCircle(
            color = Color(0xFFFFB703).copy(alpha = a * (0.15f + index * 0.03f + 0.02f * sin(time + index))),
            radius = r,
            center = c,
            style = Stroke(width = 1.2f)
        )
        // Ring label
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.argb((a * 150).toInt(), 255, 200, 100)
                textSize = 16f
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(ringLabels[index], c.x + r + 8f, c.y - 4f, paint)
        }
    }

    // Sun at center
    glow(c, short * 0.15f, Color(0xFFFFB703), a * 0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFEE93), Color(0xFFFFB703), Color(0xFFD90429)),
            center = c,
            radius = short * 0.045f
        ),
        radius = short * 0.045f,
        center = c
    )

    // Planets & Probes with 3D drop-line vertical stems & labels
    val bodies = listOf(
        Triple(0.10f, Color(0xFF94A3B8), "Mercury"),
        Triple(0.15f, Color(0xFFFDE047), "Venus"),
        Triple(0.20f, Color(0xFF38BDF8), "Earth"),
        Triple(0.25f, Color(0xFFF87171), "Mars"),
        Triple(0.32f, Color(0xFFFBBF24), "Jupiter"),
        Triple(0.39f, Color(0xFFFDE68A), "Saturn"),
        Triple(0.44f, Color(0xFF67E8F9), "Uranus"),
        Triple(0.48f, Color(0xFF60A5FA), "Neptune"),
        Triple(0.51f, Color(0xFFFFD166), "Voyager 1 (~162 AU)"),
        Triple(0.46f, Color(0xFF4CC9F0), "Voyager 2 (~136 AU)")
    )

    bodies.forEachIndexed { i, (frac, col, name) ->
        val r = short * frac
        val angle = i * 0.75 + time * (0.35f - i * 0.02f)
        val pos = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())

        val basePos = Offset(pos.x, pos.y + short * 0.07f)
        drawLine(col.copy(alpha = a * 0.35f), pos, basePos, strokeWidth = 1f)

        // Add blinking effect for Voyager probes (indices 8 and 9)
        val isVoyager = i >= 8
        val blinkAlpha = if (isVoyager) {
            a * (0.3f + 0.7f * (sin(time * 8f + i) * 0.5f + 0.5f)) // Fast blink
        } else {
            a
        }

        glow(pos, 18f, col, if (isVoyager) blinkAlpha * 0.8f else a * 0.6f)
        drawCircle(Color.White.copy(alpha = blinkAlpha), radius = 4f, center = pos)
        drawCircle(col.copy(alpha = if (isVoyager) blinkAlpha else a * 0.85f), radius = 8f, center = pos, style = Stroke(width = 1.2f))

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.argb((a * 230).toInt(), 230, 240, 255)
                textSize = 18f
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(name, pos.x + 12f, pos.y - 4f, paint)
        }
    }
}

private fun DrawScope.drawGalaxy(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    glow(c, short * 0.25f, Color(0xFFFFE8A3), a * 0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White, Color(0xFFFFD166), Color(0xFF7209B7), Color.Transparent),
            center = c,
            radius = short * 0.12f
        ),
        radius = short * 0.12f,
        center = c
    )

    val armR = short * 0.48f
    val rotation = time * 0.04f
    points.forEachIndexed { i, p ->
        val arm = i % 4
        val t = p.x
        val angle = t * 7.0 + arm * (2.0 * Math.PI / 4.0) + rotation
        val rad = armR * t
        val spread = (p.y - 0.5f) * short * 0.04f
        val pos = Offset(
            c.x + (cos(angle) * rad).toFloat() + spread,
            c.y + (sin(angle) * rad).toFloat() + spread
        )
        val starColor = when (i % 5) {
            0 -> Color(0xFF4CC9F0)
            1 -> Color(0xFFF72585)
            else -> Color.White
        }
        val brightness = a * (0.2f + 0.8f * (1 - t))
        drawCircle(starColor.copy(alpha = brightness), radius = short * (0.0015f + t * 0.002f), center = pos)
    }

    // --- Specifically point to the Sun / Solar System in the Milky Way ---
    val sunAngle = 0.35 * 7.0 + 0.0 + rotation
    val sunRad = armR * 0.55f
    val sunPos = Offset(c.x + (cos(sunAngle) * sunRad).toFloat(), c.y + (sin(sunAngle) * sunRad).toFloat())

    glow(sunPos, 35f, Color(0xFFFFD166), a * 0.8f)
    drawCircle(Color.White.copy(alpha = a), radius = 6f, center = sunPos)
    val pulse = (sin(time * 6f) * 0.5f + 0.5f)
    drawCircle(Color(0xFFFFD166).copy(alpha = a * (0.5f + pulse * 0.5f)), radius = 16f * (0.6f + pulse * 0.4f), center = sunPos, style = Stroke(width = 1.5f))

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = android.graphics.Color.argb((a * 255).toInt(), 255, 209, 102)
            textSize = 28f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.nativeCanvas.drawText("📍 The Sun (You are here)", sunPos.x + 22f, sunPos.y + 8f, paint)
    }
}

private fun DrawScope.drawLocalGroup(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    // Outer dark cosmic boundary oval / halo matching the reference diagram
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0A1128).copy(alpha = a * 0.7f), Color.Transparent),
            center = c,
            radius = short * 0.5f
        ),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f),
        size = Size(short * 1.0f, short * 0.8f)
    )

    // Concentric distance shells / rings (2M ly, 4M ly, 6M ly, 8M ly) with distance labels
    val ringRadii = listOf(0.12f, 0.24f, 0.36f, 0.47f)
    val ringLabels = listOf("2 million ly", "4 million ly", "6 million ly", "8 million ly")
    ringRadii.forEachIndexed { index, rFrac ->
        val r = short * rFrac
        drawOval(
            color = Color(0xFF4CC9F0).copy(alpha = a * (0.15f + index * 0.04f + 0.02f * sin(time + index))),
            topLeft = Offset(c.x - r, c.y - r * 0.55f),
            size = Size(r * 2f, r * 0.7f),
            style = Stroke(width = 1.2f)
        )
        // Ring label
        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.argb((a * 150).toInt(), 120, 180, 220)
                textSize = 16f
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(ringLabels[index], c.x + r - 50f, c.y - r * 0.25f + index * 12f, paint)
        }
    }

    // Milky Way & Andromeda sub-clusters with 3D drop-line vertical stems
    val mwPos = c + Offset(0f, short * 0.04f)
    val m31Pos = c + Offset(short * 0.16f, -short * 0.14f)
    val m33Pos = c + Offset(short * 0.23f, -short * 0.06f)

    val galaxies = listOf(
        Triple(mwPos, Color(0xFF4CC9F0), "Milky Way"),
        Triple(m31Pos, Color(0xFFFFD166), "M31 (Andromeda)"),
        Triple(m33Pos, Color(0xFFF72585), "M33 (Triangulum)"),
        Triple(mwPos + Offset(-short * 0.12f, short * 0.08f), Color(0xFF4CC9F0), "LMC / SMC"),
        Triple(c + Offset(-short * 0.26f, short * 0.12f), Color(0xFF67E8F9), "NGC 300"),
        Triple(c + Offset(short * 0.29f, short * 0.16f), Color(0xFF60A5FA), "IC 1613"),
        Triple(c + Offset(-0.08f * short, -0.22f * short), Color(0xFFF72585), "IC 10"),
        Triple(c + Offset(0.08f * short, 0.25f * short), Color(0xFFFFD166), "NGC 3109"),
        Triple(c + Offset(-0.32f * short, -0.15f * short), Color(0xFF4CC9F0), "WLM")
    )

    galaxies.forEach { (pos, col, name) ->
        val basePos = Offset(pos.x, pos.y + short * 0.09f)
        drawLine(Color(0xFF4CC9F0).copy(alpha = a * 0.3f), pos, basePos, strokeWidth = 1f)
        
        glow(pos, 18f, col, a * 0.6f)
        drawCircle(Color.White.copy(alpha = a), radius = 4f, center = pos)
        drawCircle(col.copy(alpha = a * 0.85f), radius = 8f, center = pos, style = Stroke(width = 1.2f))

        drawIntoCanvas { canvas ->
            val paint = Paint().apply {
                color = android.graphics.Color.argb((a * 230).toInt(), 220, 235, 255)
                textSize = 20f
                isAntiAlias = true
            }
            canvas.nativeCanvas.drawText(name, pos.x + 12f, pos.y - 4f, paint)
        }
    }

    // Use points to scatter background dwarf galaxies faintly
    points.drop(10).take(20).forEach { p ->
        val bgPos = Offset(c.x + (p.x - 0.5f) * short * 0.9f, c.y + (p.y - 0.5f) * short * 0.7f)
        drawCircle(Color.White.copy(alpha = a * 0.2f), radius = 1.5f, center = bgPos)
    }
}

private fun DrawScope.drawCosmicWeb(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val nodes = points.take(24).map {
        Offset(c.x + (it.x - 0.5f) * short * 0.95f, c.y + (it.y - 0.5f) * short * 0.95f)
    }
    val breath = 0.8f + 0.2f * sin(time * 0.6f)

    nodes.forEachIndexed { i, n ->
        val next = nodes[(i + 1) % nodes.size]
        val skip = nodes[(i + 3) % nodes.size]
        val opposite = nodes[(i + 7) % nodes.size]

        drawLine(Color(0xFF4361EE).copy(alpha = a * 0.25f * breath), n, next, strokeWidth = 3f)
        drawLine(Color(0xFF7209B7).copy(alpha = a * 0.2f * breath), n, skip, strokeWidth = 2f)
        drawLine(Color(0xFF4CC9F0).copy(alpha = a * 0.15f * breath), n, opposite, strokeWidth = 1.5f)

        for (step in 1..4) {
            val f = step / 5f
            val intermediate = Offset(n.x + (next.x - n.x) * f, n.y + (next.y - n.y) * f)
            drawCircle(Color.White.copy(alpha = a * 0.5f), radius = short * 0.002f, center = intermediate)
        }
    }

    nodes.forEachIndexed { i, n ->
        val clusterColor = when (i % 3) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            else -> Color(0xFFF72585)
        }
        glow(n, short * 0.05f, clusterColor, a * 0.4f * breath)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, clusterColor, Color.Transparent),
                center = n,
                radius = short * 0.02f
            ),
            radius = short * 0.02f,
            center = n
        )
    }
}

private fun DrawScope.drawObservableUniverse(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF4361EE).copy(alpha = a * 0.15f), Color(0xFFF72585).copy(alpha = a * 0.25f)),
            center = c,
            radius = boundary
        ),
        radius = boundary,
        center = c
    )
    drawCircle(
        Color(0xFFFFD166).copy(alpha = a * 0.3f),
        radius = boundary,
        center = c,
        style = Stroke(width = 2f)
    )

    points.forEachIndexed { i, p ->
        // Use a much tighter spread so that ALL points fall *inside* the circle boundary.
        // We use polar coordinates to ensure an even distribution within the circular bound.
        val r = boundary * (p.x * 0.95f) // slightly inset from the true edge
        val angle = p.y * 2 * Math.PI
        val pos = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())
        
        val hueSelector = (p.x * 10).toInt() % 4
        val galaxyColor = when (hueSelector) {
            0 -> Color(0xFF4CC9F0)
            1 -> Color(0xFFF72585)
            2 -> Color(0xFFFFD166)
            else -> Color.White
        }
        val twinkle = 0.5f + 0.5f * sin(time * 1.5f + i * 0.8f)
        val radius = short * (0.002f + (i % 3) * 0.0015f)
        drawCircle(galaxyColor.copy(alpha = a * (0.4f + 0.5f * p.y) * twinkle), radius = radius, center = pos)
    }
}
