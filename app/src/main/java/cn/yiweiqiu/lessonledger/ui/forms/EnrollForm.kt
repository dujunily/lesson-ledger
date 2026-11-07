package cn.yiweiqiu.lessonledger.ui.forms

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Lessons
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.Category
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.domain.Course
import cn.yiweiqiu.lessonledger.domain.CourseDraft
import cn.yiweiqiu.lessonledger.domain.CourseStatus
import cn.yiweiqiu.lessonledger.domain.LedgerCalc
import cn.yiweiqiu.lessonledger.domain.PayMethod
import cn.yiweiqiu.lessonledger.domain.PaymentDraft
import cn.yiweiqiu.lessonledger.domain.PaymentKind
import cn.yiweiqiu.lessonledger.domain.encodeDurations
import cn.yiweiqiu.lessonledger.domain.encodeStartTimes
import cn.yiweiqiu.lessonledger.ui.components.AppTextField
import cn.yiweiqiu.lessonledger.ui.components.ChipSelector
import cn.yiweiqiu.lessonledger.ui.components.DateField
import cn.yiweiqiu.lessonledger.ui.components.FieldLabel
import cn.yiweiqiu.lessonledger.ui.components.TimeField
import java.time.LocalDate

/**
 * 一门可选的已有课程。
 *
 * [lastAmountCents] / [lastLessonsHalf] 是最近一笔正数缴费的金额和课时，
 * 续费时直接拿来预填 —— 大多数情况下一期和上一期的钱一样，用户只要确认。
 */
data class CourseOption(
    val course: Course,
    val stats: LedgerCalc.CourseStats,
    val period: Int,
    val lastAmountCents: Long = 0L,
    val lastLessonsHalf: Long = 0L,
) {
    val remainingHalf: Long get() = stats.remainingHalf
    val exhausted: Boolean get() = remainingHalf <= 0L

    /**
     * 这一期是不是已经过完了 —— 只有课时上完、或者用户自己把课标成了已结束才算。
     *
     * 「开新一期」不是换档案的按钮，它是「上一期读完了，买下一期」。
     * 还剩着课时的时候给这个选项，只会让人把一门还能上的课提前封掉。
     */
    val canStartNewPeriod: Boolean get() = exhausted || course.status == CourseStatus.FINISHED
}

/** 表单提交出去的两种结果，调用方决定落库到哪条路径 */
sealed interface EnrollSubmit {
    /** 新课程 + 首笔缴费 */
    data class NewCourse(
        val childId: String,
        val course: CourseDraft,
        val pay: PaymentDraft,
    ) : EnrollSubmit

    /** 续费：接续（startNewPeriod = false）或开新一期 */
    data class Renew(
        val target: Course,
        val startNewPeriod: Boolean,
        val course: CourseDraft,
        val pay: PaymentDraft,
    ) : EnrollSubmit
}

/**
 * 缴费即录课。
 *
 * 这个表单是「先有课程、再去课程里补一笔缴费」的反转：钱是入口，课程是它的产物。
 * 机构、课程名、课时、金额都是家长交钱当下手里就有的信息，课表要等排课才知道，
 * 所以课表区默认收起，只有新建课程或开新一期时才展开。
 *
 * 输入机构/课程名时下方会列出同机构已有的课程，点一下就是续费 ——
 * 这也是避免「同一个班被录成两门课」的主要手段。
 */
@Composable
fun EnrollFormSheet(
    children: List<Child>,
    initialChildId: String?,
    options: List<CourseOption>,
    defaultThreshold: Int,
    defaultRemindBefore: Int,
    onDismiss: () -> Unit,
    onSubmit: (EnrollSubmit) -> Unit,
    /** 从课程详情进来续费时直接锁定目标，跳过选课那一步 */
    renewTarget: CourseOption? = null,
) {
    val orgsVm: OrgsViewModel = hiltViewModel()
    val knownOrgs by orgsVm.orgs.collectAsStateWithLifecycle()

    val initialCourse = renewTarget?.course

    var childId by remember {
        mutableStateOf(initialChildId ?: initialCourse?.childId ?: children.firstOrNull()?.id.orEmpty())
    }
    var orgName by remember { mutableStateOf(initialCourse?.orgName ?: "") }
    var courseName by remember { mutableStateOf(initialCourse?.courseName ?: "") }
    var category by remember { mutableStateOf(initialCourse?.category ?: Category.MUSIC) }
    var teacher by remember { mutableStateOf(initialCourse?.teacher ?: "") }
    var location by remember { mutableStateOf(initialCourse?.location ?: "") }
    var weekdayMask by remember { mutableStateOf(initialCourse?.weekdayMask ?: 0) }
    var startTime by remember { mutableStateOf(initialCourse?.startTime) }
    // 每个星期几单独的上课时间（key 0=周一 ... 6=周日）。
    // 表单里不再有「统一上课时间」这个默认值：一门课只在勾选的那几天各配一次，
    // 老数据只有统一时间的，这里把它摊到每个上课日，打开表单看到的就是实际时间。
    val perDayTimes = remember {
        mutableStateMapOf<Int, String>().apply {
            initialCourse?.let { putAll(it.seedTimes()) }
        }
    }
    // 每个星期几的时长（分钟）：平时 60、周末 90 这种，逐天配，同样不再有默认值兜着
    val perDayDurations = remember {
        mutableStateMapOf<Int, Int>().apply {
            initialCourse?.let { putAll(it.seedDurations()) }
        }
    }
    var duration by remember { mutableStateOf((initialCourse?.durationMinutes ?: 60).toString()) }
    var threshold by remember {
        mutableStateOf((initialCourse?.warningThreshold ?: defaultThreshold).toString())
    }
    var remindBefore by remember {
        mutableStateOf(initialCourse?.remindBeforeMinutes ?: defaultRemindBefore)
    }

    // 选中的已有课程 = 续费。null = 这是一门新课。
    var matched by remember { mutableStateOf(renewTarget) }
    // 这一期已经上完了才谈得上「开新一期」；还剩课时就只能接着上
    var newPeriod by remember { mutableStateOf(renewTarget?.canStartNewPeriod ?: false) }

    var payDate by remember { mutableStateOf<LocalDate?>(Dates.today()) }
    var amount by remember {
        mutableStateOf(renewTarget?.lastAmountCents?.takeIf { it > 0 }?.let { Money.plain(it) } ?: "")
    }
    var originalAmount by remember { mutableStateOf("") }
    var lessonsBought by remember {
        mutableStateOf(
            renewTarget?.lastLessonsHalf?.takeIf { it > 0 }?.let { Lessons.format(it) } ?: ""
        )
    }
    var lessonsBonus by remember { mutableStateOf("") }
    var payMethod by remember { mutableStateOf(PayMethod.WECHAT) }
    var validUntil by remember { mutableStateOf<LocalDate?>(null) }
    var receipts by remember { mutableStateOf(emptyList<String>()) }
    var note by remember { mutableStateOf("") }

    // 定金模式：这一笔只是先付一部分，整期总价和抵扣额一起决定之后要补多少尾款
    var depositMode by remember { mutableStateOf(false) }
    var creditText by remember { mutableStateOf("") }

    val amountCents = Money.parseYuan(amount)
    val lessonsHalf = Lessons.parse(lessonsBought)
    val totalLessons = (lessonsHalf ?: 0L) + (Lessons.parse(lessonsBonus) ?: 0L)
    val creditCents = Money.parseYuan(creditText) ?: 0L
    val totalAmountCents = Money.parseYuan(originalAmount)
    // 尾款 = 整期总价 − 定金抵扣，负数说明数字填错了（抵扣不可能比总价还高）
    val balanceDue = (totalAmountCents ?: 0L) - creditCents

    // 选中已有课程时把它的设置搬进表单，开新一期时直接沿用（也允许改）
    fun adopt(option: CourseOption) {
        val c = option.course
        matched = option
        newPeriod = option.canStartNewPeriod
        childId = c.childId
        orgName = c.orgName
        courseName = c.courseName
        category = c.category
        teacher = c.teacher ?: ""
        location = c.location ?: ""
        weekdayMask = c.weekdayMask
        startTime = c.startTime
        // 课表整块换掉：按天的时间要跟着这门课走，留着上一门课的会串成一锅
        perDayTimes.clear()
        perDayTimes.putAll(c.seedTimes())
        perDayDurations.clear()
        perDayDurations.putAll(c.seedDurations())
        duration = c.durationMinutes.toString()
        threshold = c.warningThreshold.toString()
        remindBefore = c.remindBeforeMinutes
        if (option.lastLessonsHalf > 0) lessonsBought = Lessons.format(option.lastLessonsHalf)
        if (option.lastAmountCents > 0) amount = Money.plain(option.lastAmountCents)
    }

    fun clearMatch() {
        matched = null
        newPeriod = false
    }

    val trimmedOrg = orgName.trim()
    val trimmedName = courseName.trim()
    val showSchedule = matched == null || newPeriod
    val isMultipleChildren = children.size > 1

    // 这个孩子、同机构（机构还没填时就是全部）的课程，用来提示"这是续费，不是新开一门"
    val candidates = remember(trimmedOrg, trimmedName, options, matched, childId) {
        if (matched != null) emptyList()
        else options
            .filter { it.course.childId == childId }
            .filter { trimmedOrg.isBlank() || it.course.orgName == trimmedOrg }
            .filter { trimmedName.isBlank() || it.course.courseName.contains(trimmedName) }
            .take(6)
    }

    val title = when {
        matched == null -> "记一笔缴费"
        newPeriod -> "开始新一期"
        else -> "续费"
    }
    val confirmLabel = when {
        matched == null -> "保存缴费"
        newPeriod -> "开第 ${(matched?.period ?: 1) + 1} 期并记账"
        else -> "确认续费"
    }
    val canSubmit = (trimmedOrg.isNotBlank() || trimmedName.isNotBlank()) &&
        amountCents != null &&
        (lessonsHalf != null || matched != null) &&
        // 定金必须知道「抵多少」和「整期多少钱」，否则之后算不出尾款
        (!depositMode || (creditCents > 0 && totalAmountCents != null))

    FormSheet(
        title = title,
        onDismiss = onDismiss,
        confirmLabel = confirmLabel,
        confirmEnabled = canSubmit,
        onConfirm = {
            val child = childId.ifBlank { children.firstOrNull()?.id.orEmpty() }
            val selectedDays = (0..6).filter { weekdayMask and (1 shl it) != 0 }
            val dayTimes = perDayTimes.filterKeys { it in selectedDays }
            val dayDurations = perDayDurations.filterKeys { it in selectedDays }
            // 表单里没有「统一上课时间 / 单节时长」这两个默认值了，全按每天配的来。
            // 老字段（startTime / durationMinutes）只是兜底：取第一个配好的上课日，
            // 保证它永远等于「这门课实际怎么上」，不会再留一个和每天配置打架的名义默认值。
            val fallbackTime = selectedDays.firstNotNullOfOrNull { dayTimes[it] } ?: startTime
            val fallbackDuration = selectedDays.firstNotNullOfOrNull { dayDurations[it] }
                ?: duration.toIntOrNull() ?: 60
            val draft = CourseDraft(
                orgName = trimmedOrg,
                courseName = courseName,
                category = category,
                teacher = teacher.ifBlank { null },
                location = location.ifBlank { null },
                weekdayMask = weekdayMask,
                startTime = fallbackTime,
                // 按天的上课时间：只存勾着的那几天，全空就是 null
                startTimesJson = encodeStartTimes(dayTimes),
                durationMinutes = fallbackDuration,
                // 按天的时长也只存勾着的那几天，全空就是 null
                durationMinutesJson = encodeDurations(dayDurations),
                warningThreshold = threshold.toIntOrNull() ?: defaultThreshold,
                remindBeforeMinutes = remindBefore,
                status = CourseStatus.ACTIVE,
                note = "",
            )
            val pay = PaymentDraft(
                payDate = payDate ?: Dates.today(),
                amountCents = amountCents ?: 0L,
                originalAmountCents = Money.parseYuan(originalAmount)?.takeIf { it > 0 },
                lessonsBoughtHalf = lessonsHalf ?: 0L,
                lessonsBonusHalf = Lessons.parse(lessonsBonus) ?: 0L,
                payMethod = payMethod,
                validUntil = validUntil,
                receipts = receipts,
                note = note,
                // 定金走 DEPOSIT，之后补尾款那笔会以这条的 id 作为 groupKey，两笔归到同一批次
                kind = if (depositMode) PaymentKind.DEPOSIT else PaymentKind.FULL,
                creditCents = if (depositMode) creditCents else 0L,
            )
            val target = matched
            onSubmit(
                if (target == null) {
                    EnrollSubmit.NewCourse(child, draft, pay)
                } else {
                    EnrollSubmit.Renew(target.course, newPeriod, draft, pay)
                }
            )
        },
    ) {
        if (isMultipleChildren) {
            FieldLabel("给谁报")
            ChipSelector(
                options = children.map { it.id to it.displayName },
                selected = setOf(childId),
                onToggle = { id -> childId = id },
            )
            Gap(14)
        }

        // ---------------------------------------------- 什么课

        FieldLabel("机构 / 培训班")
        AppTextField(
            value = orgName,
            onValueChange = { value ->
                orgName = value
                // 机构被改掉，之前选中的课程就不再是"同一门课"了
                if (matched != null && value.trim() != matched?.course?.orgName) clearMatch()
            },
            label = "机构 / 培训班名称",
            placeholder = "如：星海钢琴艺术中心",
        )
        if (knownOrgs.isNotEmpty() && matched == null) {
            Gap(8)
            ChipSelector(
                options = knownOrgs.map { it to it },
                selected = emptySet(),
                onToggle = { orgName = it },
            )
        }
        if (trimmedOrg.isNotBlank() && knownOrgs.none { it.equals(trimmedOrg, ignoreCase = true) }) {
            Gap(6)
            Text(
                text = "「$trimmedOrg」将登记为新机构",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (matched == null) {
            Gap(12)
            FieldLabel("课程名称")
            AppTextField(
                value = courseName,
                onValueChange = { courseName = it },
                label = "课程名称",
                placeholder = "如：钢琴一对一",
            )

            if (candidates.isNotEmpty()) {
                Gap(12)
                FieldLabel("已经在记的课（点一下就是续费）")
                candidates.forEach { option ->
                    CandidateRow(option = option, onClick = { adopt(option) })
                }
            }
        } else {
            val option = matched!!
            Gap(12)
            MatchedCourseCard(
                option = option,
                newPeriod = newPeriod,
                onClear = { clearMatch() },
            )
        }

        // ---------------------------------------------- 续费方式

        if (matched != null) {
            val option = matched!!
            Gap(14)
            if (option.canStartNewPeriod) {
                // 课时已经上完（或这门课已被标为结束）——这才是真正的分期点，
                // 开新一期只是给同一门课的下一段建一份新档案，旧期本来就该归档
                FieldLabel("这一期已经上完了")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = newPeriod,
                        onClick = { newPeriod = true },
                        label = { Text("开第 ${option.period + 1} 期") },
                    )
                    FilterChip(
                        selected = !newPeriod,
                        onClick = { newPeriod = false },
                        label = { Text("接着算在这期") },
                    )
                }
                Gap(6)
                Text(
                    text = "第 ${option.period} 期会归档，课表和单价冻结不再改动；" +
                        "新一期从第 ${option.period + 1} 期开始，下面可以改课表、换老师。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                // 还有余额：这一期没结束，就不该有「开新一期」这个口子。
                // 之前这里给了选项，一选旧课就被冻住，剩下的课时既上不了又还占着余额。
                Text(
                    text = "第 ${option.period} 期还剩 ${Lessons.format(option.remainingHalf)} 节，" +
                        "这笔课时会累加到这门课，课表不变。等这一期上完，再续费时就会自动进入下一期。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        // ---------------------------------------------- 钱和课时

        SectionTitle("这一笔交了多少")
        DateField(label = "缴费日期", date = payDate, onChange = { payDate = it })
        Gap(12)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = amount,
                onValueChange = { amount = it.onlyDecimal() },
                label = if (depositMode) "定金实付" else "实付金额",
                suffix = "元",
                keyboardType = KeyboardType.Decimal,
                isError = amount.isNotEmpty() && amountCents == null,
                modifier = Modifier.weight(1f),
            )
            AppTextField(
                value = originalAmount,
                onValueChange = { originalAmount = it.onlyDecimal() },
                label = if (depositMode) "整期总价" else "原价",
                suffix = "元",
                keyboardType = KeyboardType.Decimal,
                modifier = Modifier.weight(1f),
            )
        }
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
        // 定金模式下实付只是整期的一部分，拿它除整期课时算出来的单价是错的，先不显示
        if (totalLessons > 0 && amountCents != null && amountCents > 0 && !depositMode) {
            val unit = amountCents * 2 / totalLessons
            Text(
                text = "本批次共 ${Lessons.format(totalLessons)} 节，单价约 ${Money.format(unit)}/节",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
        Gap(14)
        // 定金开关放在金额/课时这一片里，交钱当下就能看到「只先付一部分」这个选项，
        // 不用再去课程详情的 ⋮ 菜单里翻
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
            Gap(8)
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
        Gap(14)
        FieldLabel("支付方式")
        ChipSelector(
            options = PayMethod.entries.map { it.name to it.label },
            selected = setOf(payMethod.name),
            onToggle = { payMethod = PayMethod.of(it) },
        )
        // 有效期和票据照片留到补尾款那一步再补，定金这里先不摊开
        if (!depositMode) {
            Gap(14)
            FieldLabel("课时有效期（可空 = 长期有效）")
            DateField(label = "有效期", date = validUntil, allowClear = true, onChange = { validUntil = it })
            Gap(14)
            FieldLabel("票据照片")
            ReceiptPicker(receipts = receipts, onChange = { receipts = it })
        }
        Gap(12)
        AppTextField(value = note, onValueChange = { note = it }, label = "备注", singleLine = false)

        // ---------------------------------------------- 课表

        if (showSchedule) {
            SectionTitle(
                title = if (matched == null) "什么时候上课" else "新一期的课表",
                hint = "不知道可以先跳过，之后在课程里补",
            )
            FieldLabel("每周上课日（可多选）")
            ChipSelector(
                options = Dates.weekdayNames.mapIndexed { index, label ->
                    index.toString() to label.removePrefix("周")
                },
                selected = (0..6).filter { weekdayMask and (1 shl it) != 0 }.map { it.toString() }.toSet(),
                onToggle = { key ->
                    val bit = 1 shl key.toInt()
                    weekdayMask = weekdayMask xor bit
                    // 取消勾选这天，它单独设的时间和时长都得删掉，
                    // 不然会存下「没选周三却有周三 16:30 / 90 分钟」的脏数据
                    if (weekdayMask and bit == 0) {
                        perDayTimes.remove(key.toInt())
                        perDayDurations.remove(key.toInt())
                    }
                },
            )
            // 选了哪几天，就给哪几天各配一行「周X + 时间 + 时长」。
            // 这里刻意没有「统一时间 / 单节时长」的默认值：一门课只配一次，
            // 有几天就配几行，看得见的都是实际生效的，不用再去猜某天到底跟的是哪个默认值。
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
                            // 清掉改成「没设」，不存空串进去
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
            Gap(12)
            AppTextField(value = teacher, onValueChange = { teacher = it }, label = "老师", placeholder = "选填")
            Gap(12)
            AppTextField(value = location, onValueChange = { location = it }, label = "上课地点", placeholder = "选填")
            Gap(14)
            FieldLabel("科目")
            ChipSelector(
                options = Category.entries.map { it.name to it.label },
                selected = setOf(category.name),
                onToggle = { category = Category.of(it) },
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, hint: String? = null) {
    Column(modifier = Modifier.padding(top = 20.dp, bottom = 10.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
        )
        if (hint != null) {
            Text(
                text = hint,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** 候选课程行：一眼看出"这门课还剩几节"，点了就进续费 */
@Composable
private fun CandidateRow(option: CourseOption, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(scheme.surfaceVariant.copy(alpha = 0.45f))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = option.course.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                if (option.period > 1) {
                    Text(
                        text = " · 第 ${option.period} 期",
                        style = MaterialTheme.typography.labelSmall,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
            Text(
                text = buildString {
                    append(option.course.orgName.ifBlank { option.course.category.label })
                    append(" · ")
                    append(
                        if (option.exhausted) "课时已用完"
                        else "还剩 ${Lessons.format(option.remainingHalf)} 节"
                    )
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (option.exhausted) scheme.onSurfaceVariant else scheme.primary,
            )
        }
        Text(
            text = if (option.exhausted) "续费" else "接着买",
            style = MaterialTheme.typography.labelMedium,
            color = scheme.primary,
        )
    }
}

/** 已经选定的续费目标：给一眼能看懂的上期摘要，避免续错课 */
@Composable
private fun MatchedCourseCard(
    option: CourseOption,
    newPeriod: Boolean,
    onClear: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.primary.copy(alpha = 0.10f))
            .padding(start = 14.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = option.course.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = option.course.orgName.ifBlank { option.course.category.label },
                    style = MaterialTheme.typography.labelSmall,
                    color = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onClear) {
                Icon(
                    Icons.Rounded.Close,
                    contentDescription = "不是这门课",
                    modifier = Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }
        Text(
            text = buildString {
                append("第 ${option.period} 期")
                append(" · 已上 ${option.stats.doneCount} 次")
                append(
                    if (option.exhausted) " · 课时已用完"
                    else " · 还剩 ${Lessons.format(option.remainingHalf)} 节"
                )
                if (newPeriod) append(" · 记到第 ${option.period + 1} 期") else append(" · 课时累加在这期")
            },
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

private fun String.onlyDecimal(): String {
    val filtered = filter { it.isDigit() || it == '.' }
    val firstDot = filtered.indexOf('.')
    return if (firstDot < 0) filtered
    else filtered.substring(0, firstDot + 1) + filtered.substring(firstDot + 1).replace(".", "")
}

/**
 * 把课程的时间配置摊成「每个上课日各一条」。
 *
 * 表单里去掉了「统一上课时间」这个默认值，但老数据只有那一个值。
 * 直接显示 startTimes 会让老课程看起来一片空白，用户以为配置丢了，
 * 所以这里把统一值铺到每个上课日上——打开表单看到的就是实际生效的时间。
 */
internal fun Course.seedTimes(): Map<Int, String> {
    val days = (0..6).filter { weekdayMask and (1 shl it) != 0 }
    val fallback = startTime
    return buildMap {
        putAll(startTimes)
        if (fallback != null) days.forEach { if (this[it] == null) put(it, fallback) }
    }
}

/** 同上，时长版本：老课程没有按天时长，就用课程的单节时长铺满 */
internal fun Course.seedDurations(): Map<Int, Int> {
    val days = (0..6).filter { weekdayMask and (1 shl it) != 0 }
    return buildMap {
        putAll(durations)
        days.forEach { if (this[it] == null) put(it, durationMinutes) }
    }
}
