package com.northloom.apeiron

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
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlin.math.cos
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// Shared helpers & constants
// ─────────────────────────────────────────────────────────────────────────────

private const val TWO_PI = 6.2831855f
private const val RAD_TO_DEG = 57.29578f

private fun Color.fade(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

private fun polar(c: Offset, angle: Float, r: Float): Offset =
    Offset(c.x + cos(angle) * r, c.y + sin(angle) * r)

/** Cheap bell-shaped random in roughly [-1.5, 1.5]. */
private fun gauss(rnd: Random): Float = rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f

// Hoisted so they are NOT re-allocated every frame.
private val avatarPaint = Paint().apply {
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
}
private val avatarShadowPaint = Paint().apply {
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
}

// ─────────────────────────────────────────────────────────────────────────────
// Pre-computed geometry (built once, reused every frame)
// ─────────────────────────────────────────────────────────────────────────────

private class PointBucket(val color: Color, val tier: Int, val pts: MutableList<Offset> = mutableListOf())

private class WebEdge(val a: Int, val b: Int, val weight: Float)

private class CmbBlob(val angle: Float, val jitter: Float, val size: Float, val warm: Boolean)

private val UNIVERSE_COLORS = listOf(Color(0xFF4CC9F0), Color(0xFFF72585), Color(0xFFFFD166), Color.White)

// Galaxy geometry
private const val GAL_ARM_R = 0.48f
private const val GAL_B = 0.30f        // log-spiral pitch
private const val GAL_R0 = 0.04f
private const val GAL_TILT = 0.58f     // disc inclination (y squash)
private const val GAL_ROT = -0.5f      // disc orientation on screen

private class CosmicCache {
    val galaxyBuckets: List<PointBucket> = buildGalaxy(Random(11))
    val universeBuckets: List<PointBucket> = buildUniverse(Random(23))
    val cmbBlobs: List<CmbBlob> = buildCmb(Random(5))

    val webNodes: List<Offset>
    val webMass: List<Float>
    val webEdges: List<WebEdge>
    val webDots: List<Offset>

    val superclusterPath: Path = buildSuperclusterPath()

    init {
        val rnd = Random(7)
        webNodes = List(36) { Offset(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) }
        webMass = List(36) { 0.3f + rnd.nextFloat() * 0.7f }
        webEdges = buildWebEdges(webNodes, webMass)
        webDots = buildWebDots(webNodes, webEdges, rnd)
    }
}

/** Logarithmic spiral position in unit space (before tilt). */
private fun spiralPoint(t: Float, arm: Int, theta0: Float = 0f): Offset {
    val theta = ln(t / GAL_R0) / GAL_B + arm * (TWO_PI / 4f) + theta0
    return polar(Offset.Zero, theta, t * GAL_ARM_R)
}

private fun buildGalaxy(rnd: Random): List<PointBucket> {
    // colours: 0 blue, 1 pink (star-forming), 2 white, 3 warm (old stars)
    val colors = listOf(Color(0xFF8FD8FF), Color(0xFFF72585), Color.White, Color(0xFFFFE2A0))
    val buckets = List(4 * 3) { PointBucket(colors[it / 3], it % 3) }
    repeat(1700) {
        val t = GAL_R0 + (1f - GAL_R0) * rnd.nextFloat().pow(1.3f)
        val diffuse = rnd.nextFloat() < 0.22f
        val pos: Offset
        if (diffuse) {
            pos = polar(Offset.Zero, rnd.nextFloat() * TWO_PI, t * GAL_ARM_R * (0.8f + 0.2f * rnd.nextFloat()))
        } else {
            // 2 strong arms (70%) + 2 weak arms (30%)
            val arm = if (rnd.nextFloat() < 0.7f) rnd.nextInt(2) * 2 else 1 + rnd.nextInt(2) * 2
            val base = spiralPoint(t, arm)
            // scatter perpendicular to the arm (≈ radial for a tight spiral)
            val radial = t * GAL_ARM_R + gauss(rnd) * 0.03f * (0.4f + t)
            val ang = kotlin.math.atan2(base.y, base.x)
            pos = polar(Offset.Zero, ang, radial.coerceAtLeast(0.01f))
        }
        val colorIdx = when {
            t < 0.22f -> 3
            diffuse -> 2
            else -> {
                val r = rnd.nextFloat()
                if (r < 0.55f) 0 else if (r < 0.65f) 1 else 2
            }
        }
        val tier = min(2, (t * 3f).toInt())
        buckets[colorIdx * 3 + tier].pts.add(pos)
    }
    return buckets
}

private fun buildUniverse(rnd: Random): List<PointBucket> {
    val centers = List(30) {
        val r = sqrt(rnd.nextFloat()) * 0.9f
        polar(Offset.Zero, rnd.nextFloat() * TWO_PI, r)
    }
    val buckets = List(12) { PointBucket(UNIVERSE_COLORS[it % 4], it / 4) }
    repeat(1300) {
        val p = if (rnd.nextFloat() < 0.8f) {
            val c = centers[rnd.nextInt(centers.size)]
            Offset(c.x + gauss(rnd) * 0.06f, c.y + gauss(rnd) * 0.06f)
        } else {
            polar(Offset.Zero, rnd.nextFloat() * TWO_PI, sqrt(rnd.nextFloat()) * 0.95f)
        }
        if (p.getDistance() <= 0.97f) buckets[rnd.nextInt(12)].pts.add(p)
    }
    return buckets
}

private fun buildCmb(rnd: Random): List<CmbBlob> =
    List(150) { CmbBlob(rnd.nextFloat() * TWO_PI, gauss(rnd) * 0.02f, 0.012f + rnd.nextFloat() * 0.02f, rnd.nextBoolean()) }

private fun buildWebEdges(nodes: List<Offset>, mass: List<Float>): List<WebEdge> {
    val seen = HashSet<Int>()
    val edges = mutableListOf<WebEdge>()
    for (i in nodes.indices) {
        nodes.indices
            .filter { it != i }
            .sortedBy { (nodes[it] - nodes[i]).getDistance() }
            .take(3)
            .forEach { j ->
                if ((nodes[j] - nodes[i]).getDistance() < 0.4f) {
                    val lo = minOf(i, j)
                    val hi = maxOf(i, j)
                    if (seen.add(lo * 1000 + hi)) edges.add(WebEdge(lo, hi, (mass[lo] + mass[hi]) / 2f))
                }
            }
    }
    return edges
}

private fun buildWebDots(nodes: List<Offset>, edges: List<WebEdge>, rnd: Random): List<Offset> {
    val dots = mutableListOf<Offset>()
    edges.forEach { e ->
        val p = nodes[e.a]
        val q = nodes[e.b]
        val dx = q.x - p.x
        val dy = q.y - p.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len
        val ny = dx / len
        val count = 4 + (e.weight * 6f).toInt()
        repeat(count) {
            val f = rnd.nextFloat()
            val off = gauss(rnd) * 0.012f
            dots.add(Offset(p.x + dx * f + nx * off, p.y + dy * f + ny * off))
        }
    }
    return dots
}

private fun buildSuperclusterPath(): Path = Path().apply {
    val count = 24
    for (i in 0 until count) {
        val base = (i / count.toFloat()) * TWO_PI
        moveTo(cos(base) * 0.48f, sin(base) * 0.48f)
        val mid = base + 0.3f
        quadraticBezierTo(cos(mid) * 0.25f, sin(mid) * 0.25f, 0f, 0f)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Entry point
// ─────────────────────────────────────────────────────────────────────────────

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

    val points = remember {
        val field = Random(42)
        List(1200) { Offset(field.nextFloat(), field.nextFloat()) }
    }
    val cache = remember { CosmicCache() }
    val surroundingsImage = ImageBitmap.imageResource(R.drawable.surroundings_landscape)
    val earth = ImageBitmap.imageResource(R.drawable.earth_apollo)
    val moon = ImageBitmap.imageResource(R.drawable.moon)

    Canvas(modifier = modifier.fillMaxSize()) {
        if (current.index == next.index) {
            drawScene(current.index, 1f, points, cache, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
        } else {
            val safeFraction = fraction.coerceIn(0f, 1f)
            val inScale = 1.15f - (0.15f * safeFraction)
            scale(scale = inScale, pivot = center) {
                drawScene(next.index, safeFraction, points, cache, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
            }

            val outScale = 1f - (0.15f * safeFraction)
            scale(scale = outScale, pivot = center) {
                drawScene(current.index, (1f - safeFraction).coerceIn(0f, 1f), points, cache, time, showCityLights, surroundingsImage, earth, moon, userAvatar)
            }
        }
    }
}

private fun DrawScope.drawScene(
    levelIndex: Int,
    alpha: Float,
    points: List<Offset>,
    cache: CosmicCache,
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
        2 -> drawEarth(c, short, safeAlpha, time, showCityLights, earth, points)
        3 -> drawMoon(c, short, safeAlpha, moon, time)
        4 -> drawSun(c, short, safeAlpha, time)
        5 -> drawSolarSystem(c, short, safeAlpha, time, points)
        6 -> drawGalaxy(c, short, safeAlpha, cache, time)
        7 -> drawLocalGroup(c, short, safeAlpha, points, time)
        8 -> drawSupercluster(c, short, safeAlpha, points, cache, time)
        9 -> drawCosmicWeb(c, short, safeAlpha, cache, time)
        10 -> drawObservableUniverse(c, short, safeAlpha, cache, time)
        11 -> drawBeyondUniverse(c, short, safeAlpha, points, time)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Primitives
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if ((safeAlpha <= 0f) || (radius <= 0f)) return
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(color.fade(safeAlpha), Color.Transparent),
            center = center,
            radius = radius
        ),
        radius = radius,
        center = center
    )
}

/**
 * Draws a square-cropped image clipped to a circle.
 * Uses a float transform instead of Int offsets so it doesn't jitter while scaling.
 */
private fun DrawScope.drawCircularImage(image: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    val safeA = alpha.coerceIn(0f, 1f)
    val sourceSize = min(image.width, image.height)
    val sourceOffset = IntOffset((image.width - sourceSize) / 2, (image.height - sourceSize) / 2)
    val k = (radius * 2f) / sourceSize
    val clip = Path().apply { addOval(Rect(center, radius)) }
    clipPath(clip) {
        withTransform({
            translate(center.x - radius, center.y - radius)
            scale(k, k, pivot = Offset.Zero)
        }) {
            drawImage(
                image = image,
                srcOffset = sourceOffset,
                srcSize = IntSize(sourceSize, sourceSize),
                dstOffset = IntOffset.Zero,
                dstSize = IntSize(sourceSize, sourceSize),
                alpha = safeA,
                filterQuality = FilterQuality.High
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 0 – You
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawYou(c: Offset, short: Float, a: Float, time: Float, userAvatar: String?) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.09f
    glow(c, r * 3.2f, Color(0xFF4CC9F0), safeA * 0.22f)
    glow(c, r * 1.9f, Color(0xFF4CC9F0), safeA * 0.28f)

    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color.White.fade(safeA * 0.10f), Color.White.fade(safeA * 0.02f)),
            center = c,
            radius = r * 1.15f
        ),
        radius = r * 1.15f,
        center = c
    )
    drawCircle(
        brush = Brush.sweepGradient(
            colors = listOf(
                Color.White.fade(safeA * 0.85f),
                Color(0xFF4CC9F0).fade(safeA * 0.15f),
                Color.Black.fade(safeA * 0.05f),
                Color(0xFF4CC9F0).fade(safeA * 0.15f),
                Color.White.fade(safeA * 0.85f)
            ),
            center = c
        ),
        radius = r * 1.15f,
        center = c,
        style = Stroke(width = 1.6f)
    )

    if (userAvatar != null) {
        drawIntoCanvas { canvas ->
            avatarShadowPaint.textSize = r * 2.15f
            avatarShadowPaint.alpha = (safeA * 90).toInt().coerceIn(0, 255)
            canvas.nativeCanvas.drawText(userAvatar, c.x, c.y + r * 0.75f + 5f, avatarShadowPaint)

            avatarPaint.textSize = r * 2.15f
            avatarPaint.alpha = (safeA * 255).toInt().coerceIn(0, 255)
            canvas.nativeCanvas.drawText(userAvatar, c.x, c.y + r * 0.75f, avatarPaint)
        }
    } else {
        val figure = Path().apply {
            val headR = r * 0.26f
            val headC = Offset(c.x, c.y - r * 0.5f)
            addOval(Rect(headC, headR))
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
                colors = listOf(Color.White.fade(safeA * 0.95f), Color(0xFF9FB4C7).fade(safeA * 0.75f)),
                startY = c.y - r,
                endY = c.y + r
            )
        )
        drawPath(figure, color = Color(0xFF4CC9F0).fade(safeA * 0.35f), style = Stroke(width = 1.2f))
    }

    for (i in 0..4) {
        val angle = i * 1.257f + time * 0.3f
        val pos = polar(c, angle, r * 1.5f)
        val twinkle = 0.55f + 0.45f * sin(time * 1.5f + i * 1.3f)
        drawCircle(Color(0xFF4CC9F0).fade(safeA * 0.6f * twinkle), radius = short * 0.0016f, center = pos)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 1 – Surroundings
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSurroundings(c: Offset, short: Float, a: Float, image: ImageBitmap, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val imageRadius = short * 0.18f
    val pulse = 1f + 0.04f * sin(time * 2.5f)
    glow(c, imageRadius * 2.6f * pulse, Color(0xFF4361EE), safeA * 0.35f)
    glow(c, imageRadius * 1.9f * pulse, Color(0xFFF72585), safeA * 0.3f)
    glow(c, imageRadius * 1.4f * pulse, Color(0xFFFFD166), safeA * 0.25f)
    drawCircularImage(image, c, imageRadius, safeA)
    drawCircle(Color(0xFFFFEE93).fade(safeA * 0.6f), radius = imageRadius * 1.04f, center = c, style = Stroke(width = 2f))

    for (i in 0..7) {
        val angle = i * 0.785f + time * 0.4f
        val dist = imageRadius * (1.2f + 0.15f * sin(time * 1.5f + i))
        val pos = polar(c, angle, dist)
        val twinkle = 0.5f + 0.5f * sin(time * 3f + i)
        drawCircle(Color(0xFFFFD166).fade(safeA * 0.7f * twinkle), radius = short * 0.0025f, center = pos)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 2 – Earth
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawEarth(
    c: Offset,
    short: Float,
    a: Float,
    time: Float,
    cityLights: Boolean,
    earth: ImageBitmap,
    points: List<Offset>
) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.22f
    val pulse = 1f + 0.03f * sin(time * 2f)
    glow(c, r * 2.7f * pulse, Color(0xFF4CC9F0), safeA * 0.45f)
    glow(c, r * 2.0f * pulse, Color(0xFF3A7DFF), safeA * 0.4f)
    glow(c, r * 1.4f * pulse, Color(0xFF7209B7), safeA * 0.28f)

    drawCircularImage(earth, c, r, safeA)

    // The photo is already lit, so only add a gentle terminator on the lower-right limb.
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.65f to Color.Transparent,
            1f to Color.Black.fade(safeA * 0.35f),
            center = Offset(c.x - r * 0.35f, c.y - r * 0.35f),
            radius = r * 1.45f
        ),
        radius = r,
        center = c
    )
    // Thin atmosphere rim
    drawCircle(Color(0xFF7CC8FF).fade(safeA * 0.55f), radius = r * 1.012f, center = c, style = Stroke(width = short * 0.004f))

    if (cityLights) {
        // City lights only appear on the dark (lower-right) side, scattered over the disc.
        points.take(500).forEachIndexed { i, p ->
            val dx = (p.x - 0.5f) * 2f * r
            val dy = (p.y - 0.5f) * 2f * r
            val inside = sqrt(dx * dx + dy * dy) <= r * 0.92f
            val darkSide = (dx + dy) > r * 0.5f
            if (inside && darkSide) {
                val twinkle = 0.5f + 0.5f * sin(time * 2f + i)
                drawCircle(
                    Color(0xFFFFE8B0).fade(safeA * (0.35f + twinkle * 0.5f)),
                    radius = short * 0.0016f,
                    center = Offset(c.x + dx, c.y + dy)
                )
            }
        }
    }

    for (i in 0..6) {
        val angle = i * 1.047f + time * 0.35f
        val dist = r * (1.25f + 0.1f * sin(time * 2f + i))
        drawCircle(Color(0xFF4CC9F0).fade(safeA * 0.75f), radius = short * 0.002f, center = polar(c, angle, dist))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 3 – Moon
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawMoon(c: Offset, short: Float, a: Float, moon: ImageBitmap, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.22f
    glow(c, r * 2.8f, Color(0xFF4CC9F0), safeA * 0.2f)
    glow(c, r * 2.0f, Color(0xFFFFD166), safeA * 0.12f)
    glow(c, r * 1.5f, Color(0xFFB8C4D8), safeA * 0.3f)

    drawCircularImage(moon, c, r, safeA)

    // Soft terminator only (no coloured tint on the lunar surface).
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color.Transparent,
            0.6f to Color.Transparent,
            1f to Color.Black.fade(safeA * 0.45f),
            center = Offset(c.x - r * 0.4f, c.y - r * 0.4f),
            radius = r * 1.45f
        ),
        radius = r,
        center = c
    )
    drawCircle(Color(0xFFE5E7EB).fade(safeA * 0.3f), radius = r * 1.01f, center = c, style = Stroke(width = 2f))

    val starCount = 8
    for (i in 0 until starCount) {
        val angle = i * (TWO_PI / starCount) + time * 0.2f
        val dist = r * (1.25f + 0.15f * sin(time * 2f + i))
        val twinkle = 0.5f + 0.5f * sin(time * 4f + i)
        val starColor = if (i % 2 == 0) Color(0xFF4CC9F0) else Color(0xFFFFD166)
        drawCircle(starColor.fade(safeA * 0.8f * twinkle), radius = short * 0.003f, center = polar(c, angle, dist))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 4 – Sun
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSun(c: Offset, short: Float, a: Float, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.16f
    val pulse = 1f + 0.02f * sin(time * 1.5f)

    // Soft corona (no spiky rays)
    glow(c, r * 3.6f * pulse, Color(0xFFFF6B00), safeA * 0.18f)
    glow(c, r * 2.4f * pulse, Color(0xFFFF9A1F), safeA * 0.30f)
    glow(c, r * 1.6f * pulse, Color(0xFFFFC857), safeA * 0.55f)

    // Prominences: thin looping arcs rising from the limb
    val loopCount = 6
    for (i in 0 until loopCount) {
        val baseAngle = i * (TWO_PI / loopCount) + time * 0.05f
        val startAngle = baseAngle - 0.18f
        val endAngle = baseAngle + 0.18f
        val peakR = r * (1.3f + 0.2f * sin(time * 1.2f + i * 1.7f))
        val p1 = polar(c, startAngle, r * 0.98f)
        val p3 = polar(c, endAngle, r * 0.98f)
        val p2 = polar(c, baseAngle, peakR * 1.12f)
        val arc = Path().apply {
            moveTo(p1.x, p1.y)
            quadraticBezierTo(p2.x, p2.y, p3.x, p3.y)
        }
        drawPath(
            path = arc,
            color = Color(0xFFFF5A1F).fade(safeA * 0.6f),
            style = Stroke(width = short * 0.004f, cap = StrokeCap.Round)
        )
    }

    // Disc with limb darkening: white-yellow centre, redder/dimmer edge
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFFFF3).fade(safeA),
            0.55f to Color(0xFFFFF1B8).fade(safeA),
            0.85f to Color(0xFFFFC14D).fade(safeA),
            0.97f to Color(0xFFFF8A1F).fade(safeA),
            1f to Color(0xFFE8590C).fade(safeA),
            center = c,
            radius = r
        ),
        radius = r,
        center = c
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 5 – Solar System
// ─────────────────────────────────────────────────────────────────────────────

private class Planet(
    val orbit: Float,   // fraction of `short`
    val size: Float,    // body radius, fraction of `short`
    val color: Color,
    val phase: Float,
    val rings: Boolean = false
)

// Roughly log-spaced orbits; each planet sits exactly on its own ring.
private val PLANETS = listOf(
    Planet(0.08f, 0.0055f, Color(0xFF94A3B8), 0.5f),            // Mercury
    Planet(0.12f, 0.0090f, Color(0xFFFDE68A), 2.1f),            // Venus
    Planet(0.17f, 0.0095f, Color(0xFF38BDF8), 4.0f),            // Earth
    Planet(0.23f, 0.0070f, Color(0xFFF87171), 1.2f),            // Mars
    Planet(0.30f, 0.0210f, Color(0xFFE9C79A), 3.3f),            // Jupiter
    Planet(0.38f, 0.0170f, Color(0xFFFBBF24), 5.4f, rings = true), // Saturn
    Planet(0.44f, 0.0120f, Color(0xFF67E8F9), 0.2f),            // Uranus
    Planet(0.48f, 0.0120f, Color(0xFF3B6FE0), 2.8f),            // Neptune
)

private fun DrawScope.drawSolarSystem(c: Offset, short: Float, a: Float, time: Float, points: List<Offset>) {
    val safeA = a.coerceIn(0f, 1f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF1E1B4B).fade(safeA * 0.7f), Color.Transparent),
            center = c,
            radius = short * 0.5f
        ),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f),
        size = Size(short * 1.0f, short * 0.8f)
    )

    // Orbit rings (one per planet)
    PLANETS.forEachIndexed { index, p ->
        drawCircle(
            color = Color(0xFFFFB703).fade(safeA * (0.12f + 0.02f * sin(time + index))),
            radius = short * p.orbit,
            center = c,
            style = Stroke(width = 1f)
        )
    }

    // Asteroid belt between Mars (0.23) and Jupiter (0.30)
    points.take(150).forEach { p ->
        val ang = p.y * TWO_PI + time * 0.02f
        val rr = short * (0.252f + p.x * 0.036f)
        drawCircle(Color(0xFFB8C4D8).fade(safeA * 0.4f), radius = short * 0.0012f, center = polar(c, ang, rr))
    }

    // Sun
    val sunR = short * 0.045f
    glow(c, short * 0.15f, Color(0xFFFFB703), safeA * 0.6f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFFFF3).fade(safeA),
            0.7f to Color(0xFFFFE08A).fade(safeA),
            1f to Color(0xFFFF9A1F).fade(safeA),
            center = c,
            radius = sunR
        ),
        radius = sunR,
        center = c
    )

    // Planets (Kepler-ish: inner planets move faster), shaded toward the Sun
    PLANETS.forEach { p ->
        val ang = p.phase + time * 0.6f * (0.08f / p.orbit).pow(1.5f)
        val pos = polar(c, ang, short * p.orbit)
        val rad = short * p.size

        glow(pos, rad * 3.2f, p.color, safeA * 0.3f)

        val ringTopLeft = Offset(pos.x - rad * 2.1f, pos.y - rad * 0.7f)
        val ringSize = Size(rad * 4.2f, rad * 1.4f)
        if (p.rings) {
            drawOval(Color(0xFFFDE68A).fade(safeA * 0.7f), ringTopLeft, ringSize, style = Stroke(width = rad * 0.35f))
        }

        val toSun = c - pos
        val len = toSun.getDistance().coerceAtLeast(1f)
        val highlight = Offset(pos.x + toSun.x / len * rad * 0.45f, pos.y + toSun.y / len * rad * 0.45f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(p.color, Color.White, 0.5f).fade(safeA),
                0.55f to p.color.fade(safeA),
                1f to lerp(p.color, Color.Black, 0.65f).fade(safeA),
                center = highlight,
                radius = rad * 1.6f
            ),
            radius = rad,
            center = pos
        )

        if (p.rings) {
            // Front half of the ring passes over the planet
            drawArc(
                color = Color(0xFFFDE68A).fade(safeA * 0.7f),
                startAngle = 0f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = ringTopLeft,
                size = ringSize,
                style = Stroke(width = rad * 0.35f)
            )
        }
    }

    // Two probes on outbound trajectories (clearly not planets: fixed bearing, trail, blinking)
    listOf(2.2f to 0f, 4.1f to 0.5f).forEach { (bearing, phase) ->
        val prog = (time * 0.015f + phase) % 1f
        val start = polar(c, bearing, short * 0.28f)
        val pos = polar(c, bearing, short * (0.28f + 0.20f * prog))
        val blink = safeA * (0.3f + 0.7f * (sin(time * 8f + bearing) * 0.5f + 0.5f))
        drawLine(Color(0xFF4CC9F0).fade(safeA * 0.25f), start, pos, strokeWidth = 1f)
        glow(pos, short * 0.012f, Color(0xFF4CC9F0), blink * 0.8f)
        drawCircle(Color.White.fade(blink), radius = short * 0.0035f, center = pos)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 6 – Galaxy (logarithmic spiral, tilted disc)
// ─────────────────────────────────────────────────────────────────────────────

/** Unit-space galaxy point -> screen position (spin, squash, rotate). */
private fun galaxyToScreen(p: Offset, spin: Float, c: Offset, short: Float): Offset {
    val cs = cos(spin)
    val sn = sin(spin)
    val x1 = p.x * cs - p.y * sn
    val y1 = (p.x * sn + p.y * cs) * GAL_TILT
    val cr = cos(GAL_ROT)
    val sr = sin(GAL_ROT)
    return Offset(c.x + (x1 * cr - y1 * sr) * short, c.y + (x1 * sr + y1 * cr) * short)
}

private fun DrawScope.drawGalaxy(c: Offset, short: Float, a: Float, cache: CosmicCache, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val spin = time * 0.04f

    withTransform({
        translate(c.x, c.y)
        rotate(GAL_ROT * RAD_TO_DEG, Offset.Zero)
        scale(short, short * GAL_TILT, Offset.Zero)
        rotate(spin * RAD_TO_DEG, Offset.Zero)
    }) {
        // Faint disc haze
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFF5B6CFF).fade(safeA * 0.16f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = GAL_ARM_R
            ),
            radius = GAL_ARM_R,
            center = Offset.Zero
        )

        // Stars, batched into a few drawPoints calls
        cache.galaxyBuckets.forEach { b ->
            if (b.pts.isEmpty()) return@forEach
            val tierT = (b.tier + 0.5f) / 3f
            val alpha = safeA * (0.2f + 0.8f * (1f - tierT))
            val diameter = short * (0.0015f + tierT * 0.002f) * 2f
            drawPoints(
                points = b.pts,
                pointMode = PointMode.Points,
                color = b.color.fade(alpha),
                strokeWidth = diameter / short,
                cap = StrokeCap.Round
            )
        }

        // Yellowish bulge
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFF6D5).fade(safeA),
                0.35f to Color(0xFFFFD166).fade(safeA * 0.9f),
                0.7f to Color(0xFFC77B30).fade(safeA * 0.35f),
                1f to Color.Transparent,
                center = Offset.Zero,
                radius = 0.11f
            ),
            radius = 0.11f,
            center = Offset.Zero
        )
    }
    glow(c, short * 0.2f, Color(0xFFFFE8A3), safeA * 0.35f)

    // "You are here": on a spiral arm, ~55% out
    val sunUnit = spiralPoint(0.6f, 0)
    val sunPos = galaxyToScreen(sunUnit, spin, c, short)
    glow(sunPos, short * 0.03f, Color(0xFFFFD166), safeA * 0.8f)
    drawCircle(Color.White.fade(safeA), radius = short * 0.005f, center = sunPos)
    val pulse = sin(time * 6f) * 0.5f + 0.5f
    drawCircle(
        Color(0xFFFFD166).fade(safeA * (0.5f + pulse * 0.5f)),
        radius = short * 0.014f * (0.6f + pulse * 0.4f),
        center = sunPos,
        style = Stroke(width = 1.5f)
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 7 – Local Group (objects sit exactly on the tilted orbit ellipses)
// ─────────────────────────────────────────────────────────────────────────────

private const val LG_TILT = 0.55f

private class LgMember(val orbit: Float, val phase: Float, val color: Color, val size: Float)

private val LOCAL_GROUP = listOf(
    LgMember(0.12f, 0.5f, Color(0xFF4CC9F0), 0.0045f),  // LMC-like
    LgMember(0.12f, 3.6f, Color(0xFF67E8F9), 0.0035f),  // SMC-like
    LgMember(0.24f, 1.0f, Color(0xFFFFD166), 0.0110f),  // Andromeda-like
    LgMember(0.24f, 4.4f, Color(0xFFF72585), 0.0040f),
    LgMember(0.36f, 2.2f, Color(0xFF60A5FA), 0.0070f),  // Triangulum-like
    LgMember(0.36f, 5.3f, Color(0xFF4CC9F0), 0.0040f),
    LgMember(0.47f, 0.2f, Color(0xFFF72585), 0.0035f),
    LgMember(0.47f, 3.0f, Color(0xFFFFD166), 0.0035f),
)

private fun DrawScope.drawLocalGroup(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    drawOval(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFF0A1128).fade(safeA * 0.7f), Color.Transparent),
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
            color = Color(0xFF4CC9F0).fade(safeA * (0.15f + index * 0.04f + 0.02f * sin(time + index))),
            topLeft = Offset(c.x - r, c.y - r * LG_TILT),
            size = Size(r * 2f, r * 2f * LG_TILT),
            style = Stroke(width = 1.2f)
        )
    }

    // Background galaxies
    points.drop(10).take(20).forEachIndexed { index, p ->
        val floatAngle = time * 0.1f + index
        val bgPos = Offset(
            c.x + (p.x - 0.5f) * short * 0.9f + cos(floatAngle) * 8f,
            c.y + (p.y - 0.5f) * short * 0.7f + sin(floatAngle) * 8f
        )
        drawCircle(Color.White.fade(safeA * 0.2f), radius = 1.5f, center = bgPos)
    }

    fun drawMember(pos: Offset, color: Color, radius: Float) {
        val basePos = Offset(pos.x, pos.y + short * 0.07f)
        drawLine(Color(0xFF4CC9F0).fade(safeA * 0.25f), pos, basePos, strokeWidth = 1f)
        glow(pos, radius * 4f, color, safeA * 0.6f)
        drawCircle(Color.White.fade(safeA), radius = radius * 0.5f, center = pos)
        drawCircle(color.fade(safeA * 0.85f), radius = radius, center = pos, style = Stroke(width = 1.2f))
    }

    // Milky Way at the centre
    drawMember(c, Color(0xFFFFE8A3), short * 0.012f)

    LOCAL_GROUP.forEach { m ->
        val ang = m.phase + time * 0.2f * (0.12f / m.orbit)
        val pos = Offset(
            c.x + cos(ang) * short * m.orbit,
            c.y + sin(ang) * short * m.orbit * LG_TILT
        )
        drawMember(pos, m.color, short * m.size + 3f)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 8 – Supercluster
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawSupercluster(
    c: Offset,
    short: Float,
    a: Float,
    points: List<Offset>,
    cache: CosmicCache,
    time: Float
) {
    val safeA = a.coerceIn(0f, 1f)
    glow(c, short * 0.35f, Color(0xFF4CC9F0), safeA * 0.4f)
    glow(c, short * 0.18f, Color(0xFFFFD166), safeA * 0.6f)

    // All 24 streams are one cached path, just rotated.
    withTransform({
        translate(c.x, c.y)
        rotate(time * 0.02f * RAD_TO_DEG, Offset.Zero)
        scale(short, short, Offset.Zero)
    }) {
        drawPath(
            path = cache.superclusterPath,
            color = Color(0xFF4CC9F0).fade(safeA * 0.25f),
            style = Stroke(width = 1.5f / short)
        )
    }

    points.take(180).forEachIndexed { i, p ->
        val flowProgress = (p.x + time * 0.05f) % 1f
        val angle = p.y * TWO_PI
        val dist = short * 0.48f * (1f - flowProgress * 0.85f)
        val pos = polar(c, angle + flowProgress, dist)
        val col = when (i % 3) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            else -> Color(0xFFF72585)
        }
        val twinkle = 0.5f + 0.5f * sin(time * 3f + i)
        drawCircle(col.fade(safeA * 0.75f * twinkle), radius = short * 0.0025f, center = pos)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 9 – Cosmic Web (nearest-neighbour filaments, mass-weighted nodes)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawCosmicWeb(c: Offset, short: Float, a: Float, cache: CosmicCache, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val s = short * 0.95f
    val breath = 0.8f + 0.2f * sin(time * 0.6f)

    fun nodePos(i: Int) = Offset(c.x + cache.webNodes[i].x * s, c.y + cache.webNodes[i].y * s)

    // Filaments: thicker/brighter where the connected nodes are more massive
    cache.webEdges.forEach { e ->
        val heavy = e.weight > 0.65f
        val col = if (heavy) Color(0xFF7B8CFF) else Color(0xFF4361EE)
        drawLine(
            color = col.fade(safeA * (0.14f + 0.24f * e.weight) * breath),
            start = nodePos(e.a),
            end = nodePos(e.b),
            strokeWidth = 1f + e.weight * 3.5f,
            cap = StrokeCap.Round
        )
    }

    // Galaxies clustered along the filaments
    withTransform({
        translate(c.x, c.y)
        scale(s, s, Offset.Zero)
    }) {
        drawPoints(
            points = cache.webDots,
            pointMode = PointMode.Points,
            color = Color.White.fade(safeA * 0.55f),
            strokeWidth = (short * 0.004f) / s,
            cap = StrokeCap.Round
        )
    }

    // Clusters at the nodes
    cache.webNodes.indices.forEach { i ->
        val mass = cache.webMass[i]
        val col = when (i % 3) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            else -> Color(0xFFF72585)
        }
        val n = nodePos(i)
        val rad = short * (0.010f + 0.016f * mass)
        glow(n, rad * 3f, col, safeA * 0.4f * breath)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.White.fade(safeA), col.fade(safeA), Color.Transparent),
                center = n,
                radius = rad
            ),
            radius = rad,
            center = n
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 10 – Observable Universe (clustered galaxies + CMB-style mottled edge)
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawObservableUniverse(c: Offset, short: Float, a: Float, cache: CosmicCache, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color.Transparent,
                Color(0xFF4361EE).fade(safeA * 0.15f),
                Color(0xFFF72585).fade(safeA * 0.25f)
            ),
            center = c,
            radius = boundary
        ),
        radius = boundary,
        center = c
    )

    // Mottled cosmic-microwave-background edge (warm / cool blobs)
    cache.cmbBlobs.forEach { blob ->
        val pos = polar(c, blob.angle, boundary * (1f + blob.jitter))
        val col = if (blob.warm) Color(0xFFFFB36B) else Color(0xFF5B8CFF)
        drawCircle(col.fade(safeA * 0.10f), radius = short * blob.size, center = pos)
    }
    drawCircle(Color(0xFFFFD166).fade(safeA * 0.3f), radius = boundary, center = c, style = Stroke(width = 2f))

    // Galaxies, batched into 12 drawPoints calls with a gentle shimmer
    withTransform({
        translate(c.x, c.y)
        scale(boundary, boundary, Offset.Zero)
    }) {
        cache.universeBuckets.forEachIndexed { bi, b ->
            if (b.pts.isEmpty()) return@forEachIndexed
            val shimmer = 0.65f + 0.35f * sin(time * 1.5f + bi * 0.9f)
            val baseAlpha = 0.55f + 0.1f * b.tier
            val diameter = short * (0.004f + 0.003f * b.tier)
            drawPoints(
                points = b.pts,
                pointMode = PointMode.Points,
                color = b.color.fade(safeA * baseAlpha * shimmer),
                strokeWidth = diameter / boundary,
                cap = StrokeCap.Round
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Level 11 – Beyond the Universe
// ─────────────────────────────────────────────────────────────────────────────

private fun DrawScope.drawBeyondUniverse(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(
                Color(0xFF7209B7).fade(safeA * 0.3f),
                Color(0xFF4361EE).fade(safeA * 0.2f),
                Color.Transparent
            ),
            center = c,
            radius = boundary * 1.2f
        ),
        radius = boundary * 1.2f,
        center = c
    )

    for (i in 1..4) {
        val ringR = boundary * (i / 4f) * (0.85f + 0.15f * sin(time * 0.4f + i))
        drawCircle(
            color = Color(0xFFFFD166).fade(safeA * (0.2f / i)),
            radius = ringR,
            center = c,
            style = Stroke(width = 1.5f)
        )
    }

    points.take(160).forEachIndexed { i, p ->
        val angle = p.x * TWO_PI + time * (0.04f + (i % 3) * 0.015f)
        val dist = boundary * (0.15f + 0.85f * p.y * (0.95f + 0.05f * sin(time * 0.8f + i)))
        val color = when (i % 4) {
            0 -> Color(0xFFFFD166)
            1 -> Color(0xFF4CC9F0)
            2 -> Color(0xFFF72585)
            else -> Color.White
        }
        val twinkle = 0.4f + 0.6f * sin(time * 2f + i)
        drawCircle(color.fade(safeA * 0.75f * twinkle), radius = short * 0.0025f, center = polar(c, angle, dist))
    }
}