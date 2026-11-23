package com.streamx.app.presentation.settings.integrations

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamx.app.presentation.theme.*

@Composable
fun IntegrationsScreen(
    viewModel: IntegrationsViewModel,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(NuvioBackground)
            .statusBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 16.dp)
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
                    text = "Integrations",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = NuvioTextPrimary
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // TMDB Card
                item(key = "integration_tmdb") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF01B4E4).copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "TMDB",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFF01B4E4)
                                        )
                                    }
                                    Text(
                                        text = "The Movie Database",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = NuvioTextPrimary
                                    )
                                }

                                Switch(
                                    checked = uiState.tmdbEnrichmentEnabled,
                                    onCheckedChange = { viewModel.setTmdbEnrichment(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NuvioAccentViolet,
                                        uncheckedThumbColor = NuvioTextSecondary,
                                        uncheckedTrackColor = NuvioPillBackground
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Enriches movies and TV series with real-world cast lists, trending carousels, genre classifications, and high-definition backdrop artwork.",
                                fontSize = 13.sp,
                                color = NuvioTextSecondary,
                                lineHeight = 18.sp
                            )

                            Spacer(modifier = Modifier.height(14.dp))

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(NuvioPillBackground)
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Endpoint", fontSize = 11.sp, color = NuvioTextSecondary)
                                    Text("https://api.themoviedb.org/3/", fontSize = 12.sp, color = NuvioTextPrimary)
                                }
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(Color(0xFF2E7D32).copy(alpha = 0.3f))
                                        .padding(horizontal = 8.dp, vertical = 3.dp)
                                ) {
                                    Text("CONNECTED", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = Color(0xFF81C784))
                                }
                            }
                        }
                    }
                }

                // Jikan Anime Metadata Card
                item(key = "integration_jikan") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF2E51A2).copy(alpha = 0.25f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "JIKAN",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFF6B8AFD)
                                        )
                                    }
                                    Text(
                                        text = "MyAnimeList (Jikan v4)",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = NuvioTextPrimary
                                    )
                                }

                                Switch(
                                    checked = uiState.jikanMetadataEnabled,
                                    onCheckedChange = { viewModel.setJikanMetadata(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NuvioAccentViolet,
                                        uncheckedThumbColor = NuvioTextSecondary,
                                        uncheckedTrackColor = NuvioPillBackground
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Fetches deep Japanese anime records including voice actors (Seiyuu), character biographies, opening/ending theme tracks, and seasonal rankings.",
                                fontSize = 13.sp,
                                color = NuvioTextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // External Ratings Card
                item(key = "integration_ratings") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFFF5C518).copy(alpha = 0.2f))
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = "★ RT / IMDb",
                                            fontWeight = FontWeight.Black,
                                            fontSize = 12.sp,
                                            color = Color(0xFFF5C518)
                                        )
                                    }
                                    Text(
                                        text = "External Critic Ratings",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = NuvioTextPrimary
                                    )
                                }

                                Switch(
                                    checked = uiState.externalRatingsEnabled,
                                    onCheckedChange = { viewModel.setExternalRatings(it) },
                                    colors = SwitchDefaults.colors(
                                        checkedThumbColor = Color.White,
                                        checkedTrackColor = NuvioAccentViolet,
                                        uncheckedThumbColor = NuvioTextSecondary,
                                        uncheckedTrackColor = NuvioPillBackground
                                    )
                                )
                            }

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Aggregates IMDb, Rotten Tomatoes Tomatometer / Popcornmeter, and Metascores directly onto the media detail cards and search results.",
                                fontSize = 13.sp,
                                color = NuvioTextSecondary,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }

                // Cache Management
                item(key = "integration_cache") {
                    Card(
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = NuvioCardSurface),
                        border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Metadata Cache",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = NuvioTextPrimary
                                )
                                Text(
                                    text = uiState.cacheStatus,
                                    fontSize = 12.sp,
                                    color = NuvioTextSecondary
                                )
                            }

                            Button(
                                onClick = { viewModel.clearMetadataCache() },
                                colors = ButtonDefaults.buttonColors(containerColor = NuvioPillBackground),
                                border = androidx.compose.foundation.BorderStroke(1.dp, NuvioBorder),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("Clear Cache", fontSize = 12.sp, color = NuvioTextPrimary)
                            }
                        }
                    }
                }
            }
        }
    }
}
