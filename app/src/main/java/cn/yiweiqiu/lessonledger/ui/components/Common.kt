package cn.yiweiqiu.lessonledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.isDarkSurface

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    background: Color = MaterialTheme.colorScheme.surface,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = RoundedCornerShape(16.dp)
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    // 默认卡片走玻璃：浅色下是半透明白，深色下是「白色微光」——
    // 底下的极光能透上来，深色界面才不会像一块块实心灰砖。
    val isDefault = background == scheme.surface
    val glass = when {
        isDefault && dark -> Color.White.copy(alpha = 0.048f)
        isDefault -> background.copy(alpha = 0.86f)
        else -> background.copy(alpha = 0.92f)
    }
    val border = when {
        isDefault && dark -> Color.White.copy(alpha = 0.10f)
        else -> LocalExtendedColors.current.cardBorder.copy(alpha = 0.9f)
    }
    var base = modifier
        .fillMaxWidth()
        .clip(shape)
        .background(glass)
        .border(1.dp, border, shape)
    if (onClick != null) base = base.clickable(onClick = onClick)
    Column(modifier = base.padding(contentPadding), content = content)
}

@Composable
fun SectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    /** 设置页那种密集列表用紧凑间距，别把一屏切成两三段 */
    dense: Boolean = false,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = if (dense) 4.dp else 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        action?.invoke()
    }
}

@Composable
fun AvatarCircle(
    initial: String,
    colorHex: String,
    size: Int = 44,
    modifier: Modifier = Modifier,
) {
    val color = parseColor(colorHex, MaterialTheme.colorScheme.primary)
    Box(
        modifier = modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.35f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = initial,
            color = color,
            fontSize = (size * 0.42f).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

fun parseColor(hex: String, fallback: Color): Color = try {
    Color(android.graphics.Color.parseColor(hex))
} catch (e: Exception) {
    fallback
}

/**
 * 会自己缩字号的单行文本。
 *
 * 账本里的金额不能截断：「¥12,345.00」被压成「¥12,3…」，少一位数字比排版难看严重得多。
 * 固定字号 + 省略号在 3 列并排的小卡片里必然出问题（¥999,999 这种六位数的真实场景），
 * 所以这里按可用宽度实测，从 [maxFontSize] 一路试到 [minFontSize]，取第一个放得下的字号。
 * 只在宽度有界时有意义（weight / fillMaxWidth / 固定宽），宽度无界就直接用最大字号。
 *
 * 注意：内部是 BoxWithConstraints（SubcomposeLayout），**不能放进请求 intrinsic 尺寸的容器**
 * （`Modifier.height(IntrinsicSize.Min)` / `width(IntrinsicSize.Max)` 这类），
 * 一旦被求 intrinsic 就会抛 IllegalStateException 闪退。需要等高的场景改用自然等高或固定高度。
 */
@Composable
fun AutoSizeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    maxFontSize: TextUnit = 18.sp,
    minFontSize: TextUnit = 10.sp,
    fontWeight: FontWeight? = null,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
) {
    BoxWithConstraints(modifier) {
        val measurer = rememberTextMeasurer()
        val weight = fontWeight ?: style.fontWeight
        val maxWidthPx = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val fontSize = remember(text, maxWidthPx, maxFontSize, minFontSize, weight, style) {
            if (maxWidthPx == Int.MAX_VALUE) {
                maxFontSize
            } else {
                var chosen = minFontSize.value
                var candidate = maxFontSize.value
                while (candidate >= minFontSize.value) {
                    val measured = measurer.measure(
                        text = AnnotatedString(text),
                        style = style.copy(fontSize = candidate.sp, fontWeight = weight),
                        maxLines = 1,
                        softWrap = false,
                    )
                    if (measured.size.width <= maxWidthPx) {
                        chosen = candidate
                        break
                    }
                    candidate -= 0.5f
                }
                chosen.sp
            }
        }
        Text(
            text = text,
            style = style,
            fontSize = fontSize,
            fontWeight = weight,
            color = color,
            textAlign = textAlign,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.onSurface,
    hint: String? = null,
    /** 传了就能点：统计数字钻取到对应明细 */
    onClick: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(LocalExtendedColors.current.mutedSurface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        Spacer(Modifier.height(6.dp))
        // 金额固定 18sp 单行 + 省略号时，¥123,456 这种六位数会被截成「¥1…」，
        // 所以这里换成按宽度自适应：字少就大，字多就把字号压到放得下为止。
        AutoSizeText(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            maxFontSize = 18.sp,
            minFontSize = 10.sp,
            fontWeight = FontWeight.SemiBold,
            color = accent,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
            )
        }
    }
}

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(Modifier.height(18.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            androidx.compose.material3.Button(
                onClick = onAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(actionLabel) }
        }
        if (secondaryActionLabel != null && onSecondaryAction != null) {
            Spacer(Modifier.height(4.dp))
            androidx.compose.material3.TextButton(
                onClick = onSecondaryAction,
                modifier = Modifier.heightIn(min = 48.dp),
            ) { Text(secondaryActionLabel) }
        }
    }
}

/** 课时进度：已用实心 + 批次分段 */
@Composable
fun BatchProgress(
    stats: LedgerCalc.CourseStats,
    modifier: Modifier = Modifier,
    height: Int = 10,
) {
    val colors = LocalExtendedColors.current
    val palette = cn.yiweiqiu.lessonledger.ui.theme.BatchPalette
    val total = stats.purchasedHalf.coerceAtLeast(1L)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(height.dp)
            .clip(RoundedCornerShape(height.dp))
            .background(colors.chartGrid),
    ) {
        stats.batches.forEachIndexed { index, batch ->
            val usedWeight = (batch.usedHalf.toFloat() / total).coerceIn(0f, 1f)
            val emptyWeight = (batch.remainingHalf.toFloat() / total).coerceIn(0f, 1f)
            val baseColor = palette[index % palette.size]
            if (usedWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(usedWeight)
                        .fillMaxWidth()
                        .height(height.dp)
                        .background(baseColor),
                )
            }
            if (emptyWeight > 0f) {
                Box(
                    modifier = Modifier
                        .weight(emptyWeight)
                        .fillMaxWidth()
                        .height(height.dp)
                        .background(baseColor.copy(alpha = 0.20f)),
                )
            }
        }
        if (stats.remainingHalf > 0 && stats.batches.isEmpty()) {
            Box(Modifier.weight(1f))
        }
    }
}

@Composable
fun lessonStatusColor(status: LessonStatus): Color {
    val colors = LocalExtendedColors.current
    return when (status) {
        LessonStatus.DONE -> colors.success
        LessonStatus.PENDING -> colors.warning
        // 请假不用灰：灰在本 App 里是「空 / 未上」的语义，请假却是已经处理完的事
        LessonStatus.LEAVE -> colors.leave
        LessonStatus.ABSENT -> colors.danger
        LessonStatus.RESCHEDULED -> colors.info
        LessonStatus.CANCELLED -> MaterialTheme.colorScheme.outline
    }
}

@Composable
fun StatusPill(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
    filled: Boolean = false,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(if (filled) color else color.copy(alpha = 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = if (filled) Color.White else color,
        )
    }
}

@Composable
fun KeyValueRow(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = MaterialTheme.colorScheme.onSurface,
    emphasize: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = if (emphasize) FontWeight.SemiBold else FontWeight.Normal,
            color = valueColor,
        )
        trailing?.invoke()
    }
}

/**
 * 「N 次 / X 节」并列展示，两者对不上时后面挂一个问号。
 *
 * 为什么会有两个数字：
 * - 「次」是这门课上了几趟（家长关心的是这个）；
 * - 「节」是按课程单节时长折算出来的课时，扣钱、算剩余都用它。
 * 单节 60 分钟的英语课，周三上 90 分钟按 1.5 节扣、周六上 120 分钟按 2 节扣，
 * 于是「6 次课」对应的是「9 节」。不解释一句，看到 6 和 9 的人第一反应是记错了。
 * 两个数字相等（普通 1 节/次的课）时不显示问号，免得每页都挂个没用的图标。
 */
@Composable
fun LessonCountText(
    count: Int,
    half: Long,
    modifier: Modifier = Modifier,
    /** 这门课的单节时长（分钟）；传了之后弹窗里会举这门课自己的例子 */
    standardMinutes: Int? = null,
    style: TextStyle = MaterialTheme.typography.bodyMedium,
    color: Color = MaterialTheme.colorScheme.onSurface,
) {
    var showHelp by remember { mutableStateOf(false) }
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = "${count} 次 / ${Lessons.format(half)} 节",
            style = style,
            color = color,
        )
        if (isCountMismatch(count, half)) {
            Spacer(Modifier.width(4.dp))
            MismatchHintDot { showHelp = true }
        }
    }
    if (showHelp) {
        LessonCountHelpDialog(
            count = count,
            half = half,
            standardMinutes = standardMinutes,
            onDismiss = { showHelp = false },
        )
    }
}

/** 只要一个问号（数字已经摆在别处了）：点开同样是那段解释 */
@Composable
fun MismatchHint(
    count: Int,
    half: Long,
    standardMinutes: Int? = null,
) {
    var showHelp by remember { mutableStateOf(false) }
    MismatchHintDot { showHelp = true }
    if (showHelp) {
        LessonCountHelpDialog(
            count = count,
            half = half,
            standardMinutes = standardMinutes,
            onDismiss = { showHelp = false },
        )
    }
}

/** 次数和节数对不上才算「需要解释」：1 节 = 2 个半节 */
fun isCountMismatch(count: Int, half: Long): Boolean = half != count.toLong() * Lessons.HALF_PER_LESSON

/** 小小的问号：不抢字号，但一眼看得见可以点 */
@Composable
fun MismatchHintDot(onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier = Modifier
            .size(16.dp)
            .clip(CircleShape)
            .background(scheme.primary.copy(alpha = 0.14f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "?",
            fontSize = 10.sp,
            lineHeight = 10.sp,
            fontWeight = FontWeight.Bold,
            color = scheme.primary,
        )
    }
}

/** 「次」和「节」为什么不一样 —— 点问号打开 */
@Composable
fun LessonCountHelpDialog(
    count: Int,
    half: Long,
    standardMinutes: Int?,
    onDismiss: () -> Unit,
) {
    val average = if (count > 0) half.toFloat() / count / Lessons.HALF_PER_LESSON else 0f
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("「次」和「节」不是一回事") },
        text = {
            Column {
                Text(
                    text = "「次」是上课的次数，看的是去了几趟；「节」是扣掉的课时，算钱和剩余都用它。",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (standardMinutes != null && standardMinutes > 0) {
                        "这门课按单节 $standardMinutes 分钟折算：上 90 分钟扣 1.5 节、上 120 分钟扣 2 节，" +
                            "所以同样的「一次课」扣掉的课时可以不一样。"
                    } else {
                        "课时按「这节课的实际时长 ÷ 课程的单节时长」折算：三门课可能一节 45 分钟、" +
                            "另一门一节 90 分钟，所以「一次课」扣掉的课时不一样。"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "这段时间：$count 次课 = ${Lessons.format(half)} 节" +
                        if (count > 0) "，平均每次 ${"%.2f".format(average).trimEnd('0').trimEnd('.')} 节" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("明白了") } },
    )
}

@Composable
fun IconBadge(
    icon: ImageVector,
    color: Color,
    size: Int = 36,
) {
    Box(
        modifier = Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size((size * 0.55f).dp))
    }
}

@Composable
fun VerticalGap(height: Int) = Spacer(Modifier.height(height.dp))

@Composable
fun HorizontalGap(width: Int) = Spacer(Modifier.width(width.dp))
