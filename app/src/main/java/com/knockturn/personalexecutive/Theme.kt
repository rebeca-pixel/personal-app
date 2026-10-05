package com.knockturn.personalexecutive
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

val Purple = Color(0xFF5B2A86); val PurpleDark = Color(0xFF3F1D61); val PurpleSoft = Color(0xFFEFE7F7)
private val scheme = lightColorScheme(primary = Purple, onPrimary = Color.White, primaryContainer = PurpleSoft,
    onPrimaryContainer = PurpleDark, secondary = PurpleDark, background = Color.White, onBackground = Color(0xFF1F1530),
    surface = Color.White, surfaceVariant = PurpleSoft, outline = Purple)
private val type = Typography(
    headlineSmall = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold),
    titleLarge = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontFamily = FontFamily.Serif, fontWeight = FontWeight.SemiBold))
@Composable fun PETheme(content: @Composable () -> Unit) =
    MaterialTheme(colorScheme = scheme, typography = type, shapes = Shapes(medium = RoundedCornerShape(10.dp)), content = content)
