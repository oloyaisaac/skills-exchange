package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.NotificationEntity
import com.example.ui.theme.*
import com.example.util.SystemNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationDrawer(
    notifications: List<NotificationEntity>,
    onDismiss: () -> Unit,
    onRequestPermissionDialog: () -> Unit = {},
    onNotificationClick: (NotificationEntity) -> Unit = {}
) {
    val context = LocalContext.current
    var hasSystemPermission by remember {
        mutableStateOf(SystemNotificationHelper.hasNotificationPermission(context))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Slate900.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(imageVector = Icons.Default.Notifications, contentDescription = "Alerts", tint = SignalBlue)
                    Text(
                        text = "NEIGHBORHOOD ACTIVITY ALERTS",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                }
            }

            // System Notification Status Card
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = if (hasSystemPermission) IndustrialEmeraldLight else AmberUrgent.copy(alpha = 0.15f),
                border = androidx.compose.foundation.BorderStroke(
                    1.dp,
                    if (hasSystemPermission) IndustrialEmerald else AmberUrgent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = if (hasSystemPermission) Icons.Outlined.CheckCircle else Icons.Outlined.Warning,
                            contentDescription = null,
                            tint = if (hasSystemPermission) IndustrialEmeraldDark else AmberUrgent,
                            modifier = Modifier.size(18.dp)
                        )
                        Column {
                            Text(
                                text = if (hasSystemPermission) "System Push Alerts: Active" else "System Push Alerts: Disabled",
                                fontSize = 11.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (hasSystemPermission) IndustrialEmeraldDark else Slate900
                            )
                            Text(
                                text = if (hasSystemPermission) "Channels configured with sound & badge" else "Tap to enable so you never miss a job",
                                fontSize = 10.sp,
                                color = Slate600
                            )
                        }
                    }

                    if (!hasSystemPermission) {
                        Button(
                            onClick = {
                                onRequestPermissionDialog()
                            },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(6.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                        ) {
                            Text("Enable", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                context.startActivity(SystemNotificationHelper.openNotificationSettingsIntent(context))
                            },
                            // R12-P2-04: 48dp touch target.
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Channel Settings",
                                tint = Slate600,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }

            // Test System Notification Button
            if (hasSystemPermission) {
                OutlinedButton(
                    onClick = {
                        SystemNotificationHelper.sendLocalNotification(
                            context = context,
                            channelId = SystemNotificationHelper.CHANNEL_ID_JOBS,
                            notificationId = 101,
                            title = "🛠️ New Job Posted Nearby",
                            body = "Plumbing repair request in Sunnyside with UGX 150,000 budget in Escrow.",
                            actionLabel = "View Job"
                        )
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.NotificationsActive, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Trigger Test OS Notification", fontSize = 11.sp)
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Slate200)

            LazyColumn(
                modifier = Modifier
                    .fillMaxHeight(0.5f)
                    .padding(top = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(notifications, key = { it.id }) { item ->
                    val timeAgo = remember(item.createdAt) {
                        val diff = System.currentTimeMillis() - item.createdAt
                        when {
                            diff < 60000 -> "Just now"
                            diff < 3600000 -> "${diff / 60000}m ago"
                            diff < 86400000 -> "${diff / 3600000}h ago"
                            else -> "${diff / 86400000}d ago"
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = if (!item.isRead) SignalBlueLight else Slate50,
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            if (!item.isRead) SignalBlue else Slate200
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onNotificationClick(item) }
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = item.title,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                                Text(
                                    text = timeAgo,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Slate500
                                )
                            }
                            Text(
                                text = item.body,
                                fontSize = 12.sp,
                                color = Slate700,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
