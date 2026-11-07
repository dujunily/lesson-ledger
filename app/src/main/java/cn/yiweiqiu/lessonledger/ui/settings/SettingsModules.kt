package cn.yiweiqiu.lessonledger.ui.settings

import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Science
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.DataSource
import cn.yiweiqiu.lessonledger.domain.DemoStats
import cn.yiweiqiu.lessonledger.data.sync.SyncPhase
import cn.yiweiqiu.lessonledger.data.update.UpdateCheck
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.AppTextField
import cn.yiweiqiu.lessonledger.ui.components.ColorDot
import cn.yiweiqiu.lessonledger.ui.components.EntryRow
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.pageBottomInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.forms.remindBeforeShortLabel
import cn.yiweiqiu.lessonledger.ui.theme.AccentTheme
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors
import cn.yiweiqiu.lessonledger.ui.theme.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 设置的模块子页：设置首页只列模块，点进来才是模块内的配置。
 *
 * 每页各自 new 一个 SettingsViewModel 实例，但底层 SettingsStore 是单例，
 * 所以状态天然共享，不需要在页面之间传递任何东西。
 */

@Composable
private fun SubPageScaffold(
    title: String,
    subtitle: String,
    onBack: () -> Unit,
    content: LazyListScope.() -> Unit,
) {
    // 三级页（外观 / 提醒 / 同步 / 规则 / 数据 / 关于）统一走这套：
    // 顶栏是浮层，列表从它下面滑过（沉浸式状态栏），底部让开小白条。
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp)
                    // Android 15+ 强制 edge-to-edge 之后，adjustResize 不再把窗口缩小，
                    // 键盘会直接盖住页面下半部分——「保存名字」按钮就点不到了。
                    .imePadding(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topBarInset() + 4.dp,
                    bottom = pageBottomInset(32.dp),
                ),
            ) {
                content()
            }
        }
        GlassTopBar(
            title = title,
            subtitle = subtitle,
            modifier = Modifier.align(Alignment.TopCenter),
            leading = { GlassBackButton(onBack) },
            scrollBack = { listState.canScrollBackward },
        )
    }
}

// ---------------------------------------------------------------- 外观

@Composable
fun AppearanceScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // 「跟随系统」色卡上直接显示系统当前的动态色，所见即所得
    val systemSwatch = remember(context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                androidx.compose.material3.dynamicLightColorScheme(context).primary
            }.getOrElse { AccentTheme.SYSTEM.swatch }
        } else {
            AccentTheme.SYSTEM.swatch
        }
    }

    SubPageScaffold(title = "外观", subtitle = "主题色只存在本机，不参与同步", onBack = onBack) {
        item(key = "appearance") {
            AppCard {
                Text(
                    text = "主题色",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(12.dp))
                // 9 个色卡放一行会挤爆，用可横滑的列表
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    items(AccentTheme.entries, key = { it.key }) { accent ->
                        Box(
                            modifier = Modifier
                                .clip(CircleShape)
                                .clickable { vm.setAccent(accent.key) }
                                .padding(2.dp)
                                .semantics { contentDescription = "主题色 ${accent.label}" },
                        ) {
                            ColorDot(
                                color = if (accent == AccentTheme.SYSTEM) systemSwatch else accent.swatch,
                                selected = settings.accentKey == accent.key,
                                size = 32,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "当前：主题 ${AccentTheme.of(settings.accentKey).label} · " +
                        "明暗 ${ThemeMode.of(settings.themeMode).label}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = "深色模式",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeMode.entries.forEach { mode ->
                        FilterChip(
                            selected = settings.themeMode == mode.key,
                            onClick = { vm.setThemeMode(mode.key) },
                            label = { Text(mode.label) },
                        )
                    }
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "状态栏和导航栏会跟着主题自动调和明暗，界面内容会从玻璃条下面滑过。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 上课提醒

@Composable
fun ReminderScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    // 通知 / 精确闹钟权限状态：进页面和从系统设置返回时都要重新读一次
    var notificationAllowed by remember { mutableStateOf(vm.canNotify()) }
    var exactAlarmAllowed by remember { mutableStateOf(vm.exactAlarmAllowed()) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        notificationAllowed = granted
        if (granted) vm.setClassReminderEnabled(true)
    }

    LifecycleResumeEffect(Unit) {
        notificationAllowed = vm.canNotify()
        exactAlarmAllowed = vm.exactAlarmAllowed()
        onPauseOrDispose { }
    }

    fun openAppNotificationSettings() {
        runCatching {
            context.startActivity(
                android.content.Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, context.packageName)
            )
        }
    }

    /**
     * 还能不能弹系统授权框：
     * 没申请过 → 能；申请过一次但用户拒绝过 → 还能再问；
     * 申请过且"不再询问" → 只能去系统设置里手动打开。
     */
    fun canAskSystemPermission(): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (!settings.notificationAsked) return true
        return context.findActivity()?.shouldShowRequestPermissionRationale(
            android.Manifest.permission.POST_NOTIFICATIONS
        ) ?: false
    }

    SubPageScaffold(title = "上课提醒", subtitle = "早上汇总今天要上的课，上课时提醒打卡", onBack = onBack) {
        item(key = "reminder") {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("上课时提醒打卡", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "到上课那一刻发一条，点一下直接去打卡",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.classReminderEnabled,
                        onCheckedChange = { vm.setClassReminderEnabled(it) },
                    )
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("每天早上汇总", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "10:00 告诉你今天几节课、几点、在哪，提前安排接送",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.dailyDigestEnabled,
                        onCheckedChange = { vm.setDailyDigestEnabled(it) },
                    )
                }
                if (settings.dailyDigestEnabled) {
                    TextButton(
                        onClick = {
                            vm.previewDigest { count ->
                                val message = if (count > 0) {
                                    "已发送今天的汇总（$count 节）"
                                } else {
                                    "今天没有要上的课，不会发通知"
                                }
                                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 0.dp, vertical = 0.dp),
                    ) {
                        Text("发一条看看效果", style = MaterialTheme.typography.labelMedium)
                    }
                }

                if (settings.classReminderEnabled || settings.dailyDigestEnabled) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "不再按「提前 15 分钟 / 半小时 / 2 小时」逐节提醒——提前太早人往往已经在路上，" +
                            "一天响好几遍反而容易静音。单门课不想被提醒，可以在「编辑课程」里关掉。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }

                if (!notificationAllowed) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "通知权限未开启",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.warning,
                            )
                            Text(
                                "Android 13 以上必须授权才能收到提醒，否则提醒不会弹出。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                if (canAskSystemPermission()) {
                                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    openAppNotificationSettings()
                                }
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) { Text("去授权") }
                    }
                }

                if (!exactAlarmAllowed) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "精确闹钟未授权",
                                style = MaterialTheme.typography.bodyMedium,
                                color = ext.warning,
                            )
                            Text(
                                "没有精确闹钟权限时，提醒可能会晚十几分钟才到。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        android.content.Intent(
                                            android.provider.Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                                            Uri.parse("package:${context.packageName}"),
                                        )
                                    )
                                }
                            },
                            modifier = Modifier.heightIn(min = 44.dp),
                        ) { Text("去设置") }
                    }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 同步与备份

/** 收起态的账号行：只摆邮箱和连接状态，改密码要按「修改」才展开 */
@Composable
private fun ConnectedAccountRow(
    email: String,
    subtitle: String,
    onEdit: () -> Unit,
) {
    val ext = LocalExtendedColors.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(RoundedCornerShape(11.dp))
                .background(ext.successContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.CloudSync,
                contentDescription = null,
                tint = ext.success,
                modifier = Modifier.size(18.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = email,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TextButton(onClick = onEdit, modifier = Modifier.heightIn(min = 44.dp)) {
            Text("修改")
        }
    }
}

/** 收起态那一行下面的状态说明：说清「通没通、上次什么时候」，而不是只写「已连接」 */
private fun connectSubtitle(lastSyncAt: Long, phase: SyncPhase): String = when {
    phase == SyncPhase.SYNCING -> "正在同步…"
    phase == SyncPhase.SERVER_ERROR -> "上次同步没成功，点下方「同步」重试"
    lastSyncAt > 0 -> "已连接 · 上次成功 ${Dates.relative(lastSyncAt)}"
    else -> "已配置，还没完成第一次同步"
}

/**
 * 同步卡片里那排按钮的文字：统一样式、强制单行。
 * 三格等宽，字号与行高一致，才不会因为字数不同（4 字 / 2 字）显得高矮不齐。
 */
@Composable
private fun ActionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        maxLines = 1,
    )
}

@Composable
fun SyncSettingsScreen(
    onBack: () -> Unit,
    onOpenSyncDetail: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val status by vm.syncState.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val ext = LocalExtendedColors.current

    var email by remember(settings.email) { mutableStateOf(settings.email) }
    var password by remember(settings.appPassword) { mutableStateOf(settings.appPassword) }
    var deviceName by remember(settings.deviceName) { mutableStateOf(settings.deviceName) }
    var passwordVisible by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var passphrase by remember(settings.backupPassphrase) { mutableStateOf(settings.backupPassphrase) }
    val keyboard = LocalSoftwareKeyboardController.current

    // 账号配好并且连通过之后就收起来，只留邮箱；要改再点「修改」。
    // 认证失败时强制展开 —— 那时候用户唯一想做的就是去改密码。
    val accountReady = settings.isConfigured && status.phase != SyncPhase.AUTH_FAILED
    var editingAccount by remember(accountReady) { mutableStateOf(!accountReady) }

    // 改名只是给这台手机贴个标签，用来在另一台手机上认出「谁最后改的」，不碰任何记录。
    // 存完必须给一句回声：之前点完「保存名字」什么都不发生，输入框看着也没变，
    // 用起来就像「名字不能改」。
    val commitDeviceName = {
        val trimmed = deviceName.trim()
        vm.saveDeviceName(trimmed)
        deviceName = trimmed
        keyboard?.hide()
        Toast.makeText(
            context,
            if (trimmed.isBlank()) "已恢复默认机型名" else "已保存：另一台手机上会显示「$trimmed」",
            Toast.LENGTH_SHORT,
        ).show()
    }

    SubPageScaffold(title = "同步与备份", subtitle = "坚果云账号、家庭共享与备份加密", onBack = onBack) {
        item(key = "cloud-header") {
            SectionHeader(title = "坚果云同步", subtitle = "数据存在你自己的坚果云里，没有自建服务器")
        }
        item(key = "cloud") {
            AppCard {
                if (accountReady && !editingAccount) {
                    ConnectedAccountRow(
                        email = settings.email,
                        subtitle = connectSubtitle(settings.lastSyncAt, status.phase),
                        onEdit = { editingAccount = true },
                    )
                } else {
                if (status.phase == SyncPhase.AUTH_FAILED) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ext.dangerContainer)
                            .padding(12.dp),
                    ) {
                        Column {
                            Text(
                                "认证失败（401）",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = ext.danger,
                            )
                            Text(
                                "坚果云 WebDAV 必须使用「应用密码」，不是登录密码。点下方按钮查看获取步骤。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }

                AppTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = "坚果云账号（邮箱）",
                    placeholder = "you@example.com",
                    keyboardType = KeyboardType.Email,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("WebDAV 应用密码") },
                    singleLine = true,
                    visualTransformation = if (passwordVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                if (passwordVisible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "显示密码",
                            )
                        }
                    },
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = "应用密码在坚果云网页端「账户信息 → 安全选项」里生成，不是登录密码。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                TextButton(
                    onClick = { showHelp = true },
                    contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                ) { Text("怎么获取应用密码？") }
                Spacer(Modifier.height(10.dp))
                // 目录不让填：两台手机目录不一致就等于两份账本，这是家庭共享最容易踩的坑
                Text(
                    text = "远端目录固定为 /keshibu/，不用填。两台手机必须指向同一个目录，否则会各记各的。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(14.dp))
                // 三个动作排成一行：等宽、等高、文字同基线，靠「实心 / 描边 / 纯文字」区分主次。
                // 之前「取消」是单独挂在下面的一个左对齐 TextButton，既不对齐也没点按面积。
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = {
                            vm.saveAccount(email, password)
                            keyboard?.hide()
                            Toast.makeText(context, "账号已保存", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { ActionLabel("保存账号") }
                    OutlinedButton(
                        onClick = {
                            vm.saveAccount(email, password)
                            vm.testConnection()
                            keyboard?.hide()
                        },
                        enabled = testResult?.loading != true,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp),
                    ) { ActionLabel(if (testResult?.loading == true) "测试中…" else "测试连接") }
                    if (accountReady) {
                        TextButton(
                            onClick = {
                                // 取消要连输入框一起还原，否则下次点「同步」会把改了一半的邮箱存进去
                                email = settings.email
                                password = settings.appPassword
                                editingAccount = false
                                keyboard?.hide()
                            },
                            modifier = Modifier
                                .weight(1f)
                                .heightIn(min = 48.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp),
                        ) { ActionLabel("取消") }
                    }
                }

                val result = testResult
                if (result != null) {
                    Spacer(Modifier.height(10.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(
                                if (result.ok) ext.successContainer else ext.dangerContainer
                            )
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (result.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.primary,
                            )
                            Spacer(Modifier.width(8.dp))
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = result.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (result.ok) ext.success else ext.danger,
                            )
                            if (result.ok && result.quotaAvailable != null) {
                                Text(
                                    text = "可用 ${formatBytes(result.quotaAvailable)} · 已用 ${
                                        formatBytes(result.quotaUsed ?: 0L)
                                    }",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    }
                }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("立即同步", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "上次成功：${
                                if (settings.lastSyncAt > 0) Dates.relative(settings.lastSyncAt)
                                else "从未同步"
                            }",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = {
                            // 收起时 email/password 就是已存的值，不必再写一遍；
                            // 展开时（改完没点保存）才要先落盘，否则同步的还是旧账号。
                            if (editingAccount || !settings.isConfigured) vm.saveAccount(email, password)
                            vm.syncNow()
                        },
                        enabled = status.phase != SyncPhase.SYNCING,
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) {
                        Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("同步")
                    }
                }

                // 顶部那个胶囊只有四个字，说不清卡在哪一步；失败原因写在这里，
                // 免得「同步冲突」看起来像是账本里真的有两份数据打起来了。
                if (status.phase != SyncPhase.SYNCED && status.phase != SyncPhase.SYNCING &&
                    status.message.isNotBlank()
                ) {
                    Spacer(Modifier.height(10.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(ext.warningContainer)
                            .padding(12.dp),
                    ) {
                        Text(
                            text = status.message,
                            style = MaterialTheme.typography.bodySmall,
                            color = ext.warning,
                        )
                        if (status.pendingCount > 0) {
                            Text(
                                text = "本机还有 ${status.pendingCount} 条改动没传上去，下次同步会继续。",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Text(
                    text = "云端第一次是空的：第一次同步会把本机这份传上去，从第二台手机开始才是两边合并。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("仅 Wi-Fi 同步", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "后台每 6 小时同步一次，开启后只在 Wi-Fi 下进行",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = settings.syncWifiOnly,
                        onCheckedChange = { vm.setWiFiOnly(it) },
                    )
                }
                Spacer(Modifier.height(6.dp))
                TextButton(
                    onClick = onOpenSyncDetail,
                    contentPadding = PaddingValues(0.dp),
                ) { Text("查看同步详情与日志 →") }
            }
        }

        item(key = "family-header") {
            SectionHeader(
                title = "家庭共享",
                subtitle = "两台手机填同一个坚果云账号，账本自动互通",
            )
        }
        item(key = "family") {
            AppCard {
                OutlinedTextField(
                    value = deviceName,
                    onValueChange = { deviceName = it },
                    label = { Text("这台手机的名字") },
                    placeholder = { Text("例如：爸爸的手机 / 妈妈的手机") },
                    singleLine = true,
                    // 键盘右下角直接是「完成」：不用再去够页面里那颗按钮
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { commitDeviceName() }),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (settings.remoteDeviceName.isBlank()) {
                                "云端暂时没有别的设备写入"
                            } else {
                                "云端最后由「${settings.remoteDeviceName}」写入"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        Text(
                            text = if (settings.remoteWriteAt > 0) {
                                Dates.relative(settings.remoteWriteAt) + " · 打开 App 会自动拉取"
                            } else {
                                "两台手机都配置好之后，这里会显示对方最近一次同步的时间"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedButton(
                        onClick = { commitDeviceName() },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) { Text("保存名字") }
                }

                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Text(
                    "两个人共用一份账本，只要满足三条：",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(6.dp))
                FamilyStep("1", "同一个坚果云账号", "两台手机都填同一个坚果云邮箱；应用密码各申请一个或共用一个都行，它们指向同一个网盘。")
                FamilyStep("2", "不用管目录", "远端固定用 /keshibu/，两台手机都一样，所以这个不用填、也不会填错。")
                FamilyStep("3", "打开 App 就会自动汇合", "改完 8 秒自动上传；对方打开 App 立即拉取，前台停留时每 2 分钟自动比对一次云端。")
                Spacer(Modifier.height(8.dp))
                Text(
                    "同一条记录被两人同时改：以最后修改的为准；若两边在 5 秒内改成不同内容，会各留一条，" +
                        "冲突的那条加「[冲突副本]」前缀，在同步详情里能看到。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "encrypt-header") {
            SectionHeader(title = "备份加密", subtitle = "默认关闭")
        }
        item(key = "encrypt") {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "开启后 data.json 用口令加密（AES-GCM）",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.backupEncrypted,
                        onCheckedChange = { vm.setBackupEncryption(it, passphrase) },
                    )
                }
                Text(
                    text = "提示：坚果云服务端能看到未加密文件的内容，加密仅在你主动开启后生效。" +
                        "口令不会上传，忘记口令将无法恢复远端数据。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (settings.backupEncrypted) {
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("备份口令") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = {
                            vm.setBackupEncryption(true, passphrase)
                            if (passphrase.isNotBlank()) vm.syncNow()
                        },
                        modifier = Modifier.heightIn(min = 44.dp),
                    ) { Text("保存口令") }
                }
            }
        }
    }

    if (showHelp) {
        AppPasswordHelpDialog(
            onDismiss = { showHelp = false },
            onOpenWeb = {
                runCatching {
                    context.startActivity(
                        android.content.Intent(
                            android.content.Intent.ACTION_VIEW,
                            Uri.parse("https://help.jianguoyun.com/?p=2064"),
                        )
                    )
                }
                showHelp = false
            },
        )
    }
}

// ---------------------------------------------------------------- 记账规则

@Composable
fun RulesScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    var budgetText by remember(settings.monthlyBudgetCents) {
        mutableStateOf(settings.monthlyBudgetCents?.let { Money.plain(it) } ?: "")
    }

    SubPageScaffold(title = "记账规则", subtitle = "扣课口径、提醒阈值与预算", onBack = onBack) {
        item(key = "rules") {
            AppCard {
                Text("默认剩余课时提醒阈值", style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(3, 5, 8, 10).forEach { value ->
                        FilterChip(
                            selected = settings.defaultThreshold == value,
                            onClick = { vm.setDefaultThreshold(value) },
                            label = { Text("$value 节") },
                        )
                    }
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "缺勤也扣课时",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.absentDeducts,
                        onCheckedChange = { vm.setDeductionRules(it, settings.leaveDeducts) },
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "请假也扣课时",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = settings.leaveDeducts,
                        onCheckedChange = { vm.setDeductionRules(settings.absentDeducts, it) },
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = MaterialTheme.colorScheme.outlineVariant,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("月支出预算", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "留空表示不设置",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    OutlinedTextField(
                        value = budgetText,
                        onValueChange = {
                            budgetText = it.filter { ch -> ch.isDigit() || ch == '.' }
                            vm.setMonthlyBudget(budgetText)
                        },
                        suffix = { Text("元") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.width(140.dp),
                    )
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 数据

@Composable
fun DataScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val message by vm.message.collectAsStateWithLifecycle()
    val sourceState by vm.sourceState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showClearConfirm by remember { mutableStateOf(false) }
    var clearText by remember { mutableStateOf("") }
    var exportCache by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json"),
    ) { uri ->
        val content = exportCache
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
        exportCache = null
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val text = withContext(Dispatchers.IO) { readText(context, uri) }
                if (text != null) vm.importJson(text)
            }
        }
    }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { vm.clearMessage() },
            title = { Text("提示") },
            text = { Text(message ?: "") },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("知道了") } },
        )
    }

    SubPageScaffold(title = "数据", subtitle = "备份、恢复与清空", onBack = onBack) {
        item(key = "data") {
            AppCard {
                Button(
                    onClick = {
                        vm.buildExportJson { text ->
                            exportCache = text
                            val tag = if (sourceState.current.isDemo) "演示数据" else "我的数据"
                            exportLauncher.launch(
                                "课外班管家-$tag-${Dates.formatDate(Dates.today())}.json"
                            )
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("导出「${sourceState.current.label}」（JSON）")
                }
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("application/json", "text/plain", "*/*")) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 48.dp),
                ) {
                    Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("从备份导入")
                }
                Spacer(Modifier.height(14.dp))
                Text(
                    text = if (sourceState.demoEnabled) {
                        "导出、导入、清空都只作用于「${sourceState.current.label}」，另一套一条都不会动。" +
                            "备份文件里也只有这一套。"
                    } else {
                        "当前正在使用「我的数据」：导出、导入、清空都只作用于这一套。"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(
                    onClick = { showClearConfirm = true },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        "清空「${sourceState.current.label}」的数据",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }

    if (showClearConfirm) {
        AlertDialog(
            onDismissRequest = { showClearConfirm = false; clearText = "" },
            title = { Text("清空「${sourceState.current.label}」？") },
            text = {
                Column {
                    Text(
                        "这一套里的孩子、课程、缴费、上课记录都会被删除，且无法恢复。" +
                            "另一套数据不受影响。"
                    )
                    Spacer(Modifier.height(12.dp))
                    Text("请输入「确认删除」以继续：", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(6.dp))
                    OutlinedTextField(
                        value = clearText,
                        onValueChange = { clearText = it },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        vm.clearCurrentSource()
                        showClearConfirm = false
                        clearText = ""
                    },
                    enabled = clearText.trim() == "确认删除",
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirm = false; clearText = "" }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 关于

@Composable
fun AboutScreen(
    onBack: () -> Unit,
    vm: UpdateViewModel = hiltViewModel(),
) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scheme = MaterialTheme.colorScheme
    var showPublish by remember { mutableStateOf(false) }
    var publishNotes by remember { mutableStateOf("") }

    // 从「安装未知应用」授权页回来时，权限状态会变，这里刷新一下
    LifecycleResumeEffect(Unit) {
        vm.refreshPermission()
        onPauseOrDispose { }
    }

    val available = state.check as? UpdateCheck.Available
    val upToDate = state.check as? UpdateCheck.UpToDate
    val rows = listOf(
        "记录每个孩子的课程、缴费与每次上课，课时和钱都算得清楚" to
            "1 节课 = 2 个半节；金额一律按分计算，不出现浮点误差。",
        "数据不出你的口袋" to
            "本地优先存储，配置坚果云后同步到自己的网盘；两台手机填同一账号即可共用一份账本。",
        "桌面小组件" to
            "今日打卡、今日课程、下一节课、快捷打卡四种组件，不打开 App 也能一键打卡。",
    )
    SubPageScaffold(title = "关于", subtitle = "课时簿 · 本地优先的课外班管家", onBack = onBack) {
        // 版本 + 更新：更新包不走应用商店，走自己坚果云里的 update/ 目录
        item(key = "version") {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "课时簿 v${state.versionName}",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = when {
                                state.checking -> "正在检查…"
                                available != null -> "有新版本 v${available.info.versionName}"
                                // 三种"没有更新"要分开说：没发过 / 一样新 / 本机更靠前
                                upToDate != null -> when (val remote = upToDate.remote) {
                                    null -> "云端还没有发布过版本"
                                    else -> if (remote.versionName == state.versionName) {
                                        "已是最新版本 · 云端 v${remote.versionName}"
                                    } else {
                                        "云端 v${remote.versionName} · 本机更新，可发布覆盖"
                                    }
                                }
                                state.check is UpdateCheck.NotConfigured -> "配置坚果云后可检查更新"
                                else -> "更新包放在你自己的坚果云里"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (available != null) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                    if (state.checking) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                        )
                    } else {
                        OutlinedButton(
                            onClick = { vm.check() },
                            enabled = !state.downloading && !state.publishing,
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 34.dp),
                        ) { Text("检查更新", style = MaterialTheme.typography.labelMedium) }
                    }
                }

                if (available != null) {
                    Spacer(Modifier.height(12.dp))
                    HorizontalDivider(color = scheme.outlineVariant.copy(alpha = 0.5f))
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "新版本 ${available.info.versionName}" +
                            if (available.info.size > 0) " · ${available.info.size / 1024} KB" else "",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    if (available.info.notes.isNotBlank()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = available.info.notes,
                            style = MaterialTheme.typography.bodySmall,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    if (state.downloading) {
                        LinearProgressIndicator(
                            progress = { state.progress },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = "正在下载 ${(state.progress * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                        )
                    } else {
                        Button(
                            onClick = {
                                // 没给「安装未知应用」权限时，先跳授权页；
                                // 回来再点一次即可，不做静默失败
                                if (state.canInstall) {
                                    vm.downloadAndInstall()
                                } else {
                                    runCatching { context.startActivity(vm.installPermissionIntent()) }
                                        .onFailure { Toast.makeText(context, "打不开授权页，请到系统设置里手动允许", Toast.LENGTH_LONG).show() }
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                            modifier = Modifier.heightIn(min = 36.dp),
                        ) {
                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = if (state.canInstall) "下载并安装" else "先允许安装应用",
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }

                state.message?.let { message ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (state.isError) scheme.error else scheme.primary,
                    )
                }
            }
        }
        // LazyColumn 的 item 之间默认零间距，两张卡片会贴死叠边，这里手动补一个间隔
        item(key = "about-gap") { Spacer(Modifier.height(12.dp)) }
        item(key = "about") {
            AppCard {
                rows.forEach { (title, body) ->
                    Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    Spacer(Modifier.height(2.dp))
                    Text(
                        body,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(10.dp))
                }
            }
        }
        item(key = "about-gap2") { Spacer(Modifier.height(12.dp)) }
        item(key = "privacy") {
            AppCard {
                Text(
                    "隐私说明",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "App 不收集任何个人信息，没有埋点、没有广告。唯一的网络请求是你自己配置的坚果云 WebDAV，",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    "账号与应用密码用 Android Keystore 加密保存在本机，不会出现在任何日志里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        // 自用分发：把「现在跑的这一版」传上去，另一台手机才有得更新。
        // 普通用户用不到，所以放在最后，且只出现在配置了同步的机器上。
        if (state.configured) {
            item(key = "dev-header") {
                Spacer(Modifier.height(14.dp))
                SectionHeader(title = "分发", subtitle = "自己两台手机之间传安装包")
            }
            item(key = "publish") {
                AppCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "把这一版发布到云端",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                "上传本机正在运行的 v${state.versionName} 到坚果云 update/，覆盖上一版；" +
                                    "另一台手机「检查更新」就能拿到。",
                                style = MaterialTheme.typography.bodySmall,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.width(8.dp))
                        TextButton(
                            onClick = { showPublish = true },
                            enabled = !state.publishing,
                        ) {
                            if (state.publishing) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(16.dp),
                                    strokeWidth = 2.dp,
                                )
                            } else {
                                Icon(Icons.Rounded.Upload, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("发布", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }
        }
    }

    if (showPublish) {
        AlertDialog(
            onDismissRequest = { showPublish = false },
            icon = { Icon(Icons.Rounded.Upload, contentDescription = null) },
            title = { Text("发布 v${state.versionName}") },
            text = {
                Column {
                    Text(
                        "把本机正在运行的安装包传到坚果云 update/，会覆盖云端上一版。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Spacer(Modifier.height(10.dp))
                    OutlinedTextField(
                        value = publishNotes,
                        onValueChange = { publishNotes = it },
                        label = { Text("更新说明（可空）") },
                        placeholder = { Text("这次改了什么，会显示在另一台手机上") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.publish(publishNotes)
                    showPublish = false
                    publishNotes = ""
                }) { Text("发布") }
            },
            dismissButton = {
                TextButton(onClick = { showPublish = false }) { Text("取消") }
            },
        )
    }
}

// ---------------------------------------------------------------- 共用小组件

@Composable
private fun AppPasswordHelpDialog(onDismiss: () -> Unit, onOpenWeb: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.Lock, contentDescription = null) },
        title = { Text("获取坚果云「应用密码」") },
        text = {
            Column {
                Text("WebDAV 不能用登录密码，必须单独生成一个应用密码：")
                Spacer(Modifier.height(8.dp))
                Text("1. 电脑浏览器登录 jianguoyun.com")
                Text("2. 右上角账户信息 → 安全选项")
                Text("3. 找到「第三方应用管理」→ 添加应用")
                Text("4. 名称填「课外班管家」，生成密码")
                Text("5. 把这串密码填到上面（不是登录密码）")
                Spacer(Modifier.height(8.dp))
                Text(
                    "该密码只在本机用 Android Keystore 加密保存，不会出现在任何日志里。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = { TextButton(onClick = onOpenWeb) { Text("打开帮助页") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun FamilyStep(step: String, title: String, body: String) {
    Row(modifier = Modifier.padding(top = 8.dp)) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                step,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
        Spacer(Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                body,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun readText(context: android.content.Context, uri: Uri): String? = try {
    context.contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
} catch (e: Exception) {
    null
}

private fun formatBytes(bytes: Long): String {
    val gb = bytes / 1024.0 / 1024.0 / 1024.0
    return if (gb >= 1) String.format("%.1f GB", gb) else String.format("%.0f MB", gb * 1024)
}

/** Compose 的 LocalContext 可能是 ContextWrapper 套壳，往下找到 Activity */
private fun android.content.Context.findActivity(): android.app.Activity? {
    var ctx: android.content.Context? = this
    while (ctx is android.content.ContextWrapper) {
        if (ctx is android.app.Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

// ---------------------------------------------------------------- 数据源

/**
 * 数据源：账本里并存两套数据，这里只决定「现在看哪一套」。
 *
 * 上一版把演示数据做成了互斥的一次性操作：账本非空就不许载入，想记真账必须把演示删掉。
 * 于是「试用」和「真用」不能同时存在 —— 试完要么删掉自己刚录的，要么带着假账继续记。
 * 现在两套都留在库里，切换只是一行设置：切过去不动任何记录，切回来自己的账原样都在。
 */
@Composable
fun DataSourceScreen(
    onBack: () -> Unit,
    onOpenData: () -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val state by vm.sourceState.collectAsStateWithLifecycle()
    val message by vm.message.collectAsStateWithLifecycle()
    var pendingClear by remember { mutableStateOf<DataSource?>(null) }

    if (message != null) {
        AlertDialog(
            onDismissRequest = { vm.clearMessage() },
            title = { Text("提示") },
            text = { Text(message ?: "") },
            confirmButton = { TextButton(onClick = { vm.clearMessage() }) { Text("知道了") } },
        )
    }

    SubPageScaffold(
        title = "数据源",
        subtitle = "默认只有「我的数据」，演示账本要在这里打开",
        onBack = onBack,
    ) {
        item(key = "demo-header") {
            SectionHeader(
                title = "演示数据",
                subtitle = "整套示例账本的总开关，默认关闭",
            )
        }
        item(key = "demo-switch") {
            AppCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("启用演示数据", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            text = "关着的时候全 App 只有「我的数据」：示例孩子、示例课程、示例缴费" +
                                "不会出现在今天、课程、统计、我的和桌面小组件里，" +
                                "上课提醒也不会为示例课响。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Switch(
                        checked = state.demoEnabled,
                        onCheckedChange = { vm.setDemoEnabled(it) },
                    )
                }
                if (state.demoEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 12.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    Text(
                        text = state.demo.brief,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = if (state.demo.present) {
                            "示例孩子「小明」名下有两门课、几笔缴费和几节课次，用来先把每个页面点一遍。" +
                                "清空只删这一套，我的数据不受影响。"
                        } else {
                            "还没有生成。切到「演示数据」时会自动建一套。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedButton(
                        onClick = { pendingClear = DataSource.DEMO },
                        enabled = state.demo.present,
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 46.dp),
                    ) {
                        Text("清空演示数据", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        item(key = "switch-header") {
            SectionHeader(
                title = "正在使用",
                subtitle = "切换不会删任何记录，切回来自己的账原样都在",
            )
        }
        item(key = "switch") {
            AppCard {
                SourceRow(
                    source = DataSource.MINE,
                    stats = state.mine,
                    selected = state.current == DataSource.MINE,
                    onClick = { if (state.current != DataSource.MINE) vm.switchSource(DataSource.MINE) },
                )
                if (state.demoEnabled) {
                    HorizontalDivider(
                        modifier = Modifier.padding(vertical = 10.dp),
                        color = MaterialTheme.colorScheme.outlineVariant,
                    )
                    SourceRow(
                        source = DataSource.DEMO,
                        stats = state.demo,
                        selected = state.current == DataSource.DEMO,
                        onClick = { if (state.current != DataSource.DEMO) vm.switchSource(DataSource.DEMO) },
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    text = if (state.demoEnabled) {
                        "今天、课程、统计、我的，还有桌面小组件，显示的都是选中的这一套。" +
                            "第一次切到演示数据会自动生成一套完整示例账本，在里面怎么改都不会写到「我的数据」里。"
                    } else {
                        "今天、课程、统计、我的，还有桌面小组件，显示的都是「我的数据」。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        item(key = "mine-header") {
            SectionHeader(title = "我的数据", subtitle = "你自己记的账")
        }
        item(key = "mine") {
            AppCard {
                Text(
                    text = state.mine.brief,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = if (state.mine.present) {
                        "课程、缴费、上课记录都在这里；导出备份或从备份恢复也在这套上做。"
                    } else {
                        "还没有记录。切回「我的数据」就可以记第一笔了。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = onOpenData,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp),
                    ) { Text("备份 / 恢复") }
                    OutlinedButton(
                        onClick = { pendingClear = DataSource.MINE },
                        enabled = state.mine.present,
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 46.dp),
                    ) {
                        Text("清空我的数据", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }

    pendingClear?.let { target ->
        val stats = if (target.isDemo) state.demo else state.mine
        AlertDialog(
            onDismissRequest = { pendingClear = null },
            title = { Text("清空「${target.label}」？") },
            text = {
                Column {
                    Text("会删除：", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        stats.summary,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (target.isDemo) {
                            "删掉之后还能再切回来，会重新生成一套新的示例账本。"
                        } else if (state.demoEnabled) {
                            "删了不能恢复，建议先去「备份 / 恢复」导出一份 JSON。演示数据不受影响。"
                        } else {
                            "删了不能恢复，建议先去「备份 / 恢复」导出一份 JSON。"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingClear = null
                        vm.clearSource(target)
                    },
                ) { Text("清空", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { pendingClear = null }) { Text("取消") }
            },
        )
    }
}

/** 数据源里的一行：图标 + 名字 + 规模，右侧是当前使用的对勾 */
@Composable
private fun SourceRow(
    source: DataSource,
    stats: DemoStats,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val accent = MaterialTheme.colorScheme.primary
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (selected) accent.copy(alpha = 0.16f)
                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (source.isDemo) Icons.Rounded.Science else Icons.Rounded.Storage,
                contentDescription = null,
                tint = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = source.label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            Text(
                text = stats.brief,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Text(
                text = "使用中",
                style = MaterialTheme.typography.labelSmall,
                color = accent,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
