package com.example.here

// import android.media.AudioManager
// import android.media.ToneGenerator
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
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
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
import kotlin.math.roundToInt
import kotlin.time.Duration.Companion.milliseconds

private enum class Phase { INTRO, JOURNEY, ARRIVED, BEYOND, EARTH_FOUND, RETURNING }

// --- TUNING KNOBS ---
private const val DRAG_SENSITIVITY = 1f / 280f
private const val PINCH_SENSITIVITY = 2.4f
private const val SETTLE_WINDOW = 0.12f
private const val RUBBER_BAND_DAMPING = 0.3f

private val COOL_BG = Color(0xFF020204)
private val WARM_BG = Color(0xFF1A0F08)

@Composable
fun CosmicZoomScreen(
    userName: String? = null,
    userAvatar: String? = null,
    onEditProfile: () -> Unit = {}
) {
    var phase by remember { mutableStateOf(Phase.INTRO) }
    var zoomLevel by remember { mutableFloatStateOf(0f) }
    var settledLevel by remember { mutableIntStateOf(0) }
    var showScaleDetail by remember { mutableStateOf(false) }
    var animJob by remember { mutableStateOf<Job?>(null) }
    var shakeOffset by remember { mutableStateOf(Offset.Zero) }
    var lastLevelForShake by remember { mutableIntStateOf(0) }

    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

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
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
        if (phase == Phase.JOURNEY && levelInt >= COSMIC_LEVELS.size - 1) {
            phase = Phase.ARRIVED
        }
        if (phase == Phase.RETURNING && levelInt <= EARTH_LEVEL) {
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
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
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
        Phase.BEYOND -> 0.55f
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

        // The "YOU" marker fades out entirely by the time you reach the Milky Way.
        // It emphasizes how totally lost and invisible humanity is at the galactic scale.
        val markerAlpha = (1f - (zoomLevel / 5.5f)).coerceIn(0f, 1f)
        if (markerAlpha > 0f) {
            // As you get further away (past the Moon), the text desperately points out where you are.
            val baseName = if (!userName.isNullOrBlank()) userName.uppercase() else "YOU"
            val markerText = if (zoomLevel > 3.5f) "● $baseName ARE HERE" else "● $baseName"
            Text(
                text = markerText,
                color = Color.White.copy(alpha = markerAlpha * 0.7f),
                fontSize = 12.sp,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(top = 48.dp, start = 20.dp)
                    .clickable(enabled = phase == Phase.INTRO || phase == Phase.JOURNEY) {
                        playClickSound()
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onEditProfile()
                    }
                    .padding(8.dp) // Increase touch target size slightly
            )
        }

        if (phase == Phase.JOURNEY || phase == Phase.ARRIVED || phase == Phase.BEYOND || phase == Phase.RETURNING || phase == Phase.EARTH_FOUND) {
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
        if (phase == Phase.JOURNEY || phase == Phase.ARRIVED || phase == Phase.BEYOND || phase == Phase.RETURNING || phase == Phase.EARTH_FOUND) {
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
                .padding(bottom = 96.dp)
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
                .padding(bottom = 96.dp)
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
                onRestart = {
                    playClickSound()
                    haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    animJob?.cancel()
                    scope.launch {
                        phase = Phase.RETURNING // Temp phase to hide narrative UI during animation
                        animate(
                            initialValue = zoomLevel,
                            targetValue = 0f,
                            animationSpec = tween(2500, easing = LinearOutSlowInEasing)
                        ) { value, _ -> zoomLevel = value }
                        phase = Phase.INTRO
                    }
                }
            )
        }
    }
}

@Composable
private fun EarthFoundSequence(onRestart: () -> Unit) {
    var step by remember { mutableIntStateOf(0) }
    
    // The sequence drives the fading in and out of the three stanzas.
    LaunchedEffect(Unit) {
        delay(1000.milliseconds) 
        step = 1 // First Load In
        
        delay(4000.milliseconds)
        step = 2 // First Load Out
        delay(1000.milliseconds)
        
        step = 3 // Second Load In
        delay(6500.milliseconds)
        step = 4 // Second Load Out
        delay(1000.milliseconds)
        
        step = 5 // Third Load In
        delay(7000.milliseconds) // Give time to read the final profound text
        step = 6 // Show the restart button
    }
    
    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 92.dp, start = 36.dp, end = 36.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // First load
            AnimatedVisibility(
                visible = step == 1,
                enter = fadeIn(tween(1500)),
                exit = fadeOut(tween(1000))
            ) {
                Text(
                    "Somewhere on this tiny world,\nyou are living your life right now.",
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    lineHeight = 24.sp
                )
            }
            
            // Second load
            AnimatedVisibility(
                visible = step == 3,
                enter = fadeIn(tween(1500)),
                exit = fadeOut(tween(1000))
            ) {
                Text(
                    "Everything you've ever known is here.\n\nEveryone you've ever loved.\nEvery joy.\nEvery loss.\nEvery moment you thought would last forever.\n\nHere.",
                    color = Color.White,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    lineHeight = 22.sp
                )
            }
            
            // Third load
            AnimatedVisibility(
                visible = step >= 5, // Stays on screen
                enter = fadeIn(tween(2000))
            ) {
                Text(
                    "Now look at where “here” really is.\n\nAn ordinary planet,\ndrifting through an incomprehensible universe.\n\nAnd for all we know…\nthis tiny blue world is the only place\nwhere any of it has ever happened.\n\nYou are a brief moment of the universe,\nbecoming aware of itself.\n\nIn a universe this vast, perhaps it's okay to let some things go.\n\nBe kind. Enjoy the little things.",
                    color = Color.White,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Light,
                    textAlign = TextAlign.Center,
                    lineHeight = 21.sp
                )
            }
        }

        // Restart Action Button
        AnimatedVisibility(
            visible = step >= 6,
            enter = fadeIn(tween(1500)),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
        ) {
            Button(
                onClick = onRestart,
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black)
            ) {
                Text("Begin Again ➔", fontSize = 12.sp)
            }
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
