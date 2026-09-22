package com.example.here

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import com.example.here.ui.theme.HereTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val prefs = getSharedPreferences("ApeironPrefs", MODE_PRIVATE)
        val hasSeenProfile = prefs.getBoolean("hasSeenProfile", false)
        
        setContent {
            HereTheme {
                var showProfile by remember { mutableStateOf(!hasSeenProfile) }
                
                if (showProfile) {
                    ProfileSetupScreen(
                        onComplete = { name, gender, avatar ->
                            prefs.edit()
                                .putBoolean("hasSeenProfile", true)
                                .putString("userName", name)
                                .putString("userGender", gender)
                                .putString("userAvatar", avatar)
                                .apply()
                            showProfile = false
                        }
                    )
                } else {
                    val userName = prefs.getString("userName", null)
                    val userAvatar = prefs.getString("userAvatar", null)
                    CosmicZoomScreen(
                        userName = userName,
                        userAvatar = userAvatar,
                        onEditProfile = { showProfile = true }
                    )
                }
            }
        }
    }
}
