package cn.yiweiqiu.lessonledger.ui.courses

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.bottomBarInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset

/**
 * 已结束 / 已退费课程的全部清单。
 *
 * 课程页那个区块只平铺 5 门——已结束是历史，不是每天要翻的清单，
 * 全铺出来会把「记一笔缴费」推到好几屏之外。多出来的都进这里，
 * 页面里可以一直往下滑，也能按孩子过滤（跟课程页保持一致）。
 */
@Composable
fun ClosedCoursesScreen(
    childId: String?,
    onBack: () -> Unit,
    onOpenCourse: (String) -> Unit,
    vm: CoursesViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    // 课程页如果按孩子筛过，进来看到的也应该是同一个孩子的历史
    LaunchedEffect(childId) { vm.selectChild(childId) }

    val closed = state.closed
    val grossPaid = closed.sumOf { it.stats.grossPaidCents }
    val refunded = closed.sumOf { it.stats.refundedCents }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarInset() + 4.dp,
                bottom = bottomBarInset(24.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (closed.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        icon = Icons.Rounded.MenuBook,
                        title = "还没有已结束的课程",
                        description = "课时上完、手动标为已结束，或者办了退费的课程，都会收进这里。",
                    )
                }
            } else {
                item(key = "summary") {
                    Text(
                        text = buildString {
                            append("共 ${closed.size} 门 · 累计缴费 ${Money.compact(grossPaid)}")
                            if (refunded > 0) append(" · 已退费 ${Money.compact(refunded)}")
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                    )
                }
                items(closed, key = { it.course.id }) { row ->
                    CourseCard(
                        row = row,
                        period = state.periods[row.course.id],
                        showChild = state.selectedChildId == null && state.children.size > 1,
                        onClick = { onOpenCourse(row.course.id) },
                    )
                }
            }
        }

        GlassTopBar(
            title = "已结束的课程",
            subtitle = if (closed.isEmpty()) "没有历史课程" else "共 ${closed.size} 门 · 按最近变动排序",
            modifier = Modifier.align(Alignment.TopCenter),
            leading = { GlassBackButton(onBack) },
            scrollBack = { listState.canScrollBackward },
        )
    }
}
