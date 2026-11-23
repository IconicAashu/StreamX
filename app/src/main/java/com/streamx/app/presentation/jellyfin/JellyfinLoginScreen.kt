package com.streamx.app.presentation.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun JellyfinLoginScreen(
    serverUrl: String,
    viewModel: JellyfinViewModel,
    onBackClick: () -> Unit,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    StreamXBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(20.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                // Back & Header
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(NuvioCardSurface)
                            .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = NuvioTextPrimary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column {
                        Text(
                            text = "Jellyfin Sign In",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                        Text(
                            text = serverUrl,
                            fontSize = 12.sp,
                            color = Color(0xFF00A4DC)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Card container
                Card(
                    shape = RoundedCornerShape(18.dp),
                    colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Username field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Username", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NuvioTextPrimary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 12.dp)
                            ) {
                                BasicTextField(
                                    value = uiState.usernameInput,
                                    onValueChange = { viewModel.setUsernameInput(it) },
                                    singleLine = true,
                                    textStyle = TextStyle(color = NuvioTextPrimary, fontSize = 14.sp),
                                    cursorBrush = SolidColor(NuvioAccentViolet),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Password field
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Password", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = NuvioTextPrimary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
                                    .padding(horizontal = 14.dp, vertical = 10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Box(modifier = Modifier.weight(1f)) {
                                        BasicTextField(
                                            value = uiState.passwordInput,
                                            onValueChange = { viewModel.setPasswordInput(it) },
                                            singleLine = true,
                                            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                            textStyle = TextStyle(color = NuvioTextPrimary, fontSize = 14.sp),
                                            cursorBrush = SolidColor(NuvioAccentViolet),
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    IconButton(
                                        onClick = { passwordVisible = !passwordVisible },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (passwordVisible) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                            contentDescription = "Toggle password",
                                            tint = NuvioTextSecondary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                }
                            }
                        }

                        // Error indicator
                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Text(
                                text = uiState.errorMessage!!,
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444)
                            )
                        }

                        // Sign In button
                        Button(
                            onClick = {
                                viewModel.login(serverUrl, onLoginSuccess)
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !uiState.isLoggingIn,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (uiState.isLoggingIn) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Sign In", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }
            }

            // Bottom security note
            Text(
                text = "Credentials are sent directly to your server over TLS/HTTP and stored locally on your device.",
                fontSize = 11.sp,
                color = NuvioTextSecondary.copy(alpha = 0.8f),
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }
    }
}
