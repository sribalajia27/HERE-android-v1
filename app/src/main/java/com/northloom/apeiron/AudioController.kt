package com.northloom.apeiron

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.*

class AudioController(private val context: Context) {
    private var player: ExoPlayer? = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var volumeJob: Job? = null

    private var isMusicEnabled = true
    private var userVolume = 0.7f
    private var phaseVolumeMultiplier = 1.0f
    private var isPlaying = false

    init {
        try {
            val resId = context.resources.getIdentifier("space_ambient", "raw", context.packageName)
            Log.d("AudioController", "Found raw resource space_ambient id: $resId")
            if (resId != 0) {
                player = ExoPlayer.Builder(context).build().apply {
                    val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
                    setMediaItem(mediaItem)
                    repeatMode = Player.REPEAT_MODE_ONE
                    prepare()
                }
                Log.d("AudioController", "ExoPlayer initialized successfully")
            } else {
                Log.e("AudioController", "space_ambient raw resource NOT found! Ensure file is named space_ambient.mp3 or space_ambient.ogg in res/raw/")
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Error initializing ExoPlayer", e)
        }
    }

    fun setCustomMusicUri(uriString: String?) {
        val p = player ?: return
        try {
            p.stop()
            if (!uriString.isNullOrBlank()) {
                val uri = Uri.parse(uriString)
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    // Ignore if persistable permission isn't needed/supported
                }
                val mediaItem = MediaItem.fromUri(uri)
                p.setMediaItem(mediaItem)
                p.repeatMode = Player.REPEAT_MODE_ONE
                p.prepare()
                Log.d("AudioController", "Loaded custom music URI: $uriString")
            } else {
                val resId = context.resources.getIdentifier("space_ambient", "raw", context.packageName)
                if (resId != 0) {
                    val mediaItem = MediaItem.fromUri("android.resource://${context.packageName}/$resId")
                    p.setMediaItem(mediaItem)
                    p.repeatMode = Player.REPEAT_MODE_ONE
                    p.prepare()
                    Log.d("AudioController", "Reverted to default space_ambient resource")
                }
            }
            if (isMusicEnabled) {
                play()
            }
        } catch (e: Exception) {
            Log.e("AudioController", "Error setting custom music URI", e)
        }
    }

    fun startDelayed(delayMillis: Long = 3000) {
        if (!isMusicEnabled) return
        scope.launch {
            delay(delayMillis)
            if (isMusicEnabled && !isPlaying) {
                play()
            }
        }
    }

    private fun play() {
        val p = player ?: return
        try {
            Log.d("AudioController", "Starting playback, volume set to 0, fading in...")
            p.playWhenReady = true
            p.volume = 0f
            isPlaying = true
            animateVolumeTo(targetVolume())
        } catch (e: Exception) {
            Log.e("AudioController", "Error playing audio", e)
        }
    }

    fun pause() {
        isPlaying = false
        player?.playWhenReady = false
        Log.d("AudioController", "Paused playback")
    }

    fun resume() {
        if (isMusicEnabled && player != null) {
            player?.playWhenReady = true
            isPlaying = true
            animateVolumeTo(targetVolume())
            Log.d("AudioController", "Resumed playback")
        }
    }

    fun setMusicEnabled(enabled: Boolean) {
        isMusicEnabled = enabled
        Log.d("AudioController", "setMusicEnabled: $enabled")
        if (enabled) {
            if (!isPlaying) play()
        } else {
            animateVolumeTo(0f) {
                player?.playWhenReady = false
                isPlaying = false
            }
        }
    }

    fun setUserVolume(volume: Float) {
        userVolume = volume.coerceIn(0f, 1f)
        if (isPlaying) {
            animateVolumeTo(targetVolume())
        }
    }

    fun setPhaseTargetVolume(multiplier: Float) {
        phaseVolumeMultiplier = multiplier.coerceIn(0f, 1f)
        if (isPlaying) {
            animateVolumeTo(targetVolume())
        }
    }

    private fun targetVolume(): Float {
        if (!isMusicEnabled) return 0f
        return (userVolume * phaseVolumeMultiplier).coerceIn(0f, 1f)
    }

    private fun animateVolumeTo(target: Float, onEnd: (() -> Unit)? = null) {
        val p = player ?: return
        volumeJob?.cancel()
        volumeJob = scope.launch {
            val startVol = p.volume
            val duration = 800L
            val startTime = System.currentTimeMillis()
            while (true) {
                val elapsed = System.currentTimeMillis() - startTime
                val fraction = (elapsed / duration.toFloat()).coerceIn(0f, 1f)
                val current = startVol + (target - startVol) * fraction
                p.volume = current
                if (fraction >= 1f) break
                delay(16)
            }
            p.volume = target
            onEnd?.invoke()
        }
    }

    fun release() {
        volumeJob?.cancel()
        scope.cancel()
        player?.release()
        player = null
        isPlaying = false
    }
}
