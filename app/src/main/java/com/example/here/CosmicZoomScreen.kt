package com.example.here

import android.content.Intent
import android.media.AudioManager
import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private enum class Phase { INTRO, JOURNEY, ARRIVED, BEYOND, SEEKING_EARTH, EARTH_FOUND, RETURNING, FINAL }

// --- TUNING KNOBS ---
private const val DRAG_SENSITIVITY = 1f / 280f
private const val PINCH_SENSITIVITY = 2.4f
private const val SETTLE_WINDOW = 0.12f
private const val RUBBER_BAND_DAMPING = 0.3f

// The big leap where the visual jump is largest — worth a small camera reaction.
private const val BIG_LEAP_LEVEL = 4

private val COOL_BG = Color(0xFF020204)
private val WARM_BG = Color(0xFF1A0F08)

@Composable
fun CosmicZoomScreen() {
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var zoomLevel by remember { mutableFloatStateOf(0f) }
    var settledLevel by remember { mutableIntStateOf(0) }
    var showScaleDetail by remember { mutableStateOf(false) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    var shakeOffset by remember { mutableStateOf(Offset.Zero) }
    var lastLevelForShake by remember { mutableIntStateOf(0) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    // Audio tone generator for natural navigation clicks/ticks
    val toneGen = remember {
        try {
            ToneGenerator(AudioManager.STREAM_MUSIC, 20)
        } catch (_: Exception) {
            null
        }
    }

    fun playClickSound() {
        try {
            toneGen?.startTone(ToneGenerator.TONE_PROP_BEEP, 15)
        } catch (_: Exception) {
            // ignore
        }
    }

    // Manual navigation is NEVER locked — users can zoom in and out freely at any time.
    val inputLocked = false

    // Continuous clock driving all idle motion (orbiting, twinkling, drifting)
    val infiniteClock = rememberInfiniteTransition(label = "clock")
    val time by infiniteClock.animateFloat(
        initialValue = 0f,
        targetValue = 1000f,
        animationSpec = infiniteRepeatable(tween(1_000_000, easing = LinearEasing)),
        label = "time"
    )

    fun onSettle(levelInt: Int) {
        if (settledLevel != levelInt) {
            settledLevel = levelInt
            playClickSound()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (phase == Phase.JOURNEY && levelInt >= COSMIC_LEVELS.size - 1) {
            phase = Phase.ARRIVED
        }
        if (phase == Phase.SEEKING_EARTH && levelInt <= EARTH_LEVEL) {
            phase = Phase.EARTH_FOUND
        }
    }

    fun animateToLevel(target: Int, durationMs: Int = 450) {
        playClickSound()
        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
        animJob?.cancel()
        val clamped = target.coerceIn(0, COSMIC_LEVELS.size - 1)
        animJob = scope.launch {
            animate(
                initialValue = zoomLevel,
                targetValue = clamped.toFloat(),
                animationSpec = tween(durationMs)
            ) { value, _ -> zoomLevel = value }
            onSettle(clamped)
        }
    }

    var lastSoundPlayedLevel by remember { mutableIntStateOf(0) }

    fun handleDrag(deltaPx: Float) {
        if (phase == Phase.INTRO) phase = Phase.JOURNEY
        val raw = zoomLevel + deltaPx * DRAG_SENSITIVITY
        zoomLevel = when {
            raw < 0f -> raw * RUBBER_BAND_DAMPING
            raw > MAX_LEVEL -> MAX_LEVEL + (raw - MAX_LEVEL) * RUBBER_BAND_DAMPING
            else -> raw
        }
        val currentInt = zoomLevel.roundToInt()
        if (currentInt != lastSoundPlayedLevel) {
            lastSoundPlayedLevel = currentInt
            playClickSound()
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleZoom(delta: Float) {
        if (phase == Phase.INTRO) phase = Phase.JOURNEY
        val raw = zoomLevel + delta
        zoomLevel = when {
            raw < 0f -> raw * RUBBER_BAND_DAMPING
            raw > MAX_LEVEL -> MAX_LEVEL + (raw - MAX_LEVEL) * RUBBER_BAND_DAMPING
            else -> raw
        }
        playClickSound()
    }

    fun finishGesture() {
        animateToLevel(zoomLevel.roundToInt(), durationMs = 380)
    }

    val gestureModifier = Modifier.pointerInput(inputLocked, phase) {
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            animJob?.cancel()
            do {
                val event = awaitPointerEvent()
                val zoomChange = event.calculateZoom()
                val panChange = event.calculatePan()
                if (zoomChange != 1f || panChange.y != 0f) {
                    handleDrag(-panChange.y)
                    if (zoomChange != 1f) {
                        handleZoom((zoomChange - 1f) * PINCH_SENSITIVITY)
                    }
                    event.changes.forEach { it.consume() }
                }
            } while (event.changes.any { it.pressed })
            finishGesture()
        }
    }

    // Automatically exit ARRIVED or BEYOND if the user manually navigates back in
    LaunchedEffect(zoomLevel) {
        if (zoomLevel < 8.5f && (phase == Phase.ARRIVED || phase == Phase.BEYOND)) {
            phase = Phase.JOURNEY
        }
    }

    val level = zoomLevel.toInt().coerceIn(0, COSMIC_LEVELS.size - 1)
    val fraction = (zoomLevel - level).coerceIn(0f, 1f)
    val settled = fraction < SETTLE_WINDOW || fraction > 1f - SETTLE_WINDOW
    val nearestLevel = COSMIC_LEVELS[zoomLevel.roundToInt().coerceIn(0, COSMIC_LEVELS.size - 1)]

    // A small camera reaction exactly at the biggest single scale leap in the journey.
    LaunchedEffect(level) {
        val crossedIntoBigLeap = level == BIG_LEAP_LEVEL && lastLevelForShake != BIG_LEAP_LEVEL
        val crossedOutOfBigLeap = lastLevelForShake == BIG_LEAP_LEVEL && level != BIG_LEAP_LEVEL
        if (crossedIntoBigLeap || crossedOutOfBigLeap) {
            val kicks = listOf(Offset(6f, 0f), Offset(-5f, 3f), Offset(4f, -3f), Offset(-2f, 2f), Offset.Zero)
            for (k in kicks) {
                shakeOffset = k
                delay(45.milliseconds)
            }
        }
        lastLevelForShake = level
    }

    val labelAlpha by animateFloatAsState(
        targetValue = if (phase == Phase.JOURNEY && settled) 1f else 0f,
        animationSpec = tween(500),
        label = "labelAlpha"
    )

    val interpolatedExponent = remember(zoomLevel) {
        val curExp = COSMIC_LEVELS[level].exponent
        val nextExp = COSMIC_LEVELS[(level + 1).coerceAtMost(COSMIC_LEVELS.size - 1)].exponent
        (curExp + (nextExp - curExp) * fraction).roundToInt()
    }

    // The emotional arc, made visible
    val warmth = when (phase) {
        Phase.EARTH_FOUND, Phase.RETURNING, Phase.FINAL -> 1f
        Phase.SEEKING_EARTH, Phase.BEYOND -> ((MAX_LEVEL - zoomLevel) / (MAX_LEVEL - EARTH_LEVEL)).coerceIn(0f, 1f) * 0.55f
        else -> 0f
    }
    val bgColor by animateColorAsState(
        targetValue = lerp(COOL_BG, WARM_BG, warmth),
        animationSpec = tween(1400),
        label = "bgColor"
    )

    val showCityLights = level == EARTH_LEVEL && (phase == Phase.EARTH_FOUND || phase == Phase.RETURNING)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(enabled = phase == Phase.INTRO) {
                playClickSound()
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                phase = Phase.JOURNEY
            }
            .then(gestureModifier)
    ) {
        CosmicVisual(
            level = level,
            fraction = fraction,
            time = time,
            showCityLights = showCityLights,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Interactive journey from one human life to the observable universe"
                }
                .offset(x = shakeOffset.x.dp, y = shakeOffset.y.dp)
        )

        val markerAlpha = (1f - (zoomLevel / MAX_LEVEL) * 1.15f).coerceIn(0f, 1f)
        if (phase != Phase.FINAL && markerAlpha > 0f) {
            Text(
                text = "● YOU",
                color = Color.White.copy(alpha = markerAlpha * 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 20.dp)
            )
        }

        if (phase == Phase.JOURNEY || phase == Phase.SEEKING_EARTH || phase == Phase.ARRIVED || phase == Phase.BEYOND || phase == Phase.RETURNING) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 20.dp)
                    .semantics { contentDescription = "Scale details" }
                    .clickable {
                        playClickSound()
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showScaleDetail = !showScaleDetail
                    }
            ) {
                Text(exponentLabel(interpolatedExponent), color = Color.White.copy(alpha = 0.55f), fontSize = 13.sp)
                nearestLevel.sizeFact?.let {
                    Text(it, color = Color.White.copy(alpha = 0.42f), fontSize = 11.sp)
                }
                AnimatedVisibility(visible = showScaleDetail) {
                    Text(
                        "Viewing ~${exponentLabel(interpolatedExponent)} across",
                        color = Color.White.copy(alpha = 0.4f),
                        fontSize = 11.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        ScaleRuler(
            currentLevel = zoomLevel,
            onTapLevel = { animateToLevel(it) },
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 14.dp)
        )
        ChevronControls(
            onCloser = { animateToLevel((settledLevel - 1).coerceAtLeast(0)) },
            onFurther = { animateToLevel((settledLevel + 1).coerceAtMost(COSMIC_LEVELS.size - 1)) },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = 20.dp)
        )

        // --- QUIET ZONE: all narrative text lives in a fixed top band with a scrim ---
        if (phase == Phase.JOURNEY || phase == Phase.ARRIVED || phase == Phase.BEYOND || phase == Phase.SEEKING_EARTH || phase == Phase.EARTH_FOUND || phase == Phase.RETURNING) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .height(210.dp)
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent)
                        )
                    )
            )
        }

        if (phase == Phase.JOURNEY) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 76.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    nearestLevel.title,
                    color = Color.White.copy(alpha = labelAlpha),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Text(
                    nearestLevel.subtitle,
                    color = Color.White.copy(alpha = labelAlpha * 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
                nearestLevel.sizeFact?.let {
                    Text(
                        it,
                        color = Color.White.copy(alpha = labelAlpha * 0.5f),
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = phase == Phase.INTRO,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IntroHint()
        }

        // Top Narrative Text for ARRIVED
        AnimatedVisibility(
            visible = phase == Phase.ARRIVED,
            enter = fadeIn(tween(1000)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp, start = 24.dp, end = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "The Observable Universe",
                    color = Color.White,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Text(
                    "~2 trillion galaxies (conservative estimate).",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 14.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Bottom Action Button for ARRIVED
        AnimatedVisibility(
            visible = phase == Phase.ARRIVED,
            enter = fadeIn(tween(1200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            Button(
                onClick = {
                    playClickSound()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    phase = Phase.BEYOND
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Peer Beyond the Veil ➔", fontSize = 12.sp)
            }
        }

        // Top Narrative Text for BEYOND
        AnimatedVisibility(
            visible = phase == Phase.BEYOND,
            enter = fadeIn(tween(1000)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp, start = 24.dp, end = 24.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Beyond the Observable Universe",
                    color = Color(0xFFFFD166),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center
                )
                Text(
                    "We can only observe the part whose light has reached us. Beyond it, realms exist that human minds can scarcely begin to comprehend.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 18.sp,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        // Bottom Action Button for BEYOND
        AnimatedVisibility(
            visible = phase == Phase.BEYOND,
            enter = fadeIn(tween(1000)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 72.dp)
        ) {
            Button(
                onClick = {
                    playClickSound()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    phase = Phase.RETURNING
                    animJob?.cancel()
                    scope.launch {
                        animate(
                            initialValue = zoomLevel,
                            targetValue = EARTH_LEVEL.toFloat(),
                            animationSpec = tween(3800)
                        ) { value, _ -> zoomLevel = value }
                        phase = Phase.EARTH_FOUND
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Journey Back Home ➔", fontSize = 12.sp)
            }
        }

        AnimatedVisibility(
            visible = phase == Phase.RETURNING,
            enter = fadeIn(tween(700)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 92.dp, start = 32.dp, end = 32.dp)
        ) {
            Text(
                "Coming home slowly, one scale at a time...",
                color = Color.White.copy(alpha = 0.85f),
                fontSize = 16.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
        }

        if (phase == Phase.EARTH_FOUND) {
            EarthFoundSequence(
                onComeHome = {
                    playClickSound()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    phase = Phase.RETURNING
                    animJob?.cancel()
                    scope.launch {
                        animate(
                            initialValue = zoomLevel,
                            targetValue = 0f,
                            animationSpec = tween(2800)
                        ) { value, _ -> zoomLevel = value }
                        phase = Phase.FINAL
                    }
                }
            )
        }

        AnimatedVisibility(
            visible = phase == Phase.FINAL,
            enter = fadeIn(tween(800)),
            modifier = Modifier
                .fillMaxSize()
        ) {
            FinalScreen(
                onReset = {
                    playClickSound()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    phase = Phase.INTRO
                    zoomLevel = 0f
                    settledLevel = 0
                    showScaleDetail = false
                },
                onShare = {
                    playClickSound()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    val send = Intent(Intent.ACTION_SEND).apply {
                        type = "text/plain"
                        putExtra(Intent.EXTRA_TITLE, "My scale of the universe")
                        putExtra(
                            Intent.EXTRA_TEXT,
                            "You're a star, experiencing itself as a human for a little life. Enjoy every small moments.\n\nHERE"
                        )
                    }
                    context.startActivity(Intent.createChooser(send, "Share your journey"))
                }
            )
        }
    }
}

@Composable
private fun IntroHint() {
    val infinite = rememberInfiniteTransition(label = "breathe")
    val bob by infinite.animateFloat(
        initialValue = 0f,
        targetValue = 14f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text("YOU ARE HERE", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
        Spacer(Modifier.height(56.dp))
        Text(
            "⌄",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 22.sp,
            modifier = Modifier.offset(y = bob.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text("Pinch out or drag down to begin", color = Color.White.copy(alpha = 0.45f), fontSize = 13.sp)
    }
}

@Composable
private fun ScaleRuler(
    currentLevel: Float,
    onTapLevel: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .padding(vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        for (i in COSMIC_LEVELS.indices.reversed()) {
            val isActive = i == currentLevel.roundToInt()
            Box(
                modifier = Modifier
                    .size(width = 48.dp, height = 28.dp)
                    .semantics {
                        contentDescription = "${COSMIC_LEVELS[i].title}, ${exponentLabel(COSMIC_LEVELS[i].exponent)}"
                        role = Role.Button
                    }
                    .clickable { onTapLevel(i) }
            ) {
                Box(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .size(width = if (isActive) 18.dp else 10.dp, height = 2.dp)
                        .background(Color.White.copy(alpha = if (isActive) 0.9f else 0.25f))
                )
            }
        }
    }
}

@Composable
private fun ChevronControls(
    onCloser: () -> Unit,
    onFurther: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = "Zoom closer"
                    role = Role.Button
                }
                .clickable { onCloser() },
            contentAlignment = Alignment.Center
        ) {
            Text("▲", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
        }
        Box(
            modifier = Modifier
                .size(48.dp)
                .semantics {
                    contentDescription = "Zoom further away"
                    role = Role.Button
                }
                .clickable { onFurther() },
            contentAlignment = Alignment.Center
        ) {
            Text("▼", color = Color.White.copy(alpha = 0.4f), fontSize = 16.sp)
        }
    }
}

/**
 * The closing beat: anchored safely at the bottom so it never overlaps the center "You" illustration.
 */
@Composable
private fun FinalScreen(onReset: () -> Unit, onShare: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(400.milliseconds)
        step = 1
        delay(1800.milliseconds)
        step = 2
        delay(1000.milliseconds)
        step = 3
    }
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp)
            .padding(bottom = 48.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            AnimatedVisibility(visible = step >= 1, enter = fadeIn(tween(1000))) {
                Text("You are here.", color = Color.White.copy(alpha = 0.95f), fontSize = 18.sp, fontWeight = FontWeight.Light)
            }
            Spacer(Modifier.height(14.dp))
            AnimatedVisibility(visible = step >= 2, enter = fadeIn(tween(1000))) {
                Text(
                    "Don't worry.\nYou're a star, experiencing itself as a human for a little life.\nEnjoy every moment.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
            Spacer(Modifier.height(24.dp))
            AnimatedVisibility(visible = step >= 3, enter = fadeIn(tween(800))) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    OutlinedButton(
                        onClick = onReset,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)
                    ) {
                        Text("AGAIN", fontSize = 12.sp, fontWeight = FontWeight.Light)
                    }
                    Button(
                        onClick = onShare,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
                    ) {
                        Text("SHARE", fontSize = 12.sp, fontWeight = FontWeight.Light)
                    }
                }
            }
        }
    }
}

@Composable
private fun EarthFoundSequence(onComeHome: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    LaunchedEffect(Unit) {
        delay(1400.milliseconds)
        step = 1
        delay(2600.milliseconds)
        step = 2
        delay(2200.milliseconds)
        step = 3
    }
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 92.dp, start = 36.dp, end = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            AnimatedVisibility(visible = step >= 1, enter = fadeIn(tween(1000))) {
                Text(
                    "Somewhere on that this world, you are living your life right now.",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
            Spacer(Modifier.height(16.dp))
            AnimatedVisibility(visible = step >= 2, enter = fadeIn(tween(1000))) {
                Text(
                    "Every human story we've ever known happened right there.",
                    color = Color.White.copy(alpha = 0.7f),
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp
                )
            }
        }
        AnimatedVisibility(
            visible = step >= 3,
            enter = fadeIn(tween(900)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 64.dp)
        ) {
            Button(
                onClick = onComeHome,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("COME HOME")
            }
        }
    }
}
