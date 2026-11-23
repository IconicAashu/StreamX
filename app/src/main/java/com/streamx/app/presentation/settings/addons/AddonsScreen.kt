package com.streamx.app.presentation.settings.addons

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.streamx.app.domain.model.AddonStatus
import com.streamx.app.domain.model.InstalledAddon
import com.streamx.app.domain.model.StremioManifest
import com.streamx.app.presentation.components.GlassPill
import com.streamx.app.presentation.components.StreamXBackground
import com.streamx.app.presentation.theme.*

@Composable
fun AddonsScreen(
    viewModel: AddonsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val installedAddons by viewModel.installedAddons.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.feedbackMessage) {
        uiState.feedbackMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearFeedbackMessage()
        }
    }

    StreamXBackground(modifier = modifier) {
        Scaffold(
            snackbarHost = { SnackbarHost(snackbarHostState) },
            containerColor = Color.Transparent,
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Top App Bar Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
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

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add-ons",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                        Text(
                            text = "Stremio Protocol Engine",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary
                        )
                    }

                    // Refresh Manifests Action
                    IconButton(
                        onClick = { viewModel.refreshAllAddons() },
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(NuvioCardSurface)
                            .border(1.dp, NuvioBorder, CircleShape)
                    ) {
                        if (uiState.isRefreshing) {
                            CircularProgressIndicator(
                                color = NuvioAccentViolet,
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(18.dp)
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Refresh Manifests",
                                tint = NuvioTextPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                // Translucent Segmented Bar: Installed vs Browse
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NuvioCardSurface)
                        .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                        .padding(4.dp)
                ) {
                    val tabs = listOf(
                        AddonTab.INSTALLED to "Installed (${installedAddons.size})",
                        AddonTab.BROWSE to "Browse Add-ons"
                    )

                    tabs.forEach { (tab, label) ->
                        val isSelected = uiState.selectedTab == tab
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) NuvioAccentViolet else Color.Transparent)
                                .clickable { viewModel.setTab(tab) }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                fontSize = 13.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) Color.White else NuvioTextSecondary
                            )
                        }
                    }
                }

                // Frosted Glass Search Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 6.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NuvioCardSurface)
                        .border(1.dp, NuvioBorder, RoundedCornerShape(16.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Search",
                            tint = NuvioTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )

                        Spacer(modifier = Modifier.width(10.dp))

                        Box(modifier = Modifier.weight(1f)) {
                            if (uiState.searchQuery.isEmpty()) {
                                Text(
                                    text = "Search add-ons...",
                                    fontSize = 14.sp,
                                    color = NuvioTextSecondary
                                )
                            }
                            BasicTextField(
                                value = uiState.searchQuery,
                                onValueChange = { viewModel.setSearchQuery(it) },
                                singleLine = true,
                                textStyle = TextStyle(
                                    color = NuvioTextPrimary,
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium
                                ),
                                cursorBrush = SolidColor(NuvioAccentViolet),
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (uiState.searchQuery.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Close,
                                contentDescription = "Clear",
                                tint = NuvioTextSecondary,
                                modifier = Modifier
                                    .size(18.dp)
                                    .clickable { viewModel.setSearchQuery("") }
                            )
                        }
                    }
                }

                // Category Filter Chips Row (All, Official, Community, Streams, Catalogs, Subtitles)
                val categories = remember {
                    listOf("All", "Official", "Community", "Streams", "Catalogs", "Subtitles")
                }
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                ) {
                    items(categories) { category ->
                        val isSelected = uiState.selectedCategory == category
                        GlassPill(
                            text = category,
                            isSelected = isSelected,
                            onClick = { viewModel.setCategoryFilter(category) }
                        )
                    }
                }

                // Tab Content with generous bottom padding for navigation clearance
                when (uiState.selectedTab) {
                    AddonTab.INSTALLED -> {
                        val filteredInstalled = installedAddons.filter { addon ->
                            val q = uiState.searchQuery.trim().lowercase()
                            val matchesSearch = q.isEmpty() ||
                                addon.name.lowercase().contains(q) ||
                                addon.description.lowercase().contains(q) ||
                                addon.resources.any { r -> r.name.lowercase().contains(q) } ||
                                addon.types.any { t -> t.lowercase().contains(q) }

                            val matchesCategory = when (uiState.selectedCategory) {
                                "Official" -> addon.name.contains("Official", ignoreCase = true) || addon.id.contains("cinemeta", ignoreCase = true)
                                "Community" -> !addon.name.contains("Official", ignoreCase = true)
                                "Streams" -> addon.isStreamProvider
                                "Catalogs" -> addon.resources.any { it.name.equals("catalog", ignoreCase = true) }
                                "Subtitles" -> addon.isSubtitlesOnly
                                else -> true
                            }

                            matchesSearch && matchesCategory
                        }

                        InstalledAddonsList(
                            addons = filteredInstalled,
                            onInstallClick = { viewModel.openInstallDialog() },
                            onToggle = { viewModel.toggleAddon(it) },
                            onMoveUp = { viewModel.moveAddonUp(it) },
                            onMoveDown = { viewModel.moveAddonDown(it) },
                            onRefresh = { viewModel.refreshAddon(it) },
                            onSelectDetails = { viewModel.selectAddonDetails(it) }
                        )
                    }
                    AddonTab.BROWSE -> {
                        val filteredBrowse = viewModel.curatedAddons.filter { item ->
                            val q = uiState.searchQuery.trim().lowercase()
                            val matchesSearch = q.isEmpty() ||
                                item.name.lowercase().contains(q) ||
                                item.description.lowercase().contains(q) ||
                                item.types.any { t -> t.lowercase().contains(q) }

                            val matchesCategory = when (uiState.selectedCategory) {
                                "Official" -> item.author.contains("Official", ignoreCase = true) || item.name.contains("Official", ignoreCase = true)
                                "Community" -> !item.author.contains("Official", ignoreCase = true)
                                "Streams" -> item.resources.any { it.contains("Stream", ignoreCase = true) }
                                "Catalogs" -> item.resources.any { it.contains("Catalog", ignoreCase = true) || it.contains("Meta", ignoreCase = true) }
                                "Subtitles" -> item.resources.any { it.contains("Subtitle", ignoreCase = true) }
                                else -> true
                            }

                            matchesSearch && matchesCategory
                        }

                        BrowseAddonsList(
                            curated = filteredBrowse,
                            installedUrls = installedAddons.map { it.manifestUrl },
                            onCustomUrlClick = { viewModel.openInstallDialog() },
                            onInstallCurated = { viewModel.installCuratedAddon(it) }
                        )
                    }
                }
            }

            // Install Add-on Dialog
            if (uiState.showInstallDialog) {
                InstallAddonDialog(
                    urlInput = uiState.installUrlInput,
                    isVerifying = uiState.isVerifyingManifest,
                    manifestPreview = uiState.manifestPreview,
                    errorMessage = uiState.installErrorMessage,
                    onUrlChange = { viewModel.onUrlInputChanged(it) },
                    onVerify = { viewModel.verifyManifest(it) },
                    onConfirm = { viewModel.confirmInstall() },
                    onDismiss = { viewModel.closeInstallDialog() }
                )
            }

            // Add-on Details / Inspection Sheet Dialog
            uiState.selectedAddonForDetails?.let { addon ->
                AddonDetailsDialog(
                    addon = addon,
                    onDismiss = { viewModel.selectAddonDetails(null) },
                    onToggle = { viewModel.toggleAddon(addon.id) },
                    onRefresh = { viewModel.refreshAddon(addon.id) },
                    onRemove = { viewModel.removeAddon(addon.id) }
                )
            }
        }
    }
}

@Composable
private fun InstalledAddonsList(
    addons: List<InstalledAddon>,
    onInstallClick: () -> Unit,
    onToggle: (String) -> Unit,
    onMoveUp: (Int) -> Unit,
    onMoveDown: (Int) -> Unit,
    onRefresh: (String) -> Unit,
    onSelectDetails: (InstalledAddon) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // "Install Add-on" primary button card
        item(key = "install_button_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                border = BorderStroke(1.dp, NuvioAccentViolet.copy(alpha = 0.4f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onInstallClick() }
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NuvioAccentViolet),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "Install Add-on",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(16.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Install Custom Add-on",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                        Text(
                            text = "Paste HTTP/HTTPS Stremio manifest URL",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.Link,
                        contentDescription = null,
                        tint = NuvioAccentViolet,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (addons.isEmpty()) {
            item(key = "empty_addons") {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ExtensionOff,
                            contentDescription = null,
                            tint = NuvioTextSecondary,
                            modifier = Modifier.size(48.dp)
                        )
                        Text(
                            text = "No add-ons matching criteria",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            color = NuvioTextSecondary
                        )
                    }
                }
            }
        } else {
            itemsIndexed(addons, key = { _, item -> item.id }) { index, addon ->
                InstalledAddonCard(
                    addon = addon,
                    index = index,
                    totalCount = addons.size,
                    onToggle = { onToggle(addon.id) },
                    onMoveUp = { onMoveUp(index) },
                    onMoveDown = { onMoveDown(index) },
                    onRefresh = { onRefresh(addon.id) },
                    onCardClick = { onSelectDetails(addon) }
                )
            }
        }
    }
}

@Composable
private fun InstalledAddonCard(
    addon: InstalledAddon,
    index: Int,
    totalCount: Int,
    onToggle: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRefresh: () -> Unit,
    onCardClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
        border = BorderStroke(1.dp, NuvioBorder),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onCardClick)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header: Logo + Title + Version + Enable Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Addon Logo / Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!addon.logo.isNullOrBlank()) {
                        AsyncImage(
                            model = addon.logo,
                            contentDescription = addon.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Filled.Extension,
                            contentDescription = null,
                            tint = NuvioAccentViolet,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = addon.name,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "v${addon.version}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = NuvioTextSecondary
                        )
                    }

                    // Status Pill
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.padding(top = 2.dp)
                    ) {
                        val (dotColor, statusText) = when (addon.status) {
                            AddonStatus.READY -> if (addon.isEnabled) Color(0xFF10B981) to "Active" else Color(0xFF86868B) to "Disabled"
                            AddonStatus.LOADING -> Color(0xFFF59E0B) to "Connecting..."
                            AddonStatus.ERROR -> Color(0xFFEF4444) to "Error"
                            AddonStatus.DISABLED -> Color(0xFF86868B) to "Disabled"
                            AddonStatus.NEEDS_CONFIGURATION -> Color(0xFFFBBF24) to "Setup Needed"
                        }

                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(dotColor)
                        )
                        Text(
                            text = statusText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = dotColor
                        )

                        if (!addon.lastErrorMessage.isNullOrBlank() && addon.status == AddonStatus.ERROR) {
                            Text(
                                text = "• ${addon.lastErrorMessage}",
                                fontSize = 11.sp,
                                color = Color(0xFFEF4444),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                // Enable/Disable Switch
                Switch(
                    checked = addon.isEnabled,
                    onCheckedChange = { onToggle() },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = NuvioAccentViolet,
                        uncheckedThumbColor = NuvioTextSecondary,
                        uncheckedTrackColor = NuvioSecondarySurface
                    )
                )
            }

            // Description
            if (addon.description.isNotBlank()) {
                Text(
                    text = addon.description,
                    fontSize = 12.sp,
                    color = NuvioTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Capability & Type Badges in a horizontal LazyRow so they NEVER clip
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (addon.isStreamProvider) {
                    item { PillBadge(text = "Streams Provider", isPrimary = true) }
                } else if (addon.isSubtitlesOnly) {
                    item { PillBadge(text = "Subtitles Only", isPrimary = false) }
                } else {
                    item { PillBadge(text = "Metadata Catalog", isPrimary = false) }
                }

                items(addon.resources) { res ->
                    PillBadge(text = res.name.replaceFirstChar(Char::titlecase), isPrimary = false)
                }

                items(addon.types) { typ ->
                    PillBadge(text = typ.replaceFirstChar(Char::titlecase), isPrimary = false)
                }
            }

            // Ordering and Actions
            HorizontalDivider(color = NuvioBorder.copy(alpha = 0.6f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Priority #${index + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = NuvioTextSecondary
                )

                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    IconButton(
                        onClick = onMoveUp,
                        enabled = index > 0,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowUpward,
                            contentDescription = "Move Up",
                            tint = if (index > 0) NuvioTextPrimary else NuvioTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onMoveDown,
                        enabled = index < totalCount - 1,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.ArrowDownward,
                            contentDescription = "Move Down",
                            tint = if (index < totalCount - 1) NuvioTextPrimary else NuvioTextSecondary.copy(alpha = 0.3f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    IconButton(
                        onClick = onRefresh,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh Addon",
                            tint = NuvioAccentViolet,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BrowseAddonsList(
    curated: List<CuratedAddon>,
    installedUrls: List<String>,
    onCustomUrlClick: () -> Unit,
    onInstallCurated: (CuratedAddon) -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 8.dp, bottom = 140.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Custom URL input card
        item(key = "custom_url_card") {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                border = BorderStroke(1.dp, NuvioBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onCustomUrlClick() }
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(NuvioSecondarySurface)
                            .border(1.dp, NuvioBorder, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.AddLink,
                            contentDescription = null,
                            tint = NuvioAccentViolet,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Add Custom Add-on",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = NuvioTextPrimary
                        )
                        Text(
                            text = "Install from any Stremio-compatible URL",
                            fontSize = 12.sp,
                            color = NuvioTextSecondary
                        )
                    }

                    Icon(
                        imageVector = Icons.Filled.ChevronRight,
                        contentDescription = null,
                        tint = NuvioTextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        item(key = "curated_header") {
            Text(
                text = "CURATED PROVIDER CATALOGUE",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = NuvioTextSecondary,
                letterSpacing = 1.sp,
                modifier = Modifier.padding(start = 4.dp, top = 8.dp)
            )
        }

        itemsIndexed(curated, key = { _, item -> item.manifestUrl }) { _, item ->
            val isAlreadyInstalled = installedUrls.any {
                it.equals(item.manifestUrl, ignoreCase = true) ||
                it.substringBeforeLast("/manifest.json") == item.manifestUrl.substringBeforeLast("/manifest.json")
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                border = BorderStroke(1.dp, NuvioBorder),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(42.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(NuvioSecondarySurface)
                                .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!item.iconUrl.isNullOrBlank()) {
                                AsyncImage(
                                    model = item.iconUrl,
                                    contentDescription = item.name,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxSize()
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Filled.Extension,
                                    contentDescription = null,
                                    tint = NuvioAccentViolet,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.width(12.dp))

                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.name,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold,
                                color = NuvioTextPrimary
                            )
                            Text(
                                text = "By ${item.author}",
                                fontSize = 11.5.sp,
                                color = NuvioTextSecondary
                            )
                        }

                        // Status Badge or Install Button
                        if (isAlreadyInstalled) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFF10B981).copy(alpha = 0.15f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color(0xFF10B981),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Installed",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFF10B981)
                                    )
                                }
                            }
                        } else if (!item.isAvailable) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CloudOff,
                                        contentDescription = null,
                                        tint = Color(0xFFEF4444),
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = "Offline",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFEF4444)
                                    )
                                }
                            }
                        } else {
                            Button(
                                onClick = { onInstallCurated(item) },
                                colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                                shape = RoundedCornerShape(10.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                            ) {
                                Text("Install", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Text(
                        text = item.description,
                        fontSize = 12.sp,
                        color = NuvioTextSecondary,
                        lineHeight = 16.sp
                    )

                    if (!item.isAvailable) {
                        Text(
                            text = "Host server offline (DNS unresolvable). Installation disabled.",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color(0xFFEF4444)
                        )
                    }

                    if (item.requiresConfiguration && item.configurationHint != null) {
                        Text(
                            text = "⚠ ${item.configurationHint}",
                            fontSize = 11.sp,
                            color = Color(0xFFFBBF24)
                        )
                    }

                    // Capability & Type Badges in a horizontal LazyRow so they NEVER clip
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        item { PillBadge(text = item.category, isPrimary = true) }
                        if (item.requiresConfiguration) {
                            item { PillBadge(text = "Config Required", isPrimary = false) }
                        }
                        items(item.resources) { res ->
                            PillBadge(text = res, isPrimary = false)
                        }
                        items(item.types) { typ ->
                            PillBadge(text = typ, isPrimary = false)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PillBadge(text: String, isPrimary: Boolean) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isPrimary) NuvioAccentViolet.copy(alpha = 0.18f) else NuvioSecondarySurface)
            .border(
                1.dp,
                if (isPrimary) NuvioAccentViolet.copy(alpha = 0.3f) else NuvioBorder,
                RoundedCornerShape(6.dp)
            )
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = text,
            fontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (isPrimary) NuvioAccentViolet else NuvioTextSecondary
        )
    }
}

@Composable
private fun InstallAddonDialog(
    urlInput: String,
    isVerifying: Boolean,
    manifestPreview: StremioManifest?,
    errorMessage: String?,
    onUrlChange: (String) -> Unit,
    onVerify: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NuvioCardSurface,
        titleContentColor = NuvioTextPrimary,
        textContentColor = NuvioTextSecondary,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Extension,
                    contentDescription = null,
                    tint = NuvioAccentViolet
                )
                Text("Install Stremio Add-on", fontWeight = FontWeight.Bold, fontSize = 18.sp, color = NuvioTextPrimary)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "Enter HTTP/HTTPS URL of the add-on manifest.json:",
                    fontSize = 13.sp,
                    color = NuvioTextSecondary
                )

                // Text Input Box (Dark text on light background)
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 11.dp)
                ) {
                    if (urlInput.isEmpty()) {
                        Text(
                            text = "https://example.com/manifest.json",
                            fontSize = 13.sp,
                            color = NuvioTextSecondary.copy(alpha = 0.6f)
                        )
                    }
                    BasicTextField(
                        value = urlInput,
                        onValueChange = onUrlChange,
                        textStyle = TextStyle(color = NuvioTextPrimary, fontSize = 13.sp),
                        cursorBrush = SolidColor(NuvioAccentViolet),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Button(
                    onClick = { onVerify(urlInput) },
                    enabled = urlInput.isNotBlank() && !isVerifying,
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isVerifying) {
                        CircularProgressIndicator(
                            color = Color.White,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Verifying Manifest...", color = Color.White, fontSize = 13.sp)
                    } else {
                        Text("Verify Manifest", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Error Message Display
                if (!errorMessage.isNullOrBlank()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFFEF4444).copy(alpha = 0.12f))
                            .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(10.dp))
                            .padding(10.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.ErrorOutline,
                                contentDescription = null,
                                tint = Color(0xFFEF4444),
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = errorMessage,
                                fontSize = 12.sp,
                                color = Color(0xFFEF4444),
                                lineHeight = 16.sp
                            )
                        }
                    }
                }

                // Manifest Preview Card
                if (manifestPreview != null) {
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioSecondarySurface),
                        border = BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Filled.CheckCircle,
                                    contentDescription = null,
                                    tint = Color(0xFF10B981),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "Valid Manifest: ${manifestPreview.name} v${manifestPreview.version}",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = NuvioTextPrimary
                                )
                            }
                            if (!manifestPreview.description.isNullOrBlank()) {
                                Text(
                                    text = manifestPreview.description ?: "",
                                    fontSize = 12.sp,
                                    color = NuvioTextSecondary,
                                    maxLines = 2
                                )
                            }
                            Text(
                                text = "Resources: ${manifestPreview.resources.joinToString { it.name }}",
                                fontSize = 11.sp,
                                color = NuvioAccentViolet,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = "Types: ${manifestPreview.types.joinToString()}",
                                fontSize = 11.sp,
                                color = NuvioTextSecondary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (manifestPreview != null) {
                Button(
                    onClick = onConfirm,
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Install & Enable", color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = NuvioTextSecondary)
            }
        }
    )
}

@Composable
private fun AddonDetailsDialog(
    addon: InstalledAddon,
    onDismiss: () -> Unit,
    onToggle: () -> Unit,
    onRefresh: () -> Unit,
    onRemove: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = NuvioCardSurface,
        titleContentColor = NuvioTextPrimary,
        textContentColor = NuvioTextSecondary,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(NuvioSecondarySurface)
                        .border(1.dp, NuvioBorder, RoundedCornerShape(8.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    if (!addon.logo.isNullOrBlank()) {
                        AsyncImage(model = addon.logo, contentDescription = null, modifier = Modifier.fillMaxSize())
                    } else {
                        Icon(Icons.Filled.Extension, null, tint = NuvioAccentViolet, modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(text = addon.name, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = NuvioTextPrimary)
                    Text(text = "ID: ${addon.id} • v${addon.version}", fontSize = 11.sp, color = NuvioTextSecondary)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (addon.description.isNotBlank()) {
                    Text(text = addon.description, fontSize = 13.sp, color = NuvioTextPrimary)
                }

                HorizontalDivider(color = NuvioBorder, thickness = 1.dp)

                Text(
                    text = "Manifest URL: ${addon.manifestUrl}",
                    fontSize = 11.sp,
                    color = NuvioTextSecondary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "Resources: ${addon.resources.joinToString { it.name }}",
                    fontSize = 12.sp,
                    color = NuvioAccentViolet,
                    fontWeight = FontWeight.Medium
                )

                Text(
                    text = "Supported Types: ${addon.types.joinToString()}",
                    fontSize = 12.sp,
                    color = NuvioTextSecondary
                )

                if (!addon.catalogs.isNullOrEmpty()) {
                    Text(
                        text = "Catalogs: ${addon.catalogs.joinToString { it.name ?: it.id }}",
                        fontSize = 12.sp,
                        color = NuvioTextSecondary
                    )
                }

                if (!addon.idPrefixes.isNullOrEmpty()) {
                    Text(
                        text = "ID Prefixes: ${addon.idPrefixes.joinToString()}",
                        fontSize = 12.sp,
                        color = NuvioTextSecondary
                    )
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(
                    onClick = {
                        onRemove()
                        onDismiss()
                    }
                ) {
                    Text("Uninstall", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
                Button(
                    onClick = {
                        onRefresh()
                        onDismiss()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = NuvioAccentViolet),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Refresh", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = NuvioTextSecondary)
            }
        }
    )
}
