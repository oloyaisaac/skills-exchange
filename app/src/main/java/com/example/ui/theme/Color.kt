package com.example.ui.theme

import androidx.compose.ui.graphics.Color

val Slate900 = Color(0xFF0F172A)
val Slate800 = Color(0xFF1E293B)
val Slate700 = Color(0xFF334155)
val Slate600 = Color(0xFF475569)
val Slate500 = Color(0xFF64748B)
// R12-P2-06: 0xFF94A3B8 measured 2.56:1 on white — below even the 3:1 non-text floor, and it was
// being used for real sentences (dialog placeholders, "Cancel", drawer captions), not just tint.
// Darkened to 3.5:1 for graphics; every sentence that used it moved to Slate500 (4.76:1).
val Slate400 = Color(0xFF6B7A90)
val Slate300 = Color(0xFFCBD5E1)
val Slate200 = Color(0xFFE2E8F0)
val Slate100 = Color(0xFFF1F5F9)
val Slate50 = Color(0xFFF8FAFC)

val SignalBlue = Color(0xFF2563EB)
val SignalBlueDark = Color(0xFF1D4ED8)
val SignalBlueLight = Color(0xFFEFF6FF)

// R12-P2-06: white text on the old 0xFF059669 measured 3.77:1 — below the 4.5:1 AA floor for body
// text, on the buttons that carry the worker's onboarding and the escrow release. Darkened one step
// (5.48:1) so the same white-on-emerald pairing passes without touching a single call site;
// `IndustrialEmeraldDark` moves down with it to stay distinguishable (7.68:1).
val IndustrialEmerald = Color(0xFF047857)
val IndustrialEmeraldDark = Color(0xFF065F46)
val IndustrialEmeraldLight = Color(0xFFECFDF5)

val AmberUrgent = Color(0xFFD97706)
val AmberUrgentLight = Color(0xFFFFFBEB)

val RedDanger = Color(0xFFDC2626)
val RedDangerLight = Color(0xFFFEF2F2)

val Emerald50 = Color(0xFFECFDF5)
val Emerald100 = Color(0xFFDCFCE7)
val Emerald500 = Color(0xFF10B981)
val Emerald600 = IndustrialEmerald
val Emerald700 = IndustrialEmeraldDark

val Blue50 = SignalBlueLight

val ForestGreen = Color(0xFF15803D)

val PrimaryIndigo = SignalBlueDark

val Amber100 = Color(0xFFFEF3C7)
val Amber500 = Color(0xFFF59E0B)
val Amber700 = Color(0xFFB45309)
val Amber800 = Color(0xFF92400E)
