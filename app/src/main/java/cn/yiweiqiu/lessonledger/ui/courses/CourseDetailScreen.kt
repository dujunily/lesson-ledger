package cn.yiweiqiu.lessonledger.ui.courses

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.DoneAll
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Receipt
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.core.Receipts
import cn.yiweiqiu.lessonledger.domain.Lesson
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.domain.Payment
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.BatchProgress
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassFab
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.KeyValueRow
import cn.yiweiqiu.lessonledger.ui.components.MismatchHint
import cn.yiweiqiu.lessonledger.ui.components.isCountMismatch
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.StatusPill
import cn.yiweiqiu.lessonledger.ui.components.lessonStatusColor
import cn.yiweiqiu.lessonledger.ui.components.pageBottomInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.forms.CourseFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.LessonFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.PaymentFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.EnrollFormSheet
import cn.yiweiqiu.lessonledger.ui.forms.EnrollSubmit
import cn.yiweiqiu.lessonledger.ui.forms.RefundSheet
import cn.yiweiqiu.lessonledger.ui.forms.SettleLessonsSheet
import cn.yiweiqiu.lessonledger.ui.theme.BatchPalette
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors

@Composable
fun CourseDetailRoute(
    courseId: String,
    onBack: () -> Unit,
    onOpenCourse: (String) -> Unit = {},
    vm: CourseDetailViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(courseId) { vm.select(courseId) }
    LaunchedEffect(state.loaded, state.course?.id) {
        if (state.loaded && state.course == null) onBack()
    }
    CourseDetailContent(state = state, onBack = onBack, vm = vm, onOpenCourse = onOpenCourse)
}

@Composable
fun CourseDetailContent(
    state: CourseDetailViewModel.UiState,
    onBack: (() -> Unit)?,
    vm: CourseDetailViewModel,
    modifier: Modifier = Modifier,
    onOpenCourse: (String) -> Unit = {},
) {
    val course = state.course ?: return
    val stats = state.stats ?: return
    val ext = LocalExtendedColors.current

    var showEnroll by remember { mutableStateOf(false) }
    var editingPayment by remember { mutableStateOf<Payment?>(null) }
    var payingDeposit by remember { mutableStateOf<Payment?>(null) }
    var showDepositForm by remember { mutableStateOf(false) }
    var deletingPayment by remember { mutableStateOf<Payment?>(null) }
    var lessonActionFor by remember { mutableStateOf<Lesson?>(null) }
    // 课次删除也要走二次确认：删完只留一个「已移除的课次」恢复入口，其他设备同步后一样会删掉
    var deletingLesson by remember { mutableStateOf<Lesson?>(null) }
    var showLessonForm by remember { mutableStateOf(false) }
    // 补打卡：忘了记的课直接在这门课里补，不用回打卡页再翻一遍日历
    var showBackfill by remember { mutableStateOf(false) }
    var showCourseForm by remember { mutableStateOf(false) }
    var showRefund by remember { mutableStateOf(false) }
    var showSettle by remember { mutableStateOf(false) }
    var confirmDeleteCourse by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    // 内容和顶栏同一个 Box：列表铺满整屏、从玻璃条下面滑过去（真沉浸式），
    // 顶栏只做一层会呼吸的雾。之前顶栏和列表排在 Column 里，等于给内容截了顶，
    // 玻璃条下面永远是空的，滚多少都不会"穿"上来。
    val listState = rememberLazyListState()
    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarInset() + 4.dp,
                // 右下角有「记一笔缴费」，底部要多留一颗按钮的高度 + 小白条
                bottom = pageBottomInset(96.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
                item(key = "header") {
                    AppCard {
                        // 这张卡的主角是「还剩几节课」，金额只配在下面当一行普通信息
                        Column {
                            Text(
                                if (stats.remainingHalf < 0) "已超出课时" else "剩余课时",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = Lessons.format(
                                        if (stats.remainingHalf < 0) -stats.remainingHalf
                                        else stats.remainingHalf
                                    ),
                                    style = MaterialTheme.typography.displaySmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (stats.remainingHalf <= Lessons.lessonsToHalf(course.warningThreshold)) {
                                        ext.danger
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                )
                                Text(
                                    text = " 节",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(bottom = 6.dp),
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        BatchProgress(stats = stats, height = 12)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = "总计 ${Lessons.format(stats.purchasedHalf)} 节 · 已用 ${Lessons.format(stats.usedHalf)} 节",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 12.dp),
                            color = MaterialTheme.colorScheme.outlineVariant,
                        )
                        KeyValueRow("有效单价", "${Money.format(stats.unitPriceCents)} / 节")
                        // 累计已付写「交了多少」而不是净额：退过费的课也要能看出当初交了 2760
                        KeyValueRow("累计已付", Money.format(stats.grossPaidCents))
                        if (stats.refundedCents > 0L) {
                            KeyValueRow(
                                "已退费",
                                "-${Money.format(stats.refundedCents)}",
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        // 待消耗学费从卡片顶部挪到这里，和单价、已付平级，不再用主色大字号抢视线
                        KeyValueRow("待消耗学费", Money.format(stats.pendingValueCents))
                        // 「次」是去了几趟，「节」是扣掉的课时：英语课一次 90 分钟扣 1.5 节，
                        // 两个数字对不上时挂个问号，点开解释，不然看着像算错
                        KeyValueRow(
                            label = "已上课",
                            value = "${stats.doneCount} 次 / ${Lessons.format(stats.doneHalf)} 节",
                            trailing = if (isCountMismatch(stats.doneCount, stats.doneHalf)) {
                                {
                                    Box(modifier = Modifier.padding(start = 4.dp)) {
                                        MismatchHint(
                                            count = stats.doneCount,
                                            half = stats.doneHalf,
                                            standardMinutes = course.durationMinutes.takeIf { it > 0 },
                                        )
                                    }
                                }
                            } else {
                                null
                            },
                        )
                        // 课次是补录进来的（历史账一次结清）时单独说一句：
                        // 否则「0 次 / 36 节」看着像哪里算错了
                        if (stats.settledHalf > 0L) {
                            KeyValueRow(
                                "其中结清",
                                "${Lessons.format(stats.settledHalf)} 节（历史补录，未逐节记录）",
                                valueColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val next = stats.nextLessonAt
                        KeyValueRow(
                            "下次上课",
                            if (next != null) "${Dates.formatDate(next)} ${Dates.formatTime(next)}" else "暂无排课",
                        )
                        val expiring = stats.expiringAt
                        if (expiring != null) {
                            KeyValueRow(
                                "课时有效期",
                                Dates.formatDate(expiring),
                                valueColor = if (Dates.daysBetween(Dates.today(), expiring) <= 30) ext.warning
                                else MaterialTheme.colorScheme.onSurface,
                            )
                        }
                        // 课表：一周可能上好几天、每天时间还不一样，按天各列一行才看得出区别
                        val weekdays = (0..6).filter { course.weekdayMask and (1 shl it) != 0 }
                        Spacer(Modifier.height(10.dp))
                        if (weekdays.isEmpty()) {
                            // 没设上课日 = 时间不固定（接龙 / 临时约），不会自动排课
                            Text(
                                text = "这门课没设固定上课时间，不会自动排课。" +
                                    "想记哪一天上课，用上面的「添加课次」手动加一节。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            Text(
                                text = "课表",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(4.dp))
                            weekdays.forEach { weekday ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = Dates.weekdayNames[weekday],
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    // 时长跟着走：这天单独设过（周末 90 分钟）就显示单独的，
                                    // 没设过 durationAt 会回落到统一时长，不会显示成空
                                    Text(
                                        text = buildString {
                                            append(
                                                course.startTimeAt(weekday)
                                                    ?.takeIf { it.isNotBlank() } ?: "时间不固定"
                                            )
                                            append(" · ")
                                            append(course.durationAt(weekday))
                                            append(" 分钟")
                                        },
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            // 排课是按天取时间的：只要选中的那天填了时间（单独填的或统一回落的）就能排，
                            // 只有一天都没填的才排不出来，这时给句提示，免得用户以为 App 坏了
                            if (weekdays.none { !course.startTimeAt(it).isNullOrBlank() }) {
                                Text(
                                    text = "这几天都没填上课时间，暂时不会自动排课。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 4.dp),
                                )
                            }
                        }
                    }
                }

                if (stats.batches.isNotEmpty()) {
                    item(key = "batches") {
                        AppCard {
                            Text(
                                "批次扣减（FIFO，先扣最早的缴费）",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(8.dp))
                            stats.batches.forEachIndexed { index, batch ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 5.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(10.dp)
                                            .clip(CircleShape)
                                            .background(BatchPalette[index % BatchPalette.size]),
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = Dates.formatDate(batch.payDate),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Spacer(Modifier.weight(1f))
                                    Text(
                                        text = "${Lessons.format(batch.usedHalf)} / ${Lessons.format(batch.totalHalf)} 节已用",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        text = Money.compact(batch.amountCents),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                            Text(
                                text = "共 ${stats.batches.size} 个批次",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                }

                // 交过定金还没补齐的，摆一条醒目的待办：钱没付完这件事不该藏在缴费记录里
                if (state.pendingDeposits.isNotEmpty()) {
                    item(key = "balance-due") {
                        AppCard {
                            state.pendingDeposits.forEach { deposit ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(34.dp)
                                            .clip(RoundedCornerShape(11.dp))
                                            .background(ext.warningContainer),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            imageVector = Icons.Rounded.Savings,
                                            contentDescription = null,
                                            tint = ext.warning,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "待补尾款 ${Money.format(deposit.balanceDueCents ?: 0L)}",
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = FontWeight.Medium,
                                        )
                                        Text(
                                            text = "${Dates.formatDate(deposit.payDate)} 交了定金 " +
                                                "${Money.format(deposit.amountCents)}" +
                                                (if (deposit.creditCents > 0) "（抵 ${Money.format(deposit.creditCents)}）" else ""),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { payingDeposit = deposit },
                                        modifier = Modifier.heightIn(min = 40.dp),
                                    ) { Text("补尾款") }
                                }
                            }
                        }
                    }
                }

                item(key = "payments-header") {
                    SectionHeader(
                        title = "缴费记录",
                        subtitle = "共 ${state.payments.size} 笔 · 点按可编辑",
                    )
                }
                if (state.payments.isEmpty()) {
                    item(key = "payments-empty") {
                        Text(
                            text = "还没有缴费记录，点右下角「记一笔缴费」",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                } else {
                    items(state.payments, key = { it.id }) { payment ->
                        PaymentRow(
                            payment = payment,
                            onEdit = { editingPayment = payment },
                            onDelete = { deletingPayment = payment },
                        )
                    }
                }

                item(key = "lessons-header") {
                    SectionHeader(
                        title = "上课记录",
                        subtitle = "已上 ${stats.doneCount} 次 / ${Lessons.format(stats.doneHalf)} 节 · 点按可改状态",
                        action = {
                            // 补打卡放前面：它是这里最高频的动作（漏记的课回来补）
                            Row {
                                TextButton(onClick = { showBackfill = true }) { Text("补打卡") }
                                TextButton(onClick = { showLessonForm = true }) { Text("添加课次") }
                            }
                        },
                    )
                }
                // 被手动删掉的课次：留个恢复入口，误删了还能找回来
                if (state.removedSlots.isNotEmpty()) {
                    item(key = "removed-slots") {
                        AppCard {
                            Text(
                                "已移除的课次",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "这几天的课被删掉了，不会再自动排回来。点一下可以恢复。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(6.dp))
                            state.removedSlots.forEach { slot ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable { vm.restoreLesson(slot.id) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(
                                        text = "${Dates.formatDate(slot.scheduledAt)} ${Dates.formatTime(slot.scheduledAt)}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        modifier = Modifier.weight(1f),
                                    )
                                    Text(
                                        text = "恢复",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            }
                        }
                    }
                }
                if (state.lessons.isEmpty()) {
                    item(key = "lessons-empty") {
                        EmptyState(
                            icon = Icons.Rounded.Receipt,
                            title = "还没有上课记录",
                            description = "点右上角「添加课次」记一次上课；固定课表的课会自动出现在这里。",
                        )
                    }
                } else {
                    // 分两个模块：待上的按时间正序（下一节在最上面），
                    // 上过的按时间倒序（最近一次在最上面）
                    if (state.pendingLessons.isNotEmpty()) {
                        item(key = "lessons-pending-header") {
                            Text(
                                text = "待上 · ${state.pendingLessons.size} 节（最近的在最上面）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, top = 2.dp, bottom = 4.dp),
                            )
                        }
                        items(state.pendingLessons, key = { it.id }) { lesson ->
                            LessonRow(
                                lesson = lesson,
                                batchLabel = stats.batches.indexOfFirst { it.paymentId == lesson.paymentId }
                                    .takeIf { it >= 0 }?.let { "第 ${it + 1} 批" },
                                overdrawn = lesson.deductedHalf > 0 && lesson.paymentId == null,
                                onClick = { lessonActionFor = lesson },
                            )
                        }
                    }
                    if (state.pastLessons.isNotEmpty()) {
                        item(key = "lessons-past-header") {
                            Text(
                                text = "已上记录 · ${state.pastLessons.size} 次 / " +
                                    "${Lessons.format(state.pastLessons.sumOf { it.deductedHalf })} 节（最近的在最上面）",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(start = 6.dp, top = 6.dp, bottom = 4.dp),
                            )
                        }
                        items(state.pastLessons, key = { it.id }) { lesson ->
                            LessonRow(
                                lesson = lesson,
                                batchLabel = stats.batches.indexOfFirst { it.paymentId == lesson.paymentId }
                                    .takeIf { it >= 0 }?.let { "第 ${it + 1} 批" },
                                overdrawn = lesson.deductedHalf > 0 && lesson.paymentId == null,
                                onClick = { lessonActionFor = lesson },
                            )
                        }
                    }
                }
            }

        GlassTopBar(
            title = course.courseName,
            subtitle = "${state.child?.displayName ?: ""} · ${course.orgName.ifBlank { course.category.label }}",
            modifier = Modifier.align(Alignment.TopCenter),
            leading = if (onBack != null) ({ GlassBackButton(onBack) }) else null,
            scrollBack = { listState.canScrollBackward },
            actions = {
                Box {
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("编辑课程") },
                            leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                            onClick = { menuOpen = false; showCourseForm = true },
                        )
                        DropdownMenuItem(
                            text = { Text("办理退费") },
                            leadingIcon = { Icon(Icons.AutoMirrored.Rounded.Undo, contentDescription = null) },
                            onClick = { menuOpen = false; showRefund = true },
                        )
                        // 换手机 / 补录老账时用：课上完了但查不到每次的日期，
                        // 一句话把剩下的课时结清，日历里不会多出一堆编造的课次
                        if (stats.remainingHalf > 0L) {
                            DropdownMenuItem(
                                text = { Text("结清剩余课时") },
                                leadingIcon = { Icon(Icons.Rounded.DoneAll, contentDescription = null) },
                                onClick = { menuOpen = false; showSettle = true },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("交定金（尾款后付）") },
                            leadingIcon = { Icon(Icons.Rounded.Savings, contentDescription = null) },
                            onClick = { menuOpen = false; showDepositForm = true },
                        )
                        DropdownMenuItem(
                            text = { Text("删除课程") },
                            leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                            onClick = { menuOpen = false; confirmDeleteCourse = true },
                        )
                    }
                }
            },
        )

        GlassFab(
            text = "记一笔缴费",
            icon = Icons.Rounded.Add,
            onClick = { showEnroll = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                // 小白条（手势条）下面不能压东西：这里按导航栏内边距让开
                .padding(end = 20.dp, bottom = pageBottomInset(14.dp)),
        )
    }

    if (showEnroll) {
        EnrollFormSheet(
            children = listOfNotNull(state.child),
            initialChildId = course.childId,
            options = listOfNotNull(state.option),
            renewTarget = state.option,
            defaultThreshold = state.fallbackThreshold,
            defaultRemindBefore = course.remindBeforeMinutes,
            onDismiss = { showEnroll = false },
            onSubmit = { result ->
                showEnroll = false
                when (result) {
                    is EnrollSubmit.NewCourse ->
                        vm.enroll(result.course, result.pay) { id -> onOpenCourse(id) }
                    is EnrollSubmit.Renew ->
                        vm.renew(result.startNewPeriod, result.course, result.pay) { id ->
                            onOpenCourse(id)
                        }
                }
            },
        )
    }
    editingPayment?.let { payment ->
        PaymentFormSheet(
            initial = payment,
            onDismiss = { editingPayment = null },
            onSave = { form ->
                vm.savePayment(payment, form)
                editingPayment = null
            },
        )
    }
    if (showDepositForm) {
        PaymentFormSheet(
            initial = null,
            startInDepositMode = true,
            // 给草稿分个组，A 课填一半不会被 B 课带出来
            courseId = course.id,
            onDismiss = { showDepositForm = false },
            onSave = { form ->
                vm.savePayment(null, form)
                showDepositForm = false
            },
        )
    }
    payingDeposit?.let { deposit ->
        PaymentFormSheet(
            initial = null,
            deposit = deposit,
            onDismiss = { payingDeposit = null },
            onSave = { form ->
                vm.payBalance(deposit, form)
                payingDeposit = null
            },
        )
    }
    deletingPayment?.let { payment ->
        val used = stats.allocation.usedByPayment[payment.id] ?: 0L
        AlertDialog(
            onDismissRequest = { deletingPayment = null },
            title = { Text("删除这笔缴费？") },
            text = {
                Text(
                    if (used > 0) {
                        "该批次已被扣减 ${Lessons.format(used)} 节，删除后剩余课时会减少，可能出现负数（标红提示）。确定删除吗？"
                    } else {
                        "删除后不影响已上课次。确定删除吗？"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deletePayment(payment.id)
                    deletingPayment = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingPayment = null }) { Text("取消") } },
        )
    }

    if (showBackfill) {
        LessonFormSheet(
            // 带上同孩子的其他在读课程，补打卡时可以直接换一门课记
            courses = state.siblingCourses.ifEmpty { listOf(course) },
            initialCourseId = course.id,
            initial = null,
            // 补打卡记的是「已经上完了的课」，所以默认已上 + 扣 1 节；
            // 「添加课次」排的是还没上的课，默认待上，两者别混。
            defaultStatus = LessonStatus.DONE,
            onDismiss = { showBackfill = false },
            onSave = { form ->
                vm.backfillLesson(form)
                showBackfill = false
            },
        )
    }

    if (showLessonForm) {
        LessonFormSheet(
            courses = listOf(course),
            initialCourseId = course.id,
            initial = null,
            defaultStatus = LessonStatus.PENDING,
            onDismiss = { showLessonForm = false },
            onSave = { form ->
                vm.addLesson(form)
                showLessonForm = false
            },
        )
    }

    lessonActionFor?.let { lesson ->
        LessonActionDialog(
            lesson = lesson,
            // 扣多少节以这门课自己定义的单节时长为基准折算（45 / 60 / 90 分钟各不相同）
            standardMinutes = state.course?.durationMinutes ?: 0,
            onDismiss = { lessonActionFor = null },
            onAction = { status, deducted ->
                vm.setLessonStatus(lesson.id, status, deducted)
                lessonActionFor = null
            },
            onDelete = {
                // 先收起状态弹窗，再弹确认框，两个对话框别叠在一起
                deletingLesson = lesson
                lessonActionFor = null
            },
        )
    }

    deletingLesson?.let { lesson ->
        // 自动课次删掉之后不会自动排回来（列表顶部有恢复入口），这点必须提前说清
        val noLongerAuto = lesson.autoGenerated && Dates.toDate(lesson.scheduledAt) >= Dates.today()
        AlertDialog(
            onDismissRequest = { deletingLesson = null },
            title = { Text("删除「${Dates.formatDate(lesson.scheduledAt)}」这节课？") },
            text = {
                Text(
                    if (noLongerAuto) {
                        "这是课表自动排的课次，删除后这一天不会再自动排回来；" +
                            "其他设备同步后也会一起删掉。"
                    } else {
                        "这条上课记录会被删除，其他设备同步后也会一起删掉。" +
                            "如果只是记错了状态，改成「请假」或「调课」更划算。"
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteLesson(lesson.id)
                    deletingLesson = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deletingLesson = null }) { Text("取消") } },
        )
    }

    if (showCourseForm) {
        CourseFormSheet(
            initial = course,
            defaultThreshold = state.fallbackThreshold,
            onDismiss = { showCourseForm = false },
            onSave = { form ->
                vm.saveCourse(form)
                showCourseForm = false
            },
        )
    }

    if (showRefund) {
        RefundSheet(
            unitPriceCents = stats.unitPriceCents,
            maxLessonsHalf = stats.remainingHalf.coerceAtLeast(0L),
            valueNumerCents = stats.valueNumerCents,
            valueDenomHalf = stats.valueDenomHalf,
            onDismiss = { showRefund = false },
            onSave = { lessonsHalf, fee, note ->
                vm.refund(lessonsHalf, fee, note)
                showRefund = false
            },
        )
    }

    if (showSettle) {
        SettleLessonsSheet(
            remainingHalf = stats.remainingHalf,
            onDismiss = { showSettle = false },
            onSave = { half, note ->
                vm.settleLessons(half, note)
                showSettle = false
            },
        )
    }

    if (confirmDeleteCourse) {
        AlertDialog(
            onDismissRequest = { confirmDeleteCourse = false },
            title = { Text("删除「${course.courseName}」？") },
            text = { Text("该课程下的 ${state.payments.size} 笔缴费和 ${state.lessons.size} 条上课记录都会被删除。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDeleteCourse = false
                    vm.deleteCourse()
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDeleteCourse = false }) { Text("取消") } },
        )
    }
}

@Composable
private fun PaymentRow(
    payment: Payment,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val ext = LocalExtendedColors.current
    var menuOpen by remember { mutableStateOf(false) }
    val context = LocalContext.current

    AppCard(onClick = onEdit, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = Dates.formatDate(payment.payDate),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = Money.format(payment.amountCents),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (payment.isRefund) ext.income else ext.expense,
                    )
                    if (payment.kind != cn.yiweiqiu.lessonledger.domain.PaymentKind.FULL) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(
                            text = if (payment.isDeposit && payment.creditCents > 0) {
                                "定金 · 抵 ${Money.compact(payment.creditCents)}"
                            } else {
                                payment.kind.label
                            },
                            color = if (payment.isDeposit) ext.warning else MaterialTheme.colorScheme.primary,
                        )
                    }
                    payment.originalAmountCents?.let {
                        Text(
                            text = " 原价 ${Money.format(it)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = buildString {
                        if (payment.isBalance) {
                            // 尾款只有钱没有课时，再说「买 0 节」就成废话了
                            append("补足整期费用")
                        } else {
                            append("买 ${Lessons.format(kotlin.math.abs(payment.lessonsBoughtHalf))} 节")
                            if (payment.lessonsBonusHalf > 0) append(" 送 ${Lessons.format(payment.lessonsBonusHalf)} 节")
                        }
                        append(" · ${payment.payMethod.label}")
                        payment.validUntil?.let { append(" · 有效至 ${Dates.formatDate(it)}") }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (payment.note.isNotBlank()) {
                    Text(
                        text = payment.note,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(32.dp)) {
                    Icon(
                        Icons.Rounded.MoreVert,
                        contentDescription = "更多",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp),
                    )
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("编辑") },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
        if (payment.receipts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                payment.receipts.take(4).forEach { name ->
                    val bitmap = remember(name) {
                        runCatching {
                            android.graphics.BitmapFactory.decodeFile(
                                Receipts.file(context, name).absolutePath
                            )
                        }.getOrNull()
                    }
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp)),
                    ) {
                        if (bitmap != null) {
                            androidx.compose.foundation.Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize(),
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonRow(
    lesson: Lesson,
    batchLabel: String?,
    overdrawn: Boolean,
    onClick: () -> Unit,
) {
    val ext = LocalExtendedColors.current
    AppCard(onClick = onClick, contentPadding = PaddingValues(14.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "${Dates.formatDate(lesson.scheduledAt)} ${Dates.formatTime(lesson.scheduledAt)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(Modifier.width(8.dp))
                    StatusPill(
                        text = lesson.status.label,
                        color = lessonStatusColor(lesson.status),
                    )
                    if (overdrawn) {
                        Spacer(Modifier.width(6.dp))
                        StatusPill(text = "超扣", color = ext.danger)
                    }
                }
                Row(modifier = Modifier.padding(top = 2.dp)) {
                    Text(
                        text = if (lesson.deductedHalf > 0) {
                            "扣 ${Lessons.format(lesson.deductedHalf)} 节"
                        } else {
                            "不扣课时"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    batchLabel?.let {
                        Text(
                            text = " · $it",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (lesson.note.isNotBlank()) {
                        Text(
                            text = " · ${lesson.note}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LessonActionDialog(
    lesson: Lesson,
    /** 这门课定义的单节时长（分钟），0 表示未知（回落按 60 分钟算） */
    standardMinutes: Int,
    onDismiss: () -> Unit,
    onAction: (LessonStatus, Long?) -> Unit,
    onDelete: () -> Unit,
) {
    // 按这节课的时长折算出建议扣数：45 分钟课扣 45 分钟的量，
    // 90 分钟扣 1.5 节——基准是课程自己定义的，不是写死的 60 分钟
    val suggestedHalf = cn.yiweiqiu.lessonledger.domain.LedgerCalc
        .deductedFromDuration(lesson.durationMinutes, standardMinutes)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${Dates.formatDate(lesson.scheduledAt)} 这节课") },
        text = {
            Column(modifier = Modifier.heightIn(min = 120.dp)) {
                Text(
                    text = "当前状态：${lesson.status.label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
                ActionItem("已上（按时长扣 ${Lessons.format(suggestedHalf)} 节）") {
                    onAction(LessonStatus.DONE, suggestedHalf)
                }
                ActionItem("已上（手动扣 1 节）") { onAction(LessonStatus.DONE, 2L) }
                ActionItem("已上（手动扣 0.5 节）") { onAction(LessonStatus.DONE, 1L) }
                ActionItem("请假（退回课时）") { onAction(LessonStatus.LEAVE, 0L) }
                ActionItem("缺勤") { onAction(LessonStatus.ABSENT, null) }
                ActionItem("调课") { onAction(LessonStatus.RESCHEDULED, 0L) }
                if (lesson.autoGenerated && Dates.toDate(lesson.scheduledAt) >= Dates.today()) {
                    Text(
                        text = "删掉自动课次后，这一天不会再自动排回来。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
                ActionItem("删除这条记录", danger = true) { onDelete() }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun ActionItem(
    label: String,
    danger: Boolean = false,
    onClick: () -> Unit,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.bodyLarge,
        color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 11.dp, horizontal = 8.dp),
    )
}
