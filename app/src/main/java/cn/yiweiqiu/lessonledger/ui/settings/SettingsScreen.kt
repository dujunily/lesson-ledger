package cn.yiweiqiu.lessonledger.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.EntryRow
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.pageBottomInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.components.SyncStatusChip
import cn.yiweiqiu.lessonledger.ui.forms.remindBeforeLabel
import cn.yiweiqiu.lessonledger.ui.theme.AccentTheme
import cn.yiweiqiu.lessonledger.ui.theme.ThemeMode

/**
 * 设置首页：只列模块，点进模块才见配置。
 *
 * 之前所有配置堆在一页里滚动，找东西要一路划；现在按「外观 / 提醒 / 同步与备份 /
 * 记账规则 / 数据 / 关于」六个模块分区，每行副标题直接显示当前状态，不进去也知道设置成了什么样。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenSyncDetail: () -> Unit,
    onOpenModule: (String) -> Unit,
    vm: SettingsViewModel = hiltViewModel(),
) {
    val settings by vm.settingsState.collectAsStateWithLifecycle()
    val status by vm.syncState.collectAsStateWithLifecycle()
    val source by vm.sourceState.collectAsStateWithLifecycle()

    // 顶栏浮在内容之上，列表从玻璃条下滑过（沉浸式）；底部让开小白条
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxHeight()
                    .widthIn(max = 720.dp),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topBarInset() + 4.dp,
                    bottom = pageBottomInset(32.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                item(key = "look-header") { SectionHeader(title = "外观与提醒", dense = true) }
                item(key = "look") {
                    AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        EntryRow(
                            icon = Icons.Rounded.Palette,
                            title = "外观",
                            subtitle = "主题 ${AccentTheme.of(settings.accentKey).label} · " +
                                "明暗 ${ThemeMode.of(settings.themeMode).label}",
                            onClick = { onOpenModule(SettingsModules.APPEARANCE) },
                            showDivider = true,
                            dense = true,
                        )
                        EntryRow(
                            icon = Icons.Rounded.NotificationsActive,
                            title = "上课提醒",
                            subtitle = when {
                                !settings.classReminderEnabled && !settings.dailyDigestEnabled -> "已关闭"
                                settings.dailyDigestEnabled && settings.classReminderEnabled ->
                                    "每天 10:00 汇总 · 上课时提醒打卡"
                                settings.dailyDigestEnabled -> "每天 10:00 汇总今天要上的课"
                                else -> "上课时提醒打卡"
                            },
                            onClick = { onOpenModule(SettingsModules.REMINDER) },
                            dense = true,
                        )
                    }
                }

                item(key = "data-sync-header") { SectionHeader(title = "数据", dense = true) }
                item(key = "data-sync") {
                    AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        EntryRow(
                            icon = Icons.Rounded.CloudSync,
                            title = "同步与备份",
                            subtitle = when {
                                !settings.isConfigured -> "未配置坚果云 · 数据只在本机"
                                settings.lastSyncAt > 0 -> "上次同步 ${Dates.relative(settings.lastSyncAt)}"
                                else -> "已配置，等待首次同步"
                            },
                            onClick = { onOpenModule(SettingsModules.SYNC) },
                            showDivider = true,
                            dense = true,
                        )
                        EntryRow(
                            icon = Icons.Rounded.Storage,
                            title = "数据导出与清空",
                            subtitle = "JSON 备份、恢复，清空当前数据",
                            onClick = { onOpenModule(SettingsModules.DATA) },
                            showDivider = true,
                            dense = true,
                        )
                        EntryRow(
                            icon = Icons.Rounded.SwapHoriz,
                            title = "数据源",
                            // 演示总开关关着时，这里连「演示」两个字都不出现——
                            // 示例账本的存在本身也不该在正常记账界面里露出来
                            subtitle = if (settings.demoEnabled) {
                                "正在使用：${source.current.label} · " +
                                    if (source.current.isDemo) source.demo.brief else source.mine.brief
                            } else {
                                "我的数据 · ${source.mine.brief}"
                            },
                            onClick = { onOpenModule(SettingsModules.DEMO) },
                            dense = true,
                        )
                    }
                }

                item(key = "rules-header") { SectionHeader(title = "记账", dense = true) }
                item(key = "rules") {
                    AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        EntryRow(
                            icon = Icons.Rounded.Tune,
                            title = "记账规则",
                            subtitle = buildString {
                                append("剩余 ${settings.defaultThreshold} 节提醒")
                                if (settings.absentDeducts) append(" · 缺勤扣课时")
                                if (settings.leaveDeducts) append(" · 请假扣课时")
                                settings.monthlyBudgetCents?.let { append(" · 月预算 ${Money.compact(it)}") }
                            },
                            onClick = { onOpenModule(SettingsModules.RULES) },
                            dense = true,
                        )
                    }
                }

                item(key = "about-header") { SectionHeader(title = "关于", dense = true) }
                item(key = "about") {
                    AppCard(contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp)) {
                        EntryRow(
                            icon = Icons.Rounded.Info,
                            title = "关于课时簿",
                            subtitle = "版本、数据说明与隐私",
                            onClick = { onOpenModule(SettingsModules.ABOUT) },
                            dense = true,
                        )
                    }
                }

                item(key = "footer") {
                    Column(modifier = Modifier.padding(top = 4.dp)) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))
                        Text(
                            text = "本地优先 · 数据只存在你的手机和你的坚果云",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontWeight = FontWeight.Normal,
                            modifier = Modifier.padding(top = 10.dp),
                        )
                    }
                }
            }
        }

        GlassTopBar(
            title = "设置",
            subtitle = "按模块进入各自的配置",
            modifier = Modifier.align(Alignment.TopCenter),
            leading = { GlassBackButton(onBack) },
            scrollBack = { listState.canScrollBackward },
            actions = {
                SyncStatusChip(
                    status = status,
                    configured = settings.isConfigured,
                    onClick = onOpenSyncDetail,
                    modifier = Modifier.padding(end = 8.dp),
                )
            },
        )
    }
}

/** 设置模块的路由键：设置首页与 AppNav 共用，避免字符串写错 */
object SettingsModules {
    const val APPEARANCE = "appearance"
    const val REMINDER = "reminder"
    const val SYNC = "sync"
    const val RULES = "rules"
    const val DATA = "data"
    const val DEMO = "demo"
    const val ABOUT = "about"
}
