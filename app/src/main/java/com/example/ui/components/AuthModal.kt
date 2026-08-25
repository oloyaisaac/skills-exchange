package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

/**
 * LOC-001: Client Onboarding & Authentication Modal
 * Collects Client Full Name, Phone Number, and District Location (via GPS or manual selection)
 * without requiring National ID for clients hiring neighbors.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthModal(
    pendingActionReason: String? = null,
    initialDistrict: String = "Sunnyside",
    onLoginSuccess: (phoneNumber: String, fullName: String, district: String) -> Unit,
    onDismiss: () -> Unit
) {
    var fullName by remember { mutableStateOf("Sarah Namukasa") }
    var phoneNumber by remember { mutableStateOf("+256 701 234 567") }
    var selectedDistrict by remember { mutableStateOf(initialDistrict) }
    var isSubmitting by remember { mutableStateOf(false) }

    val popularDistricts = listOf(
        "Sunnyside",
        "Kampala Central",
        "Nakawa",
        "Makindye",
        "Kawempe",
        "Rubaga",
        "Entebbe",
        "Mukono",
        "Wakiso",
        "Jinja"
    )

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        scrimColor = Slate900.copy(alpha = 0.65f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .background(SignalBlue, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Shield",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Text(
                        text = "CLIENT ONBOARDING & SIGN IN",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = SignalBlue
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(28.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Close",
                        tint = Slate500,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Text(
                text = "Welcome to Skills Exchange",
                fontSize = 19.sp,
                fontWeight = FontWeight.Black,
                color = Slate900,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Connect with trusted, background-checked neighborhood specialists. No National ID required for hiring neighbors.",
                fontSize = 12.sp,
                color = Slate600,
                textAlign = TextAlign.Center
            )

            // Pending action alert pill
            if (!pendingActionReason.isNullOrBlank()) {
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = AmberUrgentLight,
                    border = androidx.compose.foundation.BorderStroke(1.dp, AmberUrgent),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock",
                            tint = AmberUrgent,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "Required for: $pendingActionReason",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = AmberUrgent
                        )
                    }
                }
            }

            // Input 1: Full Name
            OutlinedTextField(
                value = fullName,
                onValueChange = { fullName = it },
                label = { Text("Your Full Name (Client Profile)", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Person,
                        contentDescription = "Name",
                        tint = SignalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SignalBlue,
                    unfocusedBorderColor = Slate300
                ),
                singleLine = true
            )

            // Input 2: Phone Number / MoMo
            OutlinedTextField(
                value = phoneNumber,
                onValueChange = { phoneNumber = it },
                label = { Text("Mobile Phone / MoMo Handset Number", fontSize = 12.sp) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.PhoneAndroid,
                        contentDescription = "Phone",
                        tint = SignalBlue,
                        modifier = Modifier.size(18.dp)
                    )
                },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = SignalBlue,
                    unfocusedBorderColor = Slate300
                ),
                singleLine = true
            )

            // Input 3: Location / District Selector
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Your Neighborhood / District",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Slate700
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.clickable { selectedDistrict = "Current Location" }
                    ) {
                        Icon(
                            imageVector = Icons.Default.MyLocation,
                            contentDescription = "GPS",
                            tint = SignalBlue,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = "Auto GPS",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalBlue
                        )
                    }
                }

                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(popularDistricts) { district ->
                        val isSelected = selectedDistrict == district
                        Surface(
                            color = if (isSelected) SignalBlue else Slate100,
                            contentColor = if (isSelected) Color.White else Slate700,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.clickable { selectedDistrict = district }
                        ) {
                            Text(
                                text = district,
                                fontSize = 11.5.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // Primary Continue Button
            Button(
                onClick = {
                    isSubmitting = true
                    onLoginSuccess(phoneNumber, fullName, selectedDistrict)
                },
                enabled = !isSubmitting && phoneNumber.isNotBlank() && fullName.isNotBlank(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
            ) {
                Text(
                    text = if (isSubmitting) "Saving Profile..." else "Complete Client Onboarding",
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            // Quick One-Tap Demo Login
            Surface(
                onClick = { onLoginSuccess("+256 772 100 200", "Sarah Namukasa", "Sunnyside") },
                shape = RoundedCornerShape(8.dp),
                color = IndustrialEmeraldLight,
                border = androidx.compose.foundation.BorderStroke(1.dp, IndustrialEmerald),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.CheckCircle,
                        contentDescription = "Demo",
                        tint = IndustrialEmerald,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "⚡ Instant One-Tap Resident Demo Sign In",
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = IndustrialEmeraldDark
                    )
                }
            }

            // Trust badge footer
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.Shield, contentDescription = null, tint = Slate400, modifier = Modifier.size(12.dp))
                    Text("Zero Spam Guarantee", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Medium)
                }
                Text("•", fontSize = 10.sp, color = Slate400)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Default.VerifiedUser, contentDescription = null, tint = Slate400, modifier = Modifier.size(12.dp))
                    Text("100% Escrow Protection", fontSize = 10.sp, color = Slate500, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}
