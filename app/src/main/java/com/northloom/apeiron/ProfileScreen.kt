package com.northloom.apeiron

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileSetupScreen(
    initialName: String? = null,
    initialAvatar: String? = null,
    initialMusicEnabled: Boolean = true,
    initialMusicVolume: Float = 0.7f,
    initialHapticsEnabled: Boolean = true,
    initialCustomMusicUri: String? = null,
    isEditMode: Boolean = false,
    onComplete: (name: String?, avatar: String?, musicEnabled: Boolean, musicVolume: Float, hapticsEnabled: Boolean, customMusicUri: String?) -> Unit
) {
    var name by remember { mutableStateOf(initialName ?: "") }
    var selectedAvatar by remember { mutableStateOf(initialAvatar) }
    var musicEnabled by remember { mutableStateOf(initialMusicEnabled) }
    var musicVolume by remember { mutableStateOf(initialMusicVolume) }
    var hapticsEnabled by remember { mutableStateOf(initialHapticsEnabled) }
    var customMusicUri by remember { mutableStateOf(initialCustomMusicUri) }

    val context = LocalContext.current
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (e: Exception) {
                // Ignore if persistable permission isn't supported
            }
            customMusicUri = uri.toString()
        }
    }

    val premiumAvatars = listOf(
        "👨‍🚀", "👩‍🚀", "🧑‍🔬", "🧭",
        "🪐", "✨", "🌌", "🔭",
        "⚛️", "🌙", "🔮", "🧬"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020204))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(top = 32.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (isEditMode) "Settings & Identity" else "Who is taking this journey?",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Text(
                text = if (isEditMode) "Customize your experience. Stored locally." else "Choose a cosmic persona. Stored only on your device.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 28.dp)
            )

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your Name (Optional)") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Color(0xFF4CC9F0),
                    unfocusedBorderColor = Color.White.copy(alpha = 0.3f),
                    focusedLabelColor = Color(0xFF4CC9F0),
                    unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White
                ),
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text("Select Your Avatar", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))

            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                premiumAvatars.chunked(4).forEach { rowItems ->
                    Row(
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowItems.forEach { avatar ->
                            val isSelected = selectedAvatar == avatar
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (isSelected) Color(0xFF4CC9F0).copy(alpha = 0.35f)
                                        else Color.White.copy(alpha = 0.05f)
                                    )
                                    .clickable { selectedAvatar = avatar },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(avatar, fontSize = 28.sp)
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // --- SETTINGS SECTION ---
            HorizontalDivider(color = Color.White.copy(alpha = 0.1f))
            Spacer(modifier = Modifier.height(24.dp))

            // Music Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Background Music", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Light)
                    Text("Ambient space score", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                Switch(
                    checked = musicEnabled,
                    onCheckedChange = { musicEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF4CC9F0),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            if (musicEnabled) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Music Volume", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                        Text("${(musicVolume * 100).toInt()}%", color = Color(0xFF4CC9F0), fontSize = 13.sp)
                    }
                    Slider(
                        value = musicVolume,
                        onValueChange = { musicVolume = it },
                        valueRange = 0f..1f,
                        colors = SliderDefaults.colors(
                            thumbColor = Color(0xFF4CC9F0),
                            activeTrackColor = Color(0xFF4CC9F0),
                            inactiveTrackColor = Color.White.copy(alpha = 0.2f)
                        )
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Custom Music Selection
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text("Custom Audio Track", color = Color.White.copy(alpha = 0.8f), fontSize = 13.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = { audioPickerLauncher.launch(arrayOf("audio/*")) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.1f), contentColor = Color.White),
                            modifier = Modifier.weight(1f).height(42.dp)
                        ) {
                            Text(if (customMusicUri != null) "Change Audio File..." else "Select Custom Audio File...", fontSize = 13.sp)
                        }
                        if (customMusicUri != null) {
                            TextButton(
                                onClick = { customMusicUri = null }
                            ) {
                                Text("Reset", color = Color(0xFFF72585), fontSize = 13.sp)
                            }
                        }
                    }
                    if (customMusicUri != null) {
                        Text(
                            text = "✦ Custom track selected",
                            color = Color(0xFF4CC9F0),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Haptics Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Haptic Feedback", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Light)
                    Text("Physical resonance during navigation", color = Color.White.copy(alpha = 0.5f), fontSize = 12.sp)
                }
                Switch(
                    checked = hapticsEnabled,
                    onCheckedChange = { hapticsEnabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.Black,
                        checkedTrackColor = Color(0xFF4CC9F0),
                        uncheckedThumbColor = Color.White.copy(alpha = 0.5f),
                        uncheckedTrackColor = Color.White.copy(alpha = 0.1f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(40.dp))

            Button(
                onClick = {
                    onComplete(
                        name.takeIf { it.isNotBlank() },
                        selectedAvatar,
                        musicEnabled,
                        musicVolume,
                        hapticsEnabled,
                        customMusicUri
                    )
                },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    text = if (isEditMode) "Save Changes" else (if (name.isBlank() && selectedAvatar == null) "Skip & Begin Journey" else "Begin Journey"),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
