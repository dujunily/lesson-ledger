package cn.yiweiqiu.lessonledger.ui.courses

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.ui.courseIcon
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.AutoSizeText
import cn.yiweiqiu.lessonledger.ui.components.AvatarCircle
import cn.yiweiqiu.lessonledger.ui.components.BatchProgress
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.IconBadge
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.StatusPill
import cn.yiweiqiu.lessonledger.ui.components.bottomBarInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.forms.ChildFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.CourseFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.EnrollFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.EnrollSubmit
import cn.yiweiqiu.lessonledger.ui.theme.LocalAccentTheme
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.categoryColor
import cn.yiweiqiu.lessonledger.ui.theme.isDarkSurface

/**
 * 「课程」tab：只管课程本身。统计已拆成独立的底部 tab，
 * 这里按"还有课时没上完"来铺：一个孩子时直接把课程卡片铺满整屏，
 * 多个孩子时用一行胶囊切换。
 *
 * 右上角的 ＋ 是「记一笔缴费」而不是「添加课程」：家长真实的动作是交钱，
 * 课程是这笔钱的产物。只建课不缴费（试听、免费课）降级成列表底部的文字按钮。
 */
@Composable
fun CoursesTabScreen(
    onOpenCourse: (String) -> Unit,
    onOpenChildren: () -> Unit,
    onAddChild: () -> Unit,
    onOpenClosed: (childId: String?) -> Unit = {},
    vm: CoursesViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()

    var showEnroll by remember { mutableStateOf(false) }
    var showCourseForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Course?>(null) }
    var deleting by remember { mutableStateOf<Course?>(null) }
    var showChildForm by remember { mutableStateOf(false) }

    val statusBarInset = topBarInset()
    // 和首页/统计页同一口径：内容从玻璃标题条下沿就开始铺，不再多留一截空白
    val headerInset = statusBarInset + 2.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val wide = maxWidth >= 600.dp
        // 提到外层：顶栏玻璃要靠它判断有没有滚起来
        val gridState = rememberLazyGridState()

        if (state.children.isEmpty()) {
            // 初始状态只有一个动作，撑不起一整页列表：不进网格，直接在可见区域里居中。
            // 上一版把它排在网格第一格，按钮被顶在屏幕上半截，下面空着一大片。
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = headerInset, bottom = bottomBarInset(24.dp)),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.PersonAdd,
                    title = "先添加一个孩子",
                    description = "每个孩子可以有多个培训班，每个班单独记学费、单独统计剩余课时。",
                    actionLabel = "添加孩子",
                    onAction = onAddChild,
                )
            }
        } else {
            CourseGrid(
                state = state,
                wide = wide,
                headerInset = headerInset,
                gridState = gridState,
                onOpenCourse = onOpenCourse,
                onOpenChildren = onOpenChildren,
                onAddChild = onAddChild,
                onAddCourse = { showCourseForm = true },
                onEnroll = { showEnroll = true },
                onEdit = { editing = it },
                onDelete = { deleting = it },
                onSelectChild = vm::selectChild,
                onOpenClosed = { onOpenClosed(state.selectedChildId) },
            )
        }

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            GlassTopBar(
                title = "课程",
                // 概览卡已经把剩余课时摆在最大的位置了，顶栏就不重复堆数字
                subtitle = if (state.open.isEmpty()) "还没有在读课程" else "在读 ${state.activeCount} 门课程",
                scrollBack = { gridState.canScrollBackward },
                // 空状态时顶栏不放按钮：整页只有「添加孩子」这一个动作，
                // 再在最上面挂两个图标，等于把操作又顶回屏幕顶端，还和主按钮重复。
                actions = {
                    if (state.children.isNotEmpty()) {
                        IconButton(onClick = onOpenChildren) {
                            Icon(
                                Icons.Rounded.PersonAdd,
                                contentDescription = "孩子档案",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = { showEnroll = true }) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = "记一笔缴费",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
            Spacer(Modifier.height(6.dp))
        }
    }

    if (showEnroll) {
        EnrollFormSheet(
            children = state.children,
            initialChildId = state.selectedChildId,
            options = state.options,
            defaultThreshold = state.defaultThreshold,
            defaultRemindBefore = state.defaultRemindBefore,
            onDismiss = { showEnroll = false },
            onSubmit = { result ->
                when (result) {
                    is EnrollSubmit.NewCourse ->
                        vm.enroll(result.childId, result.course, result.pay)
                    is EnrollSubmit.Renew ->
                        vm.renew(result.target, result.startNewPeriod, result.course, result.pay)
                }
                showEnroll = false
            },
        )
    }

    if (showCourseForm) {
        val childId = state.selectedChildId ?: state.children.firstOrNull()?.id
        if (childId == null) {
            showChildForm = true
            showCourseForm = false
        } else {
            CourseFormSheet(
                initial = null,
                defaultThreshold = state.defaultThreshold,
                defaultRemindBefore = state.defaultRemindBefore,
                onDismiss = { showCourseForm = false },
                onSave = { form ->
                    vm.saveCourse(null, form, childId)
                    showCourseForm = false
                },
            )
        }
    }
    editing?.let { course ->
        CourseFormSheet(
            initial = course,
            defaultThreshold = state.defaultThreshold,
            defaultRemindBefore = state.defaultRemindBefore,
            onDismiss = { editing = null },
            onSave = { form ->
                vm.saveCourse(course, form, course.childId)
                editing = null
            },
        )
    }
    deleting?.let { course ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除「${course.courseName}」？") },
            text = { Text("该课程下的所有缴费记录和上课记录都会被删除，其他设备同步后也会删除。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteCourse(course.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
    if (showChildForm) {
        ChildFormSheet(
            initial = null,
            onDismiss = { showChildForm = false },
            onSave = { name, nickname, birthday, color, note ->
                vm.saveChild(name, nickname, birthday, color, note)
                showChildForm = false
            },
        )
    }
}

@Composable
private fun CourseGrid(
    state: CoursesViewModel.UiState,
    wide: Boolean,
    headerInset: Dp,
    onOpenCourse: (String) -> Unit,
    onOpenChildren: () -> Unit,
    onAddChild: () -> Unit,
    onAddCourse: () -> Unit,
    onEnroll: () -> Unit,
    onEdit: (Course) -> Unit,
    onDelete: (Course) -> Unit,
    onSelectChild: (String?) -> Unit,
    onOpenClosed: () -> Unit = {},
    gridState: LazyGridState = rememberLazyGridState(),
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(if (wide) 2 else 1),
        state = gridState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = headerInset,
            bottom = bottomBarInset(24.dp),
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(span = { GridItemSpan(maxLineSpan) }) { SummaryHero(state) }

        if (state.children.size > 1) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                ChildFilterRow(state, onSelectChild)
            }
        }

        if (state.open.isEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconBadge(icon = Icons.Rounded.MenuBook, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("没有在读的课程", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                            Text(
                                "课时上完了，或者课程都标成了已结束。添加一门新课继续记账。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        } else {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(
                    title = "最近未结课",
                    subtitle = "还有剩余课时，按下次上课时间排",
                )
            }
            items(state.open, key = { it.course.id }) { row ->
                CourseCard(
                    row = row,
                    period = state.periods[row.course.id],
                    showChild = state.selectedChildId == null && state.children.size > 1,
                    onClick = { onOpenCourse(row.course.id) },
                    onEdit = { onEdit(row.course) },
                    onDelete = { onDelete(row.course) },
                )
            }
        }

        if (state.closed.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                SectionHeader(title = "已结束 / 已退费", subtitle = "共 ${state.closed.size} 门")
            }
            // 已结束是低频查看的历史，不是每天要翻的清单：
            // 全铺出来会把「记一笔缴费」这个主按钮推得很远（课多的时候要滑好几屏），
            // 所以这里只放最近 5 门，剩下的进独立页面看。不做就地展开——
            // 就地展开等于把整页拉长，和跳页看到的是同一批数据，两个入口反而犹豫点哪个。
            items(state.closed.take(CLOSED_PREVIEW), key = { it.course.id }) { row ->
                CourseCard(
                    row = row,
                    period = state.periods[row.course.id],
                    showChild = state.selectedChildId == null && state.children.size > 1,
                    onClick = { onOpenCourse(row.course.id) },
                    onEdit = { onEdit(row.course) },
                    onDelete = { onDelete(row.course) },
                )
            }
            if (state.closed.size > CLOSED_PREVIEW) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    OutlinedButton(
                        onClick = onOpenClosed,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp),
                    ) {
                        Text("查看全部 ${state.closed.size} 门已结束课程", style = MaterialTheme.typography.bodyMedium)
                        Spacer(Modifier.width(4.dp))
                        Icon(
                            Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            // 主路径是缴费（课程随缴费一起建）；只建课不缴费留给试听、免费课这类场景
            Column(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                FilledTonalButton(onClick = onEnroll, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                    Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("记一笔缴费")
                }
                TextButton(onClick = onAddCourse, modifier = Modifier.height(44.dp)) {
                    Text("只建课程，暂不缴费", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

/** 顶部概览：玻璃卡 + 主色渐变。最显眼的是「还剩几节课」，学费只跟在后面当小字 */
@Composable
private fun SummaryHero(state: CoursesViewModel.UiState) {
    val accent = LocalAccentTheme.current
    val scheme = MaterialTheme.colorScheme
    val dark = isDarkSurface()
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        scheme.primary.copy(alpha = if (dark) 0.20f else 0.14f),
                        accent.auraB.copy(alpha = if (dark) 0.10f else 0.07f),
                        if (dark) Color.White.copy(alpha = 0.045f) else scheme.surface.copy(alpha = 0.85f),
                    ),
                ),
            )
            .border(1.dp, scheme.primary.copy(alpha = 0.16f), shape)
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Text(
            text = "剩余课时",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = Lessons.format(state.remainingHalf),
                fontSize = 30.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "节 · 约 ${Money.compact(state.pendingCents)} 未消耗",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 5.dp),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 三等分：金额可能是六位数，谁也别把谁挤出去
            MiniStat("在读", "${state.activeCount} 门", Modifier.weight(1f))
            MiniStat("本月支出", Money.compact(state.monthSpendCents), Modifier.weight(1f))
            MiniStat("累计缴费", Money.compact(state.totalPaidCents), Modifier.weight(1f))
        }
    }
}

@Composable
private fun MiniStat(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
        )
        AutoSizeText(
            text = value,
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.bodyMedium,
            maxFontSize = 14.sp,
            minFontSize = 9.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChildFilterRow(state: CoursesViewModel.UiState, onSelect: (String?) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FilterChip(
            selected = state.selectedChildId == null,
            onClick = { onSelect(null) },
            label = { Text("全部") },
        )
        state.children.forEach { child ->
            FilterChip(
                selected = state.selectedChildId == child.id,
                onClick = { onSelect(child.id) },
                label = { Text(child.displayName) },
                leadingIcon = {
                    AvatarCircle(initial = child.initial, colorHex = child.avatarColor, size = 20)
                },
            )
        }
    }
}

@Composable
internal fun CourseCard(
    row: CoursesViewModel.CourseRow,
    /** 同一门课分过期时才有值，第几期 */
    period: Int?,
    showChild: Boolean,
    onClick: () -> Unit,
    /** 为 null 时不显示右上角「更多」菜单（已结束课程页是只读清单，编辑走课程详情） */
    onEdit: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    val ext = LocalExtendedColors.current
    val stats = row.stats
    val low = stats.purchasedHalf > 0 && stats.remainingHalf <= Lessons.lessonsToHalf(row.course.warningThreshold)
    var menuOpen by remember { mutableStateOf(false) }
    val accent = categoryColor(row.course.category)

    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBadge(icon = courseIcon(row.course.courseName, row.course.category), color = accent, size = 40)
            Spacer(Modifier.width(11.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.course.courseName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    // 续过费的课标出期次，一眼知道这是第几次买的
                    if (period != null) {
                        Text(
                            text = "  第 $period 期",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = buildString {
                        if (showChild && row.child != null) append("${row.child.displayName} · ")
                        append(row.course.orgName.ifBlank { row.course.category.label })
                        row.course.teacher?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (row.course.status != CourseStatus.ACTIVE) {
                StatusPill(text = row.course.status.label, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (onEdit != null || onDelete != null) {
                Box {
                    IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        if (onEdit != null) {
                            DropdownMenuItem(
                                text = { Text("编辑课程") },
                                leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                                onClick = { menuOpen = false; onEdit() },
                            )
                        }
                        if (onDelete != null) {
                            DropdownMenuItem(
                                text = { Text("删除课程") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                                onClick = { menuOpen = false; onDelete() },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        BatchProgress(stats = stats, height = 9)
        Spacer(Modifier.height(10.dp))

        // 剩余课时是卡片里最大的数字；学费挤到下面那行小字里，别抢课时的位置
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = if (stats.remainingHalf < 0) "超出" else "剩",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 5.dp),
            )
            Spacer(Modifier.width(5.dp))
            Text(
                text = Lessons.format(
                    if (stats.remainingHalf < 0) -stats.remainingHalf else stats.remainingHalf
                ),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold,
                color = if (low || stats.remainingHalf < 0) ext.danger else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = " 节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 3.dp),
            )
            // 右边放下次上课，长文本兜住不把前面的大数字挤出去
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                Text(
                    text = stats.nextLessonAt?.let { "下次 ${Dates.formatMonthDay(it)} ${Dates.formatTime(it)}" }
                        ?: courseWeekday(row.course),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (stats.nextLessonAt != null) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        Text(
            text = "共 ${Lessons.format(stats.purchasedHalf)} 节 · " +
                "已用 ${Lessons.format(stats.usedHalf)} 节 · " +
                "约 ${Money.compact(stats.pendingValueCents)} 未消耗",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * 卡片右下角那行课表。一周可能上好几天、每天时间还不一样，
 * 所以按天把时间带上（「每周 一 18:00 · 三 16:30」），那天没填时间就只显示星期几。
 */
private fun courseWeekday(course: Course): String {
    val days = (0..6).filter { course.weekdayMask and (1 shl it) != 0 }
    if (days.isEmpty()) return "未排课"
    // 省掉「周」字，一行才塞得下好几天
    val names = days.map { Dates.weekdayNames[it].removePrefix("周") }
    val times = days.map { course.startTimeAt(it)?.takeIf { t -> t.isNotBlank() } }
    // 一天都没填时间 = 时间不固定（接龙 / 临时约）
    if (times.all { it == null }) return "每周${names.joinToString("、")} · 时间不固定"
    // 时长只在各天不一样时才写出来（平时 60、周末 90）：都一样的话再刷一遍数字纯属占地方，
    // 一行里还要塞好几天，加了就放不下了
    val durations = days.map { course.durationAt(it) }
    val mixedDuration = durations.distinct().size > 1
    val text = names.mapIndexed { i, name ->
        buildString {
            append(name)
            times[i]?.let { append(" $it") }
            if (mixedDuration) append(" ${durations[i]}分")
        }
    }
    return "每周 ${text.joinToString(" · ")}"
}

/** 课程页「已结束」区块最多平铺几门，多出来的进「查看全部」页 */
private const val CLOSED_PREVIEW = 5
