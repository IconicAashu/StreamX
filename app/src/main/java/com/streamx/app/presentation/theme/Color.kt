package com.streamx.app.presentation.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Dynamic Theme Accessors:
val NuvioBackground: Color
    @Composable
    get() = LocalStreamXColors.current.background

val NuvioCardSurface: Color
    @Composable
    get() = LocalStreamXColors.current.cardSurface

val NuvioSecondarySurface: Color
    @Composable
    get() = LocalStreamXColors.current.secondarySurface

val NuvioPillBackground: Color
    @Composable
    get() = LocalStreamXColors.current.secondarySurface

val NuvioBorder: Color
    @Composable
    get() = LocalStreamXColors.current.border

val NuvioTextPrimary: Color
    @Composable
    get() = LocalStreamXColors.current.textPrimary

val NuvioTextSecondary: Color
    @Composable
    get() = LocalStreamXColors.current.textSecondary

val NuvioPillHighlight: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0xFF2C2A3D) else Color(0xFFEAEBED)

val NuvioFloatingBarBorder = Color(0x29000000)
val NuvioAccentViolet = Color(0xFF5E5CE6)

// Dark element fills matching reference images
val DarkActionPill = Color(0xFF1C1C1E)
val DarkActionPillSecondary = Color(0xFF2C2C2E)
val DarkPillBorder = Color(0x33FFFFFF)

// Liquid Glass Translucent Surface Colors
val GlassSurfaceLight: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0xCC1B1A28) else Color(0xEBFFFFFF)

val GlassSurfaceSecondary: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0xB8242234) else Color(0xD8F5F6F9)

val GlassSurfaceDark = Color(0xCC1C1C1E)

val GlassBorderLight: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0x33FFFFFF) else Color(0x1F000000)

val GlassBorderWhite = Color(0x33FFFFFF)

/**
 * Returns a cached Brush for STREAMX's signature vertical gradient background.
 * Respects the current Light/Dark theme dynamically.
 */
@Composable
fun rememberStreamXGradientBrush(): Brush {
    val isDark = LocalStreamXColors.current.isDark
    return remember(isDark) {
        if (isDark) {
            Brush.verticalGradient(
                0.0f to Color(0xFF0F0E17),
                0.5f to Color(0xFF141320),
                1.0f to Color(0xFF191826)
            )
        } else {
            Brush.verticalGradient(
                0.0f to Color(0xFFFFFFFF),
                0.5f to Color(0xFFF8F9FD),
                1.0f to Color(0xFFF1F3FA)
            )
        }
    }
}

// Surfaces & Overlays
val SurfacePurpleSlate: Color
    @Composable
    get() = NuvioCardSurface

val SurfacePurpleOpaque: Color
    @Composable
    get() = NuvioCardSurface

val SurfaceBorderLight: Color
    @Composable
    get() = NuvioBorder

val PlayButtonCirclePurple = Color(0xB36656D9)

// Floating Pill Bottom Navigation Bar Colors
val DockedBottomBarBg: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0xF01B1A28) else Color(0xEFFFFFFF)

val DockedBottomBarBorder = NuvioFloatingBarBorder

// Chips & Indicators:
val HeroChipBg: Color
    @Composable
    get() = NuvioSecondarySurface

val HeroChipBorder: Color
    @Composable
    get() = NuvioBorder

val PagerDotInactive: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0xFF4A485E) else Color(0xFFD1D1D6)

val PagerDotActive = NuvioAccentViolet

// Accent & Status
val PrimaryAccentRoyal = Color(0xFF5E5CE6)
val PrimaryAccentLight = Color(0xFF7D7AFF)
val StarRatingGold = Color(0xFFFFB800)
val RatingGold = StarRatingGold

// Typography & Content
val TextWhite: Color
    @Composable
    get() = NuvioTextPrimary

val TextLavender: Color
    @Composable
    get() = NuvioTextSecondary

val TextLavenderMuted: Color
    @Composable
    get() = NuvioTextSecondary

val TextYearMuted: Color
    @Composable
    get() = NuvioTextSecondary

val TextMuted: Color
    @Composable
    get() = if (LocalStreamXColors.current.isDark) Color(0xFF71717A) else Color(0xFF8E8E93)

val ChipBackground: Color
    @Composable
    get() = HeroChipBg

val ChipBorder: Color
    @Composable
    get() = HeroChipBorder

val MidnightDark: Color
    @Composable
    get() = NuvioBackground

val MidnightDeep: Color
    @Composable
    get() = NuvioCardSurface
