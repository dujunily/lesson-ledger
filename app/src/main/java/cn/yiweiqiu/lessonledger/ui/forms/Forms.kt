package cn.yiweiqiu.lessonledger.ui.forms

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PhotoCamera
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.core.Receipts
import cn.yiweiqiu.lessonledger.data.repo.LedgerRepository
import cn.yiweiqiu.lessonledger.domain.Category
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.Lesson
import cn.yiweiqiu.lessonledger.domain.LessonStatus
import cn.yiweiqiu.lessonledger.domain.PayMethod
import cn.yiweiqiu.lessonledger.domain.Payment
import cn.yiweiqiu.lessonledger.domain.encodeDurations
import cn.yiweiqiu.lessonledger.domain.encodeStartTimes
import cn.yiweiqiu.lessonledger.ui.components.AppTextField
import cn.yiweiqiu.lessonledger.ui.components.ChipSelector
import cn.yiweiqiu.lessonledger.ui.components.DateField
import cn.yiweiqiu.lessonledger.ui.components.FieldLabel
import cn.yiweiqiu.lessonledger.ui.components.TimeField
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlin.math.ceil
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

val avatarPalette = listOf(
    "#FF8A4C", "#E0729B", "#7C6BD6", "#3E7BD6",
    "#2FA6B8", "#4C9A6A", "#D9A020", "#8A8078",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormSheet(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmEnabled: Boolean = true,
    confirmLabel: String = "保存",
    content: @Composable ColumnScope.() -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // 键盘弹起来时，imePadding 和 navigationBarsPadding 会一起吃掉高度，
    // 原来硬性 fillMaxHeight(0.9f) 会把中间的内容区挤成一条缝，看着就像「表单没了」。
    // 改成「按内容自适应，最多占屏幕 92%」，键盘再高内容区也还有地方待着。
    val maxSheetHeight = LocalConfiguration.current.screenHeightDp.dp * 0.92f
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = maxSheetHeight)
                .padding(horizontal = 20.dp)
                .imePadding()
                .navigationBarsPadding(),
        ) {
            // 标题和表单内容放进同一个滚动容器。
            // 之前标题在容器外，手指落在标题那一片往下拉时，下面的内容没得滚，
            // 手势直接冒泡给弹窗本身，整张 sheet 跟着手滑走关掉——填一半的表单就这么没了。
            // 现在从标题往下都属于滚动区，下拉先滚内容，滚到顶再交还给弹窗。
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    // weight 一定要带 fill = false：默认的 weight(1f) 会把剩余空间「强制」分给这个 Column，
                    // 于是补打卡这种只有几个字段的短表单也被撑到屏幕 92% 高，内容全顶到屏幕上边、下面一大片空白。
                    // fill = false 时剩余空间只当上限参与测量，短表单就是矮 sheet，长表单照样最多 92% 且能滚。
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
                    .padding(top = 4.dp, bottom = 8.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Column(modifier = Modifier.padding(top = 12.dp)) {
                    content()
                }
            }
            Button(
                onClick = onConfirm,
                enabled = confirmEnabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 52.dp),
            ) { Text(confirmLabel) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun Gap(height: Int) = Spacer(Modifier.height(height.dp))

// ---------------------------------------------------------------- 孩子

@Composable
fun ChildFormSheet(
    initial: Child?,
    onDismiss: () -> Unit,
    onSave: (name: String, nickname: String, birthday: LocalDate?, color: String, note: String) -> Unit,
) {
    // 草稿只给「新建填一半被关掉」兜底；编辑已有记录时传 null，直接用 initial 的值，
    // 免得上一次没保存的草稿把这条记录的原值顶掉
    val draftKey: String? = if (initial == null) "child:new" else null
    var name by rememberDraftText(draftKey, "name", initial?.name ?: "")
    var nickname by rememberDraftText(draftKey, "nickname", initial?.nickname ?: "")
    var birthday by rememberDraftDate(draftKey, "birthday", initial?.birthday)
    var color by rememberDraftText(draftKey, "color", initial?.avatarColor ?: avatarPalette.first())
    var note by rememberDraftText(draftKey, "note", initial?.note ?: "")

    FormSheet(
        title = if (initial == null) "添加孩子" else "编辑孩子",
        onDismiss = onDismiss,
        onConfirm = {
            onSave(name, nickname, birthday, color, note)
            // 存进去了草稿就没用了，清掉；只是关掉不清，下次打开还能接着填
            draftKey?.let { FormDrafts.clear(it) }
        },
        confirmEnabled = name.isNotBlank(),
    ) {
        AppTextField(value = name, onValueChange = { name = it }, label = "姓名", placeholder = "必填")
        Gap(12)
        AppTextField(value = nickname, onValueChange = { nickname = it }, label = "昵称", placeholder = "选填，展示优先用昵称")
        Gap(12)
        FieldLabel("出生日期")
        DateField(label = "出生日期", date = birthday, allowClear = true, onChange = { birthday = it })
        Gap(16)
        FieldLabel("头像底色")
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            avatarPalette.forEach { hex ->
                val selected = color == hex
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(cn.yiweiqiu.lessonledger.ui.components.parseColor(hex, MaterialTheme.colorScheme.primary))
                        .clickable { color = hex },
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.18f)),
                        )
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = null,
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
        Gap(12)
        AppTextField(value = note, onValueChange = { note = it }, label = "备注", singleLine = false)
    }
}

// ---------------------------------------------------------------- 课程

/**
 * 提醒时机只有两种：不提醒、上课时提醒。
 *
 * 存储上沿用 remindBeforeMinutes：0 = 不提醒，非 0 = 上课时刻提醒（不再减提前量）。
 * 这样老数据里的 15/30/60/120 不用迁移，直接落进"上课时提醒"。
 */
val remindBeforeOptions = listOf(0, 30)

fun remindBeforeLabel(minutes: Int): String = if (minutes <= 0) "不提醒" else "上课时提醒"

/** 窄栏里用的短文案：设置页一行放 4 个按钮，长文案会被挤出屏幕 */
fun remindBeforeShortLabel(minutes: Int): String = if (minutes <= 0) "不提醒" else "上课时"

/** 选中态判断用：把历史值折叠成 0 / 30 两种 */
fun remindBeforeKey(minutes: Int): Int = if (minutes <= 0) 0 else 30

// 课程 / 缴费的表单字段定义搬到了 domain（仓库层也要用），这里保留旧名字，调用点不必改。
typealias CourseFormData = cn.yiweiqiu.lessonledger.domain.CourseDraft

@Composable
fun CourseFormSheet(
    initial: Course?,
    defaultThreshold: Int,
    defaultRemindBefore: Int = 30,
    onDismiss: () -> Unit,
    onSave: (CourseFormData) -> Unit,
) {
    // 已登记机构：来自历史课程的去重清单，避免同一机构因为手误拼错被拆成两个
    val orgsVm: OrgsViewModel = hiltViewModel()
    val knownOrgs by orgsVm.orgs.collectAsStateWithLifecycle()

    // 草稿只给「新建填一半被关掉」兜底；编辑已有课程时不走草稿，直接用 initial 的值
    val draftKey: String? = if (initial == null) "course:new" else null
    var orgName by rememberDraftText(draftKey, "orgName", initial?.orgName ?: "")
    var courseName by rememberDraftText(draftKey, "courseName", initial?.courseName ?: "")
    // 枚举、时间这类存字符串，取出来再转回原来的类型
    var categoryName by rememberDraftText(draftKey, "category", (initial?.category ?: Category.MUSIC).name)
    val category = Category.of(categoryName)
    var teacher by rememberDraftText(draftKey, "teacher", initial?.teacher ?: "")
    var location by rememberDraftText(draftKey, "location", initial?.location ?: "")
    var weekdayMask by rememberDraftInt(draftKey, "weekdayMask", initial?.weekdayMask ?: 0)
    var startTimeText by rememberDraftText(draftKey, "startTime", initial?.startTime ?: "")
    val startTime = startTimeText.ifBlank { null }
    // 表单里不再有「统一上课时间 / 单节时长」两个默认值：一门课只在勾选的几天各配一次。
    // 老数据只有统一值的，seedTimes/seedDurations 会把它摊到每个上课日，
    // 打开表单看到的就是实际生效的配置，而不是一片空白。
    val perDayTimes = remember {
        mutableStateMapOf<Int, String>().apply { initial?.let { putAll(it.seedTimes()) } }
    }
    val perDayDurations = remember {
        mutableStateMapOf<Int, Int>().apply { initial?.let { putAll(it.seedDurations()) } }
    }
    var duration by rememberDraftText(draftKey, "duration", (initial?.durationMinutes ?: 60).toString())
    var threshold by rememberDraftText(
        draftKey,
        "threshold",
        (initial?.warningThreshold ?: defaultThreshold).toString(),
    )
    var remindBefore by rememberDraftInt(
        draftKey,
        "remindBefore",
        initial?.remindBeforeMinutes ?: defaultRemindBefore,
    )
    var statusName by rememberDraftText(draftKey, "status", (initial?.status ?: CourseStatus.ACTIVE).name)
    val status = CourseStatus.of(statusName)
    var note by rememberDraftText(draftKey, "note", initial?.note ?: "")

    val weekdayOptions = Dates.weekdayNames.mapIndexed { index, label ->
        index.toString() to label.removePrefix("周")
    }

    FormSheet(
        title = if (initial == null) "添加课程" else "编辑课程",
        onDismiss = onDismiss,
        onConfirm = {
            val selectedDays = (0..6).filter { weekdayMask and (1 shl it) != 0 }
            val dayTimes = perDayTimes.filterKeys { it in selectedDays }
            val dayDurations = perDayDurations.filterKeys { it in selectedDays }
            // 统一时间/时长不再由用户填，老字段只做兜底：取第一个配好的上课日，
            // 保证它和「每天实际怎么上」永远一致，不会留个互相矛盾的默认值
            val fallbackTime = selectedDays.firstNotNullOfOrNull { dayTimes[it] } ?: startTime
            val fallbackDuration = selectedDays.firstNotNullOfOrNull { dayDurations[it] }
                ?: duration.toIntOrNull() ?: 60
            onSave(
                CourseFormData(
                    orgName = orgName.trim(),
                    courseName = courseName,
                    category = category,
                    teacher = teacher.ifBlank { null },
                    location = location.ifBlank { null },
                    weekdayMask = weekdayMask,
                    startTime = fallbackTime,
                    // 按天的上课时间：只存勾着的那几天，全空就是 null
                    startTimesJson = encodeStartTimes(dayTimes),
                    durationMinutes = fallbackDuration,
                    // 按天的时长同样只存勾着的那几天，全空就是 null
                    durationMinutesJson = encodeDurations(dayDurations),
                    warningThreshold = threshold.toIntOrNull() ?: defaultThreshold,
                    remindBeforeMinutes = remindBefore,
                    status = status,
                    note = note,
                )
            )
            // 已经存进去了，草稿清掉；只是关掉不清，下次打开还能接着填
            draftKey?.let { FormDrafts.clear(it) }
        },
        confirmEnabled = orgName.isNotBlank() || courseName.isNotBlank(),
    ) {
        AppTextField(
            value = orgName,
            onValueChange = { orgName = it },
            label = "机构 / 培训班名称",
            placeholder = "如：星海钢琴艺术中心",
        )
        // 机构登记：点已登记机构直接填入，防止同一机构被手误拆成两个
        if (knownOrgs.isNotEmpty()) {
            Gap(8)
            FieldLabel("已登记机构（点选填入）")
            ChipSelector(
                options = knownOrgs.map { it to it },
                selected = emptySet(),
                onToggle = { key -> orgName = key },
            )
        }
        val trimmedOrg = orgName.trim()
        if (trimmedOrg.isNotBlank() && knownOrgs.none { it.equals(trimmedOrg, ignoreCase = true) }) {
            Gap(6)
            Text(
                text = "「$trimmedOrg」将登记为新机构",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Gap(12)
        AppTextField(
            value = courseName,
            onValueChange = { courseName = it },
            label = "课程名称",
            placeholder = "如：钢琴一对一",
        )
        Gap(14)
        FieldLabel("科目")
        ChipSelector(
            options = Category.entries.map { it.name to it.label },
            selected = setOf(categoryName),
            onToggle = { key -> categoryName = key },
        )
        Gap(14)
        AppTextField(value = teacher, onValueChange = { teacher = it }, label = "老师", placeholder = "选填")
        Gap(12)
        AppTextField(value = location, onValueChange = { location = it }, label = "上课地点", placeholder = "选填")
        Gap(14)
        FieldLabel("每周上课日（可多选）")
        ChipSelector(
            options = weekdayOptions,
            selected = weekdayOptions.filter { weekdayMask and (1 shl it.first.toInt()) != 0 }
                .map { it.first }.toSet(),
            onToggle = { key ->
                val bit = 1 shl key.toInt()
                weekdayMask = weekdayMask xor bit
                // 取消勾选这天，它单独设的时间和时长都得跟着删，
                // 不然会存下「没选周三却有周三 16:30 / 90 分钟」的脏数据
                if (weekdayMask and bit == 0) {
                    perDayTimes.remove(key.toInt())
                    perDayDurations.remove(key.toInt())
                }
            },
        )
        // 有几天就配几行，不再有「统一值」这一层：
        // 一门课只配一次，看见的就是实际生效的，不用再猜某天跟的是哪个默认值。
        val selectedWeekdays = Dates.weekdayNames.indices.filter { weekdayMask and (1 shl it) != 0 }
        Gap(10)
        Text(
            text = if (selectedWeekdays.isEmpty()) {
                "勾选上面的上课日，再给这几天各配一次时间和时长。"
            } else {
                "每天的上课时间与单节时长各配一次。时间留空 = 那天时间不固定（接龙、临时约课），不会自动排课。"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (selectedWeekdays.isNotEmpty()) {
            Gap(12)
            selectedWeekdays.forEach { day ->
                WeekdayPlanRow(
                    weekday = day,
                    time = perDayTimes[day],
                    onTimeChange = { value ->
                        // 清掉改成「没设」而不是存个空串，省得后面还得过滤
                        if (value.isNullOrBlank()) perDayTimes.remove(day) else perDayTimes[day] = value
                    },
                    durationMinutes = perDayDurations[day],
                    onDurationChange = { value ->
                        if (value == null) perDayDurations.remove(day) else perDayDurations[day] = value
                    },
                )
                Gap(8)
            }
        }
        Gap(12)
        AppTextField(
            value = threshold,
            onValueChange = { threshold = it.filter { ch -> ch.isDigit() } },
            label = "剩余提醒阈值",
            suffix = "节",
            keyboardType = KeyboardType.Number,
        )
        Gap(14)
        FieldLabel("上课提醒")
        ChipSelector(
            options = remindBeforeOptions.map { it.toString() to remindBeforeLabel(it) },
            selected = setOf(remindBeforeKey(remindBefore).toString()),
            onToggle = { key -> remindBefore = key.toIntOrNull() ?: remindBefore },
        )
        Gap(14)
        FieldLabel("状态")
        ChipSelector(
            options = CourseStatus.entries.map { it.name to it.label },
            selected = setOf(statusName),
            onToggle = { statusName = it },
        )
        Gap(12)
        AppTextField(value = note, onValueChange = { note = it }, label = "备注", singleLine = false)
    }
}

/**
 * 「每天单独设」里的一行：周X + 上课时间 + 单节时长，两个表单共用。
 *
 * 三个元素挤在一行，所以时长框固定 96dp，剩下的宽度全给时间选择器 ——
 * 1080px（约 360dp）宽的屏上，时间那颗按钮还放得下「16:30」和「清除」。
 * 时长放第二行也行，但一周最多七行，摊开要滚很久，还是挤一行更好翻。
 */
@Composable
fun WeekdayPlanRow(
    weekday: Int,
    time: String?,
    onTimeChange: (String?) -> Unit,
    /** 这天的时长（分钟）；null = 还没填 */
    durationMinutes: Int?,
    onDurationChange: (Int?) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = Dates.weekdayNames[weekday],
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(40.dp),
        )
        Spacer(Modifier.width(6.dp))
        TimeField(
            // 未设置时按钮上要显示「XX（未设置）」，名字太长会把这行撑成两行，所以叫短一点
            label = "时间",
            value = time,
            onChange = onTimeChange,
            modifier = Modifier.weight(1f),
        )
        Spacer(Modifier.width(8.dp))
        OutlinedTextField(
            value = durationMinutes?.toString() ?: "",
            onValueChange = { raw ->
                val minutes = raw.filter { it.isDigit() }.take(3).toIntOrNull()
                // 清空、填 0 都算「没填」
                onDurationChange(minutes?.takeIf { it > 0 })
            },
            label = { Text("分钟") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp),
        )
    }
}

// ---------------------------------------------------------------- 缴费

typealias PaymentFormData = cn.yiweiqiu.lessonledger.domain.PaymentDraft

@Composable
fun PaymentFormSheet(
    initial: Payment?,
    onDismiss: () -> Unit,
    onSave: (PaymentFormData) -> Unit,
    /** 传了就是「补尾款」模式：金额按待付尾款预填，课时固定 0（课时在交定金时已锁定） */
    deposit: Payment? = null,
    /** 从「交定金」入口进来时直接展开定金模式 */
    startInDepositMode: Boolean = false,
    /** 新建时这笔缴费属于哪门课：只用来分开存草稿，A 课填一半不会串到 B 课 */
    courseId: String? = null,
) {
    val balanceMode = deposit != null
    // 草稿只给「新建填一半被关掉」兜底；编辑已有缴费时不走草稿，直接用 initial 的值，
    // 免得上一次没保存的草稿把这条记录的原值顶掉
    val draftKey: String? = if (initial == null) {
        "payment:" + (deposit?.let { "balance:${it.id}" } ?: "new:${courseId ?: ""}")
    } else {
        null
    }
    var payDate by rememberDraftDate(draftKey, "payDate", initial?.payDate ?: Dates.today())
    var amount by rememberDraftText(
        draftKey,
        "amount",
        when {
            initial != null -> initial.amountCents.let { Money.plain(kotlin.math.abs(it)) }
            deposit != null -> Money.plain(deposit.balanceDueCents ?: 0L)
            else -> ""
        },
    )
    var originalAmount by rememberDraftText(
        draftKey,
        "originalAmount",
        initial?.originalAmountCents?.let { Money.plain(it) }
            ?: deposit?.originalAmountCents?.let { Money.plain(it) }
            ?: "",
    )
    var lessonsBought by rememberDraftText(
        draftKey,
        "lessonsBought",
        if (balanceMode) "" else initial?.lessonsBoughtHalf?.let { Lessons.format(kotlin.math.abs(it)) } ?: "",
    )
    var lessonsBonus by rememberDraftText(
        draftKey,
        "lessonsBonus",
        if (balanceMode) "" else initial?.lessonsBonusHalf?.takeIf { it > 0 }?.let { Lessons.format(it) } ?: "",
    )
    var payMethodName by rememberDraftText(draftKey, "payMethod", (initial?.payMethod ?: PayMethod.WECHAT).name)
    val payMethod = PayMethod.of(payMethodName)
    var validUntil by rememberDraftDate(draftKey, "validUntil", initial?.validUntil)
    var receipts by remember { mutableStateOf(initial?.receipts ?: emptyList()) }
    var note by rememberDraftText(draftKey, "note", initial?.note ?: "")

    // 定金模式：只在新登记缴费时可选，编辑旧记录不掺这件事
    var depositMode by rememberDraftBool(draftKey, "depositMode", initial?.isDeposit == true || startInDepositMode)
    var creditText by rememberDraftText(
        draftKey,
        "credit",
        initial?.creditCents?.takeIf { it > 0 }?.let { Money.plain(it) } ?: "",
    )

    val amountCents = Money.parseYuan(amount)
    val lessonsHalf = Lessons.parse(lessonsBought)
    val totalLessons = (lessonsHalf ?: 0L) + (Lessons.parse(lessonsBonus) ?: 0L)
    val creditCents = Money.parseYuan(creditText) ?: 0L
    val totalAmountCents = Money.parseYuan(originalAmount)
    val balanceDue = (totalAmountCents ?: 0L) - creditCents

    FormSheet(
        title = when {
            balanceMode -> "补尾款"
            initial == null -> if (depositMode) "交定金" else "记一笔缴费"
            else -> if (initial.isDeposit) "编辑定金" else "编辑缴费"
        },
        onDismiss = onDismiss,
        onConfirm = {
            onSave(
                PaymentFormData(
                    payDate = payDate ?: Dates.today(),
                    amountCents = amountCents ?: 0L,
                    originalAmountCents = Money.parseYuan(originalAmount)?.takeIf { it > 0 },
                    lessonsBoughtHalf = if (balanceMode) 0L else lessonsHalf ?: 0L,
                    lessonsBonusHalf = if (balanceMode) 0L else Lessons.parse(lessonsBonus) ?: 0L,
                    payMethod = payMethod,
                    validUntil = validUntil,
                    receipts = receipts,
                    note = note,
                    kind = when {
                        balanceMode -> cn.yiweiqiu.lessonledger.domain.PaymentKind.BALANCE
                        depositMode -> cn.yiweiqiu.lessonledger.domain.PaymentKind.DEPOSIT
                        else -> initial?.kind ?: cn.yiweiqiu.lessonledger.domain.PaymentKind.FULL
                    },
                    groupKey = if (balanceMode) deposit?.id else initial?.groupKey,
                    creditCents = when {
                        balanceMode -> 0L
                        depositMode -> creditCents
                        else -> 0L
                    },
                )
            )
            // 已经存进去了，草稿清掉；只是关掉不清，下次打开还能接着填
            draftKey?.let { FormDrafts.clear(it) }
        },
        confirmEnabled = amountCents != null &&
            (balanceMode || lessonsHalf != null || initial != null) &&
            (!depositMode || (creditCents > 0 && totalAmountCents != null)),
    ) {
        if (balanceMode) {
            // 把「这笔钱是怎么来的」先说清楚，用户才知道为什么金额是自动填好的
            Text(
                text = "定金 ${Money.format(deposit!!.amountCents)}" +
                    (if (deposit.creditCents > 0) " 抵 ${Money.format(deposit.creditCents)}" else "") +
                    "，整期 ${Money.format(deposit.originalAmountCents ?: 0L)}，" +
                    "还需补 ${Money.format(deposit.balanceDueCents ?: 0L)}。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Gap(12)
        }
        FieldLabel(if (balanceMode) "补款日期" else "缴费日期")
        DateField(label = "缴费日期", date = payDate, onChange = { payDate = it })
        Gap(12)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = amount,
                onValueChange = { amount = it.onlyDecimal() },
                label = if (depositMode) "定金实付" else if (balanceMode) "尾款实付" else "实付金额",
                suffix = "元",
                keyboardType = KeyboardType.Decimal,
                isError = amount.isNotEmpty() && amountCents == null,
                modifier = Modifier.weight(1f),
            )
            if (!balanceMode) {
                AppTextField(
                    value = originalAmount,
                    onValueChange = { originalAmount = it.onlyDecimal() },
                    label = if (depositMode) "整期总价" else "原价",
                    suffix = "元",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
            }
        }
        if (!balanceMode) {
            Gap(12)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // 定金这条一定得把课时填上：补尾款走 payDepositBalance()，那边 lessonsBoughtHalf 写死 0，
                // 课时不在这记，这门课就永远没课时、剩余一直是 0。所以位置不动，只把说法改成整期。
                AppTextField(
                    value = lessonsBought,
                    onValueChange = { lessonsBought = it.onlyDecimal() },
                    label = if (depositMode) "整期课时" else "购买课时",
                    suffix = "节",
                    keyboardType = KeyboardType.Decimal,
                    modifier = Modifier.weight(1f),
                )
                // 定金阶段就先把整期课时记下来，赠送课时拆出来没意义，省一个格子
                if (!depositMode) {
                    AppTextField(
                        value = lessonsBonus,
                        onValueChange = { lessonsBonus = it.onlyDecimal() },
                        label = "赠送课时",
                        suffix = "节",
                        keyboardType = KeyboardType.Decimal,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
        if (totalLessons > 0 && amountCents != null && amountCents > 0 && !depositMode) {
            val unit = amountCents * 2 / totalLessons
            Text(
                text = "本批次共 ${Lessons.format(totalLessons)} 节，单价约 ${Money.format(unit)}/节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        if (initial == null && !balanceMode) {
            Gap(14)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("先付定金，尾款后付", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        // 已经开着定金模式了，就不用再解释「什么场景该用」，一句说清课时落在哪就行
                        text = if (depositMode) {
                            "课时记在这条上，之后单独「补尾款」"
                        } else {
                            "适合机构搞活动：定金能抵扣课时费，开课后再补剩下的"
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = depositMode, onCheckedChange = { on ->
                    depositMode = on
                    // 抵扣默认等于实付（「交多少抵多少」），有活动再往上调
                    if (on && creditText.isBlank()) creditText = amount
                })
            }
            if (depositMode) {
                Gap(10)
                AppTextField(
                    value = creditText,
                    onValueChange = { creditText = it.onlyDecimal() },
                    label = "定金抵扣",
                    suffix = "元",
                    keyboardType = KeyboardType.Decimal,
                    supportingText = "这笔定金在总价里当多少钱用；交 500 抵 800 就填 800",
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = when {
                        totalAmountCents == null -> "填上「整期总价」，就能算出尾款要补多少"
                        balanceDue < 0 -> "抵扣比总价还高，检查一下数字"
                        else -> "尾款还需补 ${Money.format(balanceDue)}" +
                            "（整期 ${Money.format(totalAmountCents)} − 抵扣 ${Money.format(creditCents)}）"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (balanceDue < 0) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Gap(14)
        FieldLabel("支付方式")
        ChipSelector(
            options = PayMethod.entries.map { it.name to it.label },
            selected = setOf(payMethodName),
            onToggle = { payMethodName = it },
        )
        // 有效期和票据照片留到正式缴费那一步再补，定金这里先不摊开
        if (!balanceMode && !depositMode) {
            Gap(14)
            FieldLabel("课时有效期（可空 = 长期有效）")
            DateField(label = "有效期", date = validUntil, allowClear = true, onChange = { validUntil = it })
            Gap(14)
            FieldLabel("票据照片")
            ReceiptPicker(receipts = receipts, onChange = { receipts = it })
        }
        Gap(12)
        AppTextField(value = note, onValueChange = { note = it }, label = "备注", singleLine = false)
    }
}

private fun String.onlyDecimal(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered
    else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}

@Composable
fun ReceiptPicker(receipts: List<String>, onChange: (List<String>) -> Unit) {
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var pendingName by remember { mutableStateOf<String?>(null) }
    // 删照片是不可撤销的：一旦从这条缴费里去掉，就得重新拍或从相册再选一次，所以先问一句
    var deletingReceipt by remember { mutableStateOf<String?>(null) }

    val cameraLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.TakePicture(),
    ) { success ->
        val name = pendingName
        if (success && name != null) {
            Receipts.compressFile(context, name)
            onChange(receipts + name)
        }
        pendingUri = null
        pendingName = null
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val name = Receipts.importFromUri(context, uri)
            if (name != null) onChange(receipts + name)
        }
    }

    Column {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(receipts) { name ->
                val bitmap = remember(name) {
                    runCatching {
                        android.graphics.BitmapFactory.decodeFile(Receipts.file(context, name).absolutePath)
                    }.getOrNull()
                }
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    if (bitmap != null) {
                        androidx.compose.foundation.Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(72.dp),
                        )
                    }
                    IconButton(
                        onClick = { deletingReceipt = name },
                        modifier = Modifier.align(Alignment.TopEnd).size(24.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "删除",
                            tint = androidx.compose.ui.graphics.Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = 0.45f)),
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            OutlinedButton(
                onClick = {
                    val (uri, name) = Receipts.newPhotoTarget(context)
                    pendingUri = uri
                    pendingName = name
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("拍照")
            }
            OutlinedButton(
                onClick = { galleryLauncher.launch("image/*") },
                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
            ) {
                Icon(Icons.Rounded.PhotoLibrary, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("从相册")
            }
        }

        deletingReceipt?.let { name ->
            AlertDialog(
                onDismissRequest = { deletingReceipt = null },
                title = { Text("删除这张票据照片？") },
                text = { Text("删掉后这张照片就不挂在这笔缴费上了，找不回来；需要的话可以重新拍照或从相册再选一次。") },
                confirmButton = {
                    TextButton(onClick = {
                        onChange(receipts - name)
                        deletingReceipt = null
                    }) { Text("删除", color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deletingReceipt = null }) { Text("取消") } },
            )
        }
    }
}

// ---------------------------------------------------------------- 课次

data class LessonFormData(
    val courseId: String,
    val date: LocalDate,
    val time: String?,
    /** 这节课实际上了多久（分钟）；null = 没填，按课程时长算。给默认值，老调用点不用改 */
    val durationMinutes: Int? = null,
    val status: LessonStatus,
    val deductedHalf: Long,
    val note: String,
)

/**
 * 折算基准：这门课「1 节 = 多少分钟」。
 * 用课程上的统一单节时长（默认 60），按天单独设的时长只用来预填、不当基准 ——
 * 不然周末 90 分钟那节课就成了「1 节 = 90 分钟」，周末上一小时反而扣不满。
 */
private fun baseMinutesOf(courses: List<Course>, courseId: String): Int =
    (courses.firstOrNull { it.id == courseId }?.durationMinutes ?: 60).coerceAtLeast(1)

@Composable
fun LessonFormSheet(
    courses: List<Course>,
    initialCourseId: String?,
    initial: Lesson?,
    defaultStatus: LessonStatus = LessonStatus.DONE,
    /** 新增（不是编辑）时预填的日期：从「最近记录」里点漏掉的课直接补，不用再翻日历 */
    initialDate: LocalDate? = null,
    initialTime: String? = null,
    onDismiss: () -> Unit,
    onSave: (LessonFormData) -> Unit,
) {
    // 草稿只给「新建填一半被关掉」兜底；编辑已有课次时不走草稿，直接用 initial 的值。
    // key 里带上课程和用途（补打卡 / 排课），两处默认值不一样，串在一起会带出奇怪的状态。
    val draftKey: String? = if (initial == null) {
        "lesson:new:${initialCourseId ?: courses.firstOrNull()?.id ?: ""}:${defaultStatus.name}"
    } else {
        null
    }
    var courseId by rememberDraftText(
        draftKey,
        "courseId",
        initial?.courseId ?: initialCourseId ?: courses.firstOrNull()?.id ?: "",
    )
    var date by rememberDraftDate(
        draftKey,
        "date",
        initial?.scheduledAt?.let { Dates.toDate(it) } ?: initialDate ?: Dates.today(),
    )
    var timeText by rememberDraftText(
        draftKey,
        "time",
        initial?.scheduledAt?.let { Dates.formatTime(it) }
            ?: initialTime
            // 默认按「这门课在这一天几点上课」带：补打卡落在周三就带周三的时间，
            // 一周多天时间不一样时才不会错；日期还空着就回落到统一时间
            ?: courses.firstOrNull { it.id == courseId }
                ?.let { c -> date?.let { c.startTimeOn(it) } ?: c.startTime }
            ?: "",
    )
    val time = timeText.ifBlank { null }
    var statusName by rememberDraftText(draftKey, "status", (initial?.status ?: defaultStatus).name)
    val status = LessonStatus.of(statusName)
    var lessonsText by rememberDraftText(
        draftKey,
        "lessons",
        if (initial != null) Lessons.format(initial.deductedHalf.coerceAtLeast(0)) else "1",
    )
    var note by rememberDraftText(draftKey, "note", initial?.note ?: "")
    // 上课时长（分钟）：留空 = 跟随课程。编辑时带这节课实际上的时长，
    // 新建时按「这门课在那天的时长」预填（平时 60、周末 90 这种才不会填错）
    var durationText by rememberDraftText(
        draftKey,
        "duration",
        initial?.durationMinutes?.toString()
            ?: date?.let { d -> courses.firstOrNull { it.id == courseId }?.durationOn(d)?.toString() }
            ?: "",
    )
    // 「用户自己动过」的标记：动过的字段不再被联动覆盖，
    // 不然刚手敲的 16:30 / 90 分钟会被下一次改日期冲掉。
    // 编辑已有课次（或调用方显式给了时间）时，这些值本来就是用户定好的，一开始就当动过。
    var timeTouched by remember { mutableStateOf(initial != null || !initialTime.isNullOrBlank()) }
    var durationTouched by remember { mutableStateOf(initial != null) }
    var lessonsTouched by remember { mutableStateOf(initial != null) }

    // 改日期（或换课程）时，把时间和时长一起换成「这门课在那天的安排」：
    // 补打卡落在周三就该带周三的时间，一周几天时间不一样时才不会记错。
    LaunchedEffect(courseId, date) {
        val course = courses.firstOrNull { it.id == courseId }
        if (!timeTouched) {
            timeText = course?.let { c -> date?.let { c.startTimeOn(it) } ?: c.startTime } ?: ""
        }
        if (!durationTouched) {
            durationText = date?.let { course?.durationOn(it)?.toString() } ?: ""
        }
    }
    // 时长一变就按「1 节 = 课程统一时长」把扣课时算出来：
    // 60 分钟的课上了 90 分钟 = 1.5 节、上了 2 小时 = 2 节，往上取整到半节，最少算半节。
    // 用户自己改过扣课时（比如只扣 0.5 节）就以他填的为准。
    LaunchedEffect(courseId, durationText) {
        if (lessonsTouched) return@LaunchedEffect
        val minutes = durationText.toIntOrNull() ?: return@LaunchedEffect
        val base = baseMinutesOf(courses, courseId)
        val half = ceil(minutes.toDouble() / base * 2).toLong().coerceAtLeast(1)
        lessonsText = Lessons.format(half)
    }

    FormSheet(
        title = if (initial == null) {
            if (defaultStatus == LessonStatus.PENDING) "添加课次" else "补打卡"
        } else {
            "编辑课次"
        },
        onDismiss = onDismiss,
        onConfirm = {
            // 兜底：这个框是文本状态，历史上出现过被写成 8 位数字、把整门课的剩余课时算成
            // 负两千万节的情况。单节课最多按 20 节算，超出范围就回到「按时长折算」的结果，
            // 宁可记成默认值，也不能让一次误写把账本算崩。
            val fallbackHalf = durationText.toIntOrNull()
                ?.let { ceil(it.toDouble() / baseMinutesOf(courses, courseId) * 2).toLong() }
                ?.coerceAtLeast(2L) ?: 2L
            val parsedHalf = Lessons.parse(lessonsText)?.takeIf { it in 0L..40L } ?: fallbackHalf
            onSave(
                LessonFormData(
                    courseId = courseId,
                    date = date ?: Dates.today(),
                    time = time,
                    durationMinutes = durationText.toIntOrNull(),
                    status = status,
                    deductedHalf = parsedHalf,
                    note = note,
                )
            )
            // 已经存进去了，草稿清掉；只是关掉不清，下次打开还能接着填
            draftKey?.let { FormDrafts.clear(it) }
        },
        confirmEnabled = courseId.isNotBlank(),
    ) {
        // 补打卡一定要能看到（也能改）课程：哪怕现在只有一门课，
        // 用户得先确认自己是给哪门课补的，加新课后也能直接切过去。
        val pickingCourse = initial == null && defaultStatus != LessonStatus.PENDING
        if (courses.size > 1 || pickingCourse) {
            FieldLabel("课程")
            ChipSelector(
                options = courses.map { it.id to it.courseName },
                selected = setOf(courseId),
                onToggle = { key ->
                    courseId = key
                    // 换课时时间也一起换，同样按当前选的日期取，不是拿统一时间；
                    // 用户手改过时间就别覆盖了，时长和扣课时交给上面那两个联动统一处理
                    if (!timeTouched) {
                        timeText = courses.firstOrNull { it.id == key }
                            ?.let { c -> date?.let { c.startTimeOn(it) } ?: c.startTime }
                            ?: timeText
                    }
                },
            )
            Gap(14)
        }
        FieldLabel("上课日期")
        DateField(label = "上课日期", date = date, onChange = { date = it })
        Gap(12)
        FieldLabel("上课时间")
        TimeField(
            label = "上课时间",
            value = time,
            onChange = {
                timeText = it ?: ""
                timeTouched = true
            },
        )
        Gap(12)
        AppTextField(
            value = durationText,
            onValueChange = {
                durationText = it.filter { ch -> ch.isDigit() }
                durationTouched = true
            },
            label = "上课时长",
            suffix = "分钟",
            keyboardType = KeyboardType.Number,
            supportingText = "留空 = 按课程时长算；改这里扣课时会自动折算",
        )
        Gap(14)
        FieldLabel("状态")
        ChipSelector(
            options = LessonStatus.entries.map { it.name to it.label },
            selected = setOf(statusName),
            onToggle = { statusName = it },
        )
        Gap(14)
        AppTextField(
            value = lessonsText,
            onValueChange = {
                lessonsText = it.onlyDecimal()
                lessonsTouched = true
            },
            label = "扣课时",
            suffix = "节",
            keyboardType = KeyboardType.Decimal,
            supportingText = "按 1 节 = ${baseMinutesOf(courses, courseId)} 分钟折算，改时长会自动算",
        )
        Gap(12)
        AppTextField(value = note, onValueChange = { note = it }, label = "备注", singleLine = false)
    }
}

// ---------------------------------------------------------------- 退费

/**
 * 办理退费。
 *
 * 退多少节课、退多少钱，一律以机构实际结算的为准 —— 常见的算法有按剩余课时原价退、
 * 按折扣退、扣一笔手续费、或者整期打包退一个整数（2760 交了 46 节，剩 12 节退 600）。
 * 所以这里两个都能自己填：课时默认可退到剩余节数，金额默认按有效单价折算，
 * 一旦手动改过金额就不再跟着课时跳，免得一边填一边被覆盖。
 */
@Composable
fun RefundSheet(
    unitPriceCents: Long,
    maxLessonsHalf: Long,
    valueNumerCents: Long,
    valueDenomHalf: Long,
    onDismiss: () -> Unit,
    onSave: (lessonsHalf: Long, refundCents: Long, note: String) -> Unit,
) {
    var lessonsText by remember { mutableStateOf("") }
    var amountText by remember { mutableStateOf("") }
    // 用户动过金额就不再自动联动，否则填好的数会被课时输入框一句话改掉
    var amountTouched by remember { mutableStateOf(false) }
    var note by remember { mutableStateOf("") }

    val lessonsHalf = Lessons.parse(lessonsText) ?: 0L
    val suggested = LedgerCalc.valueOfHalf(lessonsHalf, valueNumerCents, valueDenomHalf)
    LaunchedEffect(lessonsHalf, amountTouched) {
        if (!amountTouched) {
            amountText = if (lessonsHalf > 0L && suggested > 0L) Money.plain(suggested) else ""
        }
    }
    val refundCents = Money.parseYuan(amountText) ?: 0L
    // 超过剩余课时不是错误：整期退掉时本来就会退到 0 甚至退多，
    // 但要说清楚后果，别让人以为算错了
    val overRemaining = lessonsHalf > maxLessonsHalf
    val discountGap = suggested - refundCents

    FormSheet(
        title = "办理退费",
        onDismiss = onDismiss,
        onConfirm = { onSave(lessonsHalf, refundCents, note) },
        confirmEnabled = lessonsHalf > 0L && refundCents > 0L,
        confirmLabel = "确认退费",
    ) {
        Text(
            text = "剩余 ${Lessons.format(maxLessonsHalf)} 节，按有效单价 ${Money.format(unitPriceCents)}/节 折算。" +
                "课时和金额都可以按实际情况改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(12)
        AppTextField(
            value = lessonsText,
            onValueChange = { lessonsText = it.onlyDecimal() },
            label = "退费课时",
            suffix = "节",
            keyboardType = KeyboardType.Decimal,
            supportingText = if (overRemaining) {
                "比剩余课时多 ${Lessons.format(lessonsHalf - maxLessonsHalf)} 节，退完这门课的课时会变成负数"
            } else {
                null
            },
        )
        Gap(12)
        AppTextField(
            value = amountText,
            onValueChange = {
                amountTouched = true
                amountText = it.onlyDecimal()
            },
            label = "退费金额",
            suffix = "元",
            keyboardType = KeyboardType.Decimal,
            supportingText = "默认按单价折算，机构按别的算法结算时直接改成实退的钱",
        )
        if (lessonsHalf > 0L && suggested > 0L) {
            Gap(8)
            Text(
                text = when {
                    discountGap > 0L -> "比按单价折算少 ${Money.format(discountGap)}（手续费 / 折扣）"
                    discountGap < 0L -> "比按单价折算多 ${Money.format(-discountGap)}"
                    else -> "与按单价折算一致"
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Gap(16)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("应退金额", style = MaterialTheme.typography.bodyMedium)
            Spacer(Modifier.weight(1f))
            Text(
                text = Money.format(refundCents),
                style = MaterialTheme.typography.titleMedium,
                color = cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors.current.income,
            )
        }
        Gap(12)
        AppTextField(value = note, onValueChange = { note = it }, label = "备注", singleLine = false)
    }
}

// ---------------------------------------------------------------- 结清课时

/**
 * 一次性结清剩余课时。
 *
 * 补录两三年前上完的课时用：具体哪天上了哪节已经查不到，逐节铺日期是编造，
 * 所以只记一条「核销」——课时照扣、课程转入已结束，但不进打卡日历、不算一次上课。
 */
@Composable
fun SettleLessonsSheet(
    remainingHalf: Long,
    onDismiss: () -> Unit,
    onSave: (lessonsHalf: Long, note: String) -> Unit,
) {
    var lessonsText by remember {
        mutableStateOf(if (remainingHalf > 0L) Lessons.format(remainingHalf) else "")
    }
    var note by remember { mutableStateOf("") }
    val lessonsHalf = Lessons.parse(lessonsText) ?: 0L

    FormSheet(
        title = "结清剩余课时",
        onDismiss = onDismiss,
        onConfirm = { onSave(lessonsHalf, note) },
        confirmEnabled = lessonsHalf > 0L,
        confirmLabel = "确认结清",
    ) {
        Text(
            text = "适合补录老账：课上完了、但每次的日期已经查不到。" +
                "结清只记一条核销记录，扣掉课时并把课程转为已结束，日历里不会多出编造的课次。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Gap(12)
        AppTextField(
            value = lessonsText,
            onValueChange = { lessonsText = it.onlyDecimal() },
            label = "结清课时",
            suffix = "节",
            keyboardType = KeyboardType.Decimal,
            supportingText = "默认是这门课现在剩下的 ${Lessons.format(remainingHalf)} 节，可以改少",
        )
        Gap(12)
        AppTextField(
            value = note,
            onValueChange = { note = it },
            label = "备注",
            placeholder = "如：2023 年上的课，日期已不可考",
        )
    }
}

// ---------------------------------------------------------------- 机构登记

/** 已登记机构：历史课程机构名去重，供课程表单点选，避免手误把同一机构拆成两个 */
@HiltViewModel
class OrgsViewModel @Inject constructor(
    repo: LedgerRepository,
) : ViewModel() {
    val orgs: StateFlow<List<String>> = repo.data
        .map { data ->
            data.courses.filter { !it.deleted }
                .map { it.orgName.trim() }
                .filter { it.isNotBlank() }
                .distinct()
                .sorted()
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}
