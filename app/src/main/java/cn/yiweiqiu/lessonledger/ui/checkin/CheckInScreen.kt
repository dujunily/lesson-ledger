package cn.yiweiqiu.lessonledger.ui.checkin

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Block
import androidx.compose.material.icons.rounded.Cancel
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.EventBusy
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassFab
import cn.yiweiqiu.lessonledger.ui.components.MismatchHint
import cn.yiweiqiu.lessonledger.ui.components.isCountMismatch
import cn.yiweiqiu.lessonledger.ui.components.pageBottomInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.components.StatusPill
import cn.yiweiqiu.lessonledger.ui.components.lessonStatusColor
import cn.yiweiqiu.lessonledger.ui.forms.LessonFormSheet
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@Composable
fun CheckInScreen(
    onBack: () -> Unit = {},
    vm: CheckInViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    val ext = LocalExtendedColors.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 月历收起多少 px：0 = 完全展开，collapseMaxPx = 只剩月份那一条。
    // 用 Animatable 存这个值是为了让高度只在测量阶段被读到（见 collapsingHeight），
    // 拖动时不用重组整页 —— 列表不会出现"每帧重排"。
    val collapse = remember { Animatable(0f) }
    // 会被收掉的那块（星期表头 + 格子 + 图例）的自然高度，由子布局量回来
    var calendarBodyPx by remember { mutableFloatStateOf(0f) }
    val collapseMaxPx = calendarBodyPx
    val collapseFraction by remember {
        derivedStateOf {
            if (collapseMaxPx <= 0f) 0f else (collapse.value / collapseMaxPx).coerceIn(0f, 1f)
        }
    }
    val calendarCollapsed by remember { derivedStateOf { collapseFraction > 0.5f } }

    /** 手离开屏幕后把月历停到"全开 / 全收"两端，别卡在半中间 */
    fun settle(velocityY: Float = 0f) {
        val max = collapseMaxPx
        if (max <= 0f) return
        val target = when {
            velocityY < -180f -> max
            velocityY > 180f -> 0f
            collapse.value >= max / 2f -> max
            else -> 0f
        }
        if (target != collapse.value) {
            scope.launch {
                collapse.animateTo(
                    target,
                    spring(stiffness = Spring.StiffnessMediumLow, visibilityThreshold = 0.5f),
                )
            }
        }
    }

    // 月历跟着手指收放：往下翻先把日历收起来再让列表滚，往回翻到列表顶部再放出日历。
    // 全部走"消费滚动量"的方式，所以手势是连续的、跟手的，不再靠"滚过一点就整块消失"。
    val nestedScroll = remember(listState, collapseMaxPx, scope) {
        object : NestedScrollConnection {
            /** 返回本次真的拿去收/放日历的位移（符号与 available 一致） */
            private fun take(deltaY: Float): Float {
                val max = collapseMaxPx
                if (max <= 0f) return 0f
                val current = collapse.value
                val next = (current - deltaY).coerceIn(0f, max)
                if (next != current) scope.launch { collapse.snapTo(next) }
                return -(next - current)
            }

            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                // 往下看后面的内容：先收日历
                return if (available.y < 0f) Offset(0f, take(available.y)) else Offset.Zero
            }

            override fun onPostScroll(
                consumed: Offset,
                available: Offset,
                source: NestedScrollSource,
            ): Offset {
                // 列表已经到顶还在往回拽：这才轮到日历放出来
                return if (available.y > 0f && !listState.canScrollBackward) {
                    Offset(0f, take(available.y))
                } else {
                    Offset.Zero
                }
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                settle(available.y)
                return Velocity.Zero // 不吃掉惯性，列表照常甩
            }
        }
    }

    // 慢慢拖到一半松手不一定产生 fling：这里兜一下——收起量一段时间不再变化
    //（手指停了 / 动画播完了）就把它停到全开或全收，别卡在半中间
    LaunchedEffect(collapseMaxPx, scope) {
        // 新值一来就把上一次的等待取消掉：只有"最后一下变化"之后的等待会真的执行
        snapshotFlow { collapse.value }
            .distinctUntilChanged()
            .collectLatest {
                delay(140)
                settle()
            }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topBarInset() + 2.dp)
                .nestedScroll(nestedScroll),
        ) {
            // 上半屏：月历常驻顶部。位置永远不动，换哪一天都是它自己在原地更新，
            // 不再像以前那样跟着列表滚、点一次日期跳一次。
            AppCard(
                modifier = Modifier.padding(horizontal = 16.dp),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 10.dp),
            ) {
                MonthHeader(
                    month = state.month,
                    collapsed = calendarCollapsed,
                    collapseFraction = { collapseFraction },
                    onPrev = vm::previousMonth,
                    onNext = vm::nextMonth,
                    onToggle = {
                        scope.launch {
                            collapse.animateTo(
                                if (calendarCollapsed) 0f else collapseMaxPx,
                                tween(240),
                            )
                        }
                    },
                )
                // 唯一会被收掉的部分：外层先按自然高度量一次（高度无限），
                // 再把多出来的部分剪掉。高度连续变化 + 内容同时淡出，所以是"让位"而不是"消失"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clipToBounds()
                        .graphicsLayer { alpha = (1f - collapseFraction * 1.6f).coerceIn(0f, 1f) }
                        .collapsingHeight { (collapseMaxPx - collapse.value).coerceAtLeast(0f) },
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onSizeChanged { calendarBodyPx = it.height.toFloat() },
                    ) {
                        Spacer(Modifier.height(8.dp))
                        WeekdayHeader()
                        Spacer(Modifier.height(4.dp))
                        CalendarGrid(
                            month = state.month,
                            selected = state.selected,
                            today = state.today,
                            marks = state.marks,
                            onSelect = vm::select,
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            LegendDot(ext.success, "已打卡", filled = true)
                            LegendDot(ext.warning, "待打卡", filled = false)
                            LegendDot(lessonStatusColor(LessonStatus.LEAVE), "请假", filled = true)
                            LegendDot(ext.danger, "缺勤", filled = true)
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            // 下半屏：当天和当月是两块各自完整的卡片，不再挤在同一张大卡里。
            // 单独一个 LazyColumn 承担滚动，和上面的月历不互相触发重排，滑动是顺的。
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    bottom = pageBottomInset(96.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                // 第一块：选中那一天的全部课次
                item(key = "day-card") {
                    DayLessonCard(
                        state = state,
                        onBackToToday = { vm.select(state.today) },
                        onMark = vm::mark,
                    )
                }
                // 第二块：整月的账，跟着上面的月份一起翻
                item(key = "month-card") {
                    AppCard {
                        MonthSummaryBlock(
                            summary = state.monthSummary,
                            month = state.month,
                        )
                    }
                }
            }
        }

        // 全 App 统一叫「打卡日历」：首页顶栏那枚日历入口、无障碍描述、这个页面的标题，
        // 都必须是同一个词，别一处叫日历、一处叫记录。
        // 不做「连续打卡 N 天」：这是记账工具，不是打卡激励 App，
        // 断一天就清零的数字只会给人添压力，也没人靠它决定上不上课。
        GlassTopBar(
            title = "打卡日历",
            subtitle = "看看这个月上了多少节",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollBack = { listState.canScrollBackward },
            leading = { GlassBackButton(onBack) },
        )

        // 「补打卡」是低频操作，用低调的次级样式放在右下角——
        // 右手拇指区最顺手的位置，也和页面里高频的「已上」主按钮错开层级。
        // 底色和底部导航条同一套玻璃（不是原来那块糊了灰的 surfaceContainerHigh），
        // 透出一点底下的卡片，边界靠描边和投影交代。
        GlassFab(
            text = "补打卡",
            icon = Icons.Rounded.Add,
            onClick = { showForm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 16.dp, bottom = pageBottomInset(12.dp)),
        )
    }

    if (showForm) {
        LessonFormSheet(
            courses = state.courses,
            initialCourseId = state.courses.firstOrNull()?.id,
            initial = null,
            // 日历上已经翻到 / 选中了那一天，补打卡就该默认记在那天，
            // 否则每次都要再翻一遍日历，选中的日期等于白选。
            initialDate = state.selected,
            onDismiss = { showForm = false },
            onSave = { form ->
                vm.addLesson(form)
                showForm = false
            },
        )
    }
}

/**
 * 整月的上课汇总：先看这个月一共上了多少节，再往下按课程拆开。
 * 月份跟着日历一起翻，所以回看上个月、上上个月都是同一块内容。
 * 它挂在当天课表的末尾（不再单独包一张卡），免得页面碎成三块。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthSummaryBlock(
    summary: CheckInViewModel.MonthSummary,
    month: YearMonth,
    modifier: Modifier = Modifier,
) {
    val ext = LocalExtendedColors.current
    Column(modifier = modifier) {
        Column {
            Text(
                text = "${month.monthValue} 月已上",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = Lessons.format(summary.doneHalf),
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = " 节",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            // 「次」和「节」不是一个东西：英语课一次 90 分钟扣 1.5 节，
            // 同一个月的 12 节可能只上了 8 次。对不上时挂个问号，点开有解释。
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "共 ${summary.doneCount} 次课",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (isCountMismatch(summary.doneCount, summary.doneHalf)) {
                    Spacer(Modifier.width(4.dp))
                    MismatchHint(count = summary.doneCount, half = summary.doneHalf)
                }
            }
        }
        // 「这个月哪些课没上成」单独摆一行，请假排头、用最重的颜色——
        // 请了假的课是这个月最该被看见的账，不该缩在角落里当灰色小字。
        // 「待打卡」只算时间已经过去还没打的；排在后面的课走「待上」，
        // 不把"还没到日子"说成"你欠着没打"。
        if (summary.leaveCount > 0 || summary.absentCount > 0 ||
            summary.pendingCount > 0 || summary.upcomingCount > 0
        ) {
            Spacer(Modifier.height(10.dp))
            // 标签会换行，字号调大时不至于被右边裁掉
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (summary.leaveCount > 0) {
                    StatusPill(
                        text = "请假 ${summary.leaveCount}",
                        color = lessonStatusColor(LessonStatus.LEAVE),
                    )
                }
                if (summary.absentCount > 0) {
                    StatusPill(text = "缺勤 ${summary.absentCount}", color = ext.danger)
                }
                if (summary.pendingCount > 0) {
                    StatusPill(text = "待打卡 ${summary.pendingCount}", color = ext.warning)
                }
                if (summary.upcomingCount > 0) {
                    StatusPill(
                        text = "待上 ${summary.upcomingCount}",
                        color = MaterialTheme.colorScheme.outline,
                    )
                }
            }
        }

        if (summary.isEmpty) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "这个月还没有上课记录，翻到别的月份可以看看之前上的课。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant,
            )
            summary.perCourse.forEachIndexed { index, stat ->
                if (index > 0) Spacer(Modifier.height(10.dp))
                MonthCourseRow(stat)
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MonthCourseRow(stat: CheckInViewModel.MonthCourseStat) {
    val ext = LocalExtendedColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = stat.course.courseName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = buildString {
                    stat.child?.displayName?.let { append(it) }
                    val org = stat.course.orgName.ifBlank { stat.course.category.label }
                    if (isNotEmpty()) append(" · ")
                    append(org)
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(8.dp))
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // 每门课也把「次」和「节」一起摆出来：英语课一次 1.5 节，
            // 只写「已上 9 节」看不出到底去了几趟
            if (stat.doneHalf > 0) {
                StatusPill(
                    text = "${stat.doneCount} 次 / ${Lessons.format(stat.doneHalf)} 节",
                    color = ext.success,
                )
                if (isCountMismatch(stat.doneCount, stat.doneHalf)) {
                    MismatchHint(
                        count = stat.doneCount,
                        half = stat.doneHalf,
                        standardMinutes = stat.course.durationMinutes.takeIf { it > 0 },
                    )
                }
            }
            // 请假排在「待打卡」前面：这门课这个月请了几次，
            // 比"还有几节没到时间"更该被看见。颜色统一走 lessonStatusColor，
            // 和列表胶囊、周进度条上的请假段同一个紫。
            if (stat.leaveCount > 0) {
                StatusPill(
                    text = "请假 ${stat.leaveCount}",
                    color = lessonStatusColor(LessonStatus.LEAVE),
                )
            }
            if (stat.absentCount > 0) {
                StatusPill(text = "缺勤 ${stat.absentCount}", color = ext.danger)
            }
            // 只有时间已经过去还没打卡的才叫"待打卡"
            if (stat.pendingCount > 0) {
                StatusPill(text = "待打卡 ${stat.pendingCount}", color = ext.warning)
            }
            // 这门课当月只有调课 / 取消记录，上面一个标签都没有，补一个免得空着
            if (stat.doneHalf == 0L && stat.pendingCount == 0 &&
                stat.leaveCount == 0 && stat.absentCount == 0 && stat.otherCount > 0
            ) {
                StatusPill(
                    text = "调课 ${stat.otherCount}",
                    color = lessonStatusColor(LessonStatus.RESCHEDULED),
                )
            }
        }
    }
}

/**
 * 月历正下方那一行：哪一天、一共几节课、还有几节没打卡。
 *
 * 一天排了两三节的时候，下面是一张张卡片往下摞，光看卡片数不清有没有漏，
 * 所以节数写在这行常驻的位置——「3 节课 · 2 节待打卡」一眼对得上。
 */
@Composable
private fun DayHeaderRow(
    state: CheckInViewModel.UiState,
    modifier: Modifier = Modifier,
    onBackToToday: () -> Unit,
) {
    val d = state.selected
    val lessons = state.selectedLessons
    val byStatus = lessons.groupingBy { it.lesson.status }.eachCount()
    val doneCount = byStatus[LessonStatus.DONE] ?: 0
    val leaveCount = byStatus[LessonStatus.LEAVE] ?: 0
    val absentCount = byStatus[LessonStatus.ABSENT] ?: 0
    val otherCount = (byStatus[LessonStatus.RESCHEDULED] ?: 0) + (byStatus[LessonStatus.CANCELLED] ?: 0)
    val waitingCount = byStatus[LessonStatus.PENDING] ?: 0
    // 还没到的日子不叫「待打卡」，叫「待上」——那只是排着，不是欠着
    val isFutureDay = d.isAfter(state.today)
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "${d.monthValue}月${d.dayOfMonth}日 ${Dates.weekdayNames[Dates.weekdayOf(d)]}",
                    style = MaterialTheme.typography.titleMedium,
                )
                if (d == state.today) {
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(20.dp))
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f))
                            .padding(horizontal = 10.dp, vertical = 3.dp),
                    ) {
                        Text(
                            text = "今天",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
            Text(
                text = when {
                    lessons.isEmpty() -> "没有课程"
                    // 全打完卡了才说「都处理好了」；有请假 / 缺勤就摊开写，
                    // 别让一句"都处理好了"把请假这件事抹平
                    leaveCount == 0 && absentCount == 0 && otherCount == 0 && waitingCount == 0 ->
                        "${lessons.size} 节课 · 都处理好了"
                    else -> buildString {
                        append("${lessons.size} 节课 · ")
                        append(
                            buildList {
                                if (doneCount > 0) add("已上 $doneCount")
                                if (leaveCount > 0) add("请假 $leaveCount")
                                if (absentCount > 0) add("缺勤 $absentCount")
                                if (otherCount > 0) add("调课 $otherCount")
                                if (waitingCount > 0) {
                                    add(if (isFutureDay) "待上 $waitingCount" else "待打卡 $waitingCount")
                                }
                            }.joinToString(" · ")
                        )
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (d != state.today) {
            TextButton(onClick = onBackToToday) {
                Text("回到今天")
            }
        }
    }
}

@Composable
private fun MonthHeader(
    month: java.time.YearMonth,
    collapsed: Boolean,
    collapseFraction: () -> Float,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToggle: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onPrev, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = "上个月")
        }
        Text(
            text = Dates.monthLabel(month.year, month.monthValue),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onNext, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = "下个月")
        }
        IconButton(onClick = onToggle, modifier = Modifier.size(36.dp)) {
            Icon(
                imageVector = Icons.Rounded.ExpandMore,
                contentDescription = if (collapsed) "展开日历" else "收起日历",
                // 角度跟着收起进度连续转，不只停在两个端点——手势拖动时箭头和日历是同步的
                modifier = Modifier.graphicsLayer {
                    rotationZ = 180f * (1f - collapseFraction().coerceIn(0f, 1f))
                },
            )
        }
    }
}

@Composable
private fun WeekdayHeader() {
    Row(modifier = Modifier.fillMaxWidth()) {
        listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun CalendarGrid(
    month: java.time.YearMonth,
    selected: LocalDate,
    today: LocalDate,
    marks: Map<LocalDate, CheckInViewModel.DayMark>,
    onSelect: (LocalDate) -> Unit,
) {
    val ext = LocalExtendedColors.current
    val scheme = MaterialTheme.colorScheme
    val firstDay = month.atDay(1)
    val offset = Dates.weekdayOf(firstDay)
    val daysInMonth = month.lengthOfMonth()
    val totalCells = ((offset + daysInMonth + 6) / 7) * 7
    // 选中和今天都用浅色底，深浅分两档 + 选中再压一圈描边区分，
    // 不用实心块：实心块会把下面那几个状态点压得看不出颜色
    val selectedBg = scheme.primary.copy(alpha = 0.16f)
    val todayBg = scheme.primary.copy(alpha = 0.09f)
    val cellShape = RoundedCornerShape(10.dp)

    Column {
        for (row in 0 until totalCells / 7) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - offset + 1
                    if (dayNumber in 1..daysInMonth) {
                        val date = month.atDay(dayNumber)
                        val mark = marks[date]
                        val isSelected = date == selected
                        val isToday = date == today
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .padding(2.dp)
                                .clip(cellShape)
                                .background(
                                    when {
                                        isSelected -> selectedBg
                                        isToday -> todayBg
                                        else -> Color.Transparent
                                    }
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(1.5.dp, scheme.primary, cellShape)
                                    } else {
                                        Modifier
                                    }
                                )
                                .clickable { onSelect(date) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dayNumber.toString(),
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected || isToday) FontWeight.SemiBold else FontWeight.Normal,
                                    color = when {
                                        isSelected || isToday -> scheme.primary
                                        else -> scheme.onSurface
                                    },
                                )
                                // 一天好几节的时候，这几个点说的是"这天的课都怎么样了"：
                                // 实心 = 已经落定（打卡 / 请假 / 缺勤），空心圈 = 还没打。
                                // 颜色不跟着选中走——选中那天要是几个点全成一个色，就看不出哪些打过了
                                if (mark != null) {
                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(3.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        if (mark.done > 0) Dot(ext.success, filled = true)
                                        if (mark.pending > 0) Dot(ext.warning, filled = false)
                                        if (mark.leave > 0) Dot(scheme.outline, filled = true)
                                        if (mark.absent > 0) Dot(ext.danger, filled = true)
                                    }
                                }
                            }
                        }
                    } else {
                        Spacer(Modifier.weight(1f).height(44.dp))
                    }
                }
            }
        }
    }
}

/**
 * 状态点：实心 = 已经落定的结果（打卡 / 请假 / 缺勤），
 * 空心圈 = 还没到"落定"那一步（待打卡）。形状先分一层，再看颜色，
 * 就算选中那天底色变了也不会认错。
 */
@Composable
private fun Dot(color: Color, filled: Boolean = true) {
    val size = if (filled) 5.dp else 6.dp
    Box(
        modifier = Modifier
            .size(size)
            .then(
                if (filled) {
                    Modifier
                        .clip(CircleShape)
                        .background(color)
                } else {
                    Modifier.border(1.5.dp, color, CircleShape)
                }
            ),
    )
}

@Composable
private fun LegendDot(color: Color, label: String, filled: Boolean = true) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Dot(color, filled = filled)
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * 让这块的可用高度按 [heightPx] 走：子内容照自然高度量一次（不受挤压），
 * 然后按 [heightPx] 报告自己的高度，多出来的部分被剪掉。
 *
 * 关键点：[heightPx] 是在测量阶段读的，不是读 Dp 参数——拖动时只有这一个节点重新测量，
 * 下面的列表不会因为高度变化被整页重组，所以是跟手的。
 */
private fun Modifier.collapsingHeight(heightPx: () -> Float): Modifier = this.then(
    Modifier.layout { measurable, constraints ->
        val placeable = measurable.measure(
            constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity),
        )
        val h = heightPx().roundToInt().coerceAtLeast(0)
        layout(placeable.width, h) {
            placeable.place(0, 0)
        }
    },
)

/**
 * 这天的某一节课：不单独包一张卡，只是当天课表这块区域里的一行，
 * 行与行之间用一条细线分开——多节课连着看是一整块，不是几张卡拼起来。
 */
@Composable
private fun DayLessonRow(
    dayLesson: CheckInViewModel.DayLesson,
    onMark: (LessonStatus, Long?) -> Unit,
) {
    val lesson = dayLesson.lesson
    val course = dayLesson.course
    val ext = LocalExtendedColors.current
    Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = Dates.formatTime(lesson.scheduledAt),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = course.courseName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        text = lesson.status.label,
                        color = lessonStatusColor(lesson.status),
                    )
                }
                Text(
                    text = buildString {
                        dayLesson.child?.let { append("${it.displayName} · ") }
                        append(course.orgName.ifBlank { course.category.label })
                        if (lesson.note.isNotBlank()) append(" · ${lesson.note}")
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        // 「已上」是最高频的动作：整行宽的主按钮，一眼就知道这一页是来干嘛的；
        // 请假 / 缺勤 / 调课是低频操作，收在下面一行当次级选项。
        // 扣多少节不在这里定：repository 会拿这节课的时长 ÷ 课程单节时长折算
        // （45 分钟的课扣 45 分钟的量，90 分钟的扣 1.5 节），不再写死 1 节。
        // 图标上没打的那一律是空心圈，只有真打了卡才是勾——
        // 「待打卡」那一行顶着个勾，容易被看成已经打过了。
        //
        // 已经落定（请假 / 缺勤 / 调课 / 取消）的课不再顶着「打卡」按钮：
        // 请了假还摆一个整行宽、还亮着的「打卡」，第一眼就是在问"你怎么还没打"，
        // 这行最该说清的其实是"这节已经请假了"。改成同色系的状态条。
        when (lesson.status) {
            LessonStatus.PENDING -> Button(
                onClick = { onMark(LessonStatus.DONE, null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.RadioButtonUnchecked,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "打卡",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            LessonStatus.DONE -> Button(
                onClick = {},
                enabled = false,
                colors = ButtonDefaults.buttonColors(
                    disabledContainerColor = ext.successContainer,
                    disabledContentColor = ext.success,
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 44.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "已打卡",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            else -> SettledBanner(status = lesson.status, deductedHalf = lesson.deductedHalf)
        }
        Spacer(Modifier.height(8.dp))
        // 次级动作：当前状态那枚不再给（已经是它了），其余都能一键改过去。
        // 落定的课也留一个「打卡」，记错了还改得回来。
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (lesson.status != LessonStatus.PENDING && lesson.status != LessonStatus.DONE) {
                SmallAction("打卡", Modifier.weight(1f)) { onMark(LessonStatus.DONE, null) }
            }
            SmallAction("请假", Modifier.weight(1f), enabled = lesson.status != LessonStatus.LEAVE) { onMark(LessonStatus.LEAVE, 0L) }
            SmallAction("缺勤", Modifier.weight(1f), enabled = lesson.status != LessonStatus.ABSENT) { onMark(LessonStatus.ABSENT, null) }
            SmallAction("调课", Modifier.weight(1f), enabled = lesson.status != LessonStatus.RESCHEDULED) { onMark(LessonStatus.RESCHEDULED, 0L) }
        }
    }
}

/**
 * 已经落定的一节课，用一条状态带说明"这节最后是怎么算的"，占掉主按钮的位置。
 *
 * 颜色和月历格子里的状态点是同一套（请假灰 / 缺勤红 / 调课蓝），
 * 后面跟一句这一节的账怎么算——请了假到底扣不扣课时，是用户最想知道的那句。
 */
@Composable
private fun SettledBanner(status: LessonStatus, deductedHalf: Long) {
    val color = lessonStatusColor(status)
    val icon = when (status) {
        LessonStatus.LEAVE -> Icons.Rounded.EventBusy
        LessonStatus.ABSENT -> Icons.Rounded.Block
        LessonStatus.RESCHEDULED -> Icons.Rounded.SwapHoriz
        else -> Icons.Rounded.Cancel
    }
    val hint = when (status) {
        LessonStatus.LEAVE ->
            if (deductedHalf > 0) "扣 ${Lessons.format(deductedHalf)} 节" else "不扣课时"
        LessonStatus.ABSENT ->
            if (deductedHalf > 0) "扣 ${Lessons.format(deductedHalf)} 节" else "未扣课时"
        LessonStatus.RESCHEDULED -> "这节课改到别的时间上"
        else -> "这节课不用上了"
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.15f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .padding(horizontal = 14.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = status.label,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = hint,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 下面第一块卡：选中那一天的全部课次，从头到尾是完整的一张卡。
 * 卡头写清「哪一天 / 一共几节 / 还有几节没打卡」，课次一行一行往下排；
 * 当月那部分的账单独一张卡，两块内容不挤在一起。
 */
@Composable
private fun DayLessonCard(
    state: CheckInViewModel.UiState,
    modifier: Modifier = Modifier,
    onBackToToday: () -> Unit,
    onMark: (String, LessonStatus, Long?) -> Unit,
) {
    AppCard(modifier = modifier, contentPadding = PaddingValues(0.dp)) {
        DayHeaderRow(
            state = state,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            onBackToToday = onBackToToday,
        )
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        if (state.selectedLessons.isEmpty()) {
            DayEmptyState(modifier = Modifier.padding(16.dp))
        } else {
            state.selectedLessons.forEachIndexed { index, dayLesson ->
                if (index > 0) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                }
                DayLessonRow(
                    dayLesson = dayLesson,
                    onMark = { status, deducted -> onMark(dayLesson.lesson.id, status, deducted) },
                )
            }
        }
    }
}

/** 这天没有课：这块区域里的空态，不单独包卡 */
@Composable
private fun DayEmptyState(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.10f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EventAvailable,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(22.dp),
                )
            }
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "这天没有课",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "点右下角「补打卡」可以手动加一节，或在上方日历换一天看看。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun SmallAction(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = modifier.heightIn(min = 40.dp),
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}
