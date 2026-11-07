package cn.yiweiqiu.lessonledger.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yiweiqiu.lessonledger.ui.theme.LocalAccentTheme
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.isDarkSurface

/**
 * HyperOS 4 风格的一层"极光"底色。
 *
 * 玻璃之所以像玻璃，前提是背景本身有颜色和层次。这里用几枚大半径径向渐变光斑
 * （主色 + 两枚副色）缓慢漂移，上面所有半透明的 bar / card 都会透出这层颜色，
 * 形成磨砂玻璃的观感，而不是一块死灰的蒙层。
 *
 * 关键约束：光斑颜色必须先和底色**混一遍**再画。直接用主题色画在深色底上，
 * 会在左上角糊出一大团荧光污渍——这是上一版最难看的根源。
 */
@Composable
fun AuroraBackground(modifier: Modifier = Modifier) {
    val dark = isDarkSurface()
    val accent = LocalAccentTheme.current
    val base = MaterialTheme.colorScheme.background
    val primary = if (dark) accent.darkPrimary else accent.lightPrimary

    val transition = rememberInfiniteTransition(label = "aurora")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 42_000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "phase",
    )

    Canvas(modifier = modifier.fillMaxSize().background(base)) {
        val w = size.width
        val h = size.height
        val strength = if (dark) 0.13f else 0.26f

        /** 把主题色往底色里融，只保留 keep 比例的色相 */
        fun soften(color: Color, keep: Float): Color = lerp(base, color, keep)

        fun blob(cx: Float, cy: Float, radius: Float, color: Color, alpha: Float) {
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        color.copy(alpha = alpha),
                        color.copy(alpha = alpha * 0.42f),
                        color.copy(alpha = alpha * 0.10f),
                        Color.Transparent,
                    ),
                    center = Offset(cx, cy),
                    radius = radius,
                ),
                radius = radius,
                center = Offset(cx, cy),
            )
        }

        // 四枚分散的柔光：半径收小、位置错开，避免糊成一整片
        blob(
            -w * 0.10f + phase * w * 0.06f,
            -h * 0.05f + phase * h * 0.02f,
            w * 0.68f,
            soften(primary, 0.58f),
            strength,
        )
        blob(
            w * 1.06f - phase * w * 0.07f,
            h * 0.12f + phase * h * 0.02f,
            w * 0.54f,
            soften(accent.auraB, 0.50f),
            strength * 0.62f,
        )
        blob(
            w * 0.80f + phase * w * 0.05f,
            h * 1.05f - phase * h * 0.03f,
            w * 0.62f,
            soften(accent.auraC, 0.50f),
            strength * 0.55f,
        )
        blob(
            w * 0.04f,
            h * 0.60f + phase * h * 0.03f,
            w * 0.44f,
            soften(accent.auraB, 0.40f),
            strength * 0.32f,
        )
    }
}

/** 顶部玻璃条的内容高度（不含状态栏），列表预留内边距时用 */
val TopBarContentHeight: Dp = 62.dp

/** 状态栏高度 + 玻璃条高度，列表顶部要留这么多，内容才会从玻璃下面滑过 */
@Composable
fun topBarInset(): Dp {
    val statusBar = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    return statusBar + TopBarContentHeight
}

/** 底部悬浮玻璃导航条自身的高度 */
val NavBarContentHeight: Dp = 60.dp

/** 悬浮条离屏幕底边的距离（不含系统手势条） */
val NavBarFloatGap: Dp = 14.dp

/** 有底部导航的页面，列表底部留白 */
@Composable
fun bottomBarInset(extra: Dp = 12.dp): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return NavBarContentHeight + NavBarFloatGap + navBar + extra
}

/** 没有底部导航的二级页（打卡日历等），列表底部留白 */
@Composable
fun pageBottomInset(extra: Dp = 24.dp): Dp {
    val navBar = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    return navBar + extra
}

/**
 * 沉浸式顶部玻璃条：内容从它下面滑过，它本身只是一层会呼吸的雾。
 *
 * 透明度的分布不是均匀的，而是「**上薄、中厚、下化**」：
 * - 静止时整条都薄（0.55/0.74），底下的极光透得出来 —— 这才叫沉浸；
 * - 滚动起来才加厚（0.94/0.96）挡住滑上来的行文字，但因为颜色是背景色，
 *   加厚后看起来像"内容被底色吃掉"，而不是盖了一条白条；
 * - 底边永远化到全透明，内容"化"进玻璃里，不留一条硬边。
 *
 * [scrollBack] 传 `{ listState.canScrollBackward }`（LazyColumn / LazyGrid 都行），
 * 顶栏自己盯着滚动位置决定厚薄；内容不够长、不滚的页面不用传。
 */
@Composable
fun GlassTopBar(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    elevated: Boolean = false,
    scrollBack: (() -> Boolean)? = null,
    leading: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 传了 scrollBack 就自己盯滚动：滚起来了才加厚，静止时保持通透。
    // 两条分支都 remember，保证 slot 结构稳定。
    val scrolled by if (scrollBack != null) {
        remember(scrollBack) { derivedStateOf { scrollBack() } }
    } else {
        remember { mutableStateOf(false) }
    }
    val isElevated = elevated || scrolled

    val scheme = MaterialTheme.colorScheme
    val accent = scheme.primary
    val dark = isDarkSurface()
    // 关键：顶栏玻璃用**背景色**而不是 surface 白。
    // 用白色时，一旦为了遮住滚上来的文字把 alpha 提上去，它就会变成一块贴上去的白板，
    // 和底下的极光背景有色差 —— 这就是"不沉浸"的根源。用背景色则永远和底子同色，
    // 加厚时看起来像"内容被底色吃掉"，而不是"盖了一条白条"。
    val overlay = scheme.background
    val topAlpha by animateFloatAsState(
        targetValue = when {
            // 滚动时几乎压满：反正是背景色，压满也不会变成白条，只是把内容"吃掉"
            isElevated -> 0.97f
            // 深色下背景本身几乎是纯黑，同色玻璃只会把极光压暗，所以薄一点
            dark -> 0.28f
            else -> 0.55f
        },
        animationSpec = tween(260),
        label = "topAlpha",
    )
    val midAlpha by animateFloatAsState(
        targetValue = when {
            isElevated -> 0.98f
            dark -> 0.36f
            else -> 0.74f
        },
        animationSpec = tween(260),
        label = "midAlpha",
    )

    Box(modifier = modifier.fillMaxWidth()) {
        Canvas(modifier = Modifier.matchParentSize()) {
            drawRect(
                brush = Brush.verticalGradient(
                    0f to overlay.copy(alpha = topAlpha),
                    // 标题所在高度：遮字的最后一道防线
                    // 一直保持厚度到接近底边，只在最后一段化开：
                    // 化开区太早，滚上来的卡片数字会从标题行下方透出来变成重影
                    0.90f to overlay.copy(alpha = midAlpha),
                    0.97f to overlay.copy(alpha = midAlpha * 0.72f),
                    // 底边化掉，内容从这里平滑滑进玻璃
                    1f to Color.Transparent,
                ),
            )
            // 分割线只在滚动时极淡地出现，不滚动时完全隐形，保持沉浸
            if (isElevated) {
                drawLine(
                    color = accent.copy(alpha = if (dark) 0.10f else 0.05f),
                    start = Offset(0f, size.height - 1f),
                    end = Offset(size.width, size.height - 1f),
                    strokeWidth = 1f,
                )
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(TopBarContentHeight)
                .padding(start = if (leading == null) 20.dp else 4.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            leading?.invoke()
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            actions()
        }
    }
}

/**
 * 悬浮玻璃胶囊按钮：「补打卡」「添加孩子」「记一笔缴费」共用一颗。
 *
 * 之前各页各写一个 ExtendedFloatingActionButton，默认底色是 surfaceContainerHigh ——
 * 在暖白的极光背景上，它看起来像一块糊了灰的塑料，既不像玻璃也不像实心按钮：
 * 打卡日历右下角那颗「补打卡」尤其明显，压在半透卡片上边界都不清楚。
 *
 * 这里换成和底部导航条同一套玻璃：白色（深色下是冷灰）压到 0.8 上下的透明度、
 * 一圈细描边交代边界、底下带投影浮起来。文字用主色，一眼能认出是「动作」。
 */
@Composable
fun GlassFab(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    /** 次要动作（补打卡这类）用描边样式，主要动作（记一笔缴费）可传 true 走主色实底 */
    primary: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val shape = RoundedCornerShape(20.dp)
    val base = if (dark) Color(0xFF24262D) else Color.White
    // 比卡片透一档：卡片 0.86，这里 0.80 → 0.62，压在内容上仍读得清，但看得出是"浮着的玻璃"
    val topAlpha = if (dark) 0.94f else 0.84f
    val bottomAlpha = if (dark) 0.86f else 0.66f
    val edge = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.80f)
    val content = if (primary) Color.White else scheme.primary
    val fill = if (primary) {
        Brush.verticalGradient(listOf(scheme.primary, scheme.primary.copy(alpha = 0.88f)))
    } else {
        Brush.verticalGradient(listOf(base.copy(alpha = topAlpha), base.copy(alpha = bottomAlpha)))
    }

    Row(
        modifier = modifier
            .height(50.dp)
            .shadow(
                elevation = 14.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = if (dark) 0.50f else 0.14f),
                spotColor = Color.Black.copy(alpha = if (dark) 0.42f else 0.12f),
            )
            .clip(shape)
            .background(fill)
            .border(1.dp, if (primary) Color.Transparent else edge, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(7.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = content,
        )
    }
}

/** 二级页面的返回键，统一 48dp 触控区 */
@Composable
fun GlassBackButton(onClick: () -> Unit) {
    IconButton(onClick = onClick, modifier = Modifier.size(48.dp)) {
        Icon(
            imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
            contentDescription = "返回",
        )
    }
}

/**
 * 底部**悬浮**玻璃导航条（HyperOS 4 的胶囊式底栏）。
 *
 * 不再贴满屏幕底边，而是收成一条离底的胶囊：两侧留出背景、底下带投影，
 * 看上去是浮在内容之上的一块磨砂玻璃，而不是贴在边框上的一根横条。
 *
 * 打卡也是这排里的普通一员（图标 + 文字，和其它 tab 同一套视觉语言），
 * 只是图标用主色、上有待办时带一个小角标 —— 之前做成实心大药丸，
 * 夹在三个 tab 中间特别突兀，像贴错了组件。
 *
 * 选中高亮铺满整格：胶囊里不再留 6dp 内边距，每个 tab 的高亮直接铺满
 * 自己的一格；两端 tab 的外侧角交给胶囊自己的 28dp 圆角裁切，
 * 这样「首页」「打卡」选中时高亮一直铺到胶囊的左右边缘，不再留缝。
 */
@Composable
fun GlassNavBar(
    items: List<GlassNavItem>,
    currentRoute: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val navBarInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()
    val shape = RoundedCornerShape(28.dp)

    // 磨砂玻璃：浅色是半透明白，深色是压得住内容又比背景亮一档的冷灰。
    // 透明度对着极光调：太实（0.95+）就是一根白条；太透（<0.5）底下卡片的文字会清楚透出来，
    // 没有真模糊的时候会显脏。
    // 深色要更实一些：深底 + 浅字，透出 20% 就够让「10:00 游泳私教」和 tab 文字叠在一起，
    // 而背景本身很暗，多压一点不损失通透感。
    val glassTopAlpha = if (dark) 0.93f else 0.80f
    val glassBottomAlpha = if (dark) 0.86f else 0.66f
    val base = if (dark) Color(0xFF24262D) else Color.White
    val edge = if (dark) Color.White.copy(alpha = 0.16f) else Color.White.copy(alpha = 0.75f)

    Box(modifier = modifier.fillMaxWidth(), contentAlignment = Alignment.BottomCenter) {
        // 底部渐隐带：胶囊做透以后，滚动到底的卡片标题会直接压在 tab 文字上。
        // 用一层极淡的背景色渐变把内容"化"掉，胶囊本身仍然是透的。
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(NavBarContentHeight + NavBarFloatGap + navBarInset + 30.dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.55f to scheme.background.copy(alpha = if (dark) 0.42f else 0.28f),
                        // 最底下压得住：手势条那一条下面露出半行字最难看
                        1f to scheme.background.copy(alpha = if (dark) 0.96f else 0.88f),
                    ),
                ),
        )
        Row(
            modifier = Modifier
                .padding(bottom = navBarInset + NavBarFloatGap)
                .fillMaxWidth()
                .padding(horizontal = 14.dp)
                .height(NavBarContentHeight)
                .shadow(
                    elevation = 16.dp,
                    shape = shape,
                    ambientColor = Color.Black.copy(alpha = if (dark) 0.55f else 0.16f),
                    spotColor = Color.Black.copy(alpha = if (dark) 0.45f else 0.13f),
                )
                .clip(shape)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            base.copy(alpha = glassTopAlpha),
                            base.copy(alpha = glassBottomAlpha),
                        ),
                    ),
                )
                .border(1.dp, edge, shape),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            items.forEachIndexed { index, item ->
                NavItem(
                    item = item,
                    selected = currentRoute == item.route,
                    onClick = { onSelect(item.route) },
                    highlightShape = when {
                        index == 0 -> RoundedCornerShape(topEnd = 16.dp, bottomEnd = 16.dp)
                        index == items.lastIndex -> RoundedCornerShape(topStart = 16.dp, bottomStart = 16.dp)
                        else -> RoundedCornerShape(16.dp)
                    },
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

data class GlassNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
    /** 高频入口：图标与文字始终用主色，和普通 tab 区分但不出戏 */
    val prominent: Boolean = false,
    /** 待办数，>0 时图标右上角挂一个小圆点 */
    val badge: Int = 0,
)

@Composable
private fun NavItem(
    item: GlassNavItem,
    selected: Boolean,
    onClick: () -> Unit,
    highlightShape: Shape,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        targetValue = when {
            selected -> scheme.primary
            item.prominent -> scheme.primary.copy(alpha = 0.82f)
            else -> scheme.onSurfaceVariant
        },
        label = "navTint",
    )
    Column(
        modifier = modifier
            .clip(highlightShape)
            .background(if (selected) scheme.primary.copy(alpha = 0.11f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(contentAlignment = Alignment.TopEnd) {
            Icon(item.icon, contentDescription = item.label, tint = tint, modifier = Modifier.size(22.dp))
            if (item.badge > 0) {
                Box(
                    modifier = Modifier
                        .offset(x = 5.dp, y = (-3).dp)
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = item.label,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = tint,
        )
    }
}

/** 玻璃胶囊分段控件（课程 / 统计） */
@Composable
fun SegmentedPill(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val ext = LocalExtendedColors.current
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = modifier
            .height(42.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(ext.mutedSurface.copy(alpha = 0.72f))
            .border(1.dp, ext.cardBorder.copy(alpha = 0.9f), RoundedCornerShape(14.dp))
            .padding(3.dp),
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val bg by animateColorAsState(
                targetValue = if (selected) scheme.surface else Color.Transparent,
                label = "segBg",
            )
            val fg by animateColorAsState(
                targetValue = if (selected) scheme.primary else scheme.onSurfaceVariant,
                label = "segFg",
            )
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize()
                    .clip(RoundedCornerShape(11.dp))
                    .background(bg)
                    .clickable { onSelect(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    color = fg,
                )
            }
        }
    }
}

/** 「我的」页面里的一行入口。dense = 设置页那种纯入口列表，行高再压一点，一屏能多放两行 */
@Composable
fun EntryRow(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    iconTint: Color? = null,
    trailing: (@Composable () -> Unit)? = null,
    showDivider: Boolean = false,
    dense: Boolean = false,
) {
    val scheme = MaterialTheme.colorScheme
    val vPad = if (dense) 8.dp else 12.dp
    val boxSize = if (dense) 30.dp else 34.dp
    val boxRadius = if (dense) RoundedCornerShape(10.dp) else RoundedCornerShape(11.dp)
    val iconSize = if (dense) 16.dp else 18.dp
    val gap = if (dense) 10.dp else 12.dp
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(vertical = vPad),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(boxSize)
                    .clip(boxRadius)
                    .background((iconTint ?: scheme.primary).copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint ?: scheme.primary,
                    modifier = Modifier.size(iconSize),
                )
            }
            Spacer(Modifier.width(gap))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant,
                        maxLines = 2,
                    )
                }
            }
            trailing?.invoke()
            if (trailing == null) {
                Icon(
                    Icons.Rounded.ChevronRight,
                    contentDescription = null,
                    tint = scheme.outline,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        if (showDivider) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = if (dense) 40.dp else 46.dp)
                    .height(1.dp)
                    .background(LocalExtendedColors.current.cardBorder.copy(alpha = 0.7f)),
            )
        }
    }
}

/** 圆点色卡（主题色选择器用） */
@Composable
fun ColorDot(color: Color, selected: Boolean, size: Int = 34) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color)
            .border(
                width = if (selected) 2.5.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f) else Color.Transparent,
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) {
            Icon(
                imageVector = Icons.Rounded.Check,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size((size * 0.5f).dp),
            )
        }
    }
}
