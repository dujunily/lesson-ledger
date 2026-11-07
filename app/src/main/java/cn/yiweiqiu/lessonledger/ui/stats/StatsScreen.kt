package cn.yiweiqiu.lessonledger.ui.stats

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.StatsPeriod
import cn.yiweiqiu.lessonledger.domain.StatsResult
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.CategoryDonut
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.MonthlyBarChart
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.StatTile
import cn.yiweiqiu.lessonledger.ui.components.bottomBarInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.categoryColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.YearMonth
import kotlin.math.abs

/**
 * 独立「统计」tab：从课程页拆出来的看板，带自己的玻璃标题栏。
 * 看板上的数字、柱子、占比行都支持钻取到「缴费明细」。
 */
@Composable
fun StatsScreen(
    onDrill: (StatsDrill) -> Unit = {},
    vm: StatsViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        StatsContent(
            state = state,
            vm = vm,
            topPadding = topBarInset() + 6.dp,
            onDrill = onDrill,
            listState = listState,
        )
        GlassTopBar(
            title = "统计",
            subtitle = "支出、退费与科目分布",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollBack = { listState.canScrollBackward },
        )
    }
}

/**
 * 统计看板内容。现在是独立「统计」tab 的主体（也保留给嵌入式调用），
 * 顶部留白由调用方按玻璃条高度传进来。
 */
@Composable
fun StatsContent(
    state: StatsViewModel.UiState,
    vm: StatsViewModel,
    topPadding: Dp,
    modifier: Modifier = Modifier,
    onDrill: (StatsDrill) -> Unit = {},
    listState: LazyListState = rememberLazyListState(),
) {
    val ext = LocalExtendedColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var csvCache by remember { mutableStateOf<String?>(null) }
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        val content = csvCache
        if (uri != null && content != null) {
            scope.launch {
                withContext(Dispatchers.IO) {
                    runCatching {
                        context.contentResolver.openOutputStream(uri)?.use {
                            it.write(content.toByteArray(Charsets.UTF_8))
                        }
                    }
                }
            }
        }
        csvCache = null
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        state = listState,
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = topPadding,
            bottom = bottomBarInset(24.dp),
        ),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "period") {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                StatsPeriod.entries.forEach { period ->
                    FilterChip(
                        selected = state.period == period,
                        onClick = { vm.setPeriod(period) },
                        label = { Text(period.label) },
                    )
                }
            }
        }

        if (state.children.size > 1) {
            item(key = "child-filter") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = state.selectedChildId == null,
                        onClick = { vm.setChild(null) },
                        label = { Text("全部孩子") },
                    )
                    state.children.forEach { child ->
                        FilterChip(
                            selected = state.selectedChildId == child.id,
                            onClick = { vm.setChild(child.id) },
                            label = { Text(child.displayName) },
                        )
                    }
                }
            }
        }

        val result = state.result
        if (result == null || (!result.hasData && result.monthly.all { it.netCents == 0L })) {
            item(key = "empty") {
                EmptyState(
                    icon = Icons.Rounded.PieChart,
                    title = "还没有可统计的支出",
                    description = "先在课程详情里记一笔缴费，这里就会出现支出趋势和科目占比。",
                )
            }
        } else {
            item(key = "summary") {
                AppCard {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        StatTile(
                            label = "支出合计",
                            value = Money.compact(result.spendCents),
                            accent = ext.expense,
                            modifier = Modifier.weight(1f),
                            onClick = { onDrill(StatsDrill(period = state.period, childId = state.selectedChildId)) },
                        )
                        StatTile(
                            label = "退费合计",
                            value = Money.compact(abs(result.refundCents)),
                            accent = ext.income,
                            modifier = Modifier.weight(1f),
                            onClick = { onDrill(StatsDrill(period = state.period, childId = state.selectedChildId)) },
                        )
                        StatTile(
                            label = "净支出",
                            value = Money.compact(result.netCents),
                            modifier = Modifier.weight(1f),
                            onClick = { onDrill(StatsDrill(period = state.period, childId = state.selectedChildId)) },
                        )
                    }
                    Text(
                        text = "${Dates.formatDate(result.from)} ~ ${Dates.formatDate(result.to)} · 共 ${result.paymentCount} 笔 · 点数字看明细",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }

            item(key = "bar") {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "近 6 个月支出",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            "点柱子看当月明细",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    MonthlyBarChart(
                        points = result.monthly,
                        onPointClick = { point ->
                            onDrill(
                                StatsDrill(
                                    childId = state.selectedChildId,
                                    month = YearMonth.of(point.year, point.month),
                                )
                            )
                        },
                    )
                }
            }

            if (result.byCategory.isNotEmpty()) {
                item(key = "donut") {
                    AppCard {
                        Text(
                            "科目占比",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(10.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CategoryDonut(
                                slices = result.byCategory,
                                totalCents = result.byCategory.sumOf { it.cents },
                            )
                            Spacer(Modifier.width(16.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                val total = result.byCategory.sumOf { it.cents }.coerceAtLeast(1L)
                                result.byCategory.take(6).forEach { slice ->
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                onDrill(
                                                    StatsDrill(
                                                        period = state.period,
                                                        childId = state.selectedChildId,
                                                        category = slice.category,
                                                    )
                                                )
                                            }
                                            .padding(vertical = 3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(8.dp)
                                                .clip(CircleShape)
                                                .background(categoryColor(slice.category)),
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = slice.category.label,
                                            style = MaterialTheme.typography.bodySmall,
                                            modifier = Modifier.weight(1f),
                                        )
                                        Text(
                                            text = "${slice.cents * 100 / total}%",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (result.byOrg.isNotEmpty()) {
                item(key = "org-header") {
                    SectionHeader(title = "机构累计花费 Top 5")
                }
                item(key = "org-list") {
                    AppCard {
                        result.byOrg.forEachIndexed { index, org ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDrill(
                                            StatsDrill(
                                                period = state.period,
                                                childId = state.selectedChildId,
                                                org = org.orgName,
                                            )
                                        )
                                    }
                                    .padding(vertical = 7.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Text(
                                        text = "${index + 1}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = org.orgName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Money.compact(org.cents),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = if (org.cents < 0) ext.income else ext.expense,
                                )
                            }
                        }
                    }
                }
            }

            if (result.byChild.size > 1) {
                item(key = "child-header") { SectionHeader(title = "分孩子统计") }
                item(key = "child-list") {
                    AppCard {
                        result.byChild.forEach { slice ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        onDrill(StatsDrill(period = state.period, childId = slice.child.id))
                                    }
                                    .padding(vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = slice.child.displayName,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f),
                                )
                                Text(
                                    text = Money.compact(slice.cents),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = if (slice.cents < 0) ext.income else ext.expense,
                                )
                            }
                        }
                    }
                }
            }

            item(key = "export") {
                OutlinedButton(
                    onClick = {
                        scope.launch {
                            csvCache = vm.buildCsv()
                            exportLauncher.launch("课外班支出-${Dates.formatDate(Dates.today())}.csv")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) {
                    Icon(Icons.Rounded.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导出 CSV 明细")
                }
            }
        }
    }
}
