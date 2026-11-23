package com.streamx.app.presentation.welcome

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.streamx.app.R
import com.streamx.app.presentation.theme.*

@Composable
fun WelcomeScreen(
    viewModel: WelcomeViewModel,
    onGetStartedClick: () -> Unit
) {
    val posters by viewModel.posters.collectAsState()
    val gradientBrush = rememberStreamXGradientBrush()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(gradientBrush)
    ) {
        // Angled 3-column background poster grid
        Box(
            modifier = Modifier
                .fillMaxSize()
                .rotate(-7f)
                .offset(y = (-60).dp, x = (-20).dp)
        ) {
            Row(
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Column 1
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    posters.take(4).forEach { url ->
                        PosterCardBackground(url = url, height = 190)
                    }
                }

                // Column 2 (staggered slightly down)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .offset(y = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    posters.drop(4).take(4).forEach { url ->
                        PosterCardBackground(url = url, height = 190)
                    }
                }

                // Column 3
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    posters.takeLast(4).forEach { url ->
                        PosterCardBackground(url = url, height = 190)
                    }
                }
            }
        }

        // Heavy dark overlay with smooth gradient scrim fading into MidnightDark
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0.0f to MidnightDark.copy(alpha = 0.45f),
                        0.4f to MidnightDark.copy(alpha = 0.70f),
                        0.7f to MidnightDark.copy(alpha = 0.95f),
                        1.0f to MidnightDark
                    )
                )
        )

        // Foreground content: Logo emblem, titles, and CTA button
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Bottom
        ) {
            // STREAMX Emblem
            Box(
                modifier = Modifier
                    .size(110.dp)
                    .shadow(24.dp, RoundedCornerShape(26.dp), spotColor = PrimaryAccentRoyal)
                    .clip(RoundedCornerShape(26.dp))
                    .border(1.5.dp, PrimaryAccentRoyal.copy(alpha = 0.8f), RoundedCornerShape(26.dp))
                    .background(SurfacePurpleSlate),
                contentAlignment = Alignment.Center
            ) {
                Image(
                    painter = painterResource(id = R.drawable.streamx_logo),
                    contentDescription = "STREAMX Emblem",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Title
            Text(
                text = "Welcome to STREAMX",
                fontSize = 28.sp,
                fontWeight = FontWeight.Bold,
                color = TextWhite,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "WATCH • DISCOVER • ENJOY",
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 2.sp,
                color = TextLavender,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Full-width rounded pill CTA button ("Get Started", height 54.dp, corner radius 28.dp, #6E56F8)
            Button(
                onClick = { viewModel.completeOnboarding(onGetStartedClick) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .shadow(16.dp, RoundedCornerShape(28.dp), spotColor = PrimaryAccentRoyal),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = PrimaryAccentRoyal,
                    contentColor = TextWhite
                )
            ) {
                Text(
                    text = "Get Started",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextWhite
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun PosterCardBackground(url: String, height: Int) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(16.dp))
            .border(1.dp, SurfaceBorderLight.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(LocalContext.current)
                .data(url)
                .crossfade(150)
                .size(width = 300, height = 400)
                .build(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
    }
}
