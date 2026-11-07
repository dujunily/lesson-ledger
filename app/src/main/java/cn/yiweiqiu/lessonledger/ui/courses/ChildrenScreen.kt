package cn.yiweiqiu.lessonledger.ui.courses

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.yiweiqiu.lessonledger.core.Money
import cn.yiweiqiu.lessonledger.domain.Child
import cn.yiweiqiu.lessonledger.ui.components.AppCard
import cn.yiweiqiu.lessonledger.ui.components.AvatarCircle
import cn.yiweiqiu.lessonledger.ui.components.EmptyState
import cn.yiweiqiu.lessonledger.ui.components.GlassBackButton
import cn.yiweiqiu.lessonledger.ui.components.GlassFab
import cn.yiweiqiu.lessonledger.ui.components.GlassTopBar
import cn.yiweiqiu.lessonledger.ui.components.pageBottomInset
import cn.yiweiqiu.lessonledger.ui.components.topBarInset
import cn.yiweiqiu.lessonledger.ui.forms.ChildFormSheet

@Composable
fun ChildrenScreen(
    onBack: () -> Unit,
    vm: ChildrenViewModel = hiltViewModel(),
) {
    val state by vm.uiState.collectAsStateWithLifecycle()
    var showForm by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<Child?>(null) }
    var deleting by remember { mutableStateOf<Child?>(null) }

    // 顶栏浮在内容之上，列表从玻璃条下面滑过（沉浸式），底部让开小白条
    val listState = rememberLazyListState()
    Box(modifier = Modifier.fillMaxSize()) {
        if (state.ready && state.rows.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = topBarInset(), bottom = pageBottomInset(80.dp)),
                contentAlignment = Alignment.Center,
            ) {
                EmptyState(
                    icon = Icons.Rounded.Person,
                    title = "先添加一个孩子",
                    description = "每个孩子可以有多个培训班，每个班单独记账、单独统计剩余课时。",
                    actionLabel = "添加孩子",
                    onAction = { showForm = true },
                )
            }
        } else {
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = topBarInset() + 8.dp,
                    bottom = pageBottomInset(96.dp),
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(state.rows, key = { it.child.id }) { row ->
                    ChildCard(
                        row = row,
                        onClick = { editing = row.child },
                        onEdit = { editing = row.child },
                        onDelete = { deleting = row.child },
                    )
                }
            }
        }

        GlassTopBar(
            title = "孩子档案",
            subtitle = "每个孩子可以有多个培训班",
            modifier = Modifier.align(Alignment.TopCenter),
            leading = { GlassBackButton(onBack) },
            scrollBack = { listState.canScrollBackward },
        )

        GlassFab(
            text = "添加孩子",
            icon = Icons.Rounded.Add,
            onClick = { showForm = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(end = 20.dp, bottom = pageBottomInset(14.dp)),
        )
    }

    if (showForm) {
        ChildFormSheet(
            initial = null,
            onDismiss = { showForm = false },
            onSave = { name, nickname, birthday, color, note ->
                vm.saveChild(null, name, nickname, birthday, color, note)
                showForm = false
            },
        )
    }

    editing?.let { child ->
        ChildFormSheet(
            initial = child,
            onDismiss = { editing = null },
            onSave = { name, nickname, birthday, color, note ->
                vm.saveChild(child, name, nickname, birthday, color, note)
                editing = null
            },
        )
    }

    deleting?.let { child ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除「${child.displayName}」？") },
            text = { Text("该孩子名下的所有课程、缴费和上课记录都会被删除（同步到其他设备后同样删除）。") },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteChild(child.id)
                    deleting = null
                }) { Text("删除", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun ChildCard(
    row: ChildrenViewModel.ChildRow,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    AppCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            AvatarCircle(
                initial = row.child.initial,
                colorHex = row.child.avatarColor,
                size = 46,
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = row.child.displayName,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (row.child.nickname.isNotBlank() && row.child.nickname != row.child.name) {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = row.child.name,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    row.child.age?.let {
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "$it 岁",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Text(
                    text = "${row.activeCourses} 门在读 · 累计 ${Money.compact(row.totalPaidCents)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(
                Icons.Rounded.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.outline,
                modifier = Modifier.size(18.dp),
            )
            androidx.compose.foundation.layout.Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(36.dp)) {
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
                        leadingIcon = { Icon(Icons.Rounded.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() },
                    )
                    DropdownMenuItem(
                        text = { Text("删除") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDelete() },
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .padding(horizontal = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "待消耗",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = Money.compact(row.pendingCents),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
