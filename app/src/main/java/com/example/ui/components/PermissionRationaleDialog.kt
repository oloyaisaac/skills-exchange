package com.example.ui.components

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import com.example.util.SystemNotificationHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionRationaleDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    onPermissionGranted: () -> Unit
) {
    if (!isOpen) return

    val context = LocalContext.current
    var permissionDeniedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            SystemNotificationHelper.setupNotificationChannels(context)
            onPermissionGranted()
            onDismiss()
        } else {
            permissionDeniedOnce = true
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Slate900.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .background(SignalBlueLight, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.NotificationsActive,
                    contentDescription = null,
                    tint = SignalBlue,
                    modifier = Modifier.size(32.dp)
                )
            }

            Text(
                text = "Enable Real-Time Alerts",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )

            Text(
                text = "Skills Exchange uses high-priority notifications to keep you informed during critical marketplace events:",
                fontSize = 13.5.sp,
                color = Slate600,
                lineHeight = 19.sp
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Slate50, RoundedCornerShape(12.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                RationaleRow(
                    title = "Urgent Job & Quote Broadcasts",
                    desc = "Get notified immediately when a neighbor requests your trade nearby."
                )
                RationaleRow(
                    title = "Mobile Money Escrow Updates",
                    desc = "Instant confirmation when funds are locked or released to your wallet."
                )
                RationaleRow(
                    title = "Neighbor Direct Messages",
                    desc = "Real-time chat replies for service coordinate and timing."
                )
            }

            if (permissionDeniedOnce) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = AmberUrgent.copy(alpha = 0.15f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgent)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Notifications are blocked by system settings. Tap below to enable in Android App Settings.",
                            fontSize = 12.sp,
                            color = Slate800,
                            lineHeight = 16.sp
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Text("Maybe Later", color = Slate600)
                }

                Button(
                    onClick = {
                        if (permissionDeniedOnce) {
                            context.startActivity(SystemNotificationHelper.openNotificationSettingsIntent(context))
                            onDismiss()
                        } else {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                            } else {
                                SystemNotificationHelper.setupNotificationChannels(context)
                                onPermissionGranted()
                                onDismiss()
                            }
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Icon(
                        imageVector = if (permissionDeniedOnce) Icons.Default.Settings else Icons.Default.NotificationsActive,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (permissionDeniedOnce) "Open Settings" else "Allow Alerts",
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun RationaleRow(title: String, desc: String) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = Icons.Outlined.CheckCircle,
            contentDescription = null,
            tint = IndustrialEmerald,
            modifier = Modifier
                .size(16.dp)
                .padding(top = 2.dp)
        )
        Column {
            Text(
                text = title,
                fontSize = 12.5.sp,
                fontWeight = FontWeight.Bold,
                color = Slate900
            )
            Text(
                text = desc,
                fontSize = 11.sp,
                color = Slate600
            )
        }
    }
}
