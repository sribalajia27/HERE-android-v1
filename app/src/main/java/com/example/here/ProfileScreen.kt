package com.example.here

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ProfileSetupScreen(onComplete: (name: String?, gender: String?, avatar: String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var selectedAvatar by remember { mutableStateOf<String?>(null) }
    
    val avatars = listOf("👨🏽", "👩🏽", "👦🏽", "👧🏽", "👱🏼‍♂️", "👱🏼‍♀️")

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF020204))
            .padding(24.dp)
    ) {
        Column(
            modifier = Modifier.align(Alignment.Center),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Who is taking this journey?",
                color = Color.White,
                fontSize = 22.sp,
                fontWeight = FontWeight.Light,
                textAlign = TextAlign.Center
            )
            Text(
                "This is completely optional and stored only on your device.",
                color = Color.White.copy(alpha = 0.5f),
                fontSize = 13.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 8.dp, bottom = 32.dp)
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

            Spacer(modifier = Modifier.height(32.dp))
            
            Text("Select an Avatar", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.SpaceEvenly,
                modifier = Modifier.fillMaxWidth()
            ) {
                avatars.forEach { avatar ->
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(
                                if (selectedAvatar == avatar) Color(0xFF4CC9F0).copy(alpha = 0.35f) 
                                else Color.Transparent
                            )
                            .clickable { selectedAvatar = avatar },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(avatar, fontSize = 28.sp)
                    }
                }
            }

            Spacer(modifier = Modifier.height(56.dp))

            Button(
                onClick = { onComplete(name.takeIf { it.isNotBlank() }, null, selectedAvatar) },
                colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color.Black),
                modifier = Modifier.fillMaxWidth().height(54.dp)
            ) {
                Text(
                    text = if (name.isBlank() && selectedAvatar == null) "Skip & Begin Journey" else "Begin Journey",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
