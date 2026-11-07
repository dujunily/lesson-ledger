package cn.yiweiqiu.lessonledger.ui.settings

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.sync.SyncPhase
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.AppTextField
import cn.yiweiqiu.lessonledger.ui.components.KeyValueRow
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.SyncStatusChip
import cn.yiweiqiu.lessonledger.ui.components.pageBottomInset
import cn.yiweiqiu.lessonledger.ui.components.syncLabel
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors

@Composable
fun SyncDetailScreen(
    onBack: () -> Unit,
    vm: SyncDetailViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val testResult by vm.testResult.collectAsStateWithLifecycle()
    val ext = LocalExtendedColors.current
    val status = state.status

    var email by remember(state.email) { mutableStateOf(state.email) }
    var password by remember(state.appPassword) { mutableStateOf(state.appPassword) }
    var passwordVisible by remember { mutableStateOf(false) }

    // 顶栏浮层 + 列表从玻璃条下滑过：和设置其他子页保持同一套沉浸式观感
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarInset() + 8.dp,
                bottom = pageBottomInset(32.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.configured) {
                item(key = "config") {
                    AppCard {
                        Text(
                            "连接坚果云",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            "两台手机填同一个坚果云账号，账本数据自动互通；也可以只当备份用。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp, bottom = 12.dp),
                        )

                        AppTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = "坚果云账号（邮箱）",
                            placeholder = "you@example.com",
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
                        Spacer(Modifier.padding(top = 10.dp))
                        Text(
                            text = "远端目录固定为 /keshibu/，不用填。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.padding(top = 14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            Button(
                                onClick = { vm.saveAccount(email, password) },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) { Text("保存账号") }
                            OutlinedButton(
                                onClick = {
                                    vm.saveAccount(email, password)
                                    vm.testConnection()
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .heightIn(min = 48.dp),
                            ) { Text("测试连接") }
                        }

                        val result = testResult
                        if (result != null) {
                            Spacer(Modifier.padding(top = 10.dp))
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
                                Text(
                                    text = result.message,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (result.ok) ext.success else ext.danger,
                                )
                            }
                        }
                    }
                }
            } else {
                item(key = "status") {
                    AppCard {
                        Text(
                            text = syncLabel(status, state.configured),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        if (status.message.isNotBlank()) {
                            Text(
                                text = status.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Spacer(Modifier.padding(top = 6.dp))
                        KeyValueRow(
                            "待上传",
                            if (status.pendingCount > 0) "${status.pendingCount} 条" else "无",
                            valueColor = if (status.pendingCount > 0) ext.warning else ext.success,
                        )
                        KeyValueRow(
                            "上次成功",
                            if (status.lastSuccessAt > 0) Dates.relative(status.lastSuccessAt) else "从未",
                        )
                        KeyValueRow(
                            "同步游标",
                            if (state.lastSyncAt > 0) Dates.formatDateTime(state.lastSyncAt) else "未建立",
                        )
                        KeyValueRow("设备", state.deviceName)
                        KeyValueRow("远端目录", state.rootDir)

                        if (status.phase == SyncPhase.AUTH_FAILED) {
                            Spacer(Modifier.padding(top = 8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(ext.dangerContainer)
                                    .padding(12.dp),
                            ) {
                                Text(
                                    text = "坚果云 WebDAV 必须使用「应用密码」：网页端 → 账户信息 → 安全选项 → 添加应用密码。登录密码会一直 401。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = ext.danger,
                                )
                            }
                        }

                        Spacer(Modifier.padding(top = 12.dp))
                        Button(
                            onClick = vm::syncNow,
                            enabled = status.phase != SyncPhase.SYNCING,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                        ) {
                            Icon(Icons.Rounded.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(if (status.phase == SyncPhase.SYNCING) "同步中…" else "立即同步")
                        }
                    }
                }

                run {
                    item(key = "conflict-header") {
                        Column {
                            SectionHeader(
                                title = "数据清理",
                                subtitle = if (state.conflicts.isEmpty()) {
                                    "暂无冲突副本；顺手清掉历史遗留的无效数据"
                                } else {
                                    "冲突副本 ${state.conflicts.size} 条，均已保留"
                                },
                            )
                            val cleanupMsg by vm.cleanupMsg.collectAsStateWithLifecycle()
                            cleanupMsg?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.padding(start = 6.dp, bottom = 4.dp),
                                )
                            }
                            var confirmCleanup by remember { mutableStateOf(false) }
                            OutlinedButton(
                                onClick = { confirmCleanup = true },
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                            ) {
                                Text("清理冲突副本与无效数据", style = MaterialTheme.typography.labelLarge)
                            }
                            if (confirmCleanup) {
                                androidx.compose.material3.AlertDialog(
                                    onDismissRequest = { confirmCleanup = false },
                                    title = { Text("清理冲突副本与无效数据？") },
                                    text = {
                                        Text(
                                            "会删除两类记录：\n" +
                                                "1）${state.conflicts.size} 条带「[冲突副本]」标记的重复拷贝；\n" +
                                                "2）孤儿数据——所属孩子/课程已经不存在的课程、课次和缴费（列表里本来就看不到）。\n" +
                                                "正主记录不受影响；删除会同步到其他设备。"
                                        )
                                    },
                                    confirmButton = {
                                        Button(onClick = {
                                            vm.cleanupConflicts()
                                            confirmCleanup = false
                                        }) { Text("清理") }
                                    },
                                    dismissButton = {
                                        OutlinedButton(onClick = { confirmCleanup = false }) { Text("取消") }
                                    },
                                )
                            }
                        }
                    }
                    items(state.conflicts, key = { it.id }) { conflict ->
                        AppCard(contentPadding = PaddingValues(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "[${conflict.type}] ${conflict.title}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                            Text(
                                text = conflict.note,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }

                item(key = "log-header") { SectionHeader(title = "最近 20 条同步日志", subtitle = "仅本机保存") }
                if (state.logs.isEmpty()) {
                    item(key = "log-empty") {
                        Text(
                            text = "还没有同步记录",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                } else {
                    items(state.logs, key = { it.id }) { log ->
                        val color = when (log.level) {
                            "ERROR" -> ext.danger
                            "WARN" -> ext.warning
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            Text(
                                text = Dates.formatDateTime(log.at).takeLast(8),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.width(58.dp),
                            )
                            Text(
                                text = log.message,
                                style = MaterialTheme.typography.bodySmall,
                                color = color,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }

        GlassTopBar(
            // 没配置时这一页就是配置入口，标题跟着变，别让人在空状态里迷路
            title = if (state.configured) "同步详情" else "配置同步",
            subtitle = if (state.configured) "坚果云 WebDAV · 两台手机填同一个账号" else "填好账号，两台手机自动互通",
            modifier = Modifier.align(Alignment.TopCenter),
            leading = { GlassBackButton(onBack) },
            scrollBack = { listState.canScrollBackward },
            actions = {
                if (state.configured) {
                    Box(modifier = Modifier.padding(end = 8.dp)) {
                        SyncStatusChip(
                            status = status,
                            configured = true,
                            onClick = { },
                        )
                    }
                }
            },
        )
    }
}
