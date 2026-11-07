package cn.yiweiqiu.lessonledger.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.CategorySlice
import cn.yiweiqiu.lessonledger.domain.MonthPoint
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.categoryColor

/** 近 6 个月支出柱状图（Compose Canvas 手绘，无第三方图表库） */
@Composable
fun MonthlyBarChart(
    points: List<MonthPoint>,
    modifier: Modifier = Modifier,
    height: Int = 168,
    highlightLast: Boolean = true,
    /** 传了就能点某根柱子：钻取到该月的缴费明细 */
    onPointClick: ((MonthPoint) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer()
    val axisColor = LocalExtendedColors.current.chartGrid
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val barColor = MaterialTheme.colorScheme.primary
    val barColorSoft = MaterialTheme.colorScheme.primary.copy(alpha = 0.55f)

    Box(modifier = modifier.fillMaxWidth().height(height.dp)) {
        Canvas(modifier = Modifier.fillMaxSize()) {
        if (points.isEmpty()) return@Canvas
        val labelHeight = 18.dp.toPx()
        val valueHeight = 14.dp.toPx()
        val chartHeight = size.height - labelHeight - valueHeight
        val slot = size.width / points.size
        val barWidth = slot * 0.42f
        val maxValue = points.maxOf { it.netCents }.coerceAtLeast(1L)

        // 基线
        drawLine(
            color = axisColor,
            start = Offset(0f, size.height - labelHeight),
            end = Offset(size.width, size.height - labelHeight),
            strokeWidth = 1f,
        )

        points.forEachIndexed { index, point ->
            val ratio = (point.netCents.toFloat() / maxValue.toFloat()).coerceIn(0f, 1f)
            val barHeight = (chartHeight * ratio).coerceAtLeast(if (point.netCents > 0) 3f else 0f)
            val left = index * slot + (slot - barWidth) / 2f
            val top = valueHeight + (chartHeight - barHeight)

            if (point.netCents > 0) {
                drawRoundRect(
                    color = if (highlightLast && index == points.lastIndex) barColor else barColorSoft,
                    topLeft = Offset(left, top),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(barWidth / 2f, barWidth / 2f),
                )
            }

            // 金额（元）
            val yuan = point.netCents / 100
            val valueText = when {
                yuan >= 10_000 -> {
                    val wan = yuan / 10_000
                    val rest = (yuan % 10_000) / 1_000
                    if (rest > 0) "$wan.${rest}万" else "${wan}万"
                }
                else -> yuan.toString()
            }
            if (valueText.isNotEmpty() && point.netCents > 0) {
                val measured = textMeasurer.measure(
                    valueText,
                    TextStyle(fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Medium),
                )
                drawText(
                    textMeasurer = textMeasurer,
                    text = valueText,
                    topLeft = Offset(
                        left + barWidth / 2f - measured.size.width / 2f,
                        (top - measured.size.height - 2f).coerceAtLeast(0f),
                    ),
                    style = TextStyle(fontSize = 9.sp, color = labelColor, fontWeight = FontWeight.Medium),
                )
            }

            // 月份标签
            val label = textMeasurer.measure(
                point.label,
                TextStyle(fontSize = 10.sp, color = labelColor),
            )
            drawText(
                textMeasurer = textMeasurer,
                text = point.label,
                topLeft = Offset(
                    index * slot + (slot - label.size.width) / 2f,
                    size.height - labelHeight + 3.dp.toPx(),
                ),
                style = TextStyle(fontSize = 10.sp, color = labelColor),
            )
        }
        }

        // 柱子的点击层：按横向位置落到哪一格就回调哪个月
        if (onPointClick != null && points.isNotEmpty()) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .pointerInput(points) {
                        detectTapGestures { pos ->
                            val slot = size.width / points.size
                            val idx = (pos.x / slot).toInt().coerceIn(0, points.lastIndex)
                            onPointClick(points[idx])
                        }
                    },
            )
        }
    }
}

/** 科目占比环形图（Canvas 手绘） */
@Composable
fun CategoryDonut(
    slices: List<CategorySlice>,
    totalCents: Long,
    modifier: Modifier = Modifier,
    diameter: Int = 132,
    centerLabel: String = "总支出",
) {
    val trackColor = LocalExtendedColors.current.chartGrid
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant
    val positive = slices.filter { it.cents > 0 }
    val sum = positive.sumOf { it.cents }.coerceAtLeast(1L)

    Box(
        modifier = modifier.size(diameter.dp),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 22.dp.toPx()
            val inset = stroke / 2f + 2f
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = trackColor,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Butt),
            )

            var startAngle = -90f
            positive.forEach { slice ->
                val sweep = slice.cents.toFloat() / sum.toFloat() * 360f
                drawArc(
                    color = categoryColor(slice.category),
                    startAngle = startAngle + 1.5f,
                    sweepAngle = (sweep - 3f).coerceAtLeast(0.8f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = Money.compact(totalCents),
                style = MaterialTheme.typography.titleMedium,
                color = onSurface,
            )
            Text(
                text = centerLabel,
                style = MaterialTheme.typography.labelSmall,
                color = onSurfaceVariant,
            )
        }
    }
}
