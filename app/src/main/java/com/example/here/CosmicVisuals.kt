package com.example.here

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.res.imageResource
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

/**
 * Draws the current cosmic scale as an abstract scene with soft glow, gentle idle
 * motion (rotation/orbit/twinkle), and cross-fades into the next scale as `fraction`
 * (0f..1f) advances. `time` is continuous seconds, used purely for idle animation so
 * nothing on screen ever looks like a frozen diagram.
 */
@Composable
fun CosmicVisual(
    level: Int,
    fraction: Float,
    time: Float,
    showCityLights: Boolean = false,
    modifier: Modifier = Modifier
) {
    val current = COSMIC_LEVELS[level.coerceIn(0, COSMIC_LEVELS.size - 1)]
    val nextIndex = (level + 1).coerceAtMost(COSMIC_LEVELS.size - 1)
    val next = COSMIC_LEVELS[nextIndex]

    val field = remember { Random(42) }
    val points = remember { List(400) { Offset(field.nextFloat(), field.nextFloat()) } }
    val youImage = ImageBitmap.imageResource(R.drawable.you_person)
    val surroundingsImage = ImageBitmap.imageResource(R.drawable.surroundings_landscape)
    val earth = ImageBitmap.imageResource(R.drawable.earth_apollo)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawScene(current.index, 1f - fraction, points, time, showCityLights, youImage, surroundingsImage, earth)
        if (current.index != next.index) {
            drawScene(next.index, fraction, points, time, showCityLights, youImage, surroundingsImage, earth)
        }
    }
}

private fun DrawScope.drawScene(
    levelIndex: Int,
    alpha: Float,
    points: List<Offset>,
    time: Float,
    showCityLights: Boolean,
    youImage: ImageBitmap,
    surroundingsImage: ImageBitmap,
    earth: ImageBitmap
) {
    if (alpha <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val short = min(size.width, size.height)

    when (levelIndex) {
        0 -> drawYou(c, short, alpha, youImage)
        1 -> drawSurroundings(c, short, alpha, points, time, surroundingsImage)
        2 -> drawEarth(c, short, alpha, time, showCityLights, earth)
        3 -> drawMoon(c, short, alpha, time)
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
    if (alpha <= 0f || radius <= 0f) return
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

private fun DrawScope.drawYou(c: Offset, short: Float, a: Float, image: ImageBitmap) {
    val r = short * 0.09f
    glow(c, r * 2.2f, Color(0xFFB9D8FF), a * 0.2f)
    drawCircularImage(image, c, r, a)
    drawCircle(Color.White.copy(alpha = a * 0.25f), radius = r * 1.04f, center = c, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawSurroundings(
    c: Offset,
    short: Float,
    a: Float,
    points: List<Offset>,
    time: Float,
    image: ImageBitmap
) {
    val col = Color.White.copy(alpha = a * 0.8f)
    val imageRadius = short * 0.18f
    glow(c, imageRadius * 1.8f, Color(0xFF5D9CFF), a * 0.22f)
    drawCircularImage(image, c, imageRadius, a)
    drawCircle(Color.White.copy(alpha = a * 0.25f), radius = imageRadius * 1.04f, center = c, style = Stroke(width = 1.5f))
    val ringR = short * 0.32f
    drawCircle(col.copy(alpha = a * 0.25f), radius = ringR, center = c, style = Stroke(width = 1.5f))
    points.take(24).forEachIndexed { i, p ->
        val angle = (i / 24f) * 2 * Math.PI + time * 0.03
        val rad = ringR * (0.5f + p.x * 0.5f)
        val pos = Offset(c.x + (cos(angle) * rad).toFloat(), c.y + (sin(angle) * rad).toFloat())
        drawCircle(col.copy(alpha = a * 0.5f), radius = short * 0.006f, center = pos)
    }
}

private fun DrawScope.drawCircularImage(image: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    val diameter = (radius * 2f).toInt()
    val sourceSize = minOf(image.width, image.height)
    val sourceOffset = androidx.compose.ui.unit.IntOffset(
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
            srcSize = androidx.compose.ui.unit.IntSize(sourceSize, sourceSize),
            dstOffset = androidx.compose.ui.unit.IntOffset((center.x - radius).toInt(), (center.y - radius).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(diameter, diameter),
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
    glow(c, r * 1.9f, Color(0xFF3A7DFF), a * 0.35f)
    val diameter = (r * 2f).toInt()
    val sourceSize = minOf(earth.width, earth.height)
    val sourceOffset = androidx.compose.ui.unit.IntOffset(
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
            srcSize = androidx.compose.ui.unit.IntSize(sourceSize, sourceSize),
            dstOffset = androidx.compose.ui.unit.IntOffset((c.x - r).toInt(), (c.y - r).toInt()),
            dstSize = androidx.compose.ui.unit.IntSize(diameter, diameter),
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
    drawCircle(Color.White.copy(alpha = a * 0.15f), radius = r * 1.05f, center = c, style = Stroke(width = 1.5f))

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

private fun DrawScope.drawSolarSystem(c: Offset, short: Float, a: Float, time: Float) {
    glow(c, short * 0.09f, Color(0xFFFFC857), a * 0.6f)
    drawCircle(Color(0xFFFFC857).copy(alpha = a), radius = short * 0.035f, center = c)
    val radii = listOf(0.09f, 0.14f, 0.19f, 0.25f, 0.32f, 0.39f, 0.44f, 0.48f)
    radii.forEachIndexed { i, frac ->
        val r = short * frac
        drawCircle(Color.White.copy(alpha = a * 0.15f), radius = r, center = c, style = Stroke(width = 1f))
        // planets visibly orbiting, each at its own speed
        val angle = i * 47.0 + time * (6.0 - i * 0.5)
        val pos = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())
        drawCircle(Color.White.copy(alpha = a * 0.85f), radius = short * 0.006f, center = pos)
    }
}

private fun DrawScope.drawMoon(c: Offset, short: Float, a: Float, time: Float) {
    val r = short * 0.18f
    glow(c, r * 1.7f, Color(0xFFB8C4D8), a * 0.18f)
    drawCircle(Color(0xFF9DA8B8).copy(alpha = a), radius = r, center = c)
    val craters = listOf(
        Offset(-0.28f, -0.2f) to 0.12f,
        Offset(0.2f, -0.3f) to 0.08f,
        Offset(0.3f, 0.18f) to 0.14f,
        Offset(-0.12f, 0.28f) to 0.07f
    )
    craters.forEach { (offset, sizeFraction) ->
        drawCircle(
            Color(0xFF687384).copy(alpha = a * 0.65f),
            radius = r * sizeFraction,
            center = Offset(c.x + offset.x * r, c.y + offset.y * r)
        )
    }
    drawCircle(Color.White.copy(alpha = a * 0.16f), radius = r * 1.04f, center = c, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawSun(c: Offset, short: Float, a: Float, time: Float) {
    val r = short * 0.12f
    val pulse = 1f + sin(time * 0.7f) * 0.025f
    glow(c, r * 2.8f, Color(0xFFFFA62B), a * 0.42f)
    drawCircle(Color(0xFFFFC857).copy(alpha = a), radius = r * pulse, center = c)
    drawCircle(Color(0xFFFFE29A).copy(alpha = a * 0.7f), radius = r * 0.72f, center = c)
}

private fun DrawScope.drawGalaxy(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    glow(c, short * 0.14f, Color(0xFFFFF3D6), a * 0.5f)
    drawCircle(Color(0xFFFFF3D6).copy(alpha = a * 0.9f), radius = short * 0.03f, center = c)
    val armR = short * 0.46f
    val drift = time * 0.015
    points.forEachIndexed { i, p ->
        val arm = i % 3
        val t = p.x
        val angle = t * 6.2 + arm * (2 * Math.PI / 3) + drift
        val rad = armR * t
        val spread = (p.y - 0.5f) * short * 0.03f
        val pos = Offset(
            c.x + (cos(angle) * rad).toFloat() + spread,
            c.y + (sin(angle) * rad).toFloat() + spread
        )
        drawCircle(Color.White.copy(alpha = a * (0.15f + 0.5f * (1 - t))), radius = short * 0.0025f, center = pos)
    }
}

private fun DrawScope.drawLocalGroup(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val blobs = listOf(
        Offset(0f, 0f) to short * 0.05f,
        Offset(0.22f, 0.1f) to short * 0.03f,
        Offset(-0.28f, -0.08f) to short * 0.025f,
        Offset(0.05f, -0.3f) to short * 0.018f,
        Offset(-0.15f, 0.27f) to short * 0.015f
    )
    blobs.forEachIndexed { i, (offsetFrac, r) ->
        val pos = c + Offset(offsetFrac.x * short, offsetFrac.y * short)
        val twinkle = 0.75f + 0.25f * sin(time * 0.8f + i * 1.7f)
        glow(pos, r * 2.4f, Color.White, a * 0.18f * twinkle)
        drawCircle(Color.White.copy(alpha = a * 0.85f * twinkle), radius = r, center = pos)
    }
    points.drop(24).take(60).forEach { p ->
        val pos = Offset(c.x + (p.x - 0.5f) * short * 0.9f, c.y + (p.y - 0.5f) * short * 0.9f)
        drawCircle(Color.White.copy(alpha = a * 0.2f), radius = short * 0.003f, center = pos)
    }
}

private fun DrawScope.drawCosmicWeb(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val nodes = points.take(18).map {
        Offset(c.x + (it.x - 0.5f) * short * 0.95f, c.y + (it.y - 0.5f) * short * 0.95f)
    }
    val pulse = 0.7f + 0.3f * sin(time * 0.6f)
    nodes.forEachIndexed { i, n ->
        val next = nodes[(i + 1) % nodes.size]
        drawLine(Color.White.copy(alpha = a * 0.18f * pulse), n, next, strokeWidth = 1f)
        if (i % 2 == 0) {
            val skip = nodes[(i + 3) % nodes.size]
            drawLine(Color.White.copy(alpha = a * 0.1f * pulse), n, skip, strokeWidth = 1f)
        }
    }
    nodes.forEach { n ->
        glow(n, short * 0.02f, Color.White, a * 0.15f)
        drawCircle(Color.White.copy(alpha = a * 0.7f), radius = short * 0.008f, center = n)
    }
}

private fun DrawScope.drawObservableUniverse(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val boundary = short * 0.48f
    drawCircle(
        Color.White.copy(alpha = a * 0.08f),
        radius = boundary,
        center = c,
        style = Stroke(width = 1.5f)
    )
    points.forEachIndexed { i, p ->
        val pos = Offset(c.x + (p.x - 0.5f) * short * 0.95f, c.y + (p.y - 0.5f) * short * 0.95f)
        val dot = (p.x * 3).toInt() % 3
        val radius = short * (0.002f + dot * 0.0012f)
        val twinkle = 0.6f + 0.4f * sin(time * 1.3f + i * 0.7f)
        drawCircle(Color.White.copy(alpha = a * (0.3f + 0.4f * p.y) * twinkle), radius = radius, center = pos)
    }
}
