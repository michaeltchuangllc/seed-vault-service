package com.michaeltchuang.seedvault.service.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp

// Dark palette taken from the wallet-sdk-ui ColorPalette, so the app matches the blue launcher icon.
// The values are copied, not imported: this module must not depend on wallet-sdk-ui.
private const val RICH_NAVY_900 = 0xFF001423 // ColorPalette.RichNavy900
private const val SHEET_BACKGROUND = 0xFF051C34 // ColorPalette.SheetBackground
private const val TAB_CONTAINER = 0xFF0D2F4A // ColorPalette.TabContainer, opaque
private const val SLATE_BLUE_400 = 0xFF8792A0 // ColorPalette.SlateBlue400
private const val CLOSE_BUTTON_BACKGROUND = 0xFF536672 // ColorPalette.CloseButtonBackground
private const val SECTION_DIVIDER = 0xFF253D4D // ColorPalette.SectionDivider, flattened onto RichNavy900
private const val ELECTRIC_BLUE_600 = 0xFF2D2DF1 // ColorPalette.ElectricBlue600, the icon's blue

// ElectricBlue600 mixed half with white: Electric Blue text is too dark to read on navy
private const val ELECTRIC_BLUE_LIGHT = 0xFF9696F8
private const val SALMON_500 = 0xFFFF6D5F // ColorPalette.Salmon500, wallet-sdk-ui dark negative

private val seedVaultColorScheme =
    darkColorScheme(
        primary = Color.White,
        onPrimary = Color(RICH_NAVY_900),
        // Accent text and icons
        secondary = Color(ELECTRIC_BLUE_LIGHT),
        onSecondary = Color(RICH_NAVY_900),
        // Accent fills (primary button, check circle, glows)
        tertiary = Color(ELECTRIC_BLUE_600),
        onTertiary = Color.White,
        background = Color(RICH_NAVY_900),
        onBackground = Color.White,
        surface = Color(RICH_NAVY_900),
        onSurface = Color.White,
        onSurfaceVariant = Color(SLATE_BLUE_400),
        surfaceContainer = Color(SHEET_BACKGROUND),
        surfaceContainerHigh = Color(TAB_CONTAINER),
        outline = Color(CLOSE_BUTTON_BACKGROUND),
        outlineVariant = Color(SECTION_DIVIDER),
        error = Color(SALMON_500),
    )

// Row titles in the reference are a bit smaller than the Material default titleLarge (22sp)
private val seedVaultTypography =
    Typography().run {
        copy(titleLarge = titleLarge.copy(fontSize = 19.sp, lineHeight = 26.sp))
    }

@Composable
fun SeedVaultTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = seedVaultColorScheme,
        typography = seedVaultTypography,
        content = content,
    )
}
