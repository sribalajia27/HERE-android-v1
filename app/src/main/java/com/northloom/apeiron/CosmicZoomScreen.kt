package com.northloom.apeiron

// import android.media.AudioManager
// import android.media.ToneGenerator
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.CubicBezierEasing
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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.random.Random
import kotlin.time.Duration.Companion.milliseconds

private enum class Phase { INTRO, JOURNEY, ARRIVED, EARTH_FOUND, RETURNING }

// --- TUNING KNOBS ---
private const val DRAG_SENSITIVITY = 1f / 280f
private const val PINCH_SENSITIVITY = 2.4f
private const val SETTLE_WINDOW = 0.12f
private const val RUBBER_BAND_DAMPING = 0.3f

private val COOL_BG = Color(0xFF020204)
private val WARM_BG = Color(0xFF1A0F08)

// A slow-settle "ease-out-expo" curve — motion starts fast and glides to rest rather
// than the more mechanical default, which is most of what makes a transition read as
// deliberate camera movement instead of a UI animation.
private val ORGANIC_EASING = CubicBezierEasing(0.16f, 1f, 0.3f, 1f)
// A single gesture that crosses this many whole levels triggers a warp-streak flash,
// instead of just snapping into place.
private const val WARP_LEVEL_JUMP_THRESHOLD = 3

@Composable
fun CosmicZoomScreen(
    userName: String? = null,
    userAvatar: String? = null,
    musicEnabled: Boolean = true,
    musicVolume: Float = 0.7f,
    hapticsEnabled: Boolean = true,
    audioController: AudioController? = null,
    onEditProfile: () -> Unit = {}
) {
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var zoomLevel by remember { mutableFloatStateOf(0f) }
    var settledLevel by remember { mutableIntStateOf(0) }
    var showScaleDetail by remember { mutableStateOf(false) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    var shakeOffset by remember { mutableStateOf(Offset.Zero) }
    var lastLevelForShake by remember { mutableIntStateOf(0) }
    var gestureStartLevel by remember { mutableIntStateOf(0) }
    var warpFlash by remember { mutableStateOf(false) }
    var showFactsDialog by remember { mutableStateOf(false) }
    val factBags = remember { mutableMapOf<Int, FactShuffleBag>() }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun performHaptic(type: HapticFeedbackType) {
        if (hapticsEnabled) {
            haptics.performHapticFeedback(type)
        }
    }

    LaunchedEffect(musicEnabled, musicVolume) {
        audioController?.setMusicEnabled(musicEnabled)
        audioController?.setUserVolume(musicVolume)
    }

    LaunchedEffect(phase) {
        val targetMultiplier = when (phase) {
            Phase.INTRO -> 0.5f
            Phase.JOURNEY -> 1.0f
            Phase.ARRIVED -> 0.9f
            Phase.RETURNING -> 0.8f
            Phase.EARTH_FOUND -> 0.6f
        }
        audioController?.setPhaseTargetVolume(targetMultiplier)
    }

    // The ToneGenerator was too harsh/annoying for a serene space app.
    // We removed it in favor of relying purely on the subtle haptic feedback for physical presence.
    fun playClickSound() {
        // No-op: Removed annoying beep. Let the silence and haptics do the work.
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
            performHaptic(HapticFeedbackType.TextHandleMove)
        }
        if (phase == Phase.JOURNEY && levelInt >= COSMIC_LEVELS.size - 1) {
            phase = Phase.ARRIVED
        }
        if (phase == Phase.RETURNING && levelInt <= EARTH_LEVEL) {
            phase = Phase.EARTH_FOUND
        }
    }

    fun animateToLevel(target: Int, durationMs: Int = 520) {
        playClickSound()
        performHaptic(HapticFeedbackType.LongPress)
        animJob?.cancel()
        val clamped = target.coerceIn(0, COSMIC_LEVELS.size - 1)
        animJob = scope.launch {
            animate(
                initialValue = zoomLevel,
                targetValue = clamped.toFloat(),
                animationSpec = tween(durationMs, easing = ORGANIC_EASING)
            ) { value, _ -> zoomLevel = value }
            onSettle(clamped)
        }
    }

    fun triggerWarpFlash() {
        scope.launch {
            warpFlash = true
            delay(70)
            warpFlash = false
        }
    }

    var lastSoundPlayedLevel by remember { mutableIntStateOf(0) }

    fun handleDrag(deltaPx: Float) {
        if (phase == Phase.INTRO) phase = Phase.JOURNEY
        if (phase == Phase.EARTH_FOUND) phase = Phase.JOURNEY
        // Add "Cosmic Heaviness": As you zoom out to massive scales, manipulating the universe feels heavier.
        val dynamicDrag = DRAG_SENSITIVITY * (1f - (zoomLevel / MAX_LEVEL) * 0.7f)
        val raw = zoomLevel + deltaPx * dynamicDrag
        zoomLevel = when {
            raw < 0f -> raw * RUBBER_BAND_DAMPING
            raw > MAX_LEVEL -> MAX_LEVEL + (raw - MAX_LEVEL) * RUBBER_BAND_DAMPING
            else -> raw
        }
        val currentInt = zoomLevel.roundToInt()
        if (currentInt != lastSoundPlayedLevel) {
            lastSoundPlayedLevel = currentInt
            playClickSound()
            performHaptic(HapticFeedbackType.TextHandleMove)
        }
    }

    fun handleZoom(delta: Float) {
        if (phase == Phase.INTRO) phase = Phase.JOURNEY
        if (phase == Phase.EARTH_FOUND) phase = Phase.JOURNEY
        // Add "Cosmic Heaviness": As you zoom out to massive scales, manipulating the universe feels heavier.
        val dynamicPinch = PINCH_SENSITIVITY * (1f - (zoomLevel / MAX_LEVEL) * 0.7f)
        val raw = zoomLevel + delta * dynamicPinch
        zoomLevel = when {
            raw < 0f -> raw * RUBBER_BAND_DAMPING
            raw > MAX_LEVEL -> MAX_LEVEL + (raw - MAX_LEVEL) * RUBBER_BAND_DAMPING
            else -> raw
        }
        playClickSound()
    }

    fun finishGesture() {
        val target = zoomLevel.roundToInt()
        if (abs(target - gestureStartLevel) >= WARP_LEVEL_JUMP_THRESHOLD) {
            triggerWarpFlash()
        }
        animateToLevel(target, durationMs = 460)
    }

    val gestureModifier = Modifier.pointerInput(inputLocked, phase, showFactsDialog) {
        if (showFactsDialog) return@pointerInput
        awaitEachGesture {
            awaitFirstDown(requireUnconsumed = false)
            gestureStartLevel = zoomLevel.roundToInt()
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

    // Automatically exit ARRIVED if the user manually navigates back in
    LaunchedEffect(zoomLevel) {
        if (zoomLevel < 9.5f && phase == Phase.ARRIVED) {
            phase = Phase.JOURNEY
        }
    }

    val level = zoomLevel.toInt().coerceIn(0, COSMIC_LEVELS.size - 1)
    val fraction = (zoomLevel - level).coerceIn(0f, 1f)
    val settled = fraction < SETTLE_WINDOW || fraction > 1f - SETTLE_WINDOW
    val nearestLevel = COSMIC_LEVELS[zoomLevel.roundToInt().coerceIn(0, COSMIC_LEVELS.size - 1)]

    // The camera shake effect has been removed for a smoother journey experience.
    LaunchedEffect(level) {
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

    val warmth = when (phase) {
        Phase.EARTH_FOUND, Phase.RETURNING -> 1f
        Phase.ARRIVED -> 0.55f
        else -> 0f
    }
    val bgColor by animateColorAsState(
        targetValue = lerp(COOL_BG, WARM_BG, warmth),
        animationSpec = tween(1400),
        label = "bgColor"
    )

    val showCityLights = level == EARTH_LEVEL && (phase == Phase.EARTH_FOUND || phase == Phase.RETURNING)

    val cinematicDimming by animateFloatAsState(
        targetValue = if (phase == Phase.EARTH_FOUND) 0.65f else 0f,
        animationSpec = tween(3000), // slowly dim the visual over 3 seconds
        label = "cinematicDimming"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgColor)
            .clickable(enabled = phase == Phase.INTRO) {
                playClickSound()
                performHaptic(HapticFeedbackType.LongPress)
                phase = Phase.JOURNEY
            }
            .then(gestureModifier)
    ) {
        CosmicVisual(
            level = level,
            fraction = fraction,
            time = time,
            showCityLights = showCityLights,
            userAvatar = userAvatar,
            modifier = Modifier
                .fillMaxSize()
                .semantics {
                    contentDescription = "Interactive journey from one human life to the observable universe"
                }
                .offset(x = shakeOffset.x.dp, y = shakeOffset.y.dp)
        )

        // Cinematic overlay to dim the Earth and stars, bringing focus to the text
        if (cinematicDimming > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = cinematicDimming))
            )
        }

        // A soft vignette, always present — the single cheapest thing that separates a
        // "screen full of shapes" from something that reads as a shot through a lens.
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.38f))
                    )
                )
        )

        // A brief warp-speed streak flash on a big, fast jump — the visual equivalent of
        // the camera whipping past several scales at once, instead of just teleporting.
        AnimatedVisibility(
            visible = warpFlash,
            enter = fadeIn(tween(40)),
            exit = fadeOut(tween(320)),
            modifier = Modifier.fillMaxSize()
        ) {
            WarpStreaks()
        }

        // The profile & settings pill badge in the top-left.
        val markerAlpha = (1f - (zoomLevel / 5.5f)).coerceIn(0f, 1f)
        if (markerAlpha > 0f) {
            val displayName = if (!userName.isNullOrBlank()) userName else "YOU"
            val avatarSymbol = userAvatar ?: "●"
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 44.dp, start = 16.dp)
                    .graphicsLayer { alpha = markerAlpha }
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(enabled = phase == Phase.INTRO || phase == Phase.JOURNEY) {
                        playClickSound()
                        performHaptic(HapticFeedbackType.LongPress)
                        onEditProfile()
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(avatarSymbol, fontSize = 14.sp)
                    Text(
                        text = displayName.uppercase(),
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text("⚙", color = Color(0xFF4CC9F0), fontSize = 12.sp)
                }
            }
        }

        if (phase == Phase.JOURNEY || phase == Phase.ARRIVED || phase == Phase.RETURNING || phase == Phase.EARTH_FOUND) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 20.dp, bottom = 20.dp)
                    .semantics { contentDescription = "Scale details" }
                    .clickable {
                        playClickSound()
                        performHaptic(HapticFeedbackType.TextHandleMove)
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
        if (phase == Phase.JOURNEY || phase == Phase.ARRIVED || phase == Phase.RETURNING || phase == Phase.EARTH_FOUND) {
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
                    .padding(top = 76.dp, start = 24.dp, end = 24.dp)
                    .clickable {
                        playClickSound()
                        performHaptic(HapticFeedbackType.TextHandleMove)
                        showFactsDialog = true
                    },
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
                Text(
                    "ℹ Tap for deep cosmic facts",
                    color = Color(0xFF4CC9F0).copy(alpha = labelAlpha * 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        AnimatedVisibility(
            visible = phase == Phase.INTRO,
            enter = fadeIn(tween(600)),
            exit = fadeOut(tween(400)),
            modifier = Modifier.align(Alignment.Center)
        ) {
            IntroHint(userName)
        }

        // Top Narrative Text for ARRIVED (Beyond the Observable Universe)
        AnimatedVisibility(
            visible = phase == Phase.ARRIVED,
            enter = fadeIn(tween(1000)),
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 76.dp, start = 24.dp, end = 24.dp)
                .clickable {
                    playClickSound()
                    performHaptic(HapticFeedbackType.TextHandleMove)
                    showFactsDialog = true
                }
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
                Text(
                    "ℹ Tap for deep cosmic facts",
                    color = Color(0xFF4CC9F0).copy(alpha = 0.75f),
                    fontSize = 11.sp,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
        }

        // Bottom Action Button for ARRIVED (Journey Back Home)
        AnimatedVisibility(
            visible = phase == Phase.ARRIVED,
            enter = fadeIn(tween(1200)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        ) {
            Button(
                onClick = {
                    playClickSound()
                    performHaptic(HapticFeedbackType.LongPress)
                    phase = Phase.RETURNING
                    animJob?.cancel()
                    scope.launch {
                        animate(
                            initialValue = zoomLevel,
                            targetValue = EARTH_LEVEL.toFloat(),
                            animationSpec = tween(3800, easing = ORGANIC_EASING)
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
                onRestart = {
                    playClickSound()
                    performHaptic(HapticFeedbackType.LongPress)
                    animJob?.cancel()
                    scope.launch {
                        phase = Phase.RETURNING // Temp phase to hide narrative UI during animation
                        animate(
                            initialValue = zoomLevel,
                            targetValue = 0f,
                            animationSpec = tween(2500, easing = ORGANIC_EASING)
                        ) { value, _ -> zoomLevel = value }
                        phase = Phase.INTRO
                    }
                }
            )
        }

        if (showFactsDialog) {
            val currentBag = factBags.getOrPut(nearestLevel.index) { FactShuffleBag(nearestLevel.facts) }
            CosmicFactsDialog(level = nearestLevel, factBag = currentBag, onDismiss = { showFactsDialog = false })
        }
    }
}

/**
 * Two short lines, then the door out — kept deliberately brief. This is the moment the
 * whole app has been building to, and a long monologue here dilutes it rather than
 * deepening it. Say less, mean it more, let the visual (Earth, lit and warm) carry the
 * rest of the feeling.
 */
@Composable
private fun EarthFoundSequence(onRestart: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        delay(1200.milliseconds)
        step = 1
        delay(4200.milliseconds)
        step = 2
        delay(4200.milliseconds)
        step = 3
        delay(5500.milliseconds) // Slightly longer hold for emotional impact
        step = 4
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 80.dp, start = 28.dp, end = 28.dp),
            contentAlignment = Alignment.Center
        ) {
            AnimatedVisibility(
                visible = step == 1,
                enter = fadeIn(tween(1400)),
                exit = fadeOut(tween(1000))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "SOMEWHERE ON THIS TINY WORLD",
                        color = Color(0xFF4CC9F0).copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "You are living right now.",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(
                visible = step == 2,
                enter = fadeIn(tween(1400)),
                exit = fadeOut(tween(1000))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "THE ARCHIVE",
                        color = Color(0xFFFFD166).copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Every story you've ever known happened here.",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center
                    )
                }
            }

            AnimatedVisibility(
                visible = step >= 3,
                enter = fadeIn(tween(1600)),
                exit = fadeOut(tween(1200))
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        "THE JOURNEY",
                        color = Color(0xFF4CC9F0).copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        letterSpacing = 2.sp,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "So while you're here,\nenjoy every bit of it.",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Light,
                        textAlign = TextAlign.Center,
                        lineHeight = 28.sp
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = step >= 4,
            enter = fadeIn(tween(1500)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        ) {
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.height(50.dp)
            ) {
                Text("Begin Again ➔", fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun CosmicFactsDialog(
    level: CosmicLevel,
    factBag: FactShuffleBag,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.8f))
            .clickable { onDismiss() }
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            },
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.75f)
                .clickable(enabled = false) {}
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        do {
                            val event = awaitPointerEvent()
                            event.changes.forEach { it.consume() }
                        } while (event.changes.any { it.pressed })
                    }
                },
            color = Color(0xFF0A0A12),
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            tonalElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(40.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.3f))
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    text = level.title,
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "${exponentLabel(level.exponent)} · ${level.sizeFact ?: ""}",
                    color = Color(0xFF4CC9F0),
                    fontSize = 13.sp,
                    modifier = Modifier.padding(top = 4.dp, bottom = 20.dp),
                    textAlign = TextAlign.Center
                )

                val displayFacts = remember(level.index) {
                    factBag.getNextBatch(4)
                }

                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(displayFacts.size) { index ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(14.dp))
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Text(
                                text = "✦",
                                color = Color(0xFF4CC9F0),
                                fontSize = 14.sp
                            )
                            Text(
                                text = displayFacts[index],
                                color = Color.White.copy(alpha = 0.85f),
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onDismiss,
                    colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Text("Close Facts", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

@Composable
private fun IntroHint(userName: String?) {
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
    val greeting = if (!userName.isNullOrBlank()) "${userName.uppercase()}" else "YOU ARE HERE"
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(greeting, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Light)
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

/** Thin radial streaks flashed briefly on a big, fast jump — a lightweight warp-speed cue. */
@Composable
private fun WarpStreaks() {
    val angles = remember { List(28) { Random.nextFloat() * 360f } }
    Canvas(modifier = Modifier.fillMaxSize()) {
        val c = Offset(size.width / 2f, size.height / 2f)
        val maxLen = size.minDimension * 0.7f
        angles.forEach { deg ->
            val rad = Math.toRadians(deg.toDouble())
            val start = Offset(c.x + (cos(rad) * maxLen * 0.15f).toFloat(), c.y + (sin(rad) * maxLen * 0.15f).toFloat())
            val end = Offset(c.x + (cos(rad) * maxLen).toFloat(), c.y + (sin(rad) * maxLen).toFloat())
            drawLine(Color.White.copy(alpha = 0.5f), start, end, strokeWidth = 1.4f)
        }
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
