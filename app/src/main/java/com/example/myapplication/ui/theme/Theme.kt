
package com.example.myapplication.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val ExpenseFlowColorScheme = lightColorScheme(
    primary = GreenPrimary,
    onPrimary = CardWhite,

    secondary = GreenAccent,
    onSecondary = CardWhite,

    tertiary = GreenDark,
    onTertiary = CardWhite,

    background = AppBackground,
    onBackground = TextPrimary,

    surface = CardWhite,
    onSurface = TextPrimary,

    surfaceVariant = GreenLight,
    onSurfaceVariant = TextSecondary,

    error = ExpenseRed,
    onError = CardWhite,

    outline = BorderLight
)

@Composable
fun MyApplicationTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = ExpenseFlowColorScheme,
        typography = Typography,
        content = content
    )
}
