package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ChatMessageEntity
import com.example.data.db.JobPostEntity
import com.example.ui.theme.*

// Phone number masking helper
fun maskPhoneNumbersInChat(text: String): String {
    val phoneRegex = Regex("""(\+?\d{1,3}[-.\s]?)?\(?\d{2,4}\)?[-.\s]?\d{3,4}[-.\s]?\d{3,4}""")
    return if (phoneRegex.containsMatchIn(text) && text.length >= 7) {
        phoneRegex.replace(text, "[🛡️ PHONE MASKED - USE IN-APP CHAT]")
    } else {
        text
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDrawer(
    job: JobPostEntity,
    messages: List<ChatMessageEntity>,
    onSendMessage: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var inputText by remember { mutableStateOf("") }
    val containsPhoneAttempt = remember(inputText) {
        Regex("""\+?\d{7,13}""").containsMatchIn(inputText.replace(" ", "").replace("-", ""))
    }

    val quickActionChips = listOf(
        "📍 I've arrived on site",
        "📋 Sending updated quote",
        "📸 Can you send a photo?",
        "✅ Job completed & verified"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.6f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.85f)
                .padding(horizontal = 16.dp)
                .padding(bottom = 16.dp)
        ) {
            // Chat Drawer Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "DIRECT NEIGHBOR CHAT",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SignalBlue
                    )
                    Text(
                        text = job.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900,
                        maxLines = 1
                    )
                }

                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = Slate100,
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                ) {
                    Text(
                        text = job.status,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate800,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            // Safety & Anti-Disintermediation Guardrail Banner
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFEFF6FF),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFBFDBFE)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(text = "🛡️", fontSize = 14.sp)
                    Text(
                        text = "Safety Firewall: Keep contacts on-app. Off-app jobs have 0% escrow protection or insurance guarantee.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF1E40AF)
                    )
                }
            }

            HorizontalDivider(thickness = 1.dp, color = Slate200)

            // Message Stream
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(messages, key = { it.id }) { msg ->
                    if (msg.isSystemNotification) {
                        // System notification pill
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = Slate100,
                                border = androidx.compose.foundation.BorderStroke(1.dp, Slate200)
                            ) {
                                Text(
                                    text = "🔔 ${msg.message}",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Slate600,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                                )
                            }
                        }
                    } else {
                        val isUser = msg.isFromUser
                        val filteredText = maskPhoneNumbersInChat(msg.message)

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                        ) {
                            Text(
                                text = "${msg.senderName} • ${msg.timestampText}",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Slate500,
                                modifier = Modifier.padding(bottom = 2.dp)
                            )

                            Surface(
                                shape = RoundedCornerShape(
                                    topStart = 8.dp,
                                    topEnd = 8.dp,
                                    bottomStart = if (isUser) 8.dp else 0.dp,
                                    bottomEnd = if (isUser) 0.dp else 8.dp
                                ),
                                color = if (isUser) SignalBlue else Slate100,
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (isUser) SignalBlueDark else Slate300
                                )
                            ) {
                                Text(
                                    text = filteredText,
                                    fontSize = 13.sp,
                                    color = if (isUser) Color.White else Slate900,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            // Phone number warning indicator if typing digits
            if (containsPhoneAttempt) {
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = AmberUrgentLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                ) {
                    Text(
                        text = "⚠️ Direct phone numbers are masked to prevent scam fraud. Escrow guarantees apply strictly inside the app.",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = AmberUrgent,
                        modifier = Modifier.padding(6.dp)
                    )
                }
            }

            // Quick Action Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                quickActionChips.forEach { action ->
                    Surface(
                        onClick = { onSendMessage(action) },
                        shape = RoundedCornerShape(16.dp),
                        color = Slate100,
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate300)
                    ) {
                        Text(
                            text = action,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Slate800,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        )
                    }
                }
            }

            // Input Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = { Text("Type message or response...", fontSize = 13.sp, color = Slate400) },
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(6.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = SignalBlue,
                        unfocusedBorderColor = Slate300
                    ),
                    singleLine = true
                )

                IconButton(
                    onClick = {
                        if (inputText.isNotBlank()) {
                            onSendMessage(inputText)
                            inputText = ""
                        }
                    },
                    modifier = Modifier
                        .size(52.dp)
                        .background(SignalBlue, RoundedCornerShape(8.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Send",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}
