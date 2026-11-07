package cn.yiweiqiu.lessonledger.ui.theme

import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yiweiqiu.lessonledger.domain.Category

// ---------------------------------------------------------------- 品牌色（暖橙 + 米白）

val BrandOrange = Color(0xFFFF8A4C)
val BrandOrangeDark = Color(0xFFE0703A)

// ---------------------------------------------------------------- 主题色（可在「我的 → 主题外观」里切换）

/**
 * 一套主题色 = 亮色/深色两组主色 + 用于极光背景的两枚副色。
 * 只改主色（primary 系列），中性底色保持一致，换色不会让界面失去质感。
 */
enum class AccentTheme(
    val key: String,
    val label: String,
    val lightPrimary: Color,
    val lightOnPrimary: Color,
    val lightContainer: Color,
    val lightOnContainer: Color,
    val darkPrimary: Color,
    val darkOnPrimary: Color,
    val darkContainer: Color,
    val darkOnContainer: Color,
    /** 极光背景的第二/第三色（只影响玻璃后面的光斑） */
    val auraB: Color,
    val auraC: Color,
) {
    ORANGE(
        key = "orange", label = "暖橙",
        lightPrimary = Color(0xFFFF8A4C), lightOnPrimary = Color(0xFF4A2008),
        lightContainer = Color(0xFFFFE3D2), lightOnContainer = Color(0xFF3A1B08),
        darkPrimary = Color(0xFFFFB088), darkOnPrimary = Color(0xFF3A1B08),
        darkContainer = Color(0xFF5C3218), darkOnContainer = Color(0xFFFFDCC8),
        auraB = Color(0xFFFFC46B), auraC = Color(0xFFFF7AA8),
    ),
    CORAL(
        key = "coral", label = "珊瑚",
        lightPrimary = Color(0xFFE8654F), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFFDE0DA), lightOnContainer = Color(0xFF40120B),
        darkPrimary = Color(0xFFFF9E8C), darkOnPrimary = Color(0xFF40120B),
        darkContainer = Color(0xFF5A2318), darkOnContainer = Color(0xFFFFDAD3),
        auraB = Color(0xFFFFA46B), auraC = Color(0xFFF26D9C),
    ),
    PINK(
        key = "pink", label = "樱粉",
        lightPrimary = Color(0xFFD95C8C), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFFBDCE8), lightOnContainer = Color(0xFF3D0E22),
        darkPrimary = Color(0xFFFFA0BF), darkOnPrimary = Color(0xFF3D0E22),
        darkContainer = Color(0xFF5A1C31), darkOnContainer = Color(0xFFFFD9E4),
        auraB = Color(0xFFFFB3C7), auraC = Color(0xFFC58BF0),
    ),
    VIOLET(
        key = "violet", label = "紫罗兰",
        lightPrimary = Color(0xFF7C6BD6), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFE7E3FB), lightOnContainer = Color(0xFF221A4C),
        darkPrimary = Color(0xFFB9ADF2), darkOnPrimary = Color(0xFF221A4C),
        darkContainer = Color(0xFF332A5E), darkOnContainer = Color(0xFFE7E3FB),
        auraB = Color(0xFFB18CF0), auraC = Color(0xFF6BB6F0),
    ),
    BLUE(
        key = "blue", label = "靛蓝",
        lightPrimary = Color(0xFF3E7BD6), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFDEE9FB), lightOnContainer = Color(0xFF0F2440),
        darkPrimary = Color(0xFFA2C2F5), darkOnPrimary = Color(0xFF0F2440),
        darkContainer = Color(0xFF16304F), darkOnContainer = Color(0xFFDEE9FB),
        auraB = Color(0xFF6BA8F0), auraC = Color(0xFF6BD0E0),
    ),
    TEAL(
        key = "teal", label = "青碧",
        lightPrimary = Color(0xFF2FA6B8), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFD7EEF2), lightOnContainer = Color(0xFF06323A),
        darkPrimary = Color(0xFF7FD3DF), darkOnPrimary = Color(0xFF06323A),
        darkContainer = Color(0xFF0D3B44), darkOnContainer = Color(0xFFD7EEF2),
        auraB = Color(0xFF63D6C0), auraC = Color(0xFF6BB6F0),
    ),
    GREEN(
        key = "green", label = "松绿",
        lightPrimary = Color(0xFF4C9A6A), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFDDEFE3), lightOnContainer = Color(0xFF0D2A18),
        darkPrimary = Color(0xFF8FD3AC), darkOnPrimary = Color(0xFF0D2A18),
        darkContainer = Color(0xFF16351F), darkOnContainer = Color(0xFFDDEFE3),
        auraB = Color(0xFFA5D46B), auraC = Color(0xFF63CBB0),
    ),
    GRAPHITE(
        key = "graphite", label = "石墨",
        lightPrimary = Color(0xFF6E6459), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFE7E1DA), lightOnContainer = Color(0xFF201A14),
        darkPrimary = Color(0xFFC4B8AB), darkOnPrimary = Color(0xFF201A14),
        darkContainer = Color(0xFF3A322A), darkOnContainer = Color(0xFFEDE6DF),
        auraB = Color(0xFFC9B9A4), auraC = Color(0xFFA8A69E),
    ),
    /**
     * 跟随系统：Android 12+ 取系统动态配色（Material You），低版本回落到中性靛蓝。
     *
     * 放在最后一项，且是**默认**选项 —— 用户装上 App 不该被硬塞一套橙色，
     * 跟系统壁纸取色才符合"跟随系统"的直觉。
     */
    SYSTEM(
        key = "system", label = "跟随系统",
        lightPrimary = Color(0xFF4C5FD5), lightOnPrimary = Color(0xFFFFFFFF),
        lightContainer = Color(0xFFE1E4FF), lightOnContainer = Color(0xFF121A5C),
        darkPrimary = Color(0xFFBCC2FF), darkOnPrimary = Color(0xFF14205F),
        darkContainer = Color(0xFF2B3573), darkOnContainer = Color(0xFFE1E4FF),
        auraB = Color(0xFF8FA2F5), auraC = Color(0xFF7FB2F5),
    ),
    ;

    /** 设置页色卡上显示的颜色 */
    val swatch: Color get() = lightPrimary

    companion object {
        val default: AccentTheme = SYSTEM
        fun of(key: String?): AccentTheme = entries.firstOrNull { it.key == key } ?: default
    }
}

val LocalAccentTheme = staticCompositionLocalOf { AccentTheme.default }

// ---------------------------------------------------------------- 扩展语义色（支出红 / 退费绿，遵循国内习惯）

data class ExtendedColors(
    val success: Color,
    val successContainer: Color,
    val warning: Color,
    val warningContainer: Color,
    val info: Color,
    val infoContainer: Color,
    val danger: Color,
    val dangerContainer: Color,
    /**
     * 请假：以前用 onSurfaceVariant 的灰，结果灰 = "没上"的视觉语言，
     * 一节课请了假看着跟没上完一样。改成紫——和「已上绿 / 调课蓝 / 缺勤红」
     * 在色相环上各占一角，也不会和任何一套主题的 primary 撞成一片。
     */
    val leave: Color,
    val leaveContainer: Color,
    /** 支出（红） */
    val expense: Color,
    /** 退费（绿） */
    val income: Color,
    val cardBorder: Color,
    val chartGrid: Color,
    val mutedSurface: Color,
)

val LightExtended = ExtendedColors(
    success = Color(0xFF2E9E5B),
    successContainer = Color(0xFFE3F4E8),
    warning = Color(0xFFD98300),
    warningContainer = Color(0xFFFDF0DC),
    info = Color(0xFF2F7DE1),
    infoContainer = Color(0xFFE6EFFC),
    danger = Color(0xFFD6403A),
    dangerContainer = Color(0xFFFCE9E7),
    leave = Color(0xFF7A6AD8),
    leaveContainer = Color(0xFFECE9FB),
    expense = Color(0xFFD6403A),
    income = Color(0xFF2E9E5B),
    cardBorder = Color(0xFFEFE8E0),
    chartGrid = Color(0xFFEDE6DE),
    mutedSurface = Color(0xFFF4EFE9),
)

val DarkExtended = ExtendedColors(
    success = Color(0xFF6DCF95),
    successContainer = Color(0xFF1D3327),
    warning = Color(0xFFF0B052),
    warningContainer = Color(0xFF352A17),
    info = Color(0xFF7FB2F5),
    infoContainer = Color(0xFF182638),
    danger = Color(0xFFFF9B8C),
    dangerContainer = Color(0xFF3A201C),
    leave = Color(0xFFBCAEF5),
    leaveContainer = Color(0xFF2A2450),
    expense = Color(0xFFFF8E7E),
    income = Color(0xFF6DCF95),
    // 中性色统一走冷灰，避免和暖橙主色打架
    cardBorder = Color(0xFF2C2E35),
    chartGrid = Color(0xFF282A31),
    mutedSurface = Color(0xFF1E1F25),
)

val LocalExtendedColors = staticCompositionLocalOf { LightExtended }

/** 缴费批次配色（堆叠条每一段一个颜色） */
val BatchPalette = listOf(
    Color(0xFFFF8A4C),
    Color(0xFF7C6BD6),
    Color(0xFF2FA6B8),
    Color(0xFFE0A020),
    Color(0xFFD96B4A),
    Color(0xFF4C9A6A),
)

// ---------------------------------------------------------------- 科目配色

fun categoryColor(category: Category): Color = when (category) {
    Category.MUSIC -> Color(0xFF7C6BD6)
    Category.SPORTS -> Color(0xFF2FA6B8)
    Category.ACADEMIC -> Color(0xFF3E7BD6)
    Category.ART -> Color(0xFFE0729B)
    Category.DANCE -> Color(0xFFD96B4A)
    Category.CODING -> Color(0xFF4C9A6A)
    Category.OTHER -> Color(0xFF8A8078)
}

// ---------------------------------------------------------------- 形状 / 字体

val AppShapes = Shapes(
    extraSmall = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
    small = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
    medium = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
    large = androidx.compose.foundation.shape.RoundedCornerShape(20.dp),
    extraLarge = androidx.compose.foundation.shape.RoundedCornerShape(28.dp),
)

private val base = Typography()

val AppTypography = Typography(
    displaySmall = base.displaySmall.copy(fontWeight = FontWeight.SemiBold),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold, fontSize = 20.sp),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Medium, fontSize = 16.sp),
    titleSmall = base.titleSmall.copy(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 21.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium),
    labelSmall = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium),
)
