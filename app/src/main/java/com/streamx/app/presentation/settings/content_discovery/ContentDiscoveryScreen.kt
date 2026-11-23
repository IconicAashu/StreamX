package com.streamx.app.presentation.settings.content_discovery

import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.domain.model.AddonManifest
import com.streamx.app.domain.model.MediaServerConfig
import com.streamx.app.domain.model.MediaServerType
import com.streamx.app.domain.model.ProviderModule
import com.streamx.app.presentation.theme.*

@Composable
fun ContentDiscoveryScreen(
    viewModel: ContentDiscoveryViewModel,
    onBackClick: () -> Unit,
    onNavigateToAddons: () -> Unit = {},
    onNavigateToJellyfin: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val addons by viewModel.addons.collectAsState()
    val mediaServers by viewModel.mediaServers.collectAsState()
    val customProviders by viewModel.customProviders.collectAsState()

    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.testResultMessage) {
        uiState.testResultMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
        }
    }

    Scaffold(
        snackbarHost = { SnackbarHost(snackbarHostState) },
        containerColor = NuvioBackground,
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
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
                        .clip(CircleShape)
                        .background(NuvioCardSurface)
                        .border(1.dp, NuvioBorder, CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back",
                        tint = NuvioTextPrimary,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                Text(
                    text = "Content & Discovery",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Section 1: SEARCH PREFERENCES
                item(key = "section_search") {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "SEARCH PREFERENCES",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextSecondary,
                            letterSpacing = 1.sp
                        )

                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                // Toggle Recent Searches
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Recent Searches",
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = NuvioTextPrimary
                                        )
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = "Remember and suggest queries on the Search screen",
                                            fontSize = 12.sp,
                                            color = NuvioTextSecondary
                                        )
                                    }

                                    Switch(
                                        checked = uiState.recentSearchesEnabled,
                                        onCheckedChange = { viewModel.setRecentSearchesEnabled(it) },
                                        colors = SwitchDefaults.colors(
                                            checkedThumbColor = Color.White,
                                            checkedTrackColor = NuvioAccentViolet,
                                            uncheckedThumbColor = NuvioTextSecondary,
                                            uncheckedTrackColor = NuvioPillBackground
                                        )
                                    )
                                }

                                HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f), thickness = 1.dp)

                                // Clear Search History Action
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.clearSearchHistory() }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.DeleteSweep,
                                        contentDescription = null,
                                        tint = Color(0xFFFF5252),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(
                                        text = "Clear Search History",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFFF5252)
                                    )
                                }
                            }
                        }
                    }
                }

                // Section 2.1: ADDONS (MANIFESTS)
                item(key = "section_addons_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "ADDONS (MANIFESTS)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextSecondary,
                            letterSpacing = 1.sp
                        )

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            IconButton(
                                onClick = { viewModel.refreshAddons() },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Refresh,
                                    contentDescription = "Refresh Addons",
                                    tint = NuvioTextSecondary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }

                            Button(
                                onClick = onNavigateToAddons,
                                colors = ButtonDefaults.buttonColors(containerColor = NuvioCardSurface),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NuvioAccentViolet),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("Manager", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = NuvioAccentViolet)
                            }

                            Button(
                                onClick = { viewModel.toggleAddAddonDialog(true) },
                                colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                                modifier = Modifier.height(30.dp)
                            ) {
                                Text("+ Add Addon", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.White)
                            }
                        }
                    }
                }

                item(key = "section_addons_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            addons.forEachIndexed { index, addon ->
                                AddonRow(
                                    addon = addon,
                                    onToggle = { viewModel.toggleAddon(addon.id) },
                                    onRemove = { viewModel.removeAddon(addon.id) }
                                )

                                if (index < addons.size - 1) {
                                    HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f), thickness = 1.dp)
                                }
                            }
                        }
                    }
                }

                // Section 2.2: MEDIA SERVERS (JELLYFIN / EMBY)
                item(key = "section_media_servers_header") {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "MEDIA SERVERS (SELF-HOSTED)",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextSecondary,
                            letterSpacing = 1.sp
                        )

                        Button(
                            onClick = { onNavigateToJellyfin() },
                            colors = ButtonDefaults.buttonColors(containerColor = NuvioCardSurface),
                            border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                            shape = RoundedCornerShape(10.dp),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            modifier = Modifier.height(30.dp)
                        ) {
                            Text("+ Connect Jellyfin", fontSize = 11.sp, fontWeight = FontWeight.SemiBold, color = NuvioTextPrimary)
                        }
                    }
                }

                item(key = "section_media_servers_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            if (mediaServers.isEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(24.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("No self-hosted media servers connected.", color = NuvioTextSecondary, fontSize = 13.sp)
                                }
                            } else {
                                mediaServers.forEachIndexed { index, server ->
                                    MediaServerRow(
                                        server = server,
                                        isTesting = uiState.testingServerId == server.id,
                                        onTestClick = { viewModel.testServerConnection(server) },
                                        onRemoveClick = { viewModel.removeMediaServer(server.id) }
                                    )

                                    if (index < mediaServers.size - 1) {
                                        HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f), thickness = 1.dp)
                                    }
                                }
                            }
                        }
                    }
                }

                // Section 2.3: CUSTOM PROVIDER SLOTS
                item(key = "section_providers_header") {
                    Text(
                        text = "CUSTOM PROVIDER SLOTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        color = NuvioTextSecondary,
                        letterSpacing = 1.sp
                    )
                }

                item(key = "section_providers_card") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column {
                            customProviders.forEachIndexed { index, provider ->
                                ProviderSlotRow(
                                    provider = provider,
                                    onToggle = { viewModel.toggleProvider(provider.id) }
                                )

                                if (index < customProviders.size - 1) {
                                    HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f), thickness = 1.dp)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Add Addon Dialog
        if (uiState.showAddAddonDialog) {
            var name by remember { mutableStateOf("") }
            var manifestUrl by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { viewModel.toggleAddAddonDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Install Addon Manifest", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Add a community or custom JSON manifest stream addon.", fontSize = 12.sp, color = NuvioTextSecondary)

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Addon Name", fontSize = 12.sp, color = NuvioTextSecondary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (name.isEmpty()) Text("e.g. Cinemeta Movies", color = NuvioTextSecondary, fontSize = 13.sp)
                                BasicTextField(
                                    value = name,
                                    onValueChange = { name = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                                    cursorBrush = SolidColor(NuvioAccentViolet),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Manifest URL", fontSize = 12.sp, color = NuvioTextSecondary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (manifestUrl.isEmpty()) Text("https://example.com/manifest.json", color = NuvioTextSecondary, fontSize = 12.sp)
                                BasicTextField(
                                    value = manifestUrl,
                                    onValueChange = { manifestUrl = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(NuvioAccentViolet),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (manifestUrl.isNotBlank()) {
                                viewModel.addAddon(
                                    name = name.ifBlank { "Custom Addon" },
                                    manifestUrl = manifestUrl.trim()
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Install", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleAddAddonDialog(false) }) {
                        Text("Cancel", color = NuvioTextSecondary)
                    }
                }
            )
        }

        // Add Media Server Dialog
        if (uiState.showAddServerDialog) {
            var selectedType by remember { mutableStateOf(MediaServerType.JELLYFIN) }
            var serverName by remember { mutableStateOf("") }
            var baseUrl by remember { mutableStateOf("") }
            var apiKey by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { viewModel.toggleAddServerDialog(false) },
                containerColor = NuvioCardSurface,
                titleContentColor = NuvioTextPrimary,
                textContentColor = NuvioTextSecondary,
                title = { Text("Connect Media Server", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Type Selector
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MediaServerType.values().forEach { type ->
                                val isSelected = type == selectedType
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(if (isSelected) NuvioAccentViolet else NuvioPillBackground)
                                        .clickable { selectedType = type }
                                        .padding(vertical = 8.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = type.displayName,
                                        fontSize = 13.sp,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) Color.White else NuvioTextSecondary
                                    )
                                }
                            }
                        }

                        // Server Name
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Display Name", fontSize = 11.sp, color = NuvioTextSecondary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (serverName.isEmpty()) Text("Home Media Server", color = NuvioTextSecondary, fontSize = 12.sp)
                                BasicTextField(
                                    value = serverName,
                                    onValueChange = { serverName = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(NuvioAccentViolet),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // Base URL
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("Server Base URL", fontSize = 11.sp, color = NuvioTextSecondary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (baseUrl.isEmpty()) Text("http://192.168.1.100:8096", color = NuvioTextSecondary, fontSize = 12.sp)
                                BasicTextField(
                                    value = baseUrl,
                                    onValueChange = { baseUrl = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(NuvioAccentViolet),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }

                        // API Key
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("API Key / Access Token", fontSize = 11.sp, color = NuvioTextSecondary)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(NuvioPillBackground)
                                    .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                            ) {
                                if (apiKey.isEmpty()) Text("Paste token here", color = NuvioTextSecondary, fontSize = 12.sp)
                                BasicTextField(
                                    value = apiKey,
                                    onValueChange = { apiKey = it },
                                    singleLine = true,
                                    textStyle = TextStyle(color = Color.White, fontSize = 12.sp),
                                    cursorBrush = SolidColor(NuvioAccentViolet),
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (baseUrl.isNotBlank()) {
                                viewModel.addMediaServer(
                                    type = selectedType,
                                    name = serverName,
                                    url = baseUrl.trim(),
                                    apiKey = apiKey.trim()
                                )
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("Connect", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.toggleAddServerDialog(false) }) {
                        Text("Cancel", color = NuvioTextSecondary)
                    }
                }
            )
        }
    }
}

@Composable
private fun AddonRow(
    addon: AddonManifest,
    onToggle: () -> Unit,
    onRemove: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = addon.name,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(NuvioPillBackground)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = "v${addon.version}",
                        fontSize = 10.sp,
                        color = NuvioTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = addon.description.ifBlank { addon.manifestUrl },
                fontSize = 12.sp,
                color = NuvioTextSecondary,
                maxLines = 1
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Switch(
                checked = addon.isEnabled,
                onCheckedChange = { onToggle() },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = NuvioAccentViolet,
                    uncheckedThumbColor = NuvioTextSecondary,
                    uncheckedTrackColor = NuvioPillBackground
                )
            )

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Remove",
                    tint = NuvioTextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
private fun MediaServerRow(
    server: MediaServerConfig,
    isTesting: Boolean,
    onTestClick: () -> Unit,
    onRemoveClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = server.serverName,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color(0xFF00A4DC).copy(alpha = 0.2f))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = server.type.displayName,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF00A4DC)
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = server.baseUrl,
                fontSize = 12.sp,
                color = NuvioTextSecondary
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onTestClick,
                colors = ButtonDefaults.buttonColors(containerColor = NuvioPillBackground),
                border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                modifier = Modifier.height(30.dp),
                enabled = !isTesting
            ) {
                if (isTesting) {
                    CircularProgressIndicator(
                        color = NuvioAccentViolet,
                        modifier = Modifier.size(14.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text("Test", fontSize = 11.sp, color = NuvioTextPrimary)
                }
            }

            IconButton(
                onClick = onRemoveClick,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Delete",
                    tint = NuvioTextSecondary.copy(alpha = 0.7f),
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ProviderSlotRow(
    provider: ProviderModule,
    onToggle: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = provider.name,
                    fontSize = 14.5.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = NuvioTextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (provider.isAuthorized) Color(0xFF2E7D32).copy(alpha = 0.25f) else NuvioPillBackground)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                ) {
                    Text(
                        text = if (provider.isAuthorized) "System Slot" else "Sandboxed",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (provider.isAuthorized) Color(0xFF81C784) else NuvioTextSecondary
                    )
                }
            }

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = provider.description,
                fontSize = 12.sp,
                color = NuvioTextSecondary
            )
        }

        Switch(
            checked = provider.isEnabled,
            onCheckedChange = { onToggle() },
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = NuvioAccentViolet,
                uncheckedThumbColor = NuvioTextSecondary,
                uncheckedTrackColor = NuvioPillBackground
            )
        )
    }
}
