package cn.yiweiqiu.lessonledger.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.SyncProblem
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.hilt.navigation.compose.hiltViewModel
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.AvatarCircle
import cn.yiweiqiu.lessonledger.ui.components.BatchProgress
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.bottomBarInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.components.IconBadge
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.StatTile
import cn.yiweiqiu.lessonledger.ui.components.StatusPill
import cn.yiweiqiu.lessonledger.ui.components.lessonStatusColor
import cn.yiweiqiu.lessonledger.ui.forms.LessonFormSheet
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.categoryColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    onOpenCourse: (String) -> Unit,
    onOpenSync: () -> Unit,
    onGoCourses: () -> Unit,
    onOpenCheckIn: () -> Unit,
    onAddChild: () -> Unit,
    onOpenMonthPayments: () -> Unit = {},
    vm: HomeViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ext = LocalExtendedColors.current
    // 补打卡：今天页里直接加一节以前的课，不必先跳到打卡日历
    var showBackfill by remember { mutableStateOf(false) }
    var prefill by remember { mutableStateOf<HomeViewModel.AgendaRow?>(null) }
    // 顶栏玻璃跟着滚动状态加厚/变薄：不滚动时透（沉浸），滚动时稍微实一点（不撞字）。
    val listState = rememberLazyListState()

    // 内容铺满整屏，玻璃标题条浮在上面，列表从它下面滑过
    Box(modifier = Modifier.fillMaxSize()) {
        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = vm::refresh,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (!state.ready) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(modifier = Modifier.size(28.dp), strokeWidth = 2.dp)
                }
            } else if (state.data.children.isEmpty()) {
                // 初始状态只有一件事可做，所以它该待在拇指够得到的地方。
                // 上一版把空状态直接排在内容流顶端：图标和按钮吊在玻璃顶栏下面，
                // 剩下大半屏空白，看着像「还没加载完」，点起来也要往上够。
                // 现在整块在可见区域里居中，按钮落在屏幕中下部。
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topBarInset() + 2.dp,
                        bottom = bottomBarInset(24.dp),
                    ),
                ) {
                    item(key = "empty") {
                        Box(
                            modifier = Modifier.fillParentMaxSize(),
                            contentAlignment = Alignment.Center,
                        ) {
                            EmptyState(
                                icon = Icons.Rounded.Add,
                                title = "还没有记录",
                                // 演示数据挪去「设置 → 数据源」了：这里是一页空白，不是试用入口
                                description = "先添加孩子，再记第一笔学费——机构、课程、课时跟着这笔钱一起录。",
                                actionLabel = "添加孩子",
                                onAction = onAddChild,
                            )
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(
                        start = 16.dp,
                        end = 16.dp,
                        top = topBarInset() + 2.dp,
                        bottom = bottomBarInset(24.dp),
                    ),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (state.status.conflictCount > 0) {
                        item(key = "conflict") {
                            ConflictBanner(state.status.conflictCount, onOpenSync)
                        }
                    }

                    // 「今天」只做一件事：把今天的课打完卡。
                    // 课程列表交给「课程」tab，这里不再重复，页面就不会越堆越长。
                    item(key = "today") {
                        TodayLessonsCard(
                            rows = state.todayRows,
                            weekPending = state.weekPending,
                            onMarkDone = vm::markDone,
                            onBackfill = {
                                prefill = null
                                showBackfill = true
                            },
                            onOpenCalendar = onOpenCheckIn,
                        )
                    }

                    item(key = "week") {
                        WeekProgressCard(
                            count = state.weekCount,
                            done = state.weekDone,
                            pending = state.weekPending,
                            leave = state.weekLeave,
                            rescheduled = state.weekRescheduled,
                            absent = state.weekAbsent,
                            onClick = onOpenCheckIn,
                        )
                    }

                    if (state.upcomingRows.isNotEmpty()) {
                        item(key = "upcoming-header") {
                            SectionHeader(
                                title = "接下来",
                                subtitle = "未来 7 天 · 提前安排接送",
                            )
                        }
                        item(key = "upcoming") {
                            AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                                state.upcomingRows.forEachIndexed { index, row ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                    AgendaRowItem(
                                        row = row,
                                        onClick = { onOpenCourse(row.courseId) },
                                    )
                                }
                            }
                        }
                    }

                    if (state.recentRows.isNotEmpty()) {
                        item(key = "recent-header") {
                            SectionHeader(
                                title = "最近记录",
                                subtitle = "今天之前 14 天 · 漏掉的点「补」就能补上",
                                // 补卡的入口放在这里：要补的都是这几天漏掉的课，
                                // 就近在记录列表上给一颗按钮，比在顶部猜"补哪天"更直接
                                action = {
                                    TextButton(
                                        onClick = {
                                            prefill = null
                                            showBackfill = true
                                        },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                    ) {
                                        Text(
                                            text = "＋ 补卡",
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                },
                            )
                        }
                        item(key = "recent") {
                            AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 2.dp)) {
                                state.recentRows.forEachIndexed { index, row ->
                                    if (index > 0) {
                                        HorizontalDivider(
                                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                                        )
                                    }
                                    AgendaRowItem(
                                        row = row,
                                        onClick = { onOpenCourse(row.courseId) },
                                        onBackfill = {
                                            prefill = row
                                            showBackfill = true
                                        },
                                    )
                                }
                            }
                        }
                    }

                    if (state.reminders.isNotEmpty()) {
                        item(key = "reminder-header") {
                            SectionHeader(
                                title = "该处理了",
                                subtitle = "按优先级排序，最多 5 条",
                            )
                        }
                        items(state.reminders, key = { it.key }) { reminder ->
                            ReminderRow(reminder) {
                                reminder.courseId?.let(onOpenCourse)
                            }
                        }
                    }

                    item(key = "summary") {
                        Column {
                            Spacer(Modifier.height(4.dp))
                            // 以前用 height(IntrinsicSize.Min) 让三张卡等高，但卡里的金额是
                            // AutoSizeText（内部 BoxWithConstraints = SubcomposeLayout），
                            // 列表预取测量时会向它求 intrinsic 高度，直接抛异常闪退。
                            // 三张卡结构相同（标题 / 数字 / 提示各一行），本来自然等高，
                            // 直接不请求 intrinsic 即可。
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                StatTile(
                                    label = "本月支出",
                                    value = Money.compact(state.summary.monthSpendCents),
                                    accent = ext.expense,
                                    modifier = Modifier.weight(1f),
                                    hint = "看明细 ›",
                                    onClick = onOpenMonthPayments,
                                )
                                StatTile(
                                    label = "在读课程",
                                    value = "${state.summary.activeCourseCount} 门",
                                    modifier = Modifier.weight(1f),
                                    hint = "看课程 ›",
                                    onClick = onGoCourses,
                                )
                                StatTile(
                                    label = "待消耗余额",
                                    value = Money.compact(state.summary.pendingValueCents),
                                    modifier = Modifier.weight(1f),
                                    hint = "看课程 ›",
                                    onClick = onGoCourses,
                                )
                            }
                        }
                    }

                    state.data.children.forEach { child ->
                        val hasCourse = state.data.coursesOf(child.id)
                            .any { it.status == CourseStatus.ACTIVE || it.status == CourseStatus.PAUSED }
                        if (!hasCourse) {
                            item(key = "child-empty-${child.id}") {
                                Text(
                                    text = "${child.displayName} 还没有课程，去「课程」里添加一门吧",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                                )
                            }
                        }
                    }
                }
            }
        }
        GlassTopBar(
            title = "今天",
            subtitle = "%d月%d日 %s".format(
                Dates.today().monthValue,
                Dates.today().dayOfMonth,
                Dates.weekdayNames[Dates.weekdayOf(Dates.today())],
            ),
            modifier = Modifier.align(Alignment.TopCenter),
            // 跟随滚动动态变化，而不是写死 elevated = true —— 写死会把玻璃拉成实心白条
            scrollBack = { listState.canScrollBackward },
            // 同步状态不再挂在首页顶栏：冲突时用下方横幅提示，日常配置都在设置里。
            // 右上角这枚日历是「打卡日历」的常驻入口：月视图、换日期、看整月上了多少节，
            // 它是个页面，不该和「打卡 / 补卡」这两个动作挤在同一颗按钮里。
            actions = {
                IconButton(onClick = onOpenCheckIn) {
                    Icon(
                        Icons.Rounded.CalendarMonth,
                        contentDescription = "打卡日历",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }

    if (showBackfill) {
        val pre = prefill
        LessonFormSheet(
            courses = state.activeCourses,
            initialCourseId = pre?.courseId ?: state.activeCourses.firstOrNull()?.id,
            initial = null,
            initialDate = pre?.let { Dates.toDate(it.scheduledAt) },
            initialTime = pre?.time,
            onDismiss = {
                showBackfill = false
                prefill = null
            },
            onSave = { form ->
                vm.addLesson(form)
                showBackfill = false
                prefill = null
            },
        )
    }
}

@Composable
private fun TodayLessonsCard(
    rows: List<HomeViewModel.TodayRow>,
    weekPending: Int,
    onMarkDone: (String) -> Unit,
    onBackfill: () -> Unit,
    onOpenCalendar: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(16.dp)
    val pending = rows.count { it.status == LessonStatus.PENDING }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.14f),
                        scheme.primary.copy(alpha = 0.03f),
                    ),
                ),
            )
            .border(1.dp, scheme.primary.copy(alpha = 0.16f), shape)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Rounded.CalendarMonth,
                contentDescription = null,
                tint = scheme.primary,
                modifier = Modifier.size(19.dp),
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (rows.isEmpty()) "今天没排课" else "今天 ${rows.size} 节",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = when {
                        pending > 0 -> "还有 $pending 节待打卡"
                        // 今天有课且都了结了：全是上完的才叫「都打完了」，
                        // 里面夹着请假/调课的，说「结束了」才不漏信息
                        rows.isNotEmpty() && rows.all { it.status == LessonStatus.DONE } -> "都打完了"
                        rows.isNotEmpty() -> "今天的都结束了"
                        weekPending > 0 -> "本周还有 $weekPending 节没打卡"
                        else -> "歇一天"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (pending > 0) scheme.primary else scheme.onSurfaceVariant,
                )
            }

            // 这里只留一颗箭头：顶栏右上角已经挂着「打卡日历」的常驻入口，
            // 卡片里再写一遍"打卡日历"四个字纯属重复，还会把标题挤窄。
            // 卡片左侧已有日历图标做身份，所以右侧用箭头表示"进入页面"，
            // 避免同一行出现两枚一模一样的日历。
            IconButton(
                onClick = onOpenCalendar,
                modifier = Modifier.size(32.dp),
            ) {
                Icon(
                    imageVector = Icons.Rounded.ChevronRight,
                    contentDescription = "打卡日历",
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        rows.forEachIndexed { index, row ->
            if (index == 0) {
                Spacer(Modifier.height(8.dp))
            } else {
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 7.dp),
                    color = scheme.outlineVariant.copy(alpha = 0.5f),
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = row.time,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(50.dp),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = row.courseName,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.childName.isNotBlank()) {
                        Text(
                            text = row.childName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                // 打卡按钮落在每一节自己那一行：一天两节就并排两个，各打各的，
                // 不会再出现"点上面的打卡但不知道扣了哪一节"这种情况
                if (row.status == LessonStatus.PENDING) {
                    OutlinedButton(
                        onClick = { onMarkDone(row.lessonId) },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        modifier = Modifier.heightIn(min = 32.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("打卡", style = MaterialTheme.typography.labelMedium, maxLines = 1)
                    }
                } else {
                    StatusPill(
                        text = row.status.label,
                        color = lessonStatusColor(row.status),
                    )
                }
            }
        }
    }
}

/**
 * 本周进度：四种状态各用各的颜色，条铺满就代表这周全了结了。
 *
 * 请假 / 调课 / 缺勤都算这节课了结——这周这几节不会再有动作了，
 * 所以进度条把它们一起填进去，只把真正待上的留空。
 * 颜色直接复用列表里状态胶囊那一套（已上绿 / 请假灰 / 调课蓝 / 缺勤红），
 * 看条就知道哪几节是请假、哪几节是调课，不用回头再读一遍文字。
 */
@Composable
private fun WeekProgressCard(
    count: Int,
    done: Int,
    pending: Int,
    leave: Int,
    rescheduled: Int,
    absent: Int,
    onClick: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val ext = LocalExtendedColors.current
    val settled = done + leave + rescheduled + absent
    val settledFraction = if (count > 0) (settled.toFloat() / count).coerceIn(0f, 1f) else 0f

    // 和 lessonStatusColor 同一套色：条上是这个颜色，列表里的胶囊也是这个颜色
    val doneColor = lessonStatusColor(LessonStatus.DONE)
    val leaveColor = lessonStatusColor(LessonStatus.LEAVE)
    val rescheduledColor = lessonStatusColor(LessonStatus.RESCHEDULED)
    val absentColor = lessonStatusColor(LessonStatus.ABSENT)
    val segments = listOf(
        done to doneColor,
        leave to leaveColor,
        rescheduled to rescheduledColor,
        absent to absentColor,
    ).filter { it.first > 0 }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHigh.copy(alpha = 0.5f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "本周进度",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = when {
                    pending > 0 -> "还有 $pending 节待打卡"
                    count == 0 -> "本周没有排课"
                    // 请假/调课也算完事，所以只要没有待上的，这周就算结束了
                    else -> "本周都结束了"
                },
                style = MaterialTheme.typography.labelMedium,
                color = if (pending > 0) ext.warning else scheme.onSurfaceVariant,
            )
        }
        Spacer(Modifier.height(9.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(ext.chartGrid),
        ) {
            // 按节数分配宽度：3 节已上 + 1 节请假 → 条上前 3/4 是绿的、后 1/4 是灰的，
            // 铺满即「这周处理完了」，剩下的空心部分就是真正还没到的课。
            if (settledFraction > 0f) {
                Row(modifier = Modifier.fillMaxWidth(settledFraction).fillMaxHeight()) {
                    segments.forEachIndexed { index, (n, color) ->
                        if (index > 0) {
                            // 段与段之间切一道卡底色的缝：调课蓝和已上绿挨在一起时不会糊成一片
                            Spacer(
                                Modifier
                                    .width(2.dp)
                                    .fillMaxHeight()
                                    .background(scheme.surfaceContainerHigh),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(n.toFloat())
                                .fillMaxHeight()
                                .background(color),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(7.dp))
        Text(
            text = if (count > 0) "已上 $done 节 / 共 $count 节" else "这周还没有排课",
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurfaceVariant,
        )
        // 条上的颜色得有名字，否则四种色的条只是花，读不出含义。
        // 待上在条上本来就空着，所以给它一个空心点，跟实心点区分开。
        if (leave > 0 || rescheduled > 0 || absent > 0 || pending > 0) {
            Spacer(Modifier.height(5.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (leave > 0) WeekLegendDot(leaveColor, "请假 $leave")
                if (rescheduled > 0) WeekLegendDot(rescheduledColor, "调课 $rescheduled")
                if (absent > 0) WeekLegendDot(absentColor, "缺勤 $absent")
                if (pending > 0) WeekLegendDot(ext.warning, "待上 $pending", hollow = true)
            }
        }
    }
}

/** 本周进度条下面的小图例：一个色点 + 一句「请假 1」 */
@Composable
private fun WeekLegendDot(color: Color, text: String, hollow: Boolean = false) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .clip(RoundedCornerShape(4.dp))
                .then(
                    if (hollow) Modifier.border(1.dp, color, RoundedCornerShape(4.dp))
                    else Modifier.background(color),
                ),
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 「最近记录 / 接下来」里的一行；漏打卡的行右侧直接给一个「补」按钮 */
@Composable
private fun AgendaRowItem(
    row: HomeViewModel.AgendaRow,
    onClick: () -> Unit,
    onBackfill: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 日期后面还跟着星期几（如「10-31 周六」），58dp 装不下会折行，放宽到 76dp 并锁单行
        Column(modifier = Modifier.width(76.dp)) {
            Text(
                text = row.dayLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.time,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
            )
        }
        Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
            Text(
                text = row.courseName,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (row.childName.isNotBlank()) {
                Text(
                    text = row.childName,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        if (row.status == LessonStatus.PENDING && onBackfill != null) {
            OutlinedButton(
                onClick = { onBackfill?.invoke() },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.heightIn(min = 32.dp),
            ) {
                Text("补", style = MaterialTheme.typography.labelMedium)
            }
        } else {
            StatusPill(
                text = row.status.label,
                color = lessonStatusColor(row.status),
            )
        }
    }
}

@Composable
private fun ConflictBanner(count: Int, onClick: () -> Unit) {
    val ext = LocalExtendedColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(ext.warningContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.SyncProblem, contentDescription = null, tint = ext.warning, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "发现 $count 条同步冲突，点击查看",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = "两份数据都已保留，冲突副本在备注中标注了「[冲突副本]」",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = ext.warning, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun ReminderRow(reminder: LedgerCalc.Reminder, onClick: () -> Unit) {
    val ext = LocalExtendedColors.current
    val color = when (reminder.kind) {
        LedgerCalc.ReminderKind.OVERDRAWN, LedgerCalc.ReminderKind.LOW_LESSONS -> ext.danger
        LedgerCalc.ReminderKind.EXPIRING -> ext.warning
        LedgerCalc.ReminderKind.TODAY_LESSON -> ext.info
        LedgerCalc.ReminderKind.WEEK_UNCLOCKED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.07f))
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(30.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(color),
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = reminder.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = color,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = reminder.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(Icons.Rounded.ChevronRight, contentDescription = null, tint = color.copy(alpha = 0.7f), modifier = Modifier.size(18.dp))
    }
}
