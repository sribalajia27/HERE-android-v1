package com.northloom.apeiron

import android.graphics.Bitmap
import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
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
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

// ═════════════════════════════════════════════════════════════════════════════
//  Shared helpers
// ═════════════════════════════════════════════════════════════════════════════

private const val TWO_PI = 6.2831855f
private const val PI_F = 3.1415927f
private const val RAD_TO_DEG = 57.29578f
private const val DEG_TO_RAD = 0.017453292f

private fun Color.fade(alpha: Float): Color = copy(alpha = alpha.coerceIn(0f, 1f))

private fun polar(c: Offset, angle: Float, r: Float): Offset =
    Offset(c.x + cos(angle) * r, c.y + sin(angle) * r)

private fun gauss(rnd: Random): Float = rnd.nextFloat() + rnd.nextFloat() + rnd.nextFloat() - 1.5f

private fun smoothstep(e0: Float, e1: Float, x: Float): Float {
    val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

private fun rgb(r: Int, g: Int, b: Int): Int = (0xFF shl 24) or (r shl 16) or (g shl 8) or b

private fun mixRgb(c1: Int, c2: Int, t: Float): Int {
    val tt = t.coerceIn(0f, 1f)
    val r = (((c1 shr 16) and 255) * (1f - tt) + ((c2 shr 16) and 255) * tt).toInt()
    val g = (((c1 shr 8) and 255) * (1f - tt) + ((c2 shr 8) and 255) * tt).toInt()
    val b = ((c1 and 255) * (1f - tt) + (c2 and 255) * tt).toInt()
    return rgb(r, g, b)
}

// ── 3D value noise / fBm (used for procedural planets) ───────────────────────

private fun hash3(x: Int, y: Int, z: Int, seed: Int): Float {
    var h = x * 374761393 + y * 668265263 + z * 1442695041 + seed * 362437
    h = (h xor (h ushr 13)) * 1274126177
    h = h xor (h ushr 16)
    return (h and 0xFFFFFF) / 16777215f
}

private fun vnoise(x: Float, y: Float, z: Float, seed: Int): Float {
    val fx = floor(x); val fy = floor(y); val fz = floor(z)
    val xi = fx.toInt(); val yi = fy.toInt(); val zi = fz.toInt()
    val xf = x - fx; val yf = y - fy; val zf = z - fz
    val u = xf * xf * (3f - 2f * xf)
    val v = yf * yf * (3f - 2f * yf)
    val w = zf * zf * (3f - 2f * zf)
    val c000 = hash3(xi, yi, zi, seed); val c100 = hash3(xi + 1, yi, zi, seed)
    val c010 = hash3(xi, yi + 1, zi, seed); val c110 = hash3(xi + 1, yi + 1, zi, seed)
    val c001 = hash3(xi, yi, zi + 1, seed); val c101 = hash3(xi + 1, yi, zi + 1, seed)
    val c011 = hash3(xi, yi + 1, zi + 1, seed); val c111 = hash3(xi + 1, yi + 1, zi + 1, seed)
    val x00 = c000 + (c100 - c000) * u; val x10 = c010 + (c110 - c010) * u
    val x01 = c001 + (c101 - c001) * u; val x11 = c011 + (c111 - c011) * u
    val y0 = x00 + (x10 - x00) * v; val y1 = x01 + (x11 - x01) * v
    return y0 + (y1 - y0) * w
}

private fun fbm(x: Float, y: Float, z: Float, seed: Int, oct: Int = 5): Float {
    var amp = 0.5f; var f = 1f; var sum = 0f; var norm = 0f
    for (o in 0 until oct) {
        sum += amp * vnoise(x * f, y * f, z * f, seed + o * 17)
        norm += amp; amp *= 0.5f; f *= 2.03f
    }
    return sum / norm
}

/** Direction of the Sun, in view space (x right, y up, z toward viewer). Shared by Earth & Moon. */
private const val SUN_LX = -0.62f
private const val SUN_LY = 0.30f
private const val SUN_LZ = 0.72f
private val SUN_LEN = sqrt(SUN_LX * SUN_LX + SUN_LY * SUN_LY + SUN_LZ * SUN_LZ)

// Hoisted so they are NOT re-allocated every frame.
private val avatarPaint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }
private val avatarShadowPaint = Paint().apply { isAntiAlias = true; textAlign = Paint.Align.CENTER }

// ═════════════════════════════════════════════════════════════════════════════
//  PROCEDURAL EARTH
//  Real continent outlines (coarse lon/lat polygons) → fractal coastlines →
//  biomes (deserts, forest, tundra, ice, mountains) → clouds → night lights.
// ═════════════════════════════════════════════════════════════════════════════

private const val TEX_W = 640
private const val TEX_H = 320

// Coarse continent outlines, flat arrays of (longitude°, latitude°) pairs.
private val EARTH_LAND: List<FloatArray> = listOf(
    // North America
    floatArrayOf(-168f,66f, -166f,68.5f, -156f,71.3f, -141f,69.7f, -128f,70.3f, -115f,68.5f, -100f,68f, -95f,68.5f,
        -94f,64f, -93f,59f, -88f,56.5f, -82f,55f, -79f,52f, -78f,55f, -77f,60f, -78f,62.5f, -72f,62f, -70f,59f,
        -65f,60.3f, -61.5f,56.5f, -57f,53f, -56f,51.5f, -60f,50.2f, -65f,49.2f, -66f,48.5f, -64f,46.5f, -61f,45.5f,
        -66f,43.5f, -70f,43.7f, -70f,41.7f, -74f,40.5f, -75.5f,38f, -76f,35.2f, -79f,33.7f, -81f,31f, -80f,27f,
        -80.3f,25.2f, -82f,26.5f, -82.8f,29f, -85f,29.7f, -88f,30.3f, -90f,29.2f, -93f,29.7f, -97f,27.8f, -97.5f,24f,
        -97.8f,22f, -96f,19f, -94f,18.2f, -91f,18.7f, -90.5f,21f, -87f,21.5f, -87.5f,18f, -88.3f,16f, -86f,15.9f,
        -83.5f,15f, -83.5f,11f, -81.5f,8.8f, -79.5f,9.4f, -77.5f,8.7f, -77.5f,7.5f, -79f,7.2f, -81.5f,7.8f, -83.5f,8.5f,
        -85.7f,10.3f, -87.7f,13f, -91.5f,14f, -94.5f,16f, -97f,15.8f, -101f,17.5f, -105f,19.8f, -105.7f,22.5f,
        -109f,25.5f, -112f,29.5f, -114.8f,31.5f, -115f,30f, -112f,26f, -110f,23f, -112.5f,24.7f, -115f,28f, -117f,32.5f,
        -118.5f,34f, -121f,34.6f, -122.5f,37.5f, -124f,40.3f, -124.2f,43f, -124f,46.2f, -124.7f,48.4f, -127f,50.5f,
        -130f,54f, -134f,57f, -137f,58.5f, -141f,59.8f, -146f,60.7f, -150f,59.5f, -153f,58f, -157f,56.5f, -162f,55f,
        -164f,54.7f, -160f,58.3f, -162f,59.8f, -165f,61f, -165f,63f, -161f,64.5f, -166f,65.5f),
    // Greenland
    floatArrayOf(-73f,78f, -60f,82f, -30f,83.5f, -20f,81f, -18f,76f, -22f,70f, -32f,68f, -42f,60f, -48f,61f, -53f,67f, -55f,71f, -67f,76f),
    // Baffin / Victoria / Ellesmere / Newfoundland
    floatArrayOf(-80f,73f, -62f,67f, -65f,62f, -78f,64f, -90f,70f),
    floatArrayOf(-118f,71f, -104f,73f, -100f,70f, -115f,68.5f),
    floatArrayOf(-90f,77f, -62f,82f, -75f,83f, -90f,80f),
    floatArrayOf(-59f,47.6f, -53f,46.7f, -52.7f,49f, -55.5f,51.5f, -58f,49.5f),
    // Cuba, Hispaniola
    floatArrayOf(-84.9f,21.9f, -80f,23f, -76.5f,21f, -74.2f,20.2f, -77f,19.9f, -81f,21.8f),
    floatArrayOf(-74.4f,19.8f, -72.5f,19.9f, -69f,19.3f, -68.5f,18.3f, -71f,17.8f, -74.4f,18.4f),
    // South America
    floatArrayOf(-77f,8.7f, -75.5f,10.8f, -72f,12f, -71f,11f, -68f,10.5f, -64f,10.5f, -61f,10f, -58f,6.8f, -54f,5.8f,
        -51f,4.5f, -50f,1.5f, -50f,0f, -47f,-1f, -44f,-2.5f, -40f,-3f, -37f,-5f, -35f,-7f, -35f,-9.5f, -38.5f,-13f,
        -39f,-17f, -40.5f,-20.5f, -43f,-23f, -46f,-24f, -48.5f,-26f, -48.8f,-28.5f, -51f,-31f, -53.5f,-34f, -56f,-34.8f,
        -57.5f,-36f, -57f,-38f, -62f,-39f, -62.3f,-41f, -65f,-41f, -65f,-45f, -67.5f,-46.5f, -66f,-48f, -68.5f,-50f,
        -69f,-52f, -68.5f,-54f, -71f,-54f, -74f,-52f, -75f,-48f, -74f,-44f, -73.5f,-40f, -73.3f,-37f, -71.5f,-32f,
        -71.3f,-28f, -70.3f,-23f, -70.3f,-18.5f, -75f,-15.5f, -76.5f,-13f, -79f,-8f, -81f,-5.5f, -80f,-3f, -80.5f,-1f,
        -80f,0.5f, -78.8f,1.8f, -77.3f,4f, -77.5f,6.5f),
    // Africa
    floatArrayOf(-5.5f,35.8f, -2f,35f, 3f,36.8f, 10f,37.2f, 11f,35f, 10f,33.5f, 15f,32.3f, 19f,30.3f, 20f,32.5f, 23f,32.8f,
        29f,31f, 32.3f,31.2f, 34f,28f, 33f,28f, 35f,24f, 37f,20f, 39f,16f, 43f,12.5f, 44f,10.5f, 51f,12f, 51f,10.5f,
        48f,5f, 44f,1f, 41f,-2f, 39.5f,-5f, 39f,-8f, 40.5f,-11f, 40.5f,-15f, 37f,-18f, 35f,-22f, 33f,-25.5f,
        32.5f,-28.5f, 30f,-31.5f, 27f,-33.5f, 22f,-34.2f, 18.5f,-34f, 18f,-31f, 15f,-27f, 14.5f,-22f, 12f,-17.5f,
        13.5f,-12f, 12.5f,-6f, 9f,-1f, 9.5f,3.5f, 6f,4.3f, 4f,6.3f, 1f,5.7f, -2f,5f, -5f,5f, -8f,4.4f, -12f,7f,
        -13.5f,9.5f, -16.5f,12.5f, -17.5f,14.7f, -16.5f,19f, -16f,21f, -14f,26f, -10f,29f, -9.5f,32f, -6.5f,34f),
    // Madagascar
    floatArrayOf(49.5f,-12f, 50.5f,-16f, 47f,-25f, 44f,-24.5f, 43.5f,-21f, 46f,-15.5f),
    // Eurasia
    floatArrayOf(-9f,37f, -7f,37f, -5.5f,36.1f, -2f,36.8f, 0f,38.7f, 0.5f,40.5f, 3f,42f, 3.2f,43.3f, 7f,43.5f, 9f,44.3f,
        10.5f,43f, 12.3f,41.8f, 15.8f,38.2f, 17f,39f, 16.5f,40.5f, 18.5f,40.2f, 16.5f,41.8f, 14f,42.5f, 12.5f,44f,
        12.3f,45.3f, 13.7f,45.6f, 15f,44.2f, 18.5f,42.3f, 19.5f,41f, 20f,39.7f, 21f,38.5f, 22.5f,36.5f, 23.5f,37.8f,
        24f,38.1f, 23f,40f, 26f,40.8f, 26.3f,40f, 26.5f,38.5f, 28f,36.8f, 30f,36.2f, 32.5f,36.1f, 36f,36.6f, 35.8f,35f,
        35.5f,34f, 35f,33f, 34.7f,31.3f, 35f,29.5f, 35f,28f, 36f,26f, 37f,24.5f, 39f,21f, 41f,18f, 42.5f,15.5f,
        43.5f,12.7f, 45f,12.8f, 48f,14f, 52f,16f, 55f,17.2f, 57f,18.8f, 59f,22f, 58f,23.5f, 56.5f,24.5f, 56.3f,26.2f,
        54f,24.3f, 51.5f,24.3f, 51.3f,26f, 50f,26.5f, 49.5f,27f, 48.5f,28.5f, 48f,30f, 49.5f,30f, 50.8f,28.5f,
        52f,27.5f, 54.5f,26.5f, 56.2f,27f, 57f,26.5f, 58f,25.6f, 61.5f,25.2f, 66.5f,25.4f, 68f,23.5f, 70f,21f,
        72.8f,20.5f, 73f,17f, 74f,15f, 75.5f,12f, 77f,8.2f, 78.2f,8.8f, 79.8f,10.5f, 80.2f,13f, 80f,15.5f, 82f,16.7f,
        84.5f,18.5f, 86.5f,20f, 87f,21.5f, 89f,22f, 91f,22.7f, 91.8f,22.2f, 92.5f,20.5f, 94f,18.5f, 94.5f,16f,
        97.5f,16.8f, 97.7f,15.5f, 98.5f,13f, 98.6f,10f, 98.3f,8f, 99.8f,6.5f, 100.3f,5.5f, 101.5f,3.2f, 103.4f,1.4f,
        104.2f,2f, 103.4f,4.5f, 102.2f,6.3f, 101f,6.9f, 100.4f,8f, 99.2f,10f, 100f,12.5f, 100.1f,13.5f, 101f,12.7f,
        102.4f,12f, 103.5f,10.5f, 104.5f,10.5f, 105f,9f, 106.7f,10f, 108.5f,11.3f, 109.3f,12.5f, 109.3f,14f, 108f,16f,
        106.5f,18.3f, 105.8f,19.5f, 106.5f,20.7f, 108f,21.5f, 109.5f,21.5f, 110.5f,20.3f, 110.5f,21f, 113f,22f,
        114.3f,22.3f, 116.5f,23f, 118f,24.5f, 119.5f,25.8f, 120.5f,27f, 121.5f,28.5f, 122f,30f, 121f,31.3f, 120.8f,32.5f,
        120.5f,34f, 119f,35f, 120.5f,36.2f, 122.3f,37.4f, 120f,37.9f, 118.5f,38.2f, 117.7f,39f, 119.5f,39.8f,
        121.5f,40.8f, 122.2f,40.4f, 124.5f,39.8f, 125f,38.5f, 126.5f,37.5f, 126.3f,34.8f, 127.5f,34.6f, 129.2f,35.2f,
        129.5f,36.8f, 128.3f,38.5f, 127.5f,39.8f, 129.5f,41f, 130.7f,42.3f, 132f,43f, 133.5f,42.8f, 135.5f,43.5f,
        138f,46f, 140.3f,48.5f, 141.2f,52f, 140.5f,53.5f, 137.5f,54f, 135.5f,54.8f, 137f,56f, 140f,57.2f, 143f,59.2f,
        145.5f,59.4f, 149f,59.4f, 152.5f,59f, 155f,59.5f, 156.5f,57.5f, 156.8f,57f, 156f,53.5f, 156.8f,51f, 158.5f,52.5f,
        160.5f,54.5f, 162.5f,56.2f, 163.8f,58.6f, 163.3f,61.8f, 166.5f,60.4f, 170f,60f, 173.5f,61.8f, 177f,62.5f,
        179.5f,64.5f, 180f,65.5f, 180f,69f, 176.5f,69.5f, 170f,70f, 160.5f,69.5f, 155f,71f, 150f,71.5f, 142f,72.8f,
        137f,71.5f, 132f,71.2f, 129f,72.5f, 127.5f,73.5f, 120f,73.2f, 113.5f,73.7f, 112f,75.7f, 106f,77.5f, 104.5f,77.7f,
        100f,76.5f, 95f,76f, 90f,75.5f, 87f,74.2f, 81f,73.6f, 73f,72.7f, 69f,73f, 67f,70f, 69f,68.5f, 65f,69f, 60f,69.5f,
        55.5f,68.7f, 48f,67.7f, 44f,68.3f, 41f,66.8f, 40f,66.5f, 34f,66.8f, 32f,69.5f, 28f,71f, 23f,70.8f, 19.5f,70f,
        16f,68.8f, 14f,67.2f, 12.5f,65.5f, 10.5f,64f, 8f,63f, 5.2f,62f, 5f,60.5f, 5.7f,58.8f, 8f,58f, 9.5f,58.8f,
        10.8f,59.5f, 11.5f,58.5f, 12.5f,56.5f, 14.3f,55.5f, 16.5f,56.2f, 16.5f,57f, 17.5f,58.8f, 19f,60.5f, 17.5f,62.5f,
        21f,64.5f, 22f,65.5f, 24.5f,65.7f, 25f,64f, 22.8f,62.5f, 21.4f,61f, 22.5f,60f, 25f,60.2f, 28f,60.5f, 30f,60f,
        28f,59.5f, 24f,59.4f, 23.5f,58f, 24f,57f, 21f,57f, 21f,55.5f, 19f,54.5f, 15f,54f, 12f,54.2f, 10.5f,54.2f,
        10f,55.5f, 8.2f,56.5f, 8.6f,55f, 8.8f,54f, 8f,53.5f, 7f,53.5f, 5f,53.2f, 4.5f,52f, 3.5f,51.3f, 2f,51f,
        1.5f,50.5f, 0f,49.7f, -1.5f,49.7f, -1.5f,48.8f, -3.5f,48.8f, -4.6f,48.4f, -4.3f,47.8f, -2.5f,47.2f, -1.2f,46f,
        -1.3f,44.5f, -1.8f,43.4f, -4f,43.4f, -8f,43.7f, -9.3f,43f, -9f,41f, -8.9f,38.7f),
    // Great Britain, Ireland, Iceland
    floatArrayOf(-5.7f,50f, -3f,50.6f, 1.4f,51.2f, 1.7f,52.7f, 0.2f,53.4f, -0.2f,54.5f, -1.5f,55.5f, -2f,56.2f, -1.8f,57.5f,
        -3.5f,58.5f, -5f,58.6f, -6f,57.5f, -5.5f,56f, -4.8f,55f, -3.2f,54.8f, -3f,53.4f, -4.5f,53f, -4.2f,52f,
        -5.2f,51.7f, -3.2f,51.4f, -4.5f,50.8f),
    floatArrayOf(-6f,52f, -6f,54f, -8f,55.2f, -10f,54f, -10f,52f, -8f,51.6f),
    floatArrayOf(-24f,65.5f, -14f,66.2f, -14f,64.3f, -22f,63.5f),
    // Japan (Honshu/Kyushu/Shikoku, Hokkaido), Taiwan, Sri Lanka, Philippines
    floatArrayOf(130f,31f, 131f,33.5f, 133f,34f, 135f,33.5f, 137f,34.6f, 139f,35f, 140.8f,35.8f, 141f,38f, 142f,39.5f,
        141.8f,41.3f, 140f,41f, 139.8f,39.5f, 137.5f,37f, 136f,36f, 133f,35.5f, 131f,34.5f, 130f,33.5f),
    floatArrayOf(140f,42f, 141.5f,43f, 145f,43.3f, 143f,42f, 141f,41.8f),
    floatArrayOf(120.2f,23f, 121.5f,25f, 121.8f,24f, 120.8f,22f),
    floatArrayOf(80f,9.5f, 81.8f,7f, 80.5f,6f, 79.8f,7.5f),
    floatArrayOf(120f,18f, 122f,18f, 124f,13f, 126f,7f, 125f,6f, 122f,8f, 121f,13f),
    // Indonesia / New Guinea
    floatArrayOf(95f,5.5f, 98f,4f, 104f,-1f, 106f,-6f, 102f,-4f, 97f,0f),
    floatArrayOf(109f,1f, 111f,2f, 117f,7f, 119f,5f, 116f,-4f, 110f,-3f),
    floatArrayOf(105f,-6.5f, 114.5f,-7.5f, 114f,-8.5f, 106f,-7.5f),
    floatArrayOf(119f,-5f, 119.5f,0f, 121f,1f, 125f,1.5f, 123f,0.5f, 121f,-1f, 122.5f,-4f),
    floatArrayOf(131f,-1f, 141f,-2.5f, 147f,-6f, 150f,-10f, 143f,-9f, 138f,-8f, 134f,-4f),
    // Australia, Tasmania, New Zealand
    floatArrayOf(114f,-22f, 114f,-26f, 115f,-34f, 118f,-35f, 123f,-34f, 129f,-31.5f, 135f,-34.8f, 138f,-35.5f, 140f,-38f,
        144f,-38.5f, 147f,-38f, 150f,-37f, 153f,-31f, 153f,-26f, 150f,-22f, 146f,-19f, 145f,-15f, 143f,-11f, 141.5f,-13f,
        140.5f,-17.5f, 136f,-15f, 136f,-12f, 131f,-12f, 129f,-15f, 125f,-14.5f, 122f,-17f, 120f,-20f),
    floatArrayOf(145f,-41f, 148.3f,-41f, 147.5f,-43.5f, 145.5f,-43f),
    floatArrayOf(173f,-35f, 178f,-37.5f, 175f,-41.5f, 174.5f,-39f),
    floatArrayOf(172.5f,-40.5f, 174f,-41.5f, 171f,-44.5f, 167f,-46f, 168f,-44f),
    // Antarctica
    floatArrayOf(-180f,-90f, -180f,-78f, -150f,-77f, -120f,-73.5f, -100f,-73f, -80f,-73f, -72f,-70f, -67f,-66f, -62f,-64.5f,
        -58f,-63.5f, -62f,-68f, -62f,-74f, -45f,-78f, -30f,-72f, -10f,-70.5f, 10f,-70f, 30f,-69.5f, 50f,-67f, 70f,-68f,
        90f,-66.5f, 110f,-66f, 130f,-66f, 150f,-69f, 165f,-71f, 170f,-77f, 180f,-78f, 180f,-90f)
)

// Inland seas drawn as water on top of the land polygons.
private val EARTH_WATER: List<FloatArray> = listOf(
    floatArrayOf(28f,41.5f, 28f,44f, 30f,46f, 33f,46f, 36f,45.5f, 38f,47f, 41.5f,42f, 41f,41f, 36f,41.7f, 31f,41.2f), // Black Sea
    floatArrayOf(47f,45f, 50f,46.5f, 53f,45f, 53f,41f, 54f,37f, 50f,37f, 49f,40f, 47.5f,42f),                         // Caspian
    floatArrayOf(-92f,48f, -84f,48.5f, -80f,43.5f, -83f,42f, -88f,42.5f, -90f,46f)                                    // Great Lakes (blob)
)

// [lon, lat, sigmaLon, sigmaLat, strength]
private val DESERTS = listOf(
    floatArrayOf(10f, 23f, 26f, 6f, 1f),      // Sahara
    floatArrayOf(45f, 24f, 10f, 6f, 1f),      // Arabian
    floatArrayOf(103f, 42f, 13f, 4f, 0.9f),   // Gobi
    floatArrayOf(84f, 40f, 6f, 3f, 0.9f),     // Taklamakan
    floatArrayOf(71f, 27f, 5f, 3f, 0.8f),     // Thar
    floatArrayOf(134f, -25f, 13f, 7f, 1f),    // Australian outback
    floatArrayOf(22f, -23f, 7f, 5f, 0.9f),    // Kalahari
    floatArrayOf(-70f, -24f, 2f, 6f, 0.9f),   // Atacama
    floatArrayOf(-68f, -44f, 5f, 6f, 0.7f),   // Patagonia
    floatArrayOf(-113f, 35f, 7f, 6f, 0.8f),   // Sonoran / Great Basin
    floatArrayOf(55f, 32f, 7f, 4f, 0.8f),     // Iranian plateau
    floatArrayOf(45f, 5f, 4f, 4f, 0.6f)       // Horn of Africa
)

private val MOUNTAINS = listOf(
    floatArrayOf(88f, 33f, 14f, 4f, 1f),      // Himalaya / Tibet
    floatArrayOf(-112f, 45f, 5f, 14f, 0.8f),  // Rockies
    floatArrayOf(-70f, -22f, 3f, 28f, 1f),    // Andes
    floatArrayOf(10f, 46f, 6f, 2.5f, 0.8f),   // Alps
    floatArrayOf(44f, 42f, 5f, 1.5f, 0.8f),   // Caucasus
    floatArrayOf(60f, 58f, 2f, 9f, 0.6f),     // Urals
    floatArrayOf(38f, 9f, 4f, 4f, 0.7f),      // Ethiopian highlands
    floatArrayOf(-80f, 38f, 3f, 7f, 0.5f),    // Appalachians
    floatArrayOf(13f, 64f, 3f, 6f, 0.6f),     // Scandes
    floatArrayOf(-3f, 32f, 6f, 2f, 0.6f),     // Atlas
    floatArrayOf(148f, -30f, 2f, 12f, 0.4f),  // Great Dividing Range
    floatArrayOf(51f, 32f, 3f, 4f, 0.7f),     // Zagros
    floatArrayOf(80f, 42f, 8f, 2f, 0.8f),     // Tian Shan
    floatArrayOf(90f, 48f, 6f, 2f, 0.7f)      // Altai
)

// [lon, lat, sigmaDeg, strength] – major urban agglomerations (night lights)
private val CITIES = listOf(
    floatArrayOf(-74f,41f,3.5f,1f), floatArrayOf(-87.6f,41.9f,2.2f,.8f), floatArrayOf(-97f,32f,3f,.55f),
    floatArrayOf(-118.2f,34f,2.2f,.8f), floatArrayOf(-81f,28f,2f,.5f), floatArrayOf(-99f,19.4f,2.2f,.8f),
    floatArrayOf(-46.6f,-23.5f,2.5f,.9f), floatArrayOf(-58.4f,-34.6f,2.5f,.8f), floatArrayOf(-74f,4.7f,2f,.5f),
    floatArrayOf(-77f,-12f,1.8f,.5f), floatArrayOf(-0.1f,51.5f,2.5f,1f), floatArrayOf(4f,50f,3.5f,1f),
    floatArrayOf(8f,50.5f,3f,.9f), floatArrayOf(12f,42f,3f,.7f), floatArrayOf(-3.7f,40.4f,2.5f,.6f),
    floatArrayOf(37.6f,55.7f,2.5f,.7f), floatArrayOf(29f,41f,1.8f,.7f), floatArrayOf(31f,30f,2.5f,.8f),
    floatArrayOf(3.4f,6.5f,2f,.7f), floatArrayOf(28f,-26f,2f,.6f), floatArrayOf(36.8f,-1.3f,1.5f,.4f),
    floatArrayOf(51.4f,35.7f,2f,.6f), floatArrayOf(50f,26f,2.5f,.7f), floatArrayOf(77f,28.6f,3.5f,1f),
    floatArrayOf(72.8f,19f,2.2f,.8f), floatArrayOf(88.4f,23.5f,3f,.9f), floatArrayOf(68f,25f,2f,.5f),
    floatArrayOf(74.3f,31.5f,2f,.7f), floatArrayOf(100.5f,13.8f,2.5f,.7f), floatArrayOf(106.8f,-6.2f,2.5f,.9f),
    floatArrayOf(121f,14.6f,2f,.7f), floatArrayOf(116.4f,39.9f,3.5f,1f), floatArrayOf(121.5f,31.2f,3.5f,1f),
    floatArrayOf(113.3f,23.1f,2.5f,1f), floatArrayOf(127f,37.5f,2f,.9f), floatArrayOf(139.7f,35.7f,2.5f,1f),
    floatArrayOf(135.5f,34.7f,1.8f,.8f), floatArrayOf(151f,-33.9f,2f,.6f), floatArrayOf(145f,-37.8f,1.8f,.5f),
    floatArrayOf(105.8f,21f,2f,.6f), floatArrayOf(104f,30.7f,2f,.6f), floatArrayOf(114.3f,30.6f,2f,.7f)
)

private class EarthData(val surf: IntArray, val clouds: ByteArray, val lights: FloatArray)

private fun rasterizePolys(land: List<FloatArray>, water: List<FloatArray>): FloatArray {
    val bmp = Bitmap.createBitmap(TEX_W, TEX_H, Bitmap.Config.ARGB_8888)
    val cv = android.graphics.Canvas(bmp)
    cv.drawColor(0xFF000000.toInt())
    val paint = Paint().apply { isAntiAlias = true; style = Paint.Style.FILL }
    fun poly(p: FloatArray, color: Int) {
        val path = android.graphics.Path()
        var i = 0
        while (i < p.size) {
            val x = (p[i] + 180f) / 360f * TEX_W
            val y = (90f - p[i + 1]) / 180f * TEX_H
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            i += 2
        }
        path.close()
        paint.color = color
        cv.drawPath(path, paint)
    }
    land.forEach { poly(it, 0xFFFFFFFF.toInt()) }
    water.forEach { poly(it, 0xFF000000.toInt()) }
    val px = IntArray(TEX_W * TEX_H)
    bmp.getPixels(px, 0, TEX_W, 0, 0, TEX_W, TEX_H)
    bmp.recycle()
    return FloatArray(px.size) { (px[it] and 0xFF) / 255f }
}

private fun boxBlur(src: FloatArray, w: Int, h: Int, rad: Int): FloatArray {
    val tmp = FloatArray(src.size)
    val out = FloatArray(src.size)
    val n = (2 * rad + 1).toFloat()
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0f
        for (k in -rad..rad) { var xx = (x + k) % w; if (xx < 0) xx += w; s += src[y * w + xx] }
        tmp[y * w + x] = s / n
    }
    for (y in 0 until h) for (x in 0 until w) {
        var s = 0f
        for (k in -rad..rad) s += tmp[(y + k).coerceIn(0, h - 1) * w + x]
        out[y * w + x] = s / n
    }
    return out
}

private fun buildEarthData(): EarthData {
    val mask = rasterizePolys(EARTH_LAND, EARTH_WATER)
    val shore = boxBlur(mask, TEX_W, TEX_H, 7)
    val surf = IntArray(TEX_W * TEX_H)
    val clouds = ByteArray(TEX_W * TEX_H)
    val lights = FloatArray(TEX_W * TEX_H)

    val deepSea = rgb(10, 36, 88); val shallowSea = rgb(36, 118, 168)
    val tropical = rgb(30, 100, 45); val temperate = rgb(72, 122, 56)
    val boreal = rgb(44, 80, 60); val tundra = rgb(150, 150, 125)
    val desertCol = rgb(206, 176, 116); val rockCol = rgb(118, 104, 90)
    val iceCol = rgb(236, 243, 250)

    for (j in 0 until TEX_H) {
        val lat = (0.5f - (j + 0.5f) / TEX_H) * PI_F
        val latDeg = lat * RAD_TO_DEG
        val aLat = abs(latDeg)
        val cl = cos(lat); val sl = sin(lat)
        for (i in 0 until TEX_W) {
            val lon = ((i + 0.5f) / TEX_W - 0.5f) * TWO_PI
            val lonDeg = lon * RAD_TO_DEG
            val px = cl * sin(lon); val py = sl; val pz = cl * cos(lon)

            // Domain-warp the coastline so it has a fractal, natural edge.
            val wx = (fbm(px * 3f, py * 3f, pz * 3f, 1, 4) - 0.5f) * 9f
            val wy = (fbm(px * 3f + 9f, py * 3f, pz * 3f, 2, 4) - 0.5f) * 7f
            var si = (i + wx.toInt()) % TEX_W; if (si < 0) si += TEX_W
            val sj = (j + wy.toInt()).coerceIn(0, TEX_H - 1)
            val fine = fbm(px * 10f, py * 10f, pz * 10f, 3, 3)
            val mv = mask[sj * TEX_W + si] + (fine - 0.5f) * 0.28f
            val shoreV = shore[sj * TEX_W + si]
            val idx = j * TEX_W + i
            val n1 = fbm(px * 4f, py * 4f, pz * 4f, 31, 4)
            val isLand = mv > 0.5f

            // ── clouds (all texels) ─────────────────────────────────────────
            val cn = fbm(px * 2.4f + 3f, py * 2.4f, pz * 2.4f, 77, 5)
            val itcz = exp(-(latDeg / 10f) * (latDeg / 10f))
            val storm = exp(-((aLat - 50f) / 12f) * ((aLat - 50f) / 12f))
            val subtrop = exp(-((aLat - 25f) / 8f) * ((aLat - 25f) / 8f))
            val thr = 0.50f - 0.10f * (itcz + storm) + 0.08f * subtrop
            clouds[idx] = (smoothstep(thr, thr + 0.14f, cn) * 235f).toInt().coerceIn(0, 255).toByte()

            if (!isLand) {
                // Ocean: deeper away from the coast; sea ice near the poles.
                val shallow = smoothstep(0.0f, 0.38f, shoreV)
                var col = mixRgb(deepSea, shallowSea, shallow * 0.85f + (n1 - 0.5f) * 0.1f)
                val ice = max(smoothstep(75f, 83f, latDeg + (n1 - 0.5f) * 8f), smoothstep(63f, 70f, -latDeg + (n1 - 0.5f) * 8f))
                col = mixRgb(col, iceCol, ice)
                surf[idx] = col and 0x00FFFFFF
            } else {
                // Land biomes
                val n2 = fbm(px * 9f, py * 9f, pz * 9f, 41, 3)
                var des = 0f
                for (b in DESERTS) {
                    val dx = (lonDeg - b[0]) / b[2]; val dy = (latDeg - b[1]) / b[3]
                    des = max(des, b[4] * exp(-(dx * dx + dy * dy)))
                }
                des = smoothstep(0.35f, 0.7f, des + (n1 - 0.5f) * 0.5f)
                var veg = mixRgb(tropical, temperate, smoothstep(14f, 36f, aLat))
                veg = mixRgb(veg, boreal, smoothstep(46f, 58f, aLat))
                veg = mixRgb(veg, tundra, smoothstep(62f, 72f, aLat))
                veg = mixRgb(veg, rgb(90, 140, 70), (n2 - 0.5f) * 0.6f + 0.1f)
                var col = mixRgb(veg, mixRgb(desertCol, rgb(190, 150, 100), n2), des)

                var mt = 0f
                for (b in MOUNTAINS) {
                    val dx = (lonDeg - b[0]) / b[2]; val dy = (latDeg - b[1]) / b[3]
                    mt = max(mt, b[4] * exp(-(dx * dx + dy * dy)))
                }
                mt = smoothstep(0.3f, 0.8f, mt + (n2 - 0.5f) * 0.4f)
                col = mixRgb(col, rockCol, mt * 0.75f)
                col = mixRgb(col, iceCol, smoothstep(0.82f, 1f, mt) * 0.55f)

                val greenland = lonDeg > -75f && lonDeg < -12f && latDeg > 60f
                val antarctic = latDeg < -66f
                val polar = smoothstep(72f, 80f, aLat + (n1 - 0.5f) * 6f) * 0.7f
                val ice = if (greenland || antarctic) 0.95f else polar
                col = mixRgb(col, iceCol, ice)
                surf[idx] = col or (0xFF shl 24)
            }
        }
    }

    // City lights, splatted on land only.
    for (c in CITIES) {
        val sigma = c[2]
        val i0 = ((c[0] + 180f) / 360f * TEX_W).toInt()
        val j0 = ((90f - c[1]) / 180f * TEX_H).toInt()
        val rt = (sigma * 3f / 0.5625f).toInt() + 1
        for (dj in -rt..rt) for (di in -rt..rt) {
            val j = j0 + dj; if (j < 0 || j >= TEX_H) continue
            var i = (i0 + di) % TEX_W; if (i < 0) i += TEX_W
            if (mask[j * TEX_W + i] < 0.5f) continue
            val dxd = di * 0.5625f; val dyd = dj * 0.5625f
            val v = c[3] * exp(-(dxd * dxd + dyd * dyd) / (sigma * sigma))
            val speck = 0.35f + 0.65f * vnoise(i * 0.9f, j * 0.9f, 5f, 9)
            lights[j * TEX_W + i] = max(lights[j * TEX_W + i], (v * speck * 1.4f).coerceIn(0f, 1f))
        }
    }
    return EarthData(surf, clouds, lights)
}

/**
 * Orthographic globe renderer. Pre-computes everything that doesn't change and only
 * re-samples the textures (spin + cloud drift) when the rotation advances by a texel.
 */
private class EarthSphere(private val data: EarthData, private val size: Int) {
    private val bitmaps = arrayOf(
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888),
        Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    )
    private val images = arrayOf(bitmaps[0].asImageBitmap(), bitmaps[1].asImageBitmap())
    private var cur = 0
    var image: ImageBitmap = images[0]
        private set

    private val n = size * size
    private val pix = IntArray(n)
    private val row = IntArray(n)
    private val lonCol = FloatArray(n)
    private val shade = FloatArray(n)
    private val spec = FloatArray(n)
    private val rim = FloatArray(n)
    private val edgeA = IntArray(n)
    private var lastKey = Long.MIN_VALUE

    init {
        val tilt = 23.4f * DEG_TO_RAD            // Earth's axial tilt
        val ct = cos(tilt); val st = sin(tilt)
        val lx = SUN_LX / SUN_LEN; val ly = SUN_LY / SUN_LEN; val lz = SUN_LZ / SUN_LEN
        // Blinn-Phong half vector for the ocean sun-glint (viewer at +z)
        val hx0 = lx; val hy0 = ly; val hz0 = lz + 1f
        val hl = sqrt(hx0 * hx0 + hy0 * hy0 + hz0 * hz0)
        val hx = hx0 / hl; val hy = hy0 / hl; val hz = hz0 / hl
        for (py in 0 until size) for (px in 0 until size) {
            val i = py * size + px
            val x = (px + 0.5f) / size * 2f - 1f
            val y = -((py + 0.5f) / size * 2f - 1f)
            val d2 = x * x + y * y
            if (d2 >= 1f) { row[i] = -1; continue }
            val z = sqrt(1f - d2)
            val xr = x * ct - y * st
            val yr = x * st + y * ct
            val lat = asin(yr.coerceIn(-1f, 1f))
            val lon = atan2(xr, z)
            row[i] = ((0.5f - lat / PI_F) * TEX_H).toInt().coerceIn(0, TEX_H - 1)
            lonCol[i] = lon / TWO_PI * TEX_W + TEX_W * 2f
            shade[i] = x * lx + y * ly + z * lz
            val nh = max(0f, x * hx + y * hy + z * hz)
            spec[i] = nh.pow(90f)
            rim[i] = (1f - z).pow(2.6f)
            edgeA[i] = (((1f - sqrt(d2)) * (size / 2f)).coerceIn(0f, 1f) * 255f).toInt()
        }
    }

    fun update(spin: Float, cloudSpin: Float, cityLights: Boolean) {
        val sc = ((spin / TWO_PI) * TEX_W).toInt()
        val cc = ((cloudSpin / TWO_PI) * TEX_W).toInt()
        val key = (sc.toLong() shl 33) xor (cc.toLong() shl 1) xor (if (cityLights) 1L else 0L)
        if (key == lastKey) return
        lastKey = key
        val scm = (((sc % TEX_W) + TEX_W) % TEX_W).toFloat()
        val ccm = (((cc % TEX_W) + TEX_W) % TEX_W).toFloat()
        val surf = data.surf; val clouds = data.clouds; val lights = data.lights
        for (i in 0 until n) {
            val rw = row[i]
            if (rw < 0) { pix[i] = 0; continue }
            val col = ((lonCol[i] - scm).toInt()) % TEX_W
            val ccol = ((lonCol[i] - ccm).toInt()) % TEX_W
            val t = rw * TEX_W + col
            val base = surf[t]
            val br = ((base shr 16) and 255).toFloat()
            val bg = ((base shr 8) and 255).toFloat()
            val bb = (base and 255).toFloat()
            val cloud = ((clouds[rw * TEX_W + ccol].toInt() and 255) / 255f)
            val nl = shade[i]
            val day = smoothstep(-0.08f, 0.20f, nl)
            val lit = if (nl > 0f) nl * (1.25f - 0.25f * nl) else 0f
            val k = 0.022f + lit * 0.97f
            var r = br * k; var g = bg * k; var b = bb * k
            if ((base ushr 24) == 0) {                       // ocean sun-glint
                val sp = spec[i] * (1f - cloud) * 230f * day
                r += sp; g += sp; b += sp
            }
            val cl = cloud * 0.92f                             // clouds (white, lit by the sun)
            val cw = 250f * k
            r = r * (1f - cl) + cw * cl; g = g * (1f - cl) + cw * cl; b = b * (1f - cl) + cw * cl
            val rm = rim[i] * (0.10f + 0.90f * day)            // Rayleigh-scattering limb haze
            r += 70f * rm; g += 125f * rm; b += 255f * rm
            if (cityLights) {
                val l = lights[t] * (1f - day) * (1f - cloud * 0.7f)
                r += 255f * l; g += 205f * l; b += 120f * l
            }
            val ri = r.toInt().coerceIn(0, 255); val gi = g.toInt().coerceIn(0, 255); val bi = b.toInt().coerceIn(0, 255)
            pix[i] = (edgeA[i] shl 24) or (ri shl 16) or (gi shl 8) or bi
        }
        val next = 1 - cur
        bitmaps[next].setPixels(pix, 0, size, 0, 0, size, size)
        cur = next
        image = images[next]
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  PROCEDURAL MOON (near side, tidally locked)
//  Real maria positions, power-law crater population, ray systems (Tycho,
//  Copernicus, Kepler, Aristarchus), Lommel-Seeliger shading, earthshine.
// ═════════════════════════════════════════════════════════════════════════════

private const val MOON_W = 1024
private const val MOON_H = 512

// [lon, lat, radiusDeg, strength]  (east longitude positive)
private val MARIA = listOf(
    floatArrayOf(-16f, 33f, 19f, 1f),    // Imbrium
    floatArrayOf(18f, 28f, 12f, 1f),     // Serenitatis
    floatArrayOf(31f, 8f, 14f, 0.95f),   // Tranquillitatis
    floatArrayOf(59f, 17f, 7f, 1f),      // Crisium
    floatArrayOf(52f, -5f, 10f, 0.85f),  // Fecunditatis
    floatArrayOf(34f, -15f, 6f, 0.9f),   // Nectaris
    floatArrayOf(-15f, -21f, 11f, 0.9f), // Nubium
    floatArrayOf(-39f, -24f, 6.5f, 1f),  // Humorum
    floatArrayOf(-57f, 15f, 26f, 0.85f), // Oceanus Procellarum
    floatArrayOf(0f, 58f, 10f, 0.7f),    // Frigoris
    floatArrayOf(3f, 13f, 6f, 0.8f),     // Vaporum
    floatArrayOf(-23f, -10f, 6f, 0.8f),  // Cognitum
    floatArrayOf(-30f, 8f, 9f, 0.8f),    // Insularum
    floatArrayOf(-68f, -6f, 3.5f, 0.9f)  // Grimaldi basin
)

// Named craters: [lon, lat, radiusDeg, youth(0..1), hasRays]
private val NAMED_CRATERS = listOf(
    floatArrayOf(-11.4f, -43.3f, 1.5f, 1f, 1f),   // Tycho
    floatArrayOf(-20.1f, 9.7f, 0.95f, 0.9f, 1f),  // Copernicus
    floatArrayOf(-38f, 8.1f, 0.55f, 0.9f, 1f),    // Kepler
    floatArrayOf(-47.5f, 23.7f, 0.5f, 1f, 1f),    // Aristarchus
    floatArrayOf(-9.4f, 51.6f, 1.6f, 0.1f, 0f),   // Plato
    floatArrayOf(-14f, -58.8f, 3.3f, 0.1f, 0f),   // Clavius
    floatArrayOf(60.7f, -8.9f, 1.5f, 0.5f, 0f),   // Langrenus
    floatArrayOf(26.4f, -11.4f, 1.0f, 0.5f, 0f),  // Theophilus
    floatArrayOf(-4f, 29.7f, 0.8f, 0.3f, 0f),     // Archimedes
    floatArrayOf(-2f, -49f, 1.2f, 0.4f, 0f)       // Maginus-ish
)

private const val MLX = -0.9f
private const val MLY = 0.44f

private fun applyCrater(lum: FloatArray, lonDeg: Float, latDeg: Float, rDeg: Float, young: Float, relief: Boolean) {
    val infl = rDeg * 2.3f
    val latC = latDeg * DEG_TO_RAD; val lonC = lonDeg * DEG_TO_RAD
    val cosLatC = cos(latC); val sinLatC = sin(latC)
    val j0 = (((90f - (latDeg + infl)) / 180f) * MOON_H).toInt().coerceIn(0, MOON_H - 1)
    val j1 = (((90f - (latDeg - infl)) / 180f) * MOON_H).toInt().coerceIn(0, MOON_H - 1)
    val wide = abs(latDeg) + infl > 80f
    val halfW = if (wide) MOON_W / 2 else ((infl / max(cosLatC, 0.05f)) / 360f * MOON_W).toInt() + 1
    val iC = ((lonDeg + 180f) / 360f * MOON_W).toInt()
    for (j in j0..j1) {
        val lat = (0.5f - (j + 0.5f) / MOON_H) * PI_F
        val cl = cos(lat); val sl = sin(lat)
        for (di in -halfW..halfW) {
            var i = (iC + di) % MOON_W; if (i < 0) i += MOON_W
            val lon = ((i + 0.5f) / MOON_W - 0.5f) * TWO_PI
            var dl = lon - lonC
            if (dl > PI_F) dl -= TWO_PI else if (dl < -PI_F) dl += TWO_PI
            val cosd = sl * sinLatC + cl * cosLatC * cos(dl)
            val d = acos(cosd.coerceIn(-1f, 1f)) * RAD_TO_DEG
            val t = d / rDeg
            if (t > 2.3f) continue
            var delta = 0f
            if (t < 0.95f) delta += 0.05f * young - 0.045f * (1f - young)
            val rw = (t - 1.03f) / 0.1f
            delta += 0.11f * young * exp(-rw * rw)
            if (t > 1.1f) delta += 0.10f * young * max(0f, 1f - (t - 1.1f) / 1.2f)
            if (relief) {
                val dx = dl * cosLatC * RAD_TO_DEG
                val dy = (lat - latC) * RAD_TO_DEG
                val s = (dx * MLX + dy * MLY) / (sqrt(dx * dx + dy * dy) + 1e-4f)
                val a1 = (t - 0.78f) / 0.18f; val a2 = (t - 1.12f) / 0.12f
                // wall facing away from the Sun is dark, wall facing the Sun is bright
                delta += -s * 0.20f * exp(-a1 * a1) + s * 0.10f * exp(-a2 * a2)
            }
            lum[j * MOON_W + i] += delta
        }
    }
}

private fun applyRays(lum: FloatArray, lonDeg: Float, latDeg: Float, rDeg: Float, young: Float, seed: Int) {
    val reach = 22f + rDeg * 8f
    val latC = latDeg * DEG_TO_RAD; val lonC = lonDeg * DEG_TO_RAD
    val cosLatC = cos(latC); val sinLatC = sin(latC)
    val j0 = (((90f - (latDeg + reach)) / 180f) * MOON_H).toInt().coerceIn(0, MOON_H - 1)
    val j1 = (((90f - (latDeg - reach)) / 180f) * MOON_H).toInt().coerceIn(0, MOON_H - 1)
    val halfW = ((reach / max(cosLatC, 0.2f)) / 360f * MOON_W).toInt() + 1
    val iC = ((lonDeg + 180f) / 360f * MOON_W).toInt()
    for (j in j0..j1) {
        val lat = (0.5f - (j + 0.5f) / MOON_H) * PI_F
        val cl = cos(lat); val sl = sin(lat)
        for (di in -halfW..halfW) {
            var i = (iC + di) % MOON_W; if (i < 0) i += MOON_W
            val lon = ((i + 0.5f) / MOON_W - 0.5f) * TWO_PI
            val dl = lon - lonC
            val cosd = sl * sinLatC + cl * cosLatC * cos(dl)
            val d = acos(cosd.coerceIn(-1f, 1f)) * RAD_TO_DEG
            if (d < rDeg * 1.3f || d > reach) continue
            val ang = atan2((lat - latC), dl * cosLatC)
            val ray = (0.5f + 0.5f * sin(ang * 13f + 3.2f * sin(ang * 5f + seed))).pow(7f)
            val fall = exp(-d / (reach * 0.45f))
            lum[j * MOON_W + i] += 0.24f * young * ray * fall
        }
    }
}

private fun buildMoonImage(size: Int): ImageBitmap {
    val n = MOON_W * MOON_H
    val lum = FloatArray(n)
    val mare = FloatArray(n)
    // 1) base albedo + maria
    for (j in 0 until MOON_H) {
        val lat = (0.5f - (j + 0.5f) / MOON_H) * PI_F
        val cl = cos(lat); val sl = sin(lat)
        for (i in 0 until MOON_W) {
            val lon = ((i + 0.5f) / MOON_W - 0.5f) * TWO_PI
            val px = cl * sin(lon); val py = sl; val pz = cl * cos(lon)
            val nA = fbm(px * 3f, py * 3f, pz * 3f, 5, 5)
            val nB = fbm(px * 28f, py * 28f, pz * 28f, 8, 3)
            lum[j * MOON_W + i] = 0.72f + (nA - 0.5f) * 0.24f + (nB - 0.5f) * 0.12f
            var m = 0f
            for (mr in MARIA) {
                val mlat = mr[1] * DEG_TO_RAD; val mlon = mr[0] * DEG_TO_RAD
                val cosd = sl * sin(mlat) + cl * cos(mlat) * cos(lon - mlon)
                if (cosd < cos(mr[2] * 1.7f * DEG_TO_RAD)) continue
                val d = acos(cosd.coerceIn(-1f, 1f)) * RAD_TO_DEG
                val amt = mr[3] * smoothstep(0f, 0.3f, 1f - d / mr[2] + (nA - 0.5f) * 0.55f)
                if (amt > m) m = amt
            }
            mare[j * MOON_W + i] = m
        }
    }
    // 2) crater population (power-law size distribution)
    val rnd = Random(99)
    repeat(2300) {
        val rMin = 0.45f; val rMax = 6f
        val u = rnd.nextFloat()
        val r = rMin / sqrt(1f - u * (1f - (rMin / rMax) * (rMin / rMax)))
        val lat = asin(rnd.nextFloat() * 2f - 1f) * RAD_TO_DEG
        val lon = rnd.nextFloat() * 360f - 180f
        val young = rnd.nextFloat().pow(2f)
        val ji = ((90f - lat) / 180f * MOON_H).toInt().coerceIn(0, MOON_H - 1)
        val ii = ((lon + 180f) / 360f * MOON_W).toInt().coerceIn(0, MOON_W - 1)
        val inMare = mare[ji * MOON_W + ii] > 0.5f
        if (!(inMare && r > 2f)) applyCrater(lum, lon, lat, r, young, r >= 0.7f)
    }
    NAMED_CRATERS.forEachIndexed { k, c ->
        applyCrater(lum, c[0], c[1], c[2], c[3], true)
        if (c[4] > 0.5f) applyRays(lum, c[0], c[1], c[2], c[3], k * 7)
    }
    // 3) render the sphere once (near side facing us, lit by the shared Sun direction)
    val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val out = IntArray(size * size)
    val lx = SUN_LX / SUN_LEN; val ly = SUN_LY / SUN_LEN; val lz = SUN_LZ / SUN_LEN
    val hl = rgb(206, 201, 192); val mr = rgb(122, 126, 134)
    for (py in 0 until size) for (px in 0 until size) {
        val x = (px + 0.5f) / size * 2f - 1f
        val y = -((py + 0.5f) / size * 2f - 1f)
        val d2 = x * x + y * y
        if (d2 >= 1f) continue
        val z = sqrt(1f - d2)
        val lat = asin(y.coerceIn(-1f, 1f)); val lon = atan2(x, z)
        val ti = (((lon / TWO_PI + 0.5f) * MOON_W).toInt()).coerceIn(0, MOON_W - 1)
        val tj = (((0.5f - lat / PI_F) * MOON_H).toInt()).coerceIn(0, MOON_H - 1)
        val l = lum[tj * MOON_W + ti].coerceIn(0.1f, 1.1f)
        val m = mare[tj * MOON_W + ti]
        val albedo = mixRgb(hl, mr, m * 0.85f)
        val f = (l / 0.72f) * (1f - 0.18f * m)
        // Lommel-Seeliger: no limb darkening at full phase, like the real Moon
        val mu0 = x * lx + y * ly + z * lz
        val illum = if (mu0 > 0f) (1.9f * mu0 / (mu0 + z + 1e-3f)).coerceAtMost(1f) else 0f
        val earthshine = 0.035f
        val k = (illum + earthshine) * f
        val r = (((albedo shr 16) and 255) * k * (if (illum > 0.02f) 1f else 0.8f)).toInt().coerceIn(0, 255)
        val g = (((albedo shr 8) and 255) * k * (if (illum > 0.02f) 1f else 0.9f)).toInt().coerceIn(0, 255)
        val b = ((albedo and 255) * k * (if (illum > 0.02f) 1f else 1.15f)).toInt().coerceIn(0, 255)
        val a = (((1f - sqrt(d2)) * (size / 2f)).coerceIn(0f, 1f) * 255f).toInt()
        out[py * size + px] = (a shl 24) or (r shl 16) or (g shl 8) or b
    }
    bmp.setPixels(out, 0, size, 0, 0, size, size)
    return bmp.asImageBitmap()
}

// ═════════════════════════════════════════════════════════════════════════════
//  Pre-computed geometry for the cosmic-scale levels
// ═════════════════════════════════════════════════════════════════════════════

private class PointBucket(val color: Color, val tier: Int, val pts: MutableList<Offset> = mutableListOf())
private class WebEdge(val a: Int, val b: Int, val weight: Float)
private class CmbBlob(val angle: Float, val jitter: Float, val size: Float, val warm: Boolean)

// Galaxies are mostly warm-white (old stars) with some blue (young) – no magenta galaxies.
private val UNIVERSE_COLORS = listOf(Color(0xFFFFE9B0), Color.White, Color(0xFF9BC4FF), Color(0xFFFFD3A0))

// Milky Way geometry (unit space, disc radius 0.48): barred spiral, trailing logarithmic arms
private const val GAL_ARM_R = 0.48f
private const val GAL_B = 0.22f        // pitch (≈12.5°, like the Milky Way)
private const val GAL_R0 = 0.22f       // arms start at the ends of the bar
private const val GAL_BAR = 0.45f      // bar orientation
private const val GAL_TILT = 0.58f
private const val GAL_ROT = -0.5f

private class CosmicCache {
    val galaxyBuckets: List<PointBucket>
    val galaxyDust: PointBucket
    val universeBuckets: List<PointBucket> = buildUniverse(Random(23))
    val cmbBlobs: List<CmbBlob> = buildCmb(Random(5))
    val webNodes: List<Offset>
    val webMass: List<Float>
    val webEdges: List<WebEdge>
    val webDots: List<Offset>

    init {
        val g = buildGalaxy(Random(11))
        galaxyBuckets = g.first
        galaxyDust = g.second
        val rnd = Random(7)
        webNodes = List(36) { Offset(rnd.nextFloat() - 0.5f, rnd.nextFloat() - 0.5f) }
        webMass = List(36) { 0.3f + rnd.nextFloat() * 0.7f }
        webEdges = buildWebEdges(webNodes, webMass)
        webDots = buildWebDots(webNodes, webEdges, rnd)
    }
}

/** Trailing log-spiral: the arm winds back (counter-clockwise outward) against a clockwise rotation. */
private fun spiralPoint(t: Float, arm: Int): Offset {
    val theta = GAL_BAR + arm * (TWO_PI / 4f) - ln(t / GAL_R0) / GAL_B
    return polar(Offset.Zero, theta, t * GAL_ARM_R)
}

private fun buildGalaxy(rnd: Random): Pair<List<PointBucket>, PointBucket> {
    val colors = listOf(Color(0xFF8FD8FF), Color(0xFFF2709C), Color.White, Color(0xFFFFE2A0)) // blue, HII pink, white, old/warm
    val buckets = List(12) { PointBucket(colors[it / 3], it % 3) }
    val dust = PointBucket(Color(0xFF140B08), 1)
    fun add(color: Int, pos: Offset) {
        val t = pos.getDistance() / GAL_ARM_R
        buckets[color * 3 + min(2, (t * 3f).toInt())].pts.add(pos)
    }
    // spiral arms (2 strong, 2 weak)
    repeat(1150) {
        val t = GAL_R0 + (1f - GAL_R0) * rnd.nextFloat().pow(1.15f)
        val arm = if (rnd.nextFloat() < 0.7f) rnd.nextInt(2) * 2 else 1 + rnd.nextInt(2) * 2
        val base = spiralPoint(t, arm)
        val ang = atan2(base.y, base.x)
        val rad = (t * GAL_ARM_R + gauss(rnd) * 0.03f * (0.4f + t)).coerceAtLeast(0.02f)
        val col = if (t < 0.4f && rnd.nextFloat() < 0.3f) 3 else {
            val r = rnd.nextFloat(); if (r < 0.55f) 0 else if (r < 0.63f) 1 else 2
        }
        add(col, polar(Offset.Zero, ang, rad))
        if (rnd.nextFloat() < 0.3f) dust.pts.add(polar(Offset.Zero, ang, (rad - 0.014f * (0.4f + t)).coerceAtLeast(0.02f)))
    }
    // diffuse disc
    repeat(400) { add(if (rnd.nextBoolean()) 3 else 2, polar(Offset.Zero, rnd.nextFloat() * TWO_PI, GAL_ARM_R * rnd.nextFloat().pow(0.8f))) }
    // bulge
    repeat(120) { add(3, Offset(gauss(rnd) * 0.05f, gauss(rnd) * 0.05f)) }
    // central bar (elongated, warm)
    val cb = cos(GAL_BAR); val sb = sin(GAL_BAR)
    repeat(130) {
        val bx = gauss(rnd) * 0.09f; val by = gauss(rnd) * 0.02f
        add(3, Offset(bx * cb - by * sb, bx * sb + by * cb))
    }
    return buckets to dust
}

private fun buildCmb(r: Random): List<CmbBlob> =
    List(150) { CmbBlob(r.nextFloat() * TWO_PI, gauss(r) * 0.02f, 0.012f + r.nextFloat() * 0.02f, r.nextBoolean()) }

private fun buildUniverse(rnd: Random): List<PointBucket> {
    val centers = List(30) { polar(Offset.Zero, rnd.nextFloat() * TWO_PI, sqrt(rnd.nextFloat()) * 0.9f) }
    val buckets = List(12) { PointBucket(UNIVERSE_COLORS[it % 4], it / 4) }
    repeat(1300) {
        val p = if (rnd.nextFloat() < 0.8f) {
            val c = centers[rnd.nextInt(centers.size)]
            Offset(c.x + gauss(rnd) * 0.06f, c.y + gauss(rnd) * 0.06f)
        } else polar(Offset.Zero, rnd.nextFloat() * TWO_PI, sqrt(rnd.nextFloat()) * 0.95f)
        if (p.getDistance() <= 0.97f) buckets[rnd.nextInt(12)].pts.add(p)
    }
    return buckets
}

private fun buildWebEdges(nodes: List<Offset>, mass: List<Float>): List<WebEdge> {
    val seen = HashSet<Int>()
    val edges = mutableListOf<WebEdge>()
    for (i in nodes.indices) {
        nodes.indices.filter { it != i }.sortedBy { (nodes[it] - nodes[i]).getDistance() }.take(3).forEach { j ->
            if ((nodes[j] - nodes[i]).getDistance() < 0.4f) {
                val lo = minOf(i, j); val hi = maxOf(i, j)
                if (seen.add(lo * 1000 + hi)) edges.add(WebEdge(lo, hi, (mass[lo] + mass[hi]) / 2f))
            }
        }
    }
    return edges
}

private fun buildWebDots(nodes: List<Offset>, edges: List<WebEdge>, rnd: Random): List<Offset> {
    val dots = mutableListOf<Offset>()
    edges.forEach { e ->
        val p = nodes[e.a]; val q = nodes[e.b]
        val dx = q.x - p.x; val dy = q.y - p.y
        val len = sqrt(dx * dx + dy * dy).coerceAtLeast(0.001f)
        val nx = -dy / len; val ny = dx / len
        repeat(4 + (e.weight * 6f).toInt()) {
            val f = rnd.nextFloat(); val off = gauss(rnd) * 0.012f
            dots.add(Offset(p.x + dx * f + nx * off, p.y + dy * f + ny * off))
        }
    }
    return dots
}

// ═════════════════════════════════════════════════════════════════════════════
//  Entry point
// ═════════════════════════════════════════════════════════════════════════════

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
        val f = Random(42)
        List(1200) { Offset(f.nextFloat(), f.nextFloat()) }
    }
    val cache = remember { CosmicCache() }

    // Heavy procedural textures are generated off the main thread, once.
    val earth by produceState<EarthSphere?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { EarthSphere(buildEarthData(), 320) }
    }
    val moon by produceState<ImageBitmap?>(initialValue = null) {
        value = withContext(Dispatchers.Default) { buildMoonImage(512) }
    }
    val earthNow = earth
    val moonNow = moon

    Canvas(modifier = modifier.fillMaxSize()) {
        if (current.index == next.index) {
            drawScene(current.index, 1f, points, cache, time, showCityLights, earthNow, moonNow, userAvatar)
        } else {
            val f = fraction.coerceIn(0f, 1f)
            scale(scale = 1.15f - 0.15f * f, pivot = center) {
                drawScene(next.index, f, points, cache, time, showCityLights, earthNow, moonNow, userAvatar)
            }
            scale(scale = 1f - 0.15f * f, pivot = center) {
                drawScene(current.index, (1f - f).coerceIn(0f, 1f), points, cache, time, showCityLights, earthNow, moonNow, userAvatar)
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
    earth: EarthSphere?,
    moon: ImageBitmap?,
    userAvatar: String?
) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if (safeAlpha <= 0f) return
    val c = Offset(size.width / 2f, size.height / 2f)
    val short = min(size.width, size.height)

    when (levelIndex) {
        0 -> drawYou(c, short, safeAlpha, time, userAvatar)
        1 -> drawSurroundings(c, short, safeAlpha, time)
        2 -> drawEarth(c, short, safeAlpha, time, showCityLights, earth, points)
        3 -> drawMoon(c, short, safeAlpha, time, moon, points)
        4 -> drawSun(c, short, safeAlpha, time)
        5 -> drawSolarSystem(c, short, safeAlpha, time, points)
        6 -> drawGalaxy(c, short, safeAlpha, cache, time)
        7 -> drawLocalGroup(c, short, safeAlpha, points, time)
        8 -> drawSupercluster(c, short, safeAlpha, points, time)
        9 -> drawCosmicWeb(c, short, safeAlpha, cache, time)
        10 -> drawObservableUniverse(c, short, safeAlpha, cache, time)
        11 -> drawBeyondUniverse(c, short, safeAlpha, points, time)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Primitives
// ═════════════════════════════════════════════════════════════════════════════

private fun DrawScope.glow(center: Offset, radius: Float, color: Color, alpha: Float) {
    val safeAlpha = alpha.coerceIn(0f, 1f)
    if ((safeAlpha <= 0f) || (radius <= 0f)) return
    drawCircle(
        brush = Brush.radialGradient(listOf(color.fade(safeAlpha), Color.Transparent), center = center, radius = radius),
        radius = radius, center = center
    )
}

/** Draws a pre-rendered square bitmap so its disc has radius [radius]; float transform = no jitter. */
private fun DrawScope.drawSphereImage(image: ImageBitmap, center: Offset, radius: Float, alpha: Float) {
    val k = (radius * 2f) / image.width
    withTransform({
        translate(center.x - radius, center.y - radius)
        scale(k, k, pivot = Offset.Zero)
    }) {
        drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset.Zero,
            dstSize = IntSize(image.width, image.height),
            alpha = alpha.coerceIn(0f, 1f),
            filterQuality = FilterQuality.High
        )
    }
}

/** Static background stars (stars don't twinkle in space – twinkling is an atmospheric effect). */
private fun DrawScope.drawStaticStars(points: List<Offset>, a: Float, short: Float) {
    points.take(220).forEachIndexed { i, p ->
        val bright = 0.25f + 0.6f * ((i * 37) % 10) / 10f
        drawCircle(
            Color.White.fade(a * bright),
            radius = short * (0.0007f + 0.0009f * ((i * 13) % 3)),
            center = Offset(p.x * size.width, p.y * size.height)
        )
    }
}

private val labelPaint = Paint().apply {
    isAntiAlias = true
    textAlign = Paint.Align.CENTER
    typeface = android.graphics.Typeface.create("sans-serif-light", android.graphics.Typeface.NORMAL)
    letterSpacing = 0.1f
}

private fun DrawScope.drawLabel(text: String, pos: Offset, size: Float, color: Color, alpha: Float) {
    drawIntoCanvas { cv ->
        labelPaint.textSize = size
        labelPaint.color = color.toArgb()
        labelPaint.alpha = (alpha * 255f).toInt().coerceIn(0, 255)
        cv.nativeCanvas.drawText(text, pos.x, pos.y, labelPaint)
    }
}

/** Elegant instrument-style framing: hairline ring, tick scale, dotted ring, sweeping arcs, orbiting bead. */
private fun DrawScope.elegantRings(c: Offset, r: Float, a: Float, time: Float, tint: Color) {
    drawCircle(tint.fade(a * 0.35f), radius = r * 1.22f, center = c, style = Stroke(width = 1f))
    drawCircle(tint.fade(a * 0.20f), radius = r * 1.42f, center = c,
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f, 10f))))
    for (i in 0 until 90) {
        val ang = i * TWO_PI / 90f
        val long = i % 10 == 0
        val r0 = r * 1.22f
        val r1 = r0 + r * (if (long) 0.07f else 0.035f)
        drawLine(tint.fade(a * (if (long) 0.55f else 0.25f)), polar(c, ang, r0), polar(c, ang, r1), strokeWidth = if (long) 1.4f else 1f)
    }
    val sweep = time * 20f
    val tl = Offset(c.x - r * 1.31f, c.y - r * 1.31f)
    val sz = Size(r * 2.62f, r * 2.62f)
    drawArc(tint.fade(a * 0.75f), sweep, 50f, false, tl, sz, style = Stroke(width = 2f, cap = StrokeCap.Round))
    drawArc(tint.fade(a * 0.40f), sweep + 180f, 25f, false, tl, sz, style = Stroke(width = 2f, cap = StrokeCap.Round))
    val bead = polar(c, time * 0.5f, r * 1.31f)
    glow(bead, r * 0.07f, tint, a * 0.9f)
    drawCircle(Color.White.fade(a), radius = r * 0.012f, center = bead)
}

private fun DrawScope.tinyGalaxy(pos: Offset, rad: Float, a: Float) {
    galaxyBlob(pos, rad * 1.8f, 0.6f, 0.5f, Color(0xFF8FB4FF), Color(0xFFFFE2A0), a)
    withTransform({ translate(pos.x, pos.y); rotate(0.5f * RAD_TO_DEG, Offset.Zero); scale(1f, 0.6f, Offset.Zero) }) {
        for (arm in 0..1) {
            val path = Path()
            for (i in 0..14) {
                val t = i / 14f
                val th = arm * PI_F + t * 3.4f
                val rr = rad * (0.12f + 0.88f * t)
                if (i == 0) path.moveTo(cos(th) * rr, sin(th) * rr) else path.lineTo(cos(th) * rr, sin(th) * rr)
            }
            drawPath(path, Color.White.fade(a * 0.85f), style = Stroke(width = 1.4f, cap = StrokeCap.Round))
        }
        drawCircle(Color(0xFFFFF0C0).fade(a), radius = rad * 0.2f, center = Offset.Zero)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 0 – You
// ═════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawYou(c: Offset, short: Float, a: Float, time: Float, userAvatar: String?) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.09f
    glow(c, r * 3.2f, Color(0xFF4CC9F0), safeA * 0.22f)
    glow(c, r * 1.9f, Color(0xFF4CC9F0), safeA * 0.28f)
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.White.fade(safeA * 0.10f), Color.White.fade(safeA * 0.02f)), center = c, radius = r * 1.15f
        ),
        radius = r * 1.15f, center = c
    )
    drawCircle(
        brush = Brush.sweepGradient(
            listOf(
                Color.White.fade(safeA * 0.85f), Color(0xFF4CC9F0).fade(safeA * 0.15f), Color.Black.fade(safeA * 0.05f),
                Color(0xFF4CC9F0).fade(safeA * 0.15f), Color.White.fade(safeA * 0.85f)
            ),
            center = c
        ),
        radius = r * 1.15f, center = c, style = Stroke(width = 1.6f)
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
            addOval(Rect(Offset(c.x, c.y - r * 0.5f), r * 0.26f))
            moveTo(c.x - r * 0.34f, c.y + r * 0.62f)
            cubicTo(c.x - r * 0.4f, c.y + r * 0.05f, c.x - r * 0.3f, c.y - r * 0.12f, c.x, c.y - r * 0.14f)
            cubicTo(c.x + r * 0.3f, c.y - r * 0.12f, c.x + r * 0.4f, c.y + r * 0.05f, c.x + r * 0.34f, c.y + r * 0.62f)
            close()
        }
        drawPath(
            figure,
            brush = Brush.verticalGradient(
                listOf(Color.White.fade(safeA * 0.95f), Color(0xFF9FB4C7).fade(safeA * 0.75f)), startY = c.y - r, endY = c.y + r
            )
        )
        drawPath(figure, color = Color(0xFF4CC9F0).fade(safeA * 0.35f), style = Stroke(width = 1.2f))
    }
    for (i in 0..4) {
        val pos = polar(c, i * 1.257f + time * 0.3f, r * 1.5f)
        val twinkle = 0.55f + 0.45f * sin(time * 1.5f + i * 1.3f)
        drawCircle(Color(0xFF4CC9F0).fade(safeA * 0.6f * twinkle), radius = short * 0.0016f, center = pos)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 1 – Your surroundings (procedural landscape, sun upper-left → shadows fall right)
// ═════════════════════════════════════════════════════════════════════════════

private class TreeSpec(val x: Float, val base: Float, val h: Float)

private val TREES = listOf(          // sorted far → near so nearer trees overlap farther ones
    TreeSpec(0.72f, 0.69f, 0.12f), TreeSpec(0.20f, 0.74f, 0.20f), TreeSpec(0.88f, 0.73f, 0.20f),
    TreeSpec(0.10f, 0.82f, 0.28f), TreeSpec(0.82f, 0.80f, 0.30f)
)

private fun DrawScope.drawSurroundings(c: Offset, short: Float, a: Float, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.2f
    val d = r * 2f
    val left = c.x - r; val top = c.y - r
    fun fx(f: Float) = left + f * d
    fun fy(f: Float) = top + f * d

    glow(c, r * 1.7f, Color(0xFF6FB1FF), safeA * 0.22f)

    clipPath(Path().apply { addOval(Rect(c, r)) }) {
        // Sky: deep blue overhead, paler toward the horizon (Rayleigh scattering)
        drawRect(
            brush = Brush.verticalGradient(
                0f to Color(0xFF2B63C4).fade(safeA), 0.5f to Color(0xFF6FAEE6).fade(safeA), 0.66f to Color(0xFFCFE8F6).fade(safeA),
                startY = top, endY = top + d
            ),
            topLeft = Offset(left, top), size = Size(d, d)
        )
        // Sun
        val sunC = Offset(fx(0.27f), fy(0.2f))
        glow(sunC, d * 0.38f, Color(0xFFFFF2C0), safeA * 0.55f)
        drawCircle(Color(0xFFFFFBE0).fade(safeA), radius = d * 0.035f, center = sunC)

        // Drifting clouds, shaded on the side away from the Sun
        fun cloud(cxf: Float, cyf: Float, s: Float) {
            val puffs = listOf(Offset(0f, 0f) to 1f, Offset(0.9f, 0.15f) to 0.8f, Offset(-0.9f, 0.2f) to 0.75f, Offset(0.25f, -0.35f) to 0.7f)
            puffs.forEach { (o, k) ->
                drawCircle(Color(0xFFB8CCE6).fade(safeA * 0.7f), radius = d * s * k, center = Offset(fx(cxf) + o.x * d * s + d * s * 0.2f, fy(cyf) + o.y * d * s + d * s * 0.25f))
            }
            puffs.forEach { (o, k) ->
                drawCircle(Color.White.fade(safeA * 0.92f), radius = d * s * k, center = Offset(fx(cxf) + o.x * d * s, fy(cyf) + o.y * d * s))
            }
        }
        cloud(((0.62f + time * 0.004f) % 1.4f) - 0.2f, 0.28f, 0.035f)
        cloud(((0.15f + time * 0.003f) % 1.4f) - 0.2f, 0.40f, 0.028f)
        cloud(((0.95f + time * 0.005f) % 1.4f) - 0.2f, 0.17f, 0.022f)

        // Far mountains (hazy blue = aerial perspective) with snow caps
        val far = Path().apply {
            moveTo(left, fy(0.66f))
            for (k in 0..24) {
                val f = k / 24f
                val h = 0.11f * (0.5f + 0.5f * sin(f * 7.3f + 1f)) + 0.05f * (0.5f + 0.5f * sin(f * 17f))
                lineTo(fx(f), fy(0.62f - h))
            }
            lineTo(left + d, fy(0.66f)); close()
        }
        drawPath(far, Color(0xFF6C84AD).fade(safeA))
        for (k in 0..24) {
            val f = k / 24f
            val h = 0.11f * (0.5f + 0.5f * sin(f * 7.3f + 1f)) + 0.05f * (0.5f + 0.5f * sin(f * 17f))
            if (h > 0.135f) drawCircle(Color.White.fade(safeA * 0.85f), radius = d * 0.012f, center = Offset(fx(f), fy(0.62f - h + 0.012f)))
        }
        // Mid hills
        val mid = Path().apply {
            moveTo(left, fy(0.72f))
            for (k in 0..24) {
                val f = k / 24f
                lineTo(fx(f), fy(0.64f - 0.04f * sin(f * 5f + 2f) - 0.02f * sin(f * 13f)))
            }
            lineTo(left + d, fy(0.72f)); close()
        }
        drawPath(mid, Color(0xFF5D8F72).fade(safeA))
        // Near meadow
        drawRect(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF58A24F).fade(safeA), Color(0xFF2F6F3A).fade(safeA)), startY = fy(0.66f), endY = top + d
            ),
            topLeft = Offset(left, fy(0.66f)), size = Size(d, d * 0.34f)
        )
        // River (reflects the sky)
        val river = Path().apply {
            moveTo(fx(0.53f), fy(0.655f))
            cubicTo(fx(0.48f), fy(0.8f), fx(0.30f), fy(0.88f), fx(0.2f), fy(1.02f))
            lineTo(fx(0.46f), fy(1.02f))
            cubicTo(fx(0.60f), fy(0.85f), fx(0.58f), fy(0.74f), fx(0.57f), fy(0.655f))
            close()
        }
        drawPath(river, Color(0xFF6BB2E0).fade(safeA * 0.95f))
        // House: sunlit left wall, shaded right wall
        val hx = fx(0.38f); val hy = fy(0.70f); val hw = d * 0.10f; val hh = d * 0.06f
        drawRect(Color(0xFFF2E6D0).fade(safeA), Offset(hx - hw / 2f, hy - hh), Size(hw * 0.6f, hh))
        drawRect(Color(0xFFC9BCA2).fade(safeA), Offset(hx - hw / 2f + hw * 0.6f, hy - hh), Size(hw * 0.4f, hh))
        drawPath(Path().apply {
            moveTo(hx - hw * 0.58f, hy - hh); lineTo(hx, hy - hh - d * 0.045f); lineTo(hx + hw * 0.58f, hy - hh); close()
        }, Color(0xFF9E3B2E).fade(safeA))
        drawRect(Color(0xFF5B3A1E).fade(safeA), Offset(hx - hw * 0.1f, hy - hh * 0.55f), Size(hw * 0.14f, hh * 0.55f))
        // Trees: shadow to the right, highlight to the upper-left
        TREES.forEach { t ->
            val x = fx(t.x); val base = fy(t.base); val h = d * t.h
            drawOval(Color.Black.fade(safeA * 0.28f), Offset(x + h * 0.05f, base - h * 0.04f), Size(h * 0.75f, h * 0.12f))
            drawRect(Color(0xFF5B3A1E).fade(safeA), Offset(x - h * 0.035f, base - h * 0.32f), Size(h * 0.07f, h * 0.32f))
            drawCircle(Color(0xFF2E7D32).fade(safeA), radius = h * 0.30f, center = Offset(x, base - h * 0.60f))
            drawCircle(Color(0xFF1B5E20).fade(safeA * 0.8f), radius = h * 0.20f, center = Offset(x + h * 0.10f, base - h * 0.54f))
            drawCircle(Color(0xFF66BB6A).fade(safeA * 0.85f), radius = h * 0.17f, center = Offset(x - h * 0.10f, base - h * 0.68f))
        }
        // Birds
        for (i in 0..2) {
            val bx = fx(((0.2f + time * 0.01f + i * 0.09f) % 1.2f) - 0.1f)
            val by = fy(0.25f + 0.04f * sin(time * 2f + i) + i * 0.03f)
            val wing = d * 0.012f; val flap = d * 0.010f * sin(time * 8f + i)
            drawLine(Color(0xFF263238).fade(safeA * 0.8f), Offset(bx - wing, by - flap), Offset(bx, by), strokeWidth = 1.5f)
            drawLine(Color(0xFF263238).fade(safeA * 0.8f), Offset(bx, by), Offset(bx + wing, by - flap), strokeWidth = 1.5f)
        }
        // Soft edge vignette (reads as a window onto the scene)
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color.Transparent, 0.78f to Color.Transparent, 1f to Color.Black.fade(safeA * 0.35f), center = c, radius = r
            ),
            radius = r, center = c
        )
    }
    drawCircle(Color.White.fade(safeA * 0.5f), radius = r * 1.01f, center = c, style = Stroke(width = 2f))
    elegantRings(c, r, safeA, time, Color(0xFFBFE3FF))
    drawCircle(Color(0xFFFFD166).fade(safeA * 0.22f), radius = r * 1.62f, center = c,
        style = Stroke(width = 1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 12f))))
    for (i in 0 until 6) {
        val p = polar(c, i * (TWO_PI / 6f) + time * 0.12f, r * 1.62f)
        val tw = 0.5f + 0.5f * sin(time * 2f + i * 1.7f)
        val l = r * 0.05f * (0.6f + 0.4f * tw)
        val col = Color(0xFFFFE9B0).fade(safeA * 0.8f * tw)
        drawLine(col, Offset(p.x - l, p.y), Offset(p.x + l, p.y), strokeWidth = 1.2f)
        drawLine(col, Offset(p.x, p.y - l), Offset(p.x, p.y + l), strokeWidth = 1.2f)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 2 – Earth (procedural)
// ═════════════════════════════════════════════════════════════════════════════

private class Satellite(val inc: Float, val node: Float, val phase: Float, val alt: Float)

// Low-Earth-orbit satellites: altitude 400–1200 km ⇒ radius ≈ 1.06–1.19 Earth radii.
private val SATELLITES = List(14) {
    val r = Random(500 + it)
    Satellite(r.nextFloat() * PI_F, r.nextFloat() * TWO_PI, r.nextFloat() * TWO_PI, 1.07f + r.nextFloat() * 0.10f)
}

private fun DrawScope.drawEarth(
    c: Offset, short: Float, a: Float, time: Float, cityLights: Boolean, earth: EarthSphere?, points: List<Offset>
) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.22f
    drawStaticStars(points, safeA, short)

    elegantRings(c, r, safeA, time, Color(0xFF6FB6FF))

    // Atmosphere is only ~100 km thick: a thin blue halo, brightest on the sunlit side.
    val sunAng = atan2(-SUN_LY, SUN_LX) * RAD_TO_DEG
    listOf(0.012f to 0.30f, 0.030f to 0.14f, 0.060f to 0.07f).forEach { (wFrac, alpha) ->
        drawArc(
            color = Color(0xFF6FB6FF).fade(safeA * alpha), startAngle = sunAng - 110f, sweepAngle = 220f, useCenter = false,
            topLeft = Offset(c.x - r * 1.01f, c.y - r * 1.01f), size = Size(r * 2.02f, r * 2.02f),
            style = Stroke(width = r * wFrac, cap = StrokeCap.Round)
        )
    }

    if (earth == null) {
        drawCircle(Color(0xFF123A73).fade(safeA), radius = r, center = c)   // while textures generate
    } else {
        // Surface rotates west→east (~15°/hr, sped up); clouds drift slightly faster (winds).
        earth.update(time * 0.16f, time * 0.185f, cityLights)
        drawSphereImage(earth.image, c, r, safeA)
    }

    // Satellites on inclined orbits; hidden when behind the planet.
    SATELLITES.forEach { s ->
        val ph = s.phase + time * 0.5f * s.alt.pow(-1.5f) * 0.3f
        val ux = cos(s.node); val uy = sin(s.node)
        val x = s.alt * (cos(ph) * ux - sin(ph) * cos(s.inc) * uy)
        val y = s.alt * (cos(ph) * uy + sin(ph) * cos(s.inc) * ux)
        val z = s.alt * sin(ph) * sin(s.inc)
        val behind = z < 0f && (x * x + y * y) < 1f
        if (!behind) drawCircle(Color.White.fade(safeA * 0.9f), radius = short * 0.0013f, center = Offset(c.x + x * r, c.y - y * r))
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 3 – Moon (procedural). No atmosphere → no halo. Tidally locked → no spin.
// ═════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawMoon(c: Offset, short: Float, a: Float, time: Float, moon: ImageBitmap?, points: List<Offset>) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.22f
    drawStaticStars(points, safeA, short)
    glow(c, r * 1.6f, Color(0xFFCFD8E6), safeA * 0.10f)
    elegantRings(c, r, safeA, time, Color(0xFFCFD8E6))
    if (moon == null) {
        drawCircle(Color(0xFF8A8A8A).fade(safeA * 0.6f), radius = r, center = c)
    } else {
        drawSphereImage(moon, c, r, safeA)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 4 – Sun
// ═════════════════════════════════════════════════════════════════════════════

private class Sunspot(val lon: Float, val lat: Float, val size: Float)

private val SUNSPOTS = listOf(
    Sunspot(0.4f, 0.25f, 0.050f), Sunspot(0.58f, 0.22f, 0.032f), Sunspot(3.3f, -0.25f, 0.055f),
    Sunspot(3.5f, -0.20f, 0.028f), Sunspot(5.2f, 0.30f, 0.038f)
)

private fun DrawScope.drawSun(c: Offset, short: Float, a: Float, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val r = short * 0.16f
    val pulse = 1f + 0.015f * sin(time * 1.5f)
    elegantRings(c, r * 1.45f, safeA, time, Color(0xFFFFC878))

    // Corona: pearly white-yellow (the Sun is white; the orange look is atmospheric)
    glow(c, r * 3.4f * pulse, Color(0xFFFFB45A), safeA * 0.14f)
    glow(c, r * 2.3f * pulse, Color(0xFFFFD98A), safeA * 0.28f)
    glow(c, r * 1.5f * pulse, Color(0xFFFFF0C0), safeA * 0.55f)

    // Prominences glow pink-red (hydrogen-alpha emission)
    for (i in 0 until 6) {
        val baseAngle = i * (TWO_PI / 6f) + time * 0.02f
        val peakR = r * (1.3f + 0.18f * sin(time * 1.0f + i * 1.7f))
        val p1 = polar(c, baseAngle - 0.18f, r * 0.98f)
        val p3 = polar(c, baseAngle + 0.18f, r * 0.98f)
        val p2 = polar(c, baseAngle, peakR * 1.12f)
        val arc = Path().apply { moveTo(p1.x, p1.y); quadraticBezierTo(p2.x, p2.y, p3.x, p3.y) }
        drawPath(arc, Color(0xFFFF4A3A).fade(safeA * 0.6f), style = Stroke(width = short * 0.004f, cap = StrokeCap.Round))
    }

    // Disc with limb darkening: white-yellow centre → redder, dimmer edge (G2V star, ~5,800 K)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFFFF3).fade(safeA), 0.55f to Color(0xFFFFF1B8).fade(safeA), 0.85f to Color(0xFFFFC14D).fade(safeA),
            0.97f to Color(0xFFFF8A1F).fade(safeA), 1f to Color(0xFFE8590C).fade(safeA), center = c, radius = r
        ),
        radius = r, center = c
    )

    // Soft anamorphic lens streak and faint ghost reflections
    withTransform({ translate(c.x, c.y); scale(1f, 0.05f, Offset.Zero) }) {
        drawCircle(
            brush = Brush.radialGradient(listOf(Color(0xFFFFE9B0).fade(safeA * 0.45f), Color.Transparent), center = Offset.Zero, radius = r * 3.6f),
            radius = r * 3.6f, center = Offset.Zero
        )
    }
    listOf(-0.9f to 0.10f, 0.7f to 0.07f, 1.5f to 0.05f).forEach { (k, rr) ->
        drawCircle(Color(0xFFFFD98A).fade(safeA * 0.18f), radius = r * rr * 2f, center = Offset(c.x + k * r * 1.1f, c.y + k * r * 0.45f), style = Stroke(width = 1.2f))
    }

    // Sunspots: cooler (darker) regions at ±5–30° latitude; carried across the disc by rotation
    SUNSPOTS.forEach { s ->
        val lon = s.lon + time * 0.1f
        val depth = cos(s.lat) * cos(lon)
        if (depth > 0.05f) {
            val x = c.x + r * cos(s.lat) * sin(lon)
            val y = c.y - r * sin(s.lat)
            val w = s.size * r * depth; val h = s.size * r
            val vis = safeA * smoothstep(0.05f, 0.35f, depth)
            drawOval(Color(0xFF8A4B14).fade(vis * 0.8f), Offset(x - w * 1.6f, y - h * 1.6f), Size(w * 3.2f, h * 3.2f))
            drawOval(Color(0xFF2B1608).fade(vis * 0.95f), Offset(x - w, y - h), Size(w * 2f, h * 2f))
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 5 – Solar System (log-scaled distances, real planet order, Kepler periods)
// ═════════════════════════════════════════════════════════════════════════════

/** Distances are LOG-scaled (otherwise Mercury would sit on the Sun and Neptune off-screen). */
private fun auToFrac(au: Float): Float = 0.07f + (log10(au) + 0.409f) * 0.1561f

private class Planet(val au: Float, val size: Float, val color: Color, val phase: Float, val rings: Boolean = false)

// Radius ∝ (true radius)^0.5 so Jupiter isn't absurdly large next to Mercury.
private val PLANETS = listOf(
    Planet(0.387f, 0.0040f, Color(0xFF9A9A9A), 0.5f),                  // Mercury
    Planet(0.723f, 0.0063f, Color(0xFFE8D9A8), 2.1f),                  // Venus
    Planet(1.000f, 0.0065f, Color(0xFF3E7FD0), 4.0f),                  // Earth
    Planet(1.524f, 0.0047f, Color(0xFFC5603A), 1.2f),                  // Mars
    Planet(5.203f, 0.0218f, Color(0xFFD2A679), 3.3f),                  // Jupiter
    Planet(9.582f, 0.0200f, Color(0xFFE3CF9A), 5.4f, rings = true),    // Saturn
    Planet(19.20f, 0.0130f, Color(0xFF9FE0E6), 0.2f),                  // Uranus
    Planet(30.05f, 0.0128f, Color(0xFF3F5FD1), 2.8f)                   // Neptune
)

private fun DrawScope.drawSolarSystem(c: Offset, short: Float, a: Float, time: Float, points: List<Offset>) {
    val safeA = a.coerceIn(0f, 1f)
    drawOval(
        brush = Brush.radialGradient(listOf(Color(0xFF1E1B4B).fade(safeA * 0.7f), Color.Transparent), center = c, radius = short * 0.5f),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f), size = Size(short, short * 0.8f)
    )

    // Orbit rings
    PLANETS.forEach {
        drawCircle(Color(0xFFFFB703).fade(safeA * 0.13f), radius = short * auToFrac(it.au), center = c, style = Stroke(width = 1f))
    }
    // Asteroid belt (2.1–3.3 AU) and Kuiper belt (32–50 AU)
    val b0 = auToFrac(2.1f); val b1 = auToFrac(3.3f)
    points.take(160).forEach { p ->
        drawCircle(Color(0xFFB8C4D8).fade(safeA * 0.4f), radius = short * 0.0012f, center = polar(c, p.y * TWO_PI - time * 0.02f, short * (b0 + p.x * (b1 - b0))))
    }
    val k0 = auToFrac(32f); val k1 = auToFrac(50f)
    points.drop(200).take(220).forEach { p ->
        drawCircle(Color(0xFF9FB4C7).fade(safeA * 0.25f), radius = short * 0.001f, center = polar(c, p.y * TWO_PI - time * 0.004f, short * (k0 + p.x * (k1 - k0))))
    }
    // Heliopause (~120 AU): where the solar wind gives way to interstellar space
    drawCircle(
        Color(0xFF4CC9F0).fade(safeA * 0.30f), radius = short * auToFrac(120f), center = c,
        style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 10f)))
    )

    drawLabel("Heliopause · edge of the Sun's reach (~120 AU)", polar(c, 0.9f, short * auToFrac(120f) + short * 0.03f), short * 0.014f, Color(0xFF4CC9F0), safeA * 0.8f)

    // Sun
    val sunR = short * 0.03f
    glow(c, short * 0.12f, Color(0xFFFFB703), safeA * 0.55f)
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0xFFFFFFF3).fade(safeA), 0.7f to Color(0xFFFFE08A).fade(safeA), 1f to Color(0xFFFF9A1F).fade(safeA), center = c, radius = sunR
        ),
        radius = sunR, center = c
    )

    // Planets: all orbit counter-clockwise (prograde) with Kepler's 3rd law, ω ∝ a^-1.5
    PLANETS.forEach { p ->
        val ang = p.phase - time * 0.22f * p.au.pow(-0.6f)
        val pos = polar(c, ang, short * auToFrac(p.au))
        val rad = short * p.size
        glow(pos, rad * 3f, p.color, safeA * 0.28f)
        if (p.au == 1.0f) drawLabel("Earth", Offset(pos.x, pos.y + rad + short * 0.028f), short * 0.017f, Color.White, safeA * 0.85f)
        val ringTL = Offset(pos.x - rad * 2.1f, pos.y - rad * 0.7f)
        val ringSz = Size(rad * 4.2f, rad * 1.4f)
        if (p.rings) drawOval(Color(0xFFFDE68A).fade(safeA * 0.7f), ringTL, ringSz, style = Stroke(width = rad * 0.35f))
        val toSun = c - pos
        val len = toSun.getDistance().coerceAtLeast(1f)
        val hl = Offset(pos.x + toSun.x / len * rad * 0.45f, pos.y + toSun.y / len * rad * 0.45f)
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(p.color, Color.White, 0.5f).fade(safeA), 0.55f to p.color.fade(safeA),
                1f to lerp(p.color, Color.Black, 0.65f).fade(safeA), center = hl, radius = rad * 1.6f
            ),
            radius = rad, center = pos
        )
        if (p.rings) {
            drawArc(Color(0xFFFDE68A).fade(safeA * 0.7f), 0f, 180f, false, ringTL, ringSz, style = Stroke(width = rad * 0.35f))
        }
    }

    // Voyager 1 & 2: human-made probes now in interstellar space, beyond the heliopause
    listOf(Triple(4.0f, 165f, "Voyager 1"), Triple(2.4f, 140f, "Voyager 2")).forEach { (bearing, au, name) ->
        val pos = polar(c, bearing, short * auToFrac(au))
        val start = polar(c, bearing, short * auToFrac(30f))
        val ping = 0.55f + 0.45f * sin(time * 2.5f + bearing)
        drawLine(Color(0xFF4CC9F0).fade(safeA * 0.35f), start, pos, strokeWidth = 1.2f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(4f, 6f)))
        glow(pos, short * 0.018f, Color(0xFF4CC9F0), safeA * 0.8f * ping)
        drawCircle(Color.White.fade(safeA), radius = short * 0.0038f, center = pos)
        drawCircle(Color(0xFF4CC9F0).fade(safeA * 0.9f), radius = short * 0.010f, center = pos, style = Stroke(width = 1.2f))
        val ah = short * 0.012f
        val tip = polar(c, bearing, short * auToFrac(au) + short * 0.026f)
        drawLine(Color(0xFF4CC9F0).fade(safeA * 0.9f), tip, polar(tip, bearing + 2.6f, ah), strokeWidth = 1.4f)
        drawLine(Color(0xFF4CC9F0).fade(safeA * 0.9f), tip, polar(tip, bearing - 2.6f, ah), strokeWidth = 1.4f)
        val lp = polar(c, bearing, short * auToFrac(au) - short * 0.045f)
        drawLabel(name, lp, short * 0.018f, Color.White, safeA * 0.95f)
        drawLabel("${au.toInt()} AU · interstellar space", Offset(lp.x, lp.y + short * 0.022f), short * 0.013f, Color(0xFF9FDFF5), safeA * 0.8f)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 6 – Milky Way (barred spiral, trailing arms, clockwise rotation, dust lanes)
// ═════════════════════════════════════════════════════════════════════════════

private fun galaxyToScreen(p: Offset, spin: Float, c: Offset, short: Float): Offset {
    val cs = cos(spin); val sn = sin(spin)
    val x1 = p.x * cs - p.y * sn
    val y1 = (p.x * sn + p.y * cs) * GAL_TILT
    val cr = cos(GAL_ROT); val sr = sin(GAL_ROT)
    return Offset(c.x + (x1 * cr - y1 * sr) * short, c.y + (x1 * sr + y1 * cr) * short)
}

private fun DrawScope.drawGalaxy(c: Offset, short: Float, a: Float, cache: CosmicCache, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val spin = time * 0.04f    // clockwise as seen from the north galactic pole

    withTransform({
        translate(c.x, c.y)
        rotate(GAL_ROT * RAD_TO_DEG, Offset.Zero)
        scale(short, short * GAL_TILT, Offset.Zero)
        rotate(spin * RAD_TO_DEG, Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(0f to Color(0xFF5B6CFF).fade(safeA * 0.16f), 1f to Color.Transparent, center = Offset.Zero, radius = GAL_ARM_R),
            radius = GAL_ARM_R, center = Offset.Zero
        )
        cache.galaxyBuckets.forEach { b ->
            if (b.pts.isEmpty()) return@forEach
            val tierT = (b.tier + 0.5f) / 3f
            drawPoints(
                points = b.pts, pointMode = PointMode.Points,
                color = b.color.fade(safeA * (0.2f + 0.8f * (1f - tierT))),
                strokeWidth = short * (0.0015f + tierT * 0.002f) * 2f / short, cap = StrokeCap.Round
            )
        }
        // Dust lanes on the inner edge of the arms
        drawPoints(
            points = cache.galaxyDust.pts, pointMode = PointMode.Points,
            color = cache.galaxyDust.color.fade(safeA * 0.38f), strokeWidth = 0.012f, cap = StrokeCap.Round
        )
        // Warm central bulge
        drawCircle(
            brush = Brush.radialGradient(
                0f to Color(0xFFFFF6D5).fade(safeA), 0.35f to Color(0xFFFFD166).fade(safeA * 0.9f),
                0.7f to Color(0xFFC77B30).fade(safeA * 0.35f), 1f to Color.Transparent, center = Offset.Zero, radius = 0.11f
            ),
            radius = 0.11f, center = Offset.Zero
        )
    }
    glow(c, short * 0.2f, Color(0xFFFFE8A3), safeA * 0.3f)

    // Our Sun: ~26,000 ly out (≈ 55% of the radius), on a minor spur between the major arms
    val sunPos = galaxyToScreen(spiralPoint(0.55f, 1), spin, c, short)
    glow(sunPos, short * 0.03f, Color(0xFFFFD166), safeA * 0.8f)
    drawCircle(Color.White.fade(safeA), radius = short * 0.005f, center = sunPos)
    val pulse = sin(time * 6f) * 0.5f + 0.5f
    drawCircle(
        Color(0xFFFFD166).fade(safeA * (0.5f + pulse * 0.5f)), radius = short * 0.014f * (0.6f + pulse * 0.4f),
        center = sunPos, style = Stroke(width = 1.5f)
    )
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 7 – Local Group (true relative layout, MW & Andromeda approaching)
// ═════════════════════════════════════════════════════════════════════════════

private class LgGalaxy(val x: Float, val y: Float, val radius: Float, val tilt: Float, val rot: Float, val color: Color, val core: Color)

// Positions in fractions of `short`; ~0.108 ≙ 1 million light-years. Sizes are exaggerated (not to scale).
private val LG_GALAXIES = listOf(
    LgGalaxy(0.12f, -0.05f, 0.075f, 0.28f, 0.65f, Color(0xFF8FB4FF), Color(0xFFFFD88A)),   // Andromeda (M31), inclined ~77°
    LgGalaxy(0.105f, -0.135f, 0.035f, 0.60f, 0.30f, Color(0xFF8FD8FF), Color(0xFFFFF0C0)), // Triangulum (M33)
    LgGalaxy(-0.19f, 0.11f, 0.013f, 0.85f, 0f, Color(0xFFBFD8FF), Color(0xFFFFFFFF)),      // Large Magellanic Cloud
    LgGalaxy(-0.15f, 0.125f, 0.009f, 0.85f, 0f, Color(0xFFBFD8FF), Color(0xFFFFFFFF)),     // Small Magellanic Cloud
    LgGalaxy(-0.08f, 0.22f, 0.008f, 0.8f, 0.5f, Color(0xFFA8C4E8), Color(0xFFE8EEFF)),     // NGC 6822
    LgGalaxy(0.18f, -0.20f, 0.007f, 0.8f, 0.2f, Color(0xFFA8C4E8), Color(0xFFE8EEFF)),     // IC 10
    LgGalaxy(-0.22f, -0.05f, 0.006f, 0.9f, 0f, Color(0xFFFFD9A8), Color(0xFFFFEBD0)),      // Leo I (dwarf spheroidal)
    LgGalaxy(0.20f, 0.02f, 0.006f, 0.9f, 0f, Color(0xFFFFD9A8), Color(0xFFFFEBD0)),        // M32 (compact elliptical)
    LgGalaxy(-0.26f, 0.19f, 0.006f, 0.8f, 0.1f, Color(0xFFA8C4E8), Color(0xFFE8EEFF))      // WLM
)

private fun DrawScope.galaxyBlob(pos: Offset, radius: Float, tilt: Float, rot: Float, color: Color, core: Color, alpha: Float) {
    withTransform({
        translate(pos.x, pos.y)
        rotate(rot * RAD_TO_DEG, Offset.Zero)
        scale(1f, tilt, Offset.Zero)
    }) {
        drawCircle(
            brush = Brush.radialGradient(
                0f to core.fade(alpha), 0.12f to core.fade(alpha * 0.9f), 0.45f to color.fade(alpha * 0.45f),
                1f to Color.Transparent, center = Offset.Zero, radius = radius
            ),
            radius = radius, center = Offset.Zero
        )
    }
}

private fun DrawScope.drawLocalGroup(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    drawOval(
        brush = Brush.radialGradient(listOf(Color(0xFF0A1128).fade(safeA * 0.7f), Color.Transparent), center = c, radius = short * 0.5f),
        topLeft = Offset(c.x - short * 0.5f, c.y - short * 0.4f), size = Size(short, short * 0.8f)
    )
    // Gravitational extent of the group (~10 million ly across)
    drawCircle(
        Color(0xFF4CC9F0).fade(safeA * 0.18f), radius = short * 0.46f, center = c,
        style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 9f)))
    )
    // Distant background galaxies (static)
    points.drop(10).take(40).forEach { p ->
        drawCircle(Color.White.fade(safeA * 0.2f), radius = 1.4f, center = Offset(c.x + (p.x - 0.5f) * short * 0.95f, c.y + (p.y - 0.5f) * short * 0.8f))
    }

    // The Milky Way and Andromeda are falling toward each other (~110 km/s); merger in ~4–5 Gyr.
    val sway = short * 0.004f * sin(time * 0.3f)
    val mw = Offset(c.x + short * -0.13f + sway, c.y + short * 0.06f)
    val m31 = Offset(c.x + short * 0.12f - sway, c.y + short * -0.05f)
    galaxyBlob(mw, short * 0.045f, 0.62f, -0.5f, Color(0xFF8FB4FF), Color(0xFFFFE2A0), safeA)
    galaxyBlob(m31, short * LG_GALAXIES[0].radius, LG_GALAXIES[0].tilt, LG_GALAXIES[0].rot, LG_GALAXIES[0].color, LG_GALAXIES[0].core, safeA)

    val dir = (m31 - mw); val dl = dir.getDistance().coerceAtLeast(1f)
    val u = Offset(dir.x / dl, dir.y / dl)
    for (k in 0..1) {
        val sgn = if (k == 0) 1f else -1f
        val from = if (k == 0) mw else m31
        val p0 = Offset(from.x + u.x * sgn * short * 0.07f, from.y + u.y * sgn * short * 0.07f)
        val p1 = Offset(from.x + u.x * sgn * short * 0.11f, from.y + u.y * sgn * short * 0.11f)
        drawLine(Color(0xFFFFD166).fade(safeA * 0.55f), p0, p1, strokeWidth = 1.6f, cap = StrokeCap.Round)
        val nx = -u.y * sgn; val ny = u.x * sgn
        val hd = short * 0.008f
        drawLine(Color(0xFFFFD166).fade(safeA * 0.55f), p1, Offset(p1.x - u.x * sgn * hd + nx * hd * 0.6f, p1.y - u.y * sgn * hd + ny * hd * 0.6f), strokeWidth = 1.6f)
        drawLine(Color(0xFFFFD166).fade(safeA * 0.55f), p1, Offset(p1.x - u.x * sgn * hd - nx * hd * 0.6f, p1.y - u.y * sgn * hd - ny * hd * 0.6f), strokeWidth = 1.6f)
    }
    LG_GALAXIES.drop(1).forEach { g ->
        galaxyBlob(Offset(c.x + g.x * short, c.y + g.y * short), short * g.radius, g.tilt, g.rot, g.color, g.core, safeA)
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 8 – Laniakea supercluster: galaxies stream along converging flow-lines
//  toward the Great Attractor. The Local Group sits out on one stream.
// ═════════════════════════════════════════════════════════════════════════════

private const val STREAMS = 30

private fun streamPts(c: Offset, short: Float, k: Int): Pair<Offset, Offset> {
    val base = (k / STREAMS.toFloat()) * TWO_PI + (hash3(k, 1, 7, 3) - 0.5f) * 0.22f
    val reach = 0.40f + 0.10f * hash3(k, 2, 7, 3)
    val mid = base + 0.25f + 0.5f * (hash3(k, 3, 7, 3) - 0.3f)
    val midR = 0.20f + 0.12f * hash3(k, 4, 7, 3)
    return polar(c, base, short * reach) to polar(c, mid, short * midR)
}

private fun streamPoint(c: Offset, short: Float, k: Int, t: Float): Offset {
    val (p0, p1) = streamPts(c, short, k)
    val u = 1f - t
    return Offset(u * u * p0.x + 2f * u * t * p1.x + t * t * c.x, u * u * p0.y + 2f * u * t * p1.y + t * t * c.y)
}

private class Knot(val k: Int, val t: Float, val size: Float)
private val KNOTS = listOf(
    Knot(3, 0.55f, 0.016f), Knot(9, 0.68f, 0.020f), Knot(8, 0.50f, 0.014f), Knot(18, 0.50f, 0.015f),
    Knot(22, 0.45f, 0.013f), Knot(14, 0.60f, 0.017f), Knot(26, 0.62f, 0.015f)
)

private fun DrawScope.drawSupercluster(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    // Laniakea's lumpy basin of attraction
    val basin = Path().apply {
        for (i in 0..72) {
            val ang = i / 72f * TWO_PI
            val rr = short * (0.43f + 0.035f * sin(3f * ang + 1f) + 0.025f * sin(5f * ang + 2f) + 0.015f * sin(9f * ang))
            val p = polar(c, ang, rr)
            if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y)
        }
        close()
    }
    drawPath(basin, brush = Brush.radialGradient(listOf(Color(0xFF4361EE).fade(safeA * 0.22f), Color(0xFF4361EE).fade(safeA * 0.05f)), center = c, radius = short * 0.47f))
    drawPath(basin, Color(0xFF4CC9F0).fade(safeA * 0.30f), style = Stroke(width = 1.2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 9f))))
    glow(c, short * 0.30f, Color(0xFF4CC9F0), safeA * 0.22f)
    glow(c, short * 0.15f, Color(0xFFFFD166), safeA * 0.55f)   // Great Attractor

    // Flow lines converging on the Great Attractor
    for (k in 0 until STREAMS) {
        val (p0, p1) = streamPts(c, short, k)
        val path = Path().apply { moveTo(p0.x, p0.y); quadraticBezierTo(p1.x, p1.y, c.x, c.y) }
        drawPath(path, Color(0xFF4CC9F0).fade(safeA * 0.24f), style = Stroke(width = 1.5f))
    }
    // Galaxies ride the streams inward
    points.take(300).forEachIndexed { i, p ->
        val t = (p.x + time * 0.03f) % 1f
        val pos = streamPoint(c, short, i % STREAMS, t)
        val jitter = (p.y - 0.5f) * short * 0.025f * (1f - t)
        val col = when (i % 3) { 0 -> Color(0xFFFFE9B0); 1 -> Color(0xFF9BC4FF); else -> Color.White }
        drawCircle(col.fade(safeA * 0.75f), radius = short * 0.0022f, center = Offset(pos.x + jitter, pos.y - jitter))
    }
    // Major clusters
    KNOTS.forEach { kn ->
        val p = streamPoint(c, short, kn.k, kn.t)
        glow(p, short * kn.size * 2.4f, Color(0xFFFFD3A0), safeA * 0.55f)
        drawCircle(Color(0xFFFFF3D0).fade(safeA * 0.9f), radius = short * kn.size * 0.3f, center = p)
    }
    val virgo = streamPoint(c, short, 3, 0.55f)
    drawLabel("Virgo Cluster", Offset(virgo.x, virgo.y - short * 0.03f), short * 0.014f, Color(0xFFFFE9B0), safeA * 0.85f)
    drawLabel("Great Attractor", Offset(c.x, c.y + short * 0.085f), short * 0.016f, Color.White, safeA * 0.9f)

    // Our Milky Way (inside the Local Group), drifting along its stream
    val lg = streamPoint(c, short, 3, 0.36f + 0.02f * sin(time * 0.2f))
    val pulse = 0.5f + 0.5f * sin(time * 3f)
    drawCircle(Color(0xFFFFD166).fade(safeA * (0.4f + 0.5f * pulse)), radius = short * (0.024f + 0.006f * pulse), center = lg, style = Stroke(width = 1.4f))
    tinyGalaxy(lg, short * 0.012f, safeA)
    val out = Offset(lg.x - c.x, lg.y - c.y)
    val ol = out.getDistance().coerceAtLeast(1f)
    val lp = Offset(lg.x + out.x / ol * short * 0.06f, lg.y + out.y / ol * short * 0.06f)
    drawLabel("Milky Way", lp, short * 0.018f, Color(0xFFFFD166), safeA)
    drawLabel("Local Group · you are here", Offset(lp.x, lp.y + short * 0.022f), short * 0.012f, Color(0xFFFFE9B0), safeA * 0.8f)
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 9 – Cosmic web
// ═════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawCosmicWeb(c: Offset, short: Float, a: Float, cache: CosmicCache, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val s = short * 0.95f
    val breath = 0.85f + 0.15f * sin(time * 0.6f)
    fun nodePos(i: Int) = Offset(c.x + cache.webNodes[i].x * s, c.y + cache.webNodes[i].y * s)

    cache.webEdges.forEach { e ->
        val col = if (e.weight > 0.65f) Color(0xFF7B8CFF) else Color(0xFF4361EE)
        drawLine(col.fade(safeA * (0.14f + 0.24f * e.weight) * breath), nodePos(e.a), nodePos(e.b), strokeWidth = 1f + e.weight * 3.5f, cap = StrokeCap.Round)
    }
    withTransform({ translate(c.x, c.y); scale(s, s, Offset.Zero) }) {
        drawPoints(cache.webDots, PointMode.Points, Color.White.fade(safeA * 0.55f), strokeWidth = (short * 0.004f) / s, cap = StrokeCap.Round)
    }
    cache.webNodes.indices.forEach { i ->
        val mass = cache.webMass[i]
        val col = when (i % 3) { 0 -> Color(0xFFFFE9B0); 1 -> Color(0xFF9BC4FF); else -> Color(0xFFFFD3A0) }
        val n = nodePos(i)
        val rad = short * (0.010f + 0.016f * mass)
        glow(n, rad * 3f, col, safeA * 0.4f * breath)
        drawCircle(
            brush = Brush.radialGradient(listOf(Color.White.fade(safeA), col.fade(safeA), Color.Transparent), center = n, radius = rad),
            radius = rad, center = n
        )
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 10 – Observable universe (clustered galaxies, Planck-style CMB edge)
// ═════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawObservableUniverse(c: Offset, short: Float, a: Float, cache: CosmicCache, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color.Transparent, Color(0xFF4361EE).fade(safeA * 0.12f), Color(0xFFFF8A5C).fade(safeA * 0.2f)), center = c, radius = boundary
        ),
        radius = boundary, center = c
    )
    // Cosmic microwave background: hot (orange) and cold (blue) patches of ±0.01 % temperature
    cache.cmbBlobs.forEach { blob ->
        val col = if (blob.warm) Color(0xFFFF9A5C) else Color(0xFF4C7BFF)
        drawCircle(col.fade(safeA * 0.10f), radius = short * blob.size, center = polar(c, blob.angle, boundary * (1f + blob.jitter)))
    }
    drawCircle(Color(0xFFFFD166).fade(safeA * 0.28f), radius = boundary, center = c, style = Stroke(width = 2f))

    withTransform({ translate(c.x, c.y); scale(boundary, boundary, Offset.Zero) }) {
        cache.universeBuckets.forEachIndexed { bi, b ->
            if (b.pts.isEmpty()) return@forEachIndexed
            val breathe = 0.9f + 0.1f * sin(time * 0.5f + bi * 0.9f)
            drawPoints(
                b.pts, PointMode.Points, b.color.fade(safeA * (0.55f + 0.1f * b.tier) * breathe),
                strokeWidth = short * (0.004f + 0.003f * b.tier) / boundary, cap = StrokeCap.Round
            )
        }
    }
}

// ═════════════════════════════════════════════════════════════════════════════
//  Level 11 – Beyond (speculative / artistic: nobody knows what lies beyond)
// ═════════════════════════════════════════════════════════════════════════════

private fun DrawScope.drawBeyondUniverse(c: Offset, short: Float, a: Float, points: List<Offset>, time: Float) {
    val safeA = a.coerceIn(0f, 1f)
    val boundary = short * 0.48f
    drawCircle(
        brush = Brush.radialGradient(
            listOf(Color(0xFF7209B7).fade(safeA * 0.3f), Color(0xFF4361EE).fade(safeA * 0.2f), Color.Transparent), center = c, radius = boundary * 1.2f
        ),
        radius = boundary * 1.2f, center = c
    )
    for (i in 1..4) {
        val ringR = boundary * (i / 4f) * (0.85f + 0.15f * sin(time * 0.4f + i))
        drawCircle(Color(0xFFFFD166).fade(safeA * (0.2f / i)), radius = ringR, center = c, style = Stroke(width = 1.5f))
    }
    points.take(160).forEachIndexed { i, p ->
        val angle = p.x * TWO_PI + time * (0.04f + (i % 3) * 0.015f)
        val dist = boundary * (0.15f + 0.85f * p.y * (0.95f + 0.05f * sin(time * 0.8f + i)))
        val color = when (i % 4) { 0 -> Color(0xFFFFD166); 1 -> Color(0xFF4CC9F0); 2 -> Color(0xFFB794F6); else -> Color.White }
        val twinkle = 0.4f + 0.6f * sin(time * 2f + i)
        drawCircle(color.fade(safeA * 0.75f * twinkle), radius = short * 0.0025f, center = polar(c, angle, dist))
    }
}