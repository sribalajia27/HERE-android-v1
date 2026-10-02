package com.northloom.apeiron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.northloom.apeiron.ui.theme.HereTheme

class MainActivity : ComponentActivity() {
    private lateinit var audioController: AudioController

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Load rich cosmic facts from JSON assets into cache
        getCosmicLevels(this)

        audioController = AudioController(this)

        val prefs = getSharedPreferences("ApeironPrefs", MODE_PRIVATE)
        val initialHasSeen = prefs.getBoolean("hasSeenProfile", false)
        val musicEnabled = prefs.getBoolean("musicEnabled", true)
        val musicVolume = prefs.getFloat("musicVolume", 0.7f)
        val hapticsEnabled = prefs.getBoolean("hapticsEnabled", true)
        val customMusicUri = prefs.getString("customMusicUri", null)

        audioController.setMusicEnabled(musicEnabled)
        audioController.setUserVolume(musicVolume)
        audioController.setCustomMusicUri(customMusicUri)

        if (initialHasSeen && musicEnabled) {
            audioController.startDelayed(3000)
        }

        setContent {
            HereTheme {
                var hasSeenProfile by remember { mutableStateOf(initialHasSeen) }
                var showProfile by remember { mutableStateOf(!initialHasSeen) }
                var currentName by remember { mutableStateOf(prefs.getString("userName", null)) }
                var currentAvatar by remember { mutableStateOf(prefs.getString("userAvatar", null)) }
                var currentMusicEnabled by remember { mutableStateOf(musicEnabled) }
                var currentMusicVolume by remember { mutableStateOf(musicVolume) }
                var currentHapticsEnabled by remember { mutableStateOf(hapticsEnabled) }
                var currentCustomUri by remember { mutableStateOf(customMusicUri) }

                if (showProfile) {
                    ProfileSetupScreen(
                        initialName = currentName,
                        initialAvatar = currentAvatar,
                        initialMusicEnabled = currentMusicEnabled,
                        initialMusicVolume = currentMusicVolume,
                        initialHapticsEnabled = currentHapticsEnabled,
                        initialCustomMusicUri = currentCustomUri,
                        isEditMode = hasSeenProfile,
                        onComplete = { name, avatar, musicEn, musicVol, hapticsEn, customUri ->
                            prefs.edit()
                                .putBoolean("hasSeenProfile", true)
                                .putString("userName", name)
                                .putString("userAvatar", avatar)
                                .putBoolean("musicEnabled", musicEn)
                                .putFloat("musicVolume", musicVol)
                                .putBoolean("hapticsEnabled", hapticsEn)
                                .putString("customMusicUri", customUri)
                                .apply()

                            currentName = name
                            currentAvatar = avatar
                            currentMusicEnabled = musicEn
                            currentMusicVolume = musicVol
                            currentHapticsEnabled = hapticsEn
                            currentCustomUri = customUri

                            audioController.setMusicEnabled(musicEn)
                            audioController.setUserVolume(musicVol)
                            audioController.setCustomMusicUri(customUri)
                            if (musicEn && !hasSeenProfile) {
                                audioController.startDelayed(1000)
                            }
                            hasSeenProfile = true
                            showProfile = false
                        }
                    )
                } else {
                    CosmicZoomScreen(
                        userName = currentName,
                        userAvatar = currentAvatar,
                        musicEnabled = currentMusicEnabled,
                        musicVolume = currentMusicVolume,
                        hapticsEnabled = currentHapticsEnabled,
                        audioController = audioController,
                        onEditProfile = { showProfile = true }
                    )
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        audioController.resume()
    }

    override fun onPause() {
        super.onPause()
        audioController.pause()
    }

    override fun onDestroy() {
        super.onDestroy()
        audioController.release()
    }
}
