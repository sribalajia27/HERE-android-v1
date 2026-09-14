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
) {
    val current = COSMIC_LEVELS[level.coerceIn(0, COSMIC_LEVELS.size - 1)]
    val nextIndex = (level + 1).coerceAtMost(COSMIC_LEVELS.size - 1)
    val next = COSMIC_LEVELS[nextIndex]

    val field = remember { Random(42) }
    val points = remember { List(400) { Offset(field.nextFloat(), field.nextFloat()) } }
    val surroundingsImage = ImageBitmap.imageResource(R.drawable.surroundings_landscape)
    val earth = ImageBitmap.imageResource(R.drawable.earth_apollo)
    val moon = ImageBitmap.imageResource(R.drawable.moon)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawScene(current.index, 1f - fraction, points, time, showCityLights, surroundingsImage, earth, moon)
        if (current.index != next.index) {
            drawScene(next.index, fraction, points, time, showCityLights, surroundingsImage, earth, moon)
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
    moon: ImageBitmap
) {
    if (alpha <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val short = min(size.width, size.height)

    when (levelIndex) {
        0 -> drawYou(c, short, alpha, time)
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

private fun DrawScope.drawYou(c: Offset, short: Float, a: Float, time: Float) {
    val r = short * 0.09f
    // Vibrant multi-color stardust aura around You
    glow(c, r * 3.0f, Color(0xFF4CC9F0), a * 0.35f)
    glow(c, r * 2.0f, Color(0xFFFFD166), a * 0.3f)
    glow(c, r * 1.5f, Color(0xFFF72585), a * 0.25f)

    val headRadius = r * 0.28f
    drawCircle(Color.White.copy(alpha = a * 0.95f), radius = headRadius, center = Offset(c.x, c.y - r * 0.58f))
    drawOval(
        color = Color.White.copy(alpha = a * 0.95f),
        topLeft = Offset(c.x - r * 0.48f, c.y - r * 0.22f),
        size = Size(r * 0.96f, r * 1.2f)
    )
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
    // Vibrant sunset/aurora multi-color glow
    glow(c, imageRadius * 2.4f, Color(0xFF4361EE), a * 0.3f)
    glow(c, imageRadius * 1.8f, Color(0xFFF72585), a * 0.25f)
    glow(c, imageRadius * 1.3f, Color(0xFFFFD166), a * 0.2f)
    drawCircularImage(image, c, imageRadius, a)
    drawCircle(Color(0xFFFFEE93).copy(alpha = a * 0.5f), radius = imageRadius * 1.04f, center = c, style = Stroke(width = 2f))
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
    // Vivid neon blue and auroral cyan atmosphere glow
    glow(c, r * 2.5f, Color(0xFF4CC9F0), a * 0.5f)
    glow(c, r * 1.9f, Color(0xFF3A7DFF), a * 0.45f)
    glow(c, r * 1.3f, Color(0xFF7209B7), a * 0.3f)

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
    drawCircle(Color(0xFF4CC9F0).copy(alpha = a * 0.4f), radius = r * 1.05f, center = c, style = Stroke(width = 2f))

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
    val r = short * 0.15f
    glow(c, r * 3.5f, Color(0xFFFF8800), a * 0.5f)
    glow(c, r * 2.0f, Color(0xFFFFB703), a * 0.7f)

    // Animated solar flares / rays
    val rayCount = 12
    for (i in 0 until rayCount) {
        val angle = (i / rayCount.toFloat()) * 2 * Math.PI + time * 0.1
        val innerR = r * 1.05f
        val outerR = r * (1.3f + 0.15f * sin(time * 2f + i))
        val p1 = Offset(c.x + (cos(angle) * innerR).toFloat(), c.y + (sin(angle) * outerR).toFloat())
        val p2 = Offset(c.x + (cos(angle) * outerR).toFloat(), c.y + (sin(angle) * outerR).toFloat())
        drawLine(Color(0xFFFFD166).copy(alpha = a * 0.4f), p1, p2, strokeWidth = 3f)
    }

    // Sun disc
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFF3B0), Color(0xFFFFB703), Color(0xFFD90429)),
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )

    // Granulation spots
    val spotCount = 8
    for (i in 0 until spotCount) {
        val angle = i * 0.8f + time * 0.05f
        val dist = r * 0.5f * spotPosDistanceFactor(i)
        val spotPos = Offset(c.x + cos(angle.toDouble()).toFloat() * dist, c.y + sin(angle.toDouble()).toFloat() * spotPosDistanceFactor(i))
        drawCircle(Color(0xFFFF7B00).copy(alpha = a * 0.3f), radius = r * 0.15f, center = spotPos)
    }

    drawCircle(Color(0xFFFFEE93).copy(alpha = a * 0.6f), radius = r * 1.02f, center = c, style = Stroke(width = 2f))
}

private fun spotPosDistanceFactor(i: Int): Float {
    return sin(i.toFloat()).let { if (it < 0f) -it else it }
}

private data class Quad<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)

private fun DrawScope.drawSolarSystem(c: Offset, short: Float, a: Float, time: Float) {
    glow(c, short * 0.15f, Color(0xFFFFB703), a * 0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFEE93), Color(0xFFFFB703), Color(0xFFD90429)),
            center = c,
            radius = short * 0.05f
        ),
        radius = short * 0.05f,
        center = c
    )

    val planets = listOf(
        Quad(0.08f, Color(0xFF94A3B8), 0.005f, 6.0f),  // Mercury
        Quad(0.14f, Color(0xFFFDE047), 0.007f, 4.8f),  // Venus
        Quad(0.21f, Color(0xFF38BDF8), 0.009f, 3.6f),  // Earth
        Quad(0.28f, Color(0xFFF87171), 0.006f, 2.8f),  // Mars
        Quad(0.37f, Color(0xFFFBBF24), 0.015f, 2.0f),  // Jupiter
        Quad(0.46f, Color(0xFFFDE68A), 0.012f, 1.5f),  // Saturn
        Quad(0.52f, Color(0xFF67E8F9), 0.010f, 1.1f),  // Uranus
        Quad(0.57f, Color(0xFF60A5FA), 0.010f, 0.8f)   // Neptune
    )

    planets.forEachIndexed { i, (frac, color, planetSize, speed) ->
        val r = short * frac
        drawCircle(Color.White.copy(alpha = a * 0.18f), radius = r, center = c, style = Stroke(width = 1.2f))

        val angle = i * 1.57 + time * speed * 1.2
        val pos = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())

        // Motion trailing arc
        for (trailStep in 1..6) {
            val pastAngle = angle - trailStep * 0.12
            val pastPos = Offset(c.x + (cos(pastAngle) * r).toFloat(), c.y + (sin(pastAngle) * r).toFloat())
            drawCircle(color.copy(alpha = a * (0.4f / trailStep)), radius = planetSize * (1f - trailStep * 0.1f), center = pastPos)
        }

        when (i) {
            2 -> {
                glow(pos, planetSize * 3.5f, Color(0xFF38BDF8), a * 0.6f)
                drawCircle(Color(0xFF1E40AF).copy(alpha = a), radius = planetSize * 1.3f, center = pos)
                drawCircle(Color(0xFF38BDF8).copy(alpha = a), radius = planetSize, center = pos)
                val moonAngle = time * 10f
                val moonPos = Offset(pos.x + cos(moonAngle.toDouble()).toFloat() * planetSize * 2.8f, pos.y + sin(moonAngle.toDouble()).toFloat() * planetSize * 2.8f)
                drawCircle(Color.White.copy(alpha = a * 0.9f), radius = planetSize * 0.35f, center = moonPos)
            }
            5 -> {
                drawCircle(Color(0xFFFDE68A).copy(alpha = a * 0.7f), radius = planetSize * 2.4f, center = pos, style = Stroke(width = 2f))
                drawCircle(color.copy(alpha = a), radius = planetSize, center = pos)
            }
            else -> {
                glow(pos, planetSize * 2f, color, a * 0.4f)
                drawCircle(color.copy(alpha = a), radius = planetSize, center = pos)
            }
        }
    }

    // --- Voyager 1 & Voyager 2 fixed in place, actively blinking ---
    val v1Angle = 2.15
    val v1Dist = short * 0.52f
    val v1Pos = Offset(c.x + (cos(v1Angle) * v1Dist).toFloat(), c.y + (sin(v1Angle) * v1Dist).toFloat())

    val v2Angle = 3.45
    val v2Dist = short * 0.48f
    val v2Pos = Offset(c.x + (cos(v2Angle) * v2Dist).toFloat(), c.y + (sin(v2Angle) * v2Dist).toFloat())

    drawVoyagerPath(c, v1Angle, short * 0.35f, v1Dist, a)
    drawVoyagerPath(c, v2Angle, short * 0.35f, v2Dist, a)

    drawVoyagerProbe(v1Pos, a, time, "V1", blinking = true)
    drawVoyagerProbe(v2Pos, a, time + 1.5f, "V2", blinking = true)
}

private fun DrawScope.drawVoyagerPath(c: Offset, angle: Double, startR: Float, endR: Float, a: Float) {
    val steps = 15
    for (step in 0 until steps) {
        val f1 = step / steps.toFloat()
        val f2 = (step + 0.6f) / steps.toFloat()
        val r1 = startR + (endR - startR) * f1
        val r2 = startR + (endR - startR) * f2
        val p1 = Offset(c.x + (cos(angle) * r1).toFloat(), c.y + (sin(angle) * r1).toFloat())
        val p2 = Offset(c.x + (cos(angle) * r2).toFloat(), c.y + (sin(angle) * r2).toFloat())
        drawLine(Color(0xFFFFD166).copy(alpha = a * 0.35f), p1, p2, strokeWidth = 1.5f)
    }
}

private fun DrawScope.drawVoyagerProbe(pos: Offset, a: Float, time: Float, label: String, blinking: Boolean) {
    glow(pos, 30f, Color(0xFFFFD166), a * 0.7f)
    drawCircle(Color.White.copy(alpha = a), radius = 5f, center = pos)
    
    val blink = if (blinking) {
        if ((time * 3f).toInt() % 2 == 0) 1f else 0.2f
    } else {
        1f
    }
    
    val pulse = (sin(time * 6f) * 0.5f + 0.5f) * blink
    drawCircle(Color(0xFFFFD166).copy(alpha = a * (0.4f + pulse * 0.6f)), radius = 14f * (0.5f + pulse * 0.5f), center = pos, style = Stroke(width = 1.5f))

    drawIntoCanvas { canvas ->
        val paint = Paint().apply {
            color = android.graphics.Color.argb((a * blink * 255).toInt(), 255, 209, 102)
            textSize = 30f
            isAntiAlias = true
            isFakeBoldText = true
        }
        canvas.nativeCanvas.drawText(label, pos.x + 18f, pos.y + 8f, paint)
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
    val galaxies = listOf(
        Triple(Offset(0f, 0f), short * 0.12f, Color(0xFF4CC9F0)),
        Triple(Offset(0.25f, -0.12f), short * 0.15f, Color(0xFFFFE8A3)),
        Triple(Offset(-0.28f, 0.15f), short * 0.08f, Color(0xFFF72585)),
        Triple(Offset(0.15f, 0.28f), short * 0.05f, Color(0xFF4361EE)),
        Triple(Offset(-0.20f, -0.25f), short * 0.04f, Color(0xFF4CC9F0))
    )

    galaxies.forEachIndexed { i, (offsetFrac, r, col) ->
        val pos = c + Offset(offsetFrac.x * short, offsetFrac.y * short)
        val pulse = 0.85f + 0.15f * sin(time * 0.8f + i * 1.3f)
        glow(pos, r * 2.5f, col, a * 0.25f * pulse)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White, col.copy(alpha = 0.8f), Color.Transparent),
                center = pos,
                radius = r
            ),
            radius = r,
            center = pos
        )
    }

    points.drop(30).take(90).forEachIndexed { index, p ->
        val pos = Offset(c.x + (p.x - 0.5f) * short * 0.95f, c.y + (p.y - 0.5f) * short * 0.95f)
        val twinkle = 0.5f + 0.5f * sin(time * 1.2f + index)
        drawCircle(Color.White.copy(alpha = a * 0.35f * twinkle), radius = short * 0.0025f, center = pos)
    }
}

private fun DrawScope.drawCosmicWeb(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val nodes = points.take(22).map {
        Offset(c.x + (it.x - 0.5f) * short * 0.95f, c.y + (it.y - 0.5f) * short * 0.95f)
    }
    val breath = 0.75f + 0.25f * sin(time * 0.7f)

    nodes.forEachIndexed { i, n ->
        val next = nodes[(i + 1) % nodes.size]
        val skip = nodes[(i + 4) % nodes.size]
        drawLine(Color(0xFF4CC9F0).copy(alpha = a * 0.22f * breath), n, next, strokeWidth = 2f)
        drawLine(Color(0xFFF72585).copy(alpha = a * 0.15f * breath), n, skip, strokeWidth = 1.5f)
    }

    nodes.forEachIndexed { i, n ->
        val nodeColor = if (i % 2 == 0) Color(0xFFFFD166) else Color(0xFF4CC9F0)
        glow(n, short * 0.035f, nodeColor, a * 0.3f * breath)
        drawCircle(nodeColor.copy(alpha = a * 0.85f), radius = short * 0.01f, center = n)
        drawCircle(Color.White.copy(alpha = a), radius = short * 0.004f, center = n)
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
        val pos = Offset(c.x + (p.x - 0.5f) * short * 0.96f, c.y + (p.y - 0.5f) * short * 0.96f)
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
