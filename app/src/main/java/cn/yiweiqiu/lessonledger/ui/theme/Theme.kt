package cn.yiweiqiu.lessonledger.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

/** 深色模式策略：跟随系统 / 强制浅色 / 强制深色 */
enum class ThemeMode(val key: String, val label: String) {
    SYSTEM("system", "跟随系统"),
    LIGHT("light", "浅色"),
    DARK("dark", "深色"),
    ;

    companion object {
        fun of(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

private fun lightScheme(accent: AccentTheme) = lightColorScheme(
    primary = accent.lightPrimary,
    onPrimary = accent.lightOnPrimary,
    primaryContainer = accent.lightContainer,
    onPrimaryContainer = accent.lightOnContainer,
    secondary = Color(0xFF7C6A58),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF0E5D9),
    onSecondaryContainer = Color(0xFF2C2117),
    tertiary = Color(0xFF4C9A6A),
    onTertiary = Color.White,
    background = Color(0xFFFAF8F5),
    onBackground = Color(0xFF211C17),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF211C17),
    surfaceVariant = Color(0xFFF2ECE5),
    onSurfaceVariant = Color(0xFF6E6459),
    surfaceContainer = Color(0xFFF7F2EC),
    surfaceContainerHigh = Color(0xFFF4EEE7),
    surfaceTint = accent.lightPrimary,
    outline = Color(0xFFDCD4CA),
    outlineVariant = Color(0xFFEAE3DA),
    error = Color(0xFFD6403A),
    onError = Color.White,
    errorContainer = Color(0xFFFCE9E7),
    onErrorContainer = Color(0xFF4A120E),
    inverseSurface = Color(0xFF322C26),
    inverseOnSurface = Color(0xFFF7F1EA),
)

/**
 * 深色色板走 HyperOS 的石墨路线：**冷灰**而不是暖棕。
 *
 * 之前用暖棕（#14120F / #1C1917）叠暖橙极光，整屏像蒙了层咖啡渣；
 * 深色下背景必须去饱和、偏冷，主题色只作为"高光"出现，不能染满整块底。
 */
private fun darkScheme(accent: AccentTheme) = darkColorScheme(
    primary = accent.darkPrimary,
    onPrimary = accent.darkOnPrimary,
    primaryContainer = accent.darkContainer,
    onPrimaryContainer = accent.darkOnContainer,
    secondary = Color(0xFFB9BCC6),
    onSecondary = Color(0xFF23242A),
    secondaryContainer = Color(0xFF2C2E35),
    onSecondaryContainer = Color(0xFFD9DBE1),
    tertiary = Color(0xFF8FD3AC),
    onTertiary = Color(0xFF16351F),
    background = Color(0xFF0E0F12),
    onBackground = Color(0xFFEDEEF0),
    surface = Color(0xFF16171B),
    onSurface = Color(0xFFEDEEF0),
    surfaceVariant = Color(0xFF232429),
    onSurfaceVariant = Color(0xFF9EA0A8),
    surfaceContainer = Color(0xFF1B1C21),
    surfaceContainerHigh = Color(0xFF202127),
    surfaceTint = accent.darkPrimary,
    outline = Color(0xFF3B3D45),
    outlineVariant = Color(0xFF2A2C33),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF5C120C),
    errorContainer = Color(0xFF3A1E1B),
    onErrorContainer = Color(0xFFFFDAD4),
    inverseSurface = Color(0xFFEDEEF0),
    inverseOnSurface = Color(0xFF1B1C21),
)

@Composable
fun LessonLedgerTheme(
    accent: AccentTheme = AccentTheme.default,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    // 「跟随系统」主题色：Android 12+ 直接取系统动态配色（Material You）
    val dynamicScheme = remember(accent, darkTheme, context) {
        if (accent == AccentTheme.SYSTEM && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
            }.getOrNull()
        } else {
            null
        }
    }

    val colorScheme = remember(accent, darkTheme, dynamicScheme) {
        val base = if (darkTheme) darkScheme(accent) else lightScheme(accent)
        if (dynamicScheme == null) {
            base
        } else {
            // 只接管主色这一组，中性底色仍用我们调好的板子：
            // 动态配色跟着壁纸走，若连 background 都交给它，深色底会跟着壁纸变脏。
            base.copy(
                primary = dynamicScheme.primary,
                onPrimary = dynamicScheme.onPrimary,
                primaryContainer = dynamicScheme.primaryContainer,
                onPrimaryContainer = dynamicScheme.onPrimaryContainer,
                surfaceTint = dynamicScheme.primary,
            )
        }
    }
    val extended = if (darkTheme) DarkExtended else LightExtended

    SystemBarsAppearance(dark = darkTheme)

    CompositionLocalProvider(
        LocalExtendedColors provides extended,
        LocalAccentTheme provides accent,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            shapes = AppShapes,
            content = content,
        )
    }
}

/**
 * 沉浸式系统栏：状态栏和导航栏都全透明，图标明暗跟随主题。
 * 真正"沉浸"的部分由页面自己把内容画到系统栏下面（见 ui/components/Glass.kt）。
 */
@Composable
private fun SystemBarsAppearance(dark: Boolean) {
    val view = LocalView.current
    if (view.isInEditMode) return
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.getInsetsController(window, view).apply {
            isAppearanceLightStatusBars = !dark
            isAppearanceLightNavigationBars = !dark
        }
    }
}

/** 当前是否深色（用于极光背景取色，避免被强制浅色时取错） */
@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f
