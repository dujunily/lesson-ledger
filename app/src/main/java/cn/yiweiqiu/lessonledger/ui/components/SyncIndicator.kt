package cn.yiweiqiu.lessonledger.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import cn.yiweiqiu.lessonledger.core.Dates
import cn.yiweiqiu.lessonledger.data.sync.SyncPhase
import cn.yiweiqiu.lessonledger.data.sync.SyncStatus
import cn.yiweiqiu.lessonledger.ui.theme.LocalExtendedColors

fun syncLabel(status: SyncStatus, configured: Boolean): String {
    if (!configured && status.phase == SyncPhase.NOT_CONFIGURED) return "未配置同步"
    return when (status.phase) {
        SyncPhase.SYNCED -> "已同步 · ${Dates.relative(status.lastSuccessAt)}"
        SyncPhase.SYNCING -> "同步中…"
        SyncPhase.OFFLINE -> if (status.pendingCount > 0) "离线 · 待上传 ${status.pendingCount} 条" else "离线"
        SyncPhase.CONFLICT -> if (status.conflictCount > 0) "同步冲突 ${status.conflictCount} 条" else "同步未完成"
        SyncPhase.AUTH_FAILED -> "认证失败"
        SyncPhase.SERVER_ERROR -> "同步异常"
        SyncPhase.SPACE_FULL -> "空间不足"
        SyncPhase.UPGRADE_REQUIRED -> "需升级 App"
        SyncPhase.NOT_CONFIGURED -> "未配置同步"
    }
}

@Composable
fun syncColor(status: SyncStatus, configured: Boolean): Color {
    val ext = LocalExtendedColors.current
    if (!configured) return MaterialTheme.colorScheme.onSurfaceVariant
    return when (status.phase) {
        SyncPhase.SYNCED -> ext.success
        SyncPhase.SYNCING -> ext.info
        SyncPhase.OFFLINE -> MaterialTheme.colorScheme.onSurfaceVariant
        SyncPhase.CONFLICT -> ext.warning
        SyncPhase.AUTH_FAILED, SyncPhase.SERVER_ERROR, SyncPhase.SPACE_FULL, SyncPhase.UPGRADE_REQUIRED -> ext.danger
        SyncPhase.NOT_CONFIGURED -> MaterialTheme.colorScheme.onSurfaceVariant
    }
}

@Composable
fun SyncStatusChip(
    status: SyncStatus,
    configured: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val color = syncColor(status, configured)
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(color.copy(alpha = 0.10f))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (status.phase == SyncPhase.SYNCING) {
            CircularProgressIndicator(
                modifier = Modifier.size(10.dp),
                strokeWidth = 1.5.dp,
                color = color,
            )
        } else {
            Box(
                Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
        Spacer(Modifier.width(6.dp))
        Text(
            text = syncLabel(status, configured),
            style = MaterialTheme.typography.labelSmall,
            color = color,
            maxLines = 1,
        )
    }
}
