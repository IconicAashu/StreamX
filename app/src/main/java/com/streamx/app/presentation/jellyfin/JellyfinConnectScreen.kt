package com.streamx.app.presentation.jellyfin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.network.jellyfin.client.JellyfinClientFactory
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun JellyfinConnectScreen(
    viewModel: JellyfinViewModel,
    onBackClick: () -> Unit,
    onNavigateToLogin: (String) -> Unit,
    onNavigateToLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val activeServer by viewModel.activeServer.collectAsState()

    StreamXBackground(modifier = modifier) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                        text = "Connect Jellyfin",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextPrimary
                    )
                    Text(
                        text = "Personal Media Streaming Server",
                        fontSize = 12.sp,
                        color = NuvioTextSecondary
                    )
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // If active server is already connected, show Connected banner
                if (activeServer != null && activeServer!!.isConnected) {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF00A4DC).copy(alpha = 0.12f)),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00A4DC).copy(alpha = 0.4f)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF00A4DC),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Currently Connected",
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00A4DC)
                                )
                            }
                            Text(
                                text = "${activeServer!!.serverName} • ${activeServer!!.username.ifBlank { "User" }}",
                                fontSize = 13.sp,
                                color = NuvioTextPrimary
                            )
                            Text(
                                text = activeServer!!.baseUrl,
                                fontSize = 11.sp,
                                color = NuvioTextSecondary
                            )
                            Button(
                                onClick = onNavigateToLibrary,
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00A4DC)),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                            ) {
                                Text("Open Jellyfin Library", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Server Address Input Card
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Server Address",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = NuvioTextPrimary
                        )

                        Text(
                            text = "Enter your Jellyfin server IP or domain (e.g. https://jellyfin.local:8096)",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary
                        )

                        // Input field
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(NuvioPillBackground)
                                .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
                                .padding(horizontal = 14.dp, vertical = 12.dp)
                        ) {
                            BasicTextField(
                                value = uiState.serverUrlInput,
                                onValueChange = { viewModel.setServerUrlInput(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = NuvioTextPrimary,
                                    fontSize = 14.sp
                                ),
                                cursorBrush = SolidColor(NuvioAccentViolet),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Error message
                        if (!uiState.errorMessage.isNullOrBlank()) {
                            Text(
                                text = uiState.errorMessage!!,
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444)
                            )
                        }

                        // Connect Button
                        Button(
                            onClick = {
                                viewModel.connectServer { validUrl ->
                                    onNavigateToLogin(validUrl)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                            shape = RoundedCornerShape(12.dp),
                            enabled = !uiState.isConnecting,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(46.dp)
                        ) {
                            if (uiState.isConnecting) {
                                CircularProgressIndicator(color = Color.White, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                            } else {
                                Text("Connect to Server", fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                // Quick Connect Section
                Card(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                    border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.QrCodeScanner,
                                contentDescription = null,
                                tint = NuvioAccentViolet,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Quick Connect",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = NuvioTextPrimary
                            )
                        }

                        Text(
                            text = "Authenticate securely using a Quick Connect PIN on your Jellyfin web dashboard or client.",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary
                        )

                        val qc = uiState.quickConnectInitiate
                        if (qc != null) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(NuvioPillBackground)
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        text = qc.code,
                                        fontSize = 28.sp,
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 4.sp,
                                        color = NuvioTextPrimary
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Enter this code in Jellyfin > Quick Connect",
                                        fontSize = 11.sp,
                                        color = NuvioTextSecondary
                                    )
                                    if (uiState.isQuickConnectPolling) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(12.dp), strokeWidth = 2.dp, color = NuvioAccentViolet)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Waiting for authorization...", fontSize = 11.sp, color = NuvioAccentViolet)
                                        }
                                    }
                                }
                            }
                        } else {
                            OutlinedButton(
                                onClick = {
                                    val rawUrl = uiState.serverUrlInput
                                    val valid = JellyfinClientFactory.validateServerUrl(rawUrl)
                                    if (valid.isSuccess) {
                                        viewModel.initiateQuickConnect(valid.getOrThrow())
                                    } else {
                                        viewModel.setServerUrlInput(rawUrl)
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Generate Quick Connect Code", color = NuvioTextPrimary, fontSize = 13.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
