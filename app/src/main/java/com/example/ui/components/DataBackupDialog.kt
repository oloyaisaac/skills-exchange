package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.JobPostEntity
import com.example.data.db.ServiceProviderEntity
import com.example.ui.theme.*
import com.example.util.DataBackupHelper

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DataBackupDialog(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    providers: List<ServiceProviderEntity>,
    jobs: List<JobPostEntity>
) {
    if (!isOpen) return

    val context = LocalContext.current
    val backupJson = remember(providers, jobs) {
        DataBackupHelper.generateBackupJson(providers, jobs)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        scrimColor = Slate900.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(imageVector = Icons.Default.FileDownload, contentDescription = null, tint = SignalBlue)
                Text(
                    text = "DATA PORTABILITY & BACKUP (JSON)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = 0.5.sp,
                    color = Slate900
                )
            }

            Text(
                text = "Export your full on-device database in open standard JSON format (GDPR Art. 20 compliant):",
                fontSize = 12.5.sp,
                color = Slate600
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Active Jobs", fontSize = 11.sp, color = Slate500)
                        Text("${jobs.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }

                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = Slate100,
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp)) {
                        Text("Providers & Craftsmen", fontSize = 11.sp, color = Slate500)
                        Text("${providers.size}", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = Slate900)
                    }
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = Slate900,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(140.dp)
            ) {
                Box(
                    modifier = Modifier
                        .padding(10.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = backupJson,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = Color(0xFFA7F3D0)
                    )
                }
            }

            Button(
                onClick = {
                    DataBackupHelper.exportAndShareBackup(context, backupJson)
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
            ) {
                Icon(imageVector = Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Export & Share Backup JSON", fontWeight = FontWeight.Bold)
            }
        }
    }
}
