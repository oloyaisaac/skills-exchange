package com.example.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import coil.compose.rememberAsyncImagePainter
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.UserProfileEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion

/**
 * Extract initials from a full name string.
 * Examples: "Isaac Kato" → "IK", "Isaac" → "I", empty/null → "N"
 */
fun extractInitials(fullName: String?): String {
    if (fullName.isNullOrBlank()) return "N"
    val parts = fullName.trim().split("\\s+".toRegex()).filter { it.isNotEmpty() }
    return when {
        parts.size >= 2 -> "${parts[0].first()}${parts[1].first()}"
        parts.size == 1 -> "${parts[0].first()}"
        else -> "N"
    }.uppercase()
}

@Composable
fun AppNavigationDrawer(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    userRoleMode: String,
    onToggleUserRole: () -> Unit,
    themeMode: String,
    onSetThemeMode: (String) -> Unit,
    isAuthenticated: Boolean,
    currentUserPhone: String?,
    currentDistrict: String,
    onTriggerAuth: () -> Unit,
    onNavigateToOnboarding: () -> Unit = {},
    onLogout: () -> Unit,
    currentUserProfile: UserProfileEntity? = null,
    onOpenWallet: () -> Unit = {},
    /**
     * REG-003 / REG-004: opens the worker's profile photo + backup contact sheet.
     */
    onOpenProfileSettings: () -> Unit = {},
    onOpenHistory: () -> Unit = {},
    onOpenDisputes: () -> Unit = {},
    onOpenDataBackup: () -> Unit = {},
    onOpenPrivacyPolicy: () -> Unit = {},
    onOpenDeleteAccount: () -> Unit = {},
    /**
     * AUDIT FIX (H4): Real escrow figure supplied by MainScreen from the job records.
     * Previously this drawer hardcoded `CurrencyRegion.format(240.0)` and presented it to
     * every user as "Available Escrow".
     */
    escrowHeldTotalLabel: String = "—",
    /**
     * AUDIT FIX (H3/G5): Real KYC state. Previously any authenticated account was shown a
     * "Verified" / "National ID & Phone Verified" badge regardless of actual KYC status.
     */
    isKycVerified: Boolean = false,
    kycStatusLabel: String = "Not verified",
    onOpenIdentity: () -> Unit = {},
    /**
     * AUDIT-A-CONSOLE: opens the operator verification queue. Locked by default —
     * the host decides the unlock; the backend `admin` claim stays the authorizer.
     */
    isAdminUnlocked: Boolean = false,
    adminPendingCount: Int = 0,
    onOpenAdminQueue: () -> Unit = {},
    /** AUDIT-A-ARBITRATE: opens the dispute arbitration console. UI visibility only —
     * the `resolveDispute` callable + firestore.rules remain the real authorizer.
     */
    onOpenAdminArbitration: () -> Unit = {}
) {
    if (!isOpen) return

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.55f))
            .clickable { onDismiss() }
    ) {
        Surface(
            modifier = Modifier
                .fillMaxHeight()
                .width(310.dp)
                .clickable(enabled = false) {}, // Prevent dismiss when clicking inside
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp,
            shadowElevation = 16.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // 1. Profile & Neighborhood Header
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Slate900)
                        .padding(horizontal = 20.dp, vertical = 22.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        // Top Action Row
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
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(SignalBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Handshake,
                                        contentDescription = "Logo",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                Column {
                                    Text(
                                        text = "SKILLS EXCHANGE",
                                        fontSize = 13.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        letterSpacing = 0.5.sp,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "Hyperlocal Resident Network",
                                        fontSize = 9.5.sp,
                                        color = Slate400
                                    )
                                }
                            }

                            // Close Button with proper padding
                            IconButton(
                                onClick = onDismiss,
                                modifier = Modifier.size(36.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Close Drawer",
                                    tint = Slate300,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // Profile & Status Block (Spacious with breathing room)
                        Column(
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            // Row 1: Large Avatar + User Name
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(SignalBlue),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = if (isAuthenticated) {
                                            extractInitials(currentUserProfile?.fullName)
                                        } else {
                                            "G"
                                        },
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    // REG-004: show the uploaded profile photo; a missing or
                                    // failed-to-load image falls back to the initials underneath.
                                    val avatarUrl = currentUserProfile?.avatarUrl.orEmpty()
                                    if (isAuthenticated && avatarUrl.isNotBlank()) {
                                        Image(
                                            painter = rememberAsyncImagePainter(model = avatarUrl),
                                            contentDescription = "Profile photo",
                                            contentScale = ContentScale.Crop,
                                            modifier = Modifier
                                                .matchParentSize()
                                                .clip(CircleShape)
                                        )
                                    }
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        val displayName = if (isAuthenticated) {
                                            // AUDIT FIX (L2): no fabricated phone number fallback.
                                            currentUserProfile?.fullName?.ifBlank { null }
                                                ?: currentUserPhone
                                                ?: "Signed-in account"
                                        } else {
                                            "Guest Neighbor"
                                        }
                                        Text(
                                            text = displayName,
                                            fontSize = 15.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White
                                        )
                                        // AUDIT FIX (H3/G5): the verified tick now reflects real KYC
                                        // status instead of being shown for every signed-in account.
                                        if (isAuthenticated && isKycVerified) {
                                            Icon(
                                                imageVector = Icons.Default.Verified,
                                                contentDescription = "National ID verified",
                                                tint = IndustrialEmerald,
                                                modifier = Modifier.size(16.dp)
                                            )
                                        }
                                    }
                                    if (isAuthenticated && !currentUserProfile?.fullName.isNullOrBlank() && currentUserPhone != null) {
                                        Text(
                                            text = currentUserPhone,
                                            fontSize = 11.5.sp,
                                            color = Slate400
                                        )
                                    }
                                }
                            }

                            // Row 2: Status Pill with high contrast background
                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = when {
                                    isKycVerified -> IndustrialEmerald.copy(alpha = 0.2f)
                                    isAuthenticated -> SignalBlue.copy(alpha = 0.25f)
                                    else -> AmberUrgent.copy(alpha = 0.2f)
                                },
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    when {
                                        isKycVerified -> IndustrialEmerald
                                        isAuthenticated -> SignalBlue
                                        else -> AmberUrgent
                                    }
                                )
                            ) {
                                Text(
                                    // AUDIT FIX (H3/G5): states the real verification level rather
                                    // than claiming "Verified Resident • ID Linked" for everyone.
                                    text = when {
                                        isKycVerified -> "🛡️ ID Verified"
                                        isAuthenticated -> "✅ Signed in • ID not verified"
                                        else -> "🔒 Guest Mode (Sign in to unlock)"
                                    },
                                    fontSize = 10.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = when {
                                        isKycVerified -> Color(0xFFA7F3D0)
                                        isAuthenticated -> Color(0xFFBFDBFE)
                                        else -> Color(0xFFFDE68A)
                                    },
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }

                            // Row 3: Location Chip
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.LocationOn,
                                    contentDescription = null,
                                    tint = Slate400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = "$currentDistrict District",
                                    fontSize = 11.sp,
                                    color = Slate300
                                )
                            }
                        }
                    }
                }

                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 2. Dual Role Switch Banner
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (userRoleMode == "Offer Skills") IndustrialEmeraldDark else SignalBlue,
                        contentColor = Color.White,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                            .clickable { onToggleUserRole() }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = if (userRoleMode == "Offer Skills") "👷 Worker Dashboard Active" else "🏡 Resident / Client Mode",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Text(
                                    text = if (userRoleMode == "Offer Skills") "Tap to switch to hiring mode" else "Tap to switch to worker dashboard",
                                    fontSize = 11.sp,
                                    lineHeight = 14.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                            Surface(
                                shape = CircleShape,
                                color = Color.White.copy(alpha = 0.2f),
                                modifier = Modifier.size(32.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Switch Role",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 3. Theme Selector
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "APPEARANCE & THEME",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 0.8.sp
                        )

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Slate100, RoundedCornerShape(8.dp))
                                .padding(3.dp),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            val options = listOf(
                                Triple("LIGHT", "Light", Icons.Default.LightMode),
                                Triple("DARK", "Dark", Icons.Default.DarkMode),
                                Triple("SYSTEM", "System", Icons.Default.SettingsSuggest)
                            )

                            options.forEach { (modeKey, label, icon) ->
                                val isSelected = themeMode == modeKey
                                Surface(
                                    onClick = { onSetThemeMode(modeKey) },
                                    modifier = Modifier.weight(1f),
                                    shape = RoundedCornerShape(6.dp),
                                    color = if (isSelected) Slate900 else Color.Transparent,
                                    contentColor = if (isSelected) Color.White else Slate700
                                ) {
                                    Row(
                                        modifier = Modifier.padding(vertical = 8.dp),
                                        horizontalArrangement = Arrangement.Center,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = icon,
                                            contentDescription = label,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = label,
                                            fontSize = 11.sp,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Divider(color = Slate200)

                    // 4. Menu Actions
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "ACCOUNT & SERVICES",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = Slate500,
                            letterSpacing = 0.8.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        DrawerMenuItem(
                            icon = Icons.Outlined.AccountBalanceWallet,
                            title = "MoMo & Escrow Wallet",
                            // AUDIT FIX (H4): real escrow total, not a hardcoded figure.
                            subtitle = if (isAuthenticated) "Escrow held: $escrowHeldTotalLabel" else "Sign in to view your escrow",
                            badgeText = "Active",
                            onClick = {
                                onDismiss()
                                onOpenWallet()
                            }
                        )

                        // REG-003 / REG-004: worker backup contact & profile photo entry point.
                        DrawerMenuItem(
                            icon = Icons.Outlined.ManageAccounts,
                            title = "Profile Photo & Backup Contact",
                            subtitle = if (isAuthenticated) "Update photo, backup number & profile" else "Sign in to edit your profile",
                            onClick = {
                                onDismiss()
                                if (!isAuthenticated) onTriggerAuth() else onOpenProfileSettings()
                            }
                        )

                        DrawerMenuItem(
                            icon = Icons.Outlined.ReceiptLong,
                            title = "Job History & Material Receipts",
                            subtitle = "View past contracts & uploaded receipts",
                            onClick = {
                                onDismiss()
                                onOpenHistory()
                            }
                        )

                        DrawerMenuItem(
                            icon = Icons.Outlined.VerifiedUser,
                            title = "National ID & Verification",
                            // AUDIT FIX (H3/G5): report the actual KYC state instead of
                            // unconditionally asserting "Verified" for any signed-in account.
                            subtitle = if (isAuthenticated) "KYC status: $kycStatusLabel" else "Action required to unlock contact info",
                            badgeText = if (isKycVerified) "Verified" else if (isAuthenticated) "Pending" else "Guest",
                            badgeColor = if (isKycVerified) IndustrialEmerald else AmberUrgent,
                            onClick = {
                                onDismiss()
                                if (!isAuthenticated) onTriggerAuth() else onOpenIdentity()
                            }
                        )

                        DrawerMenuItem(
                            icon = Icons.Outlined.Shield,
                            title = "Trust, Safety & Emergency SOS",
                            subtitle = "Dispute arbitration & job-scoped panic alerts",
                            onClick = {
                                onDismiss()
                                onOpenDisputes()
                            }
                        )

                        // AUDIT-A-CONSOLE: operator-only queue entry. Locked subtitle by
                        // default; the sheet itself enforces the same lock on actions.
                        DrawerMenuItem(
                            icon = Icons.Outlined.AdminPanelSettings,
                            title = "Admin Verification Queue",
                            subtitle = if (isAdminUnlocked) {
                                if (adminPendingCount > 0) "$adminPendingCount application(s) awaiting review"
                                else "Queue open — none awaiting review"
                            } else "Operator access locked",
                            badgeText = if (isAdminUnlocked) "Operator" else "Locked",
                            badgeColor = if (isAdminUnlocked) IndustrialEmerald else Slate500,
                            onClick = {
                                onDismiss()
                                onOpenAdminQueue()
                            }
                        )

                        // AUDIT-A-ARBITRATE: dispute arbitration console entry. UI
                        // visibility only — the `resolveDispute` callable authorizes.
                        DrawerMenuItem(
                            icon = Icons.Outlined.Gavel,
                            title = "Dispute Arbitration",
                            subtitle = "Review disputes, evidence, and issue resolutions",
                            onClick = {
                                onDismiss()
                                onOpenAdminArbitration()
                            }
                        )

                        DrawerMenuItem(
                            icon = Icons.Outlined.FileDownload,
                            title = "Data Portability & Backup",
                            subtitle = "Export database in open JSON format (GDPR)",
                            onClick = {
                                onDismiss()
                                onOpenDataBackup()
                            }
                        )

                        DrawerMenuItem(
                            icon = Icons.Outlined.Policy,
                            title = "Privacy Policy & Terms",
                            subtitle = "Data handling, MoMo escrow & safety guidelines",
                            onClick = {
                                onDismiss()
                                onOpenPrivacyPolicy()
                            }
                        )

                        if (isAuthenticated) {
                            DrawerMenuItem(
                                icon = Icons.Outlined.DeleteForever,
                                title = "Delete Account & Data",
                                subtitle = "Permanently purge records & profile",
                                badgeText = "Permanent",
                                badgeColor = RedDanger,
                                onClick = {
                                    onDismiss()
                                    onOpenDeleteAccount()
                                }
                            )
                        }
                    }

                    Divider(color = Slate200)

                    // 5. Authentication Button
                    if (isAuthenticated) {
                        OutlinedButton(
                            onClick = {
                                onLogout()
                                onDismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = RedDanger)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Logout,
                                contentDescription = "Log Out",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign Out of Account", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = {
                                onDismiss()
                                onNavigateToOnboarding()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = SignalBlue)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LockOpen,
                                contentDescription = "Sign In",
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Sign In / Register", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Text(
                        text = "Skills Exchange App v2.4.0 • Hyperlocal Network",
                        fontSize = 10.sp,
                        color = Slate400,
                        modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun DrawerMenuItem(
    icon: ImageVector,
    title: String,
    subtitle: String,
    badgeText: String? = null,
    badgeColor: Color = IndustrialEmerald,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = Color.Transparent,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp, horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Slate100, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = Slate800,
                    modifier = Modifier.size(18.dp)
                )
            }

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = title,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.weight(1f, fill = false),
                        maxLines = 1
                    )

                    if (badgeText != null) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = badgeColor.copy(alpha = 0.15f)
                        ) {
                            Text(
                                text = badgeText,
                                fontSize = 9.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = badgeColor,
                                maxLines = 1,
                                softWrap = false,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    fontSize = 10.5.sp,
                    color = Slate500,
                    maxLines = 1
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Slate400,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}
