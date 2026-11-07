package cn.yiweiqiu.lessonledger.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.FamilyRestroom
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.AutoSizeText
import cn.yiweiqiu.lessonledger.ui.components.AvatarCircle
import cn.yiweiqiu.lessonledger.ui.components.EntryRow
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.SectionHeader
import cn.yiweiqiu.lessonledger.ui.components.bottomBarInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors

/**
 * 「我的」= 家庭视角页：
 *
 * - 每个孩子一张卡：剩余课时 / 待消耗 / 累计缴费，点卡片看该孩子的缴费明细；
 * - 设置类条目（外观 / 同步 / 关于）不在这里出现——右上角齿轮直达设置页；
 * - 「孩子档案」是数据管理入口，保留在页尾。
 */

/** 剩余课时（半节）→ 显示文案：42 → "42"，43 → "42.5"，负数表示超扣 */
private fun lessonsText(half: Long): String {
    val whole = half / 2
    return if (half % 2 == 0L) "$whole" else "$whole.5"
}

@Composable
fun MineScreen(
    onOpenSettings: () -> Unit,
    onOpenChildren: () -> Unit,
    onAddChild: () -> Unit = {},
    onOpenChildPayments: (String) -> Unit = {},
    onOpenDataSource: () -> Unit = {},
    vm: MineViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    val ext = LocalExtendedColors.current
    val listState = rememberLazyListState()

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            state = listState,
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = topBarInset(),
                bottom = bottomBarInset(24.dp),
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // 演示模式下先把话说明白：这一页的数字来自示例账本，不是你自己的账
            if (state.source.isDemo) {
                item(key = "source-banner") {
                    AppCard {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "正在看演示数据",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "这里的孩子、课时和花销都是示例；你在这里记的东西也只留在演示那一套。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Button(onClick = onOpenDataSource) { Text("切换") }
                        }
                    }
                }
            }

            item(key = "children-header") {
                SectionHeader(
                    title = "家庭成员",
                    subtitle = "每个孩子的课时与余额，点卡片看缴费明细",
                )
            }

            if (state.children.isEmpty()) {
                item(key = "children-empty") {
                    AppCard {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            Text(
                                "还没有孩子档案",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "先添加孩子，再给 TA 报名课程，课时和花费都会算在 TA 名下。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Spacer(Modifier.height(14.dp))
                            Button(onClick = onAddChild) { Text("添加孩子") }
                        }
                    }
                }
            } else {
                items(state.children, key = { it.childId }) { card ->
                    ChildCardItem(
                        card = card,
                        onClick = { onOpenChildPayments(card.childId) },
                        onEdit = onOpenChildren,
                    )
                }
            }

            item(key = "manage-header") {
                SectionHeader(title = "档案与备份")
            }
            item(key = "manage") {
                AppCard {
                    EntryRow(
                        icon = Icons.Rounded.FamilyRestroom,
                        title = "孩子档案",
                        subtitle = "添加、编辑、删除孩子",
                        onClick = onOpenChildren,
                    )
                }
            }

            item(key = "footer") {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = if (state.lastSyncAt > 0) "上次同步 ${Dates.relative(state.lastSyncAt)}" else "本地优先，随时可离线记账",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    )
                }
            }
        }

        GlassTopBar(
            title = "我的",
            subtitle = "${state.children.size} 个孩子 · ${state.activeCourseCount} 门在读课程",
            modifier = Modifier.align(Alignment.TopCenter),
            scrollBack = { listState.canScrollBackward },
            actions = {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        Icons.Rounded.Settings,
                        contentDescription = "设置",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            },
        )
    }
}

@Composable
private fun ChildCardItem(
    card: MineViewModel.ChildCard,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    val ext = LocalExtendedColors.current
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(initial = card.name.take(1), colorHex = card.colorHex, size = 44)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (card.age != null) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "${card.age} 岁",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = if (card.activeCourses > 0) "在读 ${card.activeCourses} 门课程" else "暂无在读课程",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(
                    Icons.Rounded.Edit,
                    contentDescription = "编辑孩子档案",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }
        }
        if (card.overdrawn) {
            Spacer(Modifier.height(8.dp))
            Text(
                "有课程已超扣课时，记得去续费或办理退费",
                style = MaterialTheme.typography.labelSmall,
                color = ext.warning,
            )
        }
        Spacer(Modifier.height(12.dp))
        Row {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "${lessonsText(card.remainingHalf)} 节",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (card.remainingHalf < 0) ext.danger else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "剩余课时",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                // 三列并排 + 金额可能到六位数：让字号自己让路，别截成「¥12,3…」
                AutoSizeText(
                    text = Money.compact(card.pendingCents),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    maxFontSize = 16.sp,
                    minFontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "待消耗",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                AutoSizeText(
                    text = Money.compact(card.paidCents),
                    modifier = Modifier.fillMaxWidth(),
                    style = MaterialTheme.typography.titleMedium,
                    maxFontSize = 16.sp,
                    minFontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "累计缴费",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
