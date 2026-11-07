package cn.yiweiqiu.lessonledger.ui.stats

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.Category
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.Payment
import cn.yiweiqiu.lessonledger.domain.StatsCalc
import cn.yiweiqiu.lessonledger.domain.StatsPeriod
import cn.yiweiqiu.lessonledger.ui.courseIcon
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.bottomBarInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.categoryColor
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.YearMonth
import javax.inject.Inject

/**
 * 「缴费明细」钻取页：统计页 / 首页的数字点进来后看的流水。
 * 过滤条件从导航参数来：period / childId / category / org / month（月优先于 period）。
 */
@HiltViewModel
class PaymentsDetailViewModel @Inject constructor(
    repo: LedgerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val period: StatsPeriod =
        savedStateHandle.get<String>("period")?.let { runCatching { StatsPeriod.valueOf(it) }.getOrNull() }
            ?: StatsPeriod.ALL
    private val childId: String? = savedStateHandle.get<String>("childId")?.takeIf { it.isNotBlank() }
    private val category: Category? =
        savedStateHandle.get<String>("category")?.let { runCatching { Category.valueOf(it) }.getOrNull() }
    private val org: String? = savedStateHandle.get<String>("org")?.takeIf { it.isNotBlank() }
    private val month: YearMonth? =
        savedStateHandle.get<String>("month")?.takeIf { it.isNotBlank() }?.let { runCatching { YearMonth.parse(it) }.getOrNull() }

    data class Row(
        val payment: Payment,
        val course: Course?,
        val child: Child?,
    )

    data class UiState(
        val rows: List<Row> = emptyList(),
        val spendCents: Long = 0,
        val refundCents: Long = 0,
        val subtitle: String = "",
        val ready: Boolean = false,
    )

    val uiState = repo.data.map { data ->
        val courseById = data.courses.associateBy { it.id }
        val childName = childId?.let { data.childById(it)?.displayName }

        val scoped = data.payments
            .asSequence()
            .filter { !it.deleted }
            .filter { p -> childId == null || courseById[p.courseId]?.childId == childId }
            .filter { p -> category == null || courseById[p.courseId]?.category == category }
            .filter { p ->
                org == null || (courseById[p.courseId]?.orgName?.ifBlank { "未填写机构" } ?: "未填写机构") == org
            }
            .filter { p ->
                when {
                    month != null -> YearMonth.from(p.payDate) == month
                    else -> {
                        val (from, to) = StatsCalc.rangeOf(period, data)
                        !p.payDate.isBefore(from) && !p.payDate.isAfter(to)
                    }
                }
            }
            .sortedWith(compareByDescending<Payment> { it.payDate }.thenByDescending { it.createdAt })
            .toList()

        UiState(
            rows = scoped.map { Row(it, courseById[it.courseId], courseById[it.courseId]?.let { c -> data.childById(c.childId) }) },
            spendCents = scoped.filter { it.amountCents > 0 }.sumOf { it.amountCents },
            refundCents = scoped.filter { it.amountCents < 0 }.sumOf { it.amountCents },
            subtitle = subtitle(data, childName),
            ready = true,
        )
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UiState())

    private fun subtitle(data: cn.yiweiqiu.lessonledger.domain.LedgerData, childName: String?): String {
        val range = when {
            month != null -> "${month.year}年${month.monthValue}月"
            else -> {
                val (from, to) = StatsCalc.rangeOf(period, data)
                if (period == StatsPeriod.ALL) "全部" else "${Dates.formatDate(from)} ~ ${Dates.formatDate(to)}"
            }
        }
        return listOfNotNull(range, childName, category?.label, org).joinToString(" · ")
    }
}

@Composable
fun PaymentsDetailScreen(
    onOpenCourse: (String) -> Unit,
    onBack: () -> Unit,
    vm: PaymentsDetailViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ext = LocalExtendedColors.current
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarInset() + 2.dp,
                bottom = 36.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (!state.ready) {
                return@LazyColumn
            }
            if (state.rows.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.Receipt,
                        title = "没有符合条件的缴费记录",
                        description = "换个筛选条件试试，或先去课程详情里记一笔缴费。",
                    )
                }
            } else {
                item(key = "summary") {
                    AppCard(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "共 ${state.rows.size} 笔",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            if (state.spendCents > 0) {
                                Text(
                                    text = "支出 ${Money.compact(state.spendCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.expense,
                                )
                            }
                            if (state.refundCents < 0) {
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "退费 ${Money.compact(-state.refundCents)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.income,
                                )
                            }
                        }
                    }
                }
                items(state.rows.size, key = { state.rows[it].payment.id }) { index ->
                    val row = state.rows[index]
                    PaymentRow(
                        row = row,
                        onClick = { onOpenCourse(row.payment.courseId) },
                    )
                }
            }
        }

        GlassTopBar(
            title = "缴费明细",
            subtitle = state.subtitle.ifBlank { "全部记录" },
            leading = { GlassBackButton(onBack) },
            scrollBack = { listState.canScrollBackward },
        )
    }
}

@Composable
private fun PaymentRow(
    row: PaymentsDetailViewModel.Row,
    onClick: () -> Unit,
) {
    val ext = LocalExtendedColors.current
    val payment = row.payment
    val course = row.course
    val accent = course?.let { categoryColor(it.category) } ?: MaterialTheme.colorScheme.primary

    AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center,
            ) {
                if (payment.isRefund) {
                    Icon(
                        Icons.AutoMirrored.Rounded.Undo,
                        contentDescription = "退费",
                        tint = ext.income,
                        modifier = Modifier.size(19.dp),
                    )
                } else {
                    Icon(
                        course?.let { courseIcon(it.courseName, it.category) } ?: Icons.Rounded.Receipt,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(19.dp),
                    )
                }
            }
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = course?.courseName ?: "已删除的课程",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Dates.formatDate(payment.payDate),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = buildString {
                        row.child?.let { append("${it.displayName} · ") }
                        course?.let { append(it.orgName.ifBlank { it.category.label }) }
                        val lessons = payment.totalHalf
                        if (lessons > 0) {
                            if (isNotEmpty()) append(" · ")
                            append(Lessons.format(lessons))
                        }
                        if (payment.note.isNotBlank()) {
                            if (isNotEmpty()) append(" · ")
                            append(payment.note)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(
                text = if (payment.isRefund) {
                    "+${Money.compact(-payment.amountCents)}"
                } else {
                    Money.compact(payment.amountCents)
                },
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = if (payment.isRefund) ext.income else ext.expense,
            )
        }
    }
}
