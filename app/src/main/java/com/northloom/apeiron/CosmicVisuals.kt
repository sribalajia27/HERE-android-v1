package com.northloom.apeiron

import com.northloom.apeiron.R
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

@Composable
fun CosmicVisual(
    level: Int,
    fraction: Float,
    time: Float,
    modifier: Modifier = Modifier,
    showCityLights: Boolean = false,
    userAvatar: String? = null,
) {
    val safeLevel = level.coerceIn(0, COSMIC_LEVELS.size - 1)
    val current = COSMIC_LEVELS[safeLevel]
    val nextIndex = (safeLevel + 1).coerceAtMost(COSMIC_LEVELS.size - 1)
    val next = COSMIC_LEVELS[nextIndex]

    val field = remember { Random(42) }
    val points = remember { List(1200) { Offset(field.nextFloat(), field.nextFloat()) } }
    val surroundingsImage = ImageBitmap.imageResource(R.drawable.surroundings_landscape)
    val earth = ImageBitmap.imageResource(R.drawable.earth_apollo)
    val moon = ImageBitmap.imageResource(R.drawable.moon)

    Canvas(modifier = modifier.fillMaxSize()) {
        if (current.index == next.index) {
            drawScene(current.index, 1f, points, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
        } else {
            val safeFraction = fraction.coerceIn(0f, 1f)
            val inScale = 1.15f - (0.15f * safeFraction)
            scale(scale = inScale, pivot = center) {
                drawScene(next.index, safeFraction, points, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
            }

            val outScale = 1f - (0.15f * safeFraction)
            scale(scale = outScale, pivot = center) {
                drawScene(current.index, (1f - safeFraction).coerceIn(0f, 1f), points, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
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
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if (safeAlpha <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val short = min(size.width, size.height)

    when (levelIndex) {
        0 -> drawYou(c, short, safeAlpha, time, userAvatar)
        1 -> drawSurroundings(c, short, safeAlpha, surroundingsImage, time)
        2 -> drawEarth(c, short, safeAlpha, time, showCityLights, earth)
        3 -> drawMoon(c, short, safeAlpha, moon, time)
        4 -> drawSun(c, short, safeAlpha, time)
        5 -> drawSolarSystem(c, short, safeAlpha, time)
        6 -> drawGalaxy(c, short, safeAlpha, points, time)
        7 -> drawLocalGroup(c, short, safeAlpha, points, time)
        8 -> drawSupercluster(c, short, safeAlpha, points, time)
        9 -> drawCosmicWeb(c, short, safeAlpha, points, time)
        10 -> drawObservableUniverse(c, short, safeAlpha, points, time)
        11 -> drawBeyondUniverse(c, short, safeAlpha, points, time)
    }
}

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if ((safeAlpha <= 0f) || (radius <= 0f)) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.copy(alpha = safeAlpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

private fun DrawScope.drawYou(c: Offset, short: Float, a: Float, time: Float, userAvatar: String?) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.09f
    glow(c, r * 3.2f, Color(0xFF4CC9F0), safeA * 0.22f)
    glow(c, r * 1.9f, Color(0xFF4CC9F0), safeA * 0.28f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.copy(alpha = (safeA * 0.10f).coerceIn(0f, 1f)), Color.White.copy(alpha = (safeA * 0.02f).coerceIn(0f, 1f))),
            center = c,
            radius = r * 1.15f
        ),
        radius = r * 1.15f,
        center = c
    )
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.White.copy(alpha = (safeA * 0.85f).coerceIn(0f, 1f)),
                Color(0xFF4CC9F0).copy(alpha = (safeA * 0.15f).coerceIn(0f, 1f)),
                Color.Black.copy(alpha = (safeA * 0.05f).coerceIn(0f, 1f)),
                Color(0xFF4CC9F0).copy(alpha = (safeA * 0.15f).coerceIn(0f, 1f)),
                Color.White.copy(alpha = (safeA * 0.85f).coerceIn(0f, 1f))
            ),
            center = c
        ),
        radius = r * 1.15f,
        center = c,
        style = Stroke(width = 1.6f)
    )

    if (userAvatar != null) {
        drawIntoCanvas { canvas ->
            val shadowPaint = Paint().apply {
                textSize = r * 2.15f
                isAntiAlias = true
                alpha = (safeA * 90).toInt().coerceIn(0, 255)
                textAlign = Paint.Align.CENTER
            }
            canvas.nativeCanvas.drawText(userAvatar, c.x, c.y + r * 0.75f + 5f, shadowPaint)

            val paint = Paint().apply {
                textSize = r * 2.15f
                isAntiAlias = true
                alpha = (safeA * 255).toInt().coerceIn(0, 255)
                textAlign = Paint.Align.CENTER
            }
            canvas.nativeCanvas.drawText(userAvatar, c.x, c.y + r * 0.75f, paint)
        }
    } else {
        val figure = Path().apply {
            val headR = r * 0.26f
            val headC = Offset(c.x, c.y - r * 0.5f)
            addOval(Rect(headC.x - headR, headC.y - headR, headC.x + headR, headC.y + headR))
            moveTo(c.x - r * 0.34f, c.y + r * 0.62f)
            cubicTo(
                c.x - r * 0.4f, c.y + r * 0.05f,
                c.x - r * 0.3f, c.y - r * 0.12f,
                c.x, c.y - r * 0.14f
            )
            cubicTo(
                c.x + r * 0.3f, c.y - r * 0.12f,
                c.x + r * 0.4f, c.y + r * 0.05f,
                c.x + r * 0.34f, c.y + r * 0.62f
            )
            close()
        }
        drawPath(
            path = figure,
            brush = Brush.verticalGradient(
                colors = listOf(Color.White.copy(alpha = (safeA * 0.95f).coerceIn(0f, 1f)), Color(0xFF9FB4C7).copy(alpha = (safeA * 0.75f).coerceIn(0f, 1f))),
                startY = c.y - r,
                endY = c.y + r
            )
        )
        drawPath(figure, color = Color(0xFF4CC9F0).copy(alpha = (safeA * 0.35f).coerceIn(0f, 1f)), style = Stroke(width = 1.2f))
    }

    for (i in 0..4) {
        val angle = i * 1.257 + time * 0.3
        val dist = r * 1.5f
        val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        val twinkle = 0.55f + 0.45f * sin(time * 1.5f + i * 1.3f)
        drawCircle(Color(0xFF4CC9F0).copy(alpha = (safeA * 0.6f * twinkle).coerceIn(0f, 1f)), radius = short * 0.0016f, center = pos)
    }
}

private fun DrawScope.drawSurroundings(
    c: Offset,
    short: Float,
    a: Float,
    image: ImageBitmap,
    time: Float
) {
    val safeA = a.coerceIn(0f, 1f)
    val imageRadius = short * 0.18f
    val pulse = 1f + 0.04f * sin(time * 2.5f)
    glow(c, imageRadius * 2.6f * pulse, Color(0xFF4361EE), safeA * 0.35f)
    glow(c, imageRadius * 1.9f * pulse, Color(0xFFF72585), safeA * 0.3f)
    glow(c, imageRadius * 1.4f * pulse, Color(0xFFFFD166), safeA * 0.25f)
    drawCircularImage(image, c, imageRadius, safeA)
    drawCircle(Color(0xFFFFEE93).copy(alpha = (safeA * 0.6f).coerceIn(0f, 1f)), radius = imageRadius * 1.04f, center = c, style = Stroke(width = 2f))

    for (i in 0..7) {
        val angle = i * 0.785 + time * 0.4
        val dist = imageRadius * (1.2f + 0.15f * sin(time * 1.5f + i))
        val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        val twinkle = 0.5f + 0.5f * sin(time * 3f + i)
        drawCircle(Color(0xFFFFD166).copy(alpha = (safeA * 0.7f * twinkle).coerceIn(0f, 1f)), radius = short * 0.0025f, center = pos)
    }
}

private fun DrawScope.drawCircularImage(image: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    val safeA = alpha.coerceIn(0f, 1f)
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
            alpha = safeA
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
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.22f
    val pulse = 1f + 0.03f * sin(time * 2f)
    glow(c, r * 2.7f * pulse, Color(0xFF4CC9F0), safeA * 0.55f)
    glow(c, r * 2.0f * pulse, Color(0xFF3A7DFF), safeA * 0.5f)
    glow(c, r * 1.4f * pulse, Color(0xFF7209B7), safeA * 0.35f)

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
            alpha = safeA
        )
    }

    val rot = time * 0.04
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = (safeA * 0.45f).coerceIn(0f, 1f))),
            center = Offset(c.x - r * 0.4f, c.y - r * 0.4f),
            radius = r * 1.4f
        ),
        radius = r,
        center = c
    )
    drawCircle(Color(0xFF4CC9F0).copy(alpha = (safeA * 0.5f).coerceIn(0f, 1f)), radius = r * 1.05f, center = c, style = Stroke(width = 2f))

    if (cityLights) {
        val lightCount = 16
        for (i in 0 until lightCount) {
            val angle = (i / lightCount.toFloat()) * 2 * Math.PI + rot * 0.5
            val rad = r * 0.9f
            val pos = Offset(c.x + (cos(angle) * rad).toFloat(), c.y + (sin(angle) * rad).toFloat())
            val twinkle = (sin(time * 3f + i) * 0.5f + 0.5f)
            drawCircle(
                Color(0xFFFFE8B0).copy(alpha = (safeA * (0.3f + twinkle * 0.5f)).coerceIn(0f, 1f)),
                radius = short * 0.0025f,
                center = pos
            )
        }
    }

    for (i in 0..6) {
        val angle = i * 1.047 + time * 0.35
        val dist = r * (1.25f + 0.1f * sin(time * 2f + i))
        val satPos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        drawCircle(Color(0xFF4CC9F0).copy(alpha = (safeA * 0.75f).coerceIn(0f, 1f)), radius = short * 0.002f, center = satPos)
    }
}

private fun DrawScope.drawMoon(c: Offset, short: Float, a: Float, moon: ImageBitmap, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.22f
    glow(c, r * 2.8f, Color(0xFF4CC9F0), safeA * 0.2f)
    glow(c, r * 2.0f, Color(0xFFFFD166), safeA * 0.15f)
    glow(c, r * 1.5f, Color(0xFFB8C4D8), safeA * 0.3f)

    drawCircularImage(moon, c, r, safeA)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF4CC9F0).copy(alpha = (safeA * 0.12f).coerceIn(0f, 1f)), Color(0xFFFFD166).copy(alpha = (safeA * 0.08f).coerceIn(0f, 1f)), Color.Transparent),
            center = Offset(c.x - r * 0.2f, c.y - r * 0.2f),
            radius = r * 1.2f
        ),
        radius = r,
        center = c
    )

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = (safeA * 0.5f).coerceIn(0f, 1f))),
            center = Offset(c.x - r * 0.45f, c.y - r * 0.45f),
            radius = r * 1.3f
        ),
        radius = r,
        center = c
    )

    drawCircle(Color(0xFFFFEE93).copy(alpha = (safeA * 0.3f).coerceIn(0f, 1f)), radius = r * 1.02f, center = c, style = Stroke(width = 2f))

    val starCount = 8
    for (i in 0 until starCount) {
        val angle = i * (2.0 * Math.PI / starCount) + time * 0.2
        val dist = r * (1.25f + 0.15f * sin(time * 2f + i))
        val starPos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())
        val twinkle = 0.5f + 0.5f * sin(time * 4f + i)
        val starColor = if (i % 2 == 0) Color(0xFF4CC9F0) else Color(0xFFFFD166)
        drawCircle(starColor.copy(alpha = (safeA * 0.8f * twinkle).coerceIn(0f, 1f)), radius = short * 0.003f, center = starPos)
    }
}

private fun DrawScope.drawSun(c: Offset, short: Float, a: Float, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.16f
    val pulse = 1f + 0.05f * sin(time * 3f)

    glow(c, r * 4.2f * pulse, Color(0xFFD90429), safeA * 0.4f)
    glow(c, r * 3.2f * pulse, Color(0xFFFF6B00), safeA * 0.6f)
    glow(c, r * 2.2f * pulse, Color(0xFFFFB703), safeA * 0.8f)

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
            color = Color(0xFFFF4500).copy(alpha = (safeA * 0.7f).coerceIn(0f, 1f)),
            style = Stroke(width = 4f)
        )
    }

    val rayCount = 20
    for (i in 0 until rayCount) {
        val angle = (i / rayCount.toFloat()) * 2 * Math.PI + time * 0.08
        val innerR = r * 1.02f
        val outerR = r * (1.2f + 0.3f * ((i * 37) % 10) / 10f * (0.5f + 0.5f * sin(time * 4f + i)))
        val p1 = Offset(c.x + (cos(angle) * innerR).toFloat(), c.y + (sin(angle) * innerR).toFloat())
        val p2 = Offset(c.x + (cos(angle) * outerR).toFloat(), c.y + (sin(angle) * outerR).toFloat())
        drawLine(Color(0xFFFFD166).copy(alpha = (safeA * 0.5f).coerceIn(0f, 1f)), p1, p2, strokeWidth = 2.5f)
    }

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFFFF3), Color(0xFFFFEE93), Color(0xFFFFB703), Color(0xFFFF4500), Color(0xFFD90429)),
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )

    drawCircle(Color(0xFFFFEE93).copy(alpha = (safeA * 0.8f).coerceIn(0f, 1f)), radius = r * 1.02f, center = c, style = Stroke(width = 2.5f))
}

private fun DrawScope.drawSolarSystem(c: Offset, short: Float, a: Float, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1E1B4B).copy(alpha = (safeA * 0.7f).coerceIn(0f, 1f)), Color.Transparent),
            center = c,
            radius = short * 0.5f
        ),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f),
        size = Size(short * 1.0f, short * 0.8f)
    )

    val ringRadii = listOf(0.10f, 0.20f, 0.30f, 0.40f, 0.48f)
    ringRadii.forEachIndexed { index, rFrac ->
        val r = short * rFrac
        drawCircle(
            color = Color(0xFFFFB703).copy(alpha = (safeA * (0.15f + index * 0.03f + 0.02f * sin(time + index))).coerceIn(0f, 1f)),
            radius = r,
            center = c,
            style = Stroke(width = 1.2f)
        )
    }

    glow(c, short * 0.15f, Color(0xFFFFB703), safeA * 0.7f)
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFFFEE93), Color(0xFFFFB703), Color(0xFFD90429)),
            center = c,
            radius = short * 0.045f
        ),
        radius = short * 0.045f,
        center = c
    )

    val bodies = listOf(
        0.10f to Color(0xFF94A3B8),
        0.15f to Color(0xFFFDE047),
        0.20f to Color(0xFF38BDF8),
        0.25f to Color(0xFFF87171),
        0.32f to Color(0xFFFBBF24),
        0.39f to Color(0xFFFDE68A),
        0.44f to Color(0xFF67E8F9),
        0.48f to Color(0xFF60A5FA),
        0.51f to Color(0xFFFFD166),
        0.46f to Color(0xFF4CC9F0)
    )

    bodies.forEachIndexed { i, (frac, col) ->
        val r = short * frac
        val angle = i * 0.75 + time * (0.35f - i * 0.02f)
        val pos = Offset(c.x + (cos(angle) * r).toFloat(), c.y + (sin(angle) * r).toFloat())

        val basePos = Offset(pos.x, pos.y + short * 0.07f)
        drawLine(col.copy(alpha = (safeA * 0.35f).coerceIn(0f, 1f)), pos, basePos, strokeWidth = 1f)

        val isVoyager = i >= 8
        val blinkAlpha = if (isVoyager) {
            safeA * (0.3f + 0.7f * (sin(time * 8f + i) * 0.5f + 0.5f))
        } else {
            safeA
        }

        glow(pos, 18f, col, if (isVoyager) (blinkAlpha * 0.8f).coerceIn(0f, 1f) else (safeA * 0.6f).coerceIn(0f, 1f))
        drawCircle(Color.White.copy(alpha = blinkAlpha.coerceIn(0f, 1f)), radius = 4f, center = pos)
        drawCircle(col.copy(alpha = (if (isVoyager) blinkAlpha else safeA * 0.85f).coerceIn(0f, 1f)), radius = 8f, center = pos, style = Stroke(width = 1.2f))
    }
}

private fun DrawScope.drawGalaxy(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    glow(c, short * 0.25f, Color(0xFFFFE8A3), safeA * 0.7f)
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
        val brightness = safeA * (0.2f + 0.8f * (1 - t))
        drawCircle(starColor.copy(alpha = brightness.coerceIn(0f, 1f)), radius = short * (0.0015f + t * 0.002f), center = pos)
    }

    val sunAngle = 0.35 * 7.0 + 0.0 + rotation
    val sunRad = armR * 0.55f
    val sunPos = Offset(c.x + (cos(sunAngle) * sunRad).toFloat(), c.y + (sin(sunAngle) * sunRad).toFloat())

    glow(sunPos, 35f, Color(0xFFFFD166), safeA * 0.8f)
    drawCircle(Color.White.copy(alpha = safeA), radius = 6f, center = sunPos)
    val pulse = (sin(time * 6f) * 0.5f + 0.5f)
    drawCircle(Color(0xFFFFD166).copy(alpha = (safeA * (0.5f + pulse * 0.5f)).coerceIn(0f, 1f)), radius = 16f * (0.6f + pulse * 0.4f), center = sunPos, style = Stroke(width = 1.5f))
}

private fun DrawScope.drawLocalGroup(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0A1128).copy(alpha = (safeA * 0.7f).coerceIn(0f, 1f)), Color.Transparent),
            center = c,
            radius = short * 0.5f
        ),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f),
        size = Size(short * 1.0f, short * 0.8f)
    )

    val ringRadii = listOf(0.12f, 0.24f, 0.36f, 0.47f)
    ringRadii.forEachIndexed { index, rFrac ->
        val r = short * rFrac
        drawOval(
            color = Color(0xFF4CC9F0).copy(alpha = (safeA * (0.15f + index * 0.04f + 0.02f * sin(time + index))).coerceIn(0f, 1f)),
            topLeft = Offset(c.x - r, c.y - r * 0.55f),
            size = Size(r * 2f, r * 0.7f),
            style = Stroke(width = 1.2f)
        )
    }

    val baseGalaxies = listOf(
        Triple(Offset(0f, short * 0.04f), Color(0xFF4CC9F0), 0f),
        Triple(Offset(short * 0.16f, -short * 0.14f), Color(0xFFFFD166), 1.0f),
        Triple(Offset(short * 0.23f, -short * 0.06f), Color(0xFFF72585), 2.0f),
        Triple(Offset(-short * 0.12f, short * 0.08f), Color(0xFF4CC9F0), 3.0f),
        Triple(Offset(-short * 0.26f, short * 0.12f), Color(0xFF67E8F9), 4.0f),
        Triple(Offset(short * 0.29f, short * 0.16f), Color(0xFF60A5FA), 5.0f),
        Triple(Offset(-0.08f * short, -0.22f * short), Color(0xFFF72585), 6.0f),
        Triple(Offset(0.08f * short, 0.25f * short), Color(0xFFFFD166), 7.0f),
        Triple(Offset(-0.32f * short, -0.15f * short), Color(0xFF4CC9F0), 8.0f)
    )

    baseGalaxies.forEach { (offset, col, phaseOffset) ->
        val driftAngle = time * 0.15f + phaseOffset
        val driftX = offset.x + (cos(driftAngle) * short * 0.015f)
        val driftY = offset.y + (sin(driftAngle * 0.8f) * short * 0.015f)
        val pos = c + Offset(driftX, driftY)

        val basePos = Offset(pos.x, pos.y + short * 0.09f)
        drawLine(Color(0xFF4CC9F0).copy(alpha = (safeA * 0.3f).coerceIn(0f, 1f)), pos, basePos, strokeWidth = 1f)

        glow(pos, 18f, col, safeA * 0.6f)
        drawCircle(Color.White.copy(alpha = safeA), radius = 4f, center = pos)
        drawCircle(col.copy(alpha = (safeA * 0.85f).coerceIn(0f, 1f)), radius = 8f, center = pos, style = Stroke(width = 1.2f))
    }

    points.drop(10).take(20).forEachIndexed { index, p ->
        val floatAngle = time * 0.1f + index
        val bgPos = Offset(
            c.x + (p.x - 0.5f) * short * 0.9f + (cos(floatAngle) * 8f),
            c.y + (p.y - 0.5f) * short * 0.7f + (sin(floatAngle) * 8f)
        )
        drawCircle(Color.White.copy(alpha = (safeA * 0.2f).coerceIn(0f, 1f)), radius = 1.5f, center = bgPos)
    }
}

private fun DrawScope.drawSupercluster(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    glow(c, short * 0.35f, Color(0xFF4CC9F0), safeA * 0.4f)
    glow(c, short * 0.18f, Color(0xFFFFD166), safeA * 0.6f)

    val streamCount = 24
    for (i in 0 until streamCount) {
        val baseAngle = (i / streamCount.toFloat()) * 6.28f + time * 0.02f
        val path = Path().apply {
            val startDist = short * 0.48f
            val startX = c.x + (cos(baseAngle) * startDist)
            val startY = c.y + (sin(baseAngle) * startDist)
            moveTo(startX, startY)
            val midAngle = baseAngle + 0.3f
            val midDist = short * 0.25f
            val midX = c.x + (cos(midAngle) * midDist)
            val midY = c.y + (sin(midAngle) * midDist)
            quadraticBezierTo(midX, midY, c.x, c.y)
        }
        drawPath(
            path = path,
            color = Color(0xFF4CC9F0).copy(alpha = (safeA * 0.25f).coerceIn(0f, 1f)),
            style = Stroke(width = 1.5f)
        )
    }

    points.take(180).forEachIndexed { i, p ->
        val flowProgress = (p.x + time * 0.05f) % 1f
        val angle = p.y * 6.28f
        val dist = short * 0.48f * (1f - flowProgress * 0.85f)
        val pos = Offset(
            c.x + (cos(angle + flowProgress) * dist),
            c.y + (sin(angle + flowProgress) * dist)
        )
        val col = when (i % 3) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            else -> Color(0xFFF72585)
        }
        val twinkle = 0.5f + 0.5f * sin(time * 3f + i)
        drawCircle(col.copy(alpha = (safeA * 0.75f * twinkle).coerceIn(0f, 1f)), radius = short * 0.0025f, center = pos)
    }
}

private fun DrawScope.drawCosmicWeb(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val nodes = points.take(24).map {
        Offset(c.x + (it.x - 0.5f) * short * 0.95f, c.y + (it.y - 0.5f) * short * 0.95f)
    }
    val breath = 0.8f + 0.2f * sin(time * 0.6f)

    nodes.forEachIndexed { i, n ->
        val next = nodes[(i + 1) % nodes.size]
        val skip = nodes[(i + 3) % nodes.size]
        val opposite = nodes[(i + 7) % nodes.size]

        drawLine(Color(0xFF4361EE).copy(alpha = (safeA * 0.25f * breath).coerceIn(0f, 1f)), n, next, strokeWidth = 3f)
        drawLine(Color(0xFF7209B7).copy(alpha = (safeA * 0.2f * breath).coerceIn(0f, 1f)), n, skip, strokeWidth = 2f)
        drawLine(Color(0xFF4CC9F0).copy(alpha = (safeA * 0.15f * breath).coerceIn(0f, 1f)), n, opposite, strokeWidth = 1.5f)

        for (step in 1..4) {
            val f = step / 5f
            val intermediate = Offset(n.x + (next.x - n.x) * f, n.y + (next.y - n.y) * f)
            drawCircle(Color.White.copy(alpha = (safeA * 0.5f).coerceIn(0f, 1f)), radius = short * 0.002f, center = intermediate)
        }
    }

    nodes.forEachIndexed { i, n ->
        val clusterColor = when (i % 3) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            else -> Color(0xFFF72585)
        }
        glow(n, short * 0.05f, clusterColor, safeA * 0.4f * breath)
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
    val safeA = a.coerceIn(0f, 1f)
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.Transparent, Color(0xFF4361EE).copy(alpha = (safeA * 0.15f).coerceIn(0f, 1f)), Color(0xFFF72585).copy(alpha = (safeA * 0.25f).coerceIn(0f, 1f))),
            center = c,
            radius = boundary
        ),
        radius = boundary,
        center = c
    )
    drawCircle(
        Color(0xFFFFD166).copy(alpha = (safeA * 0.3f).coerceIn(0f, 1f)),
        radius = boundary,
        center = c,
        style = Stroke(width = 2f)
    )

    points.forEachIndexed { i, p ->
        val r = boundary * (p.x * 0.95f)
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
        drawCircle(galaxyColor.copy(alpha = (safeA * (0.4f + 0.5f * p.y) * twinkle).coerceIn(0f, 1f)), radius = radius, center = pos)
    }
}

private fun DrawScope.drawBeyondUniverse(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF7209B7).copy(alpha = (safeA * 0.3f).coerceIn(0f, 1f)), Color(0xFF4361EE).copy(alpha = (safeA * 0.2f).coerceIn(0f, 1f)), Color.Transparent),
            center = c,
            radius = boundary * 1.2f
        ),
        radius = boundary * 1.2f,
        center = c
    )

    for (i in 1..4) {
        val ringR = boundary * (i / 4f) * (0.85f + 0.15f * sin(time * 0.4f + i))
        drawCircle(
            color = Color(0xFFFFD166).copy(alpha = (safeA * (0.2f / i)).coerceIn(0f, 1f)),
            radius = ringR,
            center = c,
            style = Stroke(width = 1.5f)
        )
    }

    points.take(160).forEachIndexed { i, p ->
        val angle = p.x * 6.28 + time * (0.04f + (i % 3) * 0.015f)
        val dist = boundary * (0.15f + 0.85f * p.y * (0.95f + 0.05f * sin(time * 0.8f + i)))
        val pos = Offset(c.x + (cos(angle) * dist).toFloat(), c.y + (sin(angle) * dist).toFloat())

        val color = when (i % 4) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            2 -> Color(0xFFF72585)
            else -> Color.White
        }
        val twinkle = 0.4f + 0.6f * sin(time * 2f + i)
        drawCircle(color.copy(alpha = (safeA * 0.75f * twinkle).coerceIn(0f, 1f)), radius = short * 0.0025f, center = pos)
    }
}
