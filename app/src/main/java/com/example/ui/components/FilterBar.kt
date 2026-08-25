package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

@Composable
fun FilterBar(
    selectedCategory: String?,
    onCategorySelect: (String?) -> Unit,
    selectedRadius: Double,
    onRadiusSelect: (Double) -> Unit,
    onlyVerified: Boolean,
    onToggleVerified: () -> Unit,
    onOpenFilterDrawer: () -> Unit,
    modifier: Modifier = Modifier
) {
    val categories = listOf("Plumbing", "Electrical", "Mechanic", "Tutoring", "Cleaning", "Carpentry", "Gardening")
    val radiusOptions = listOf(0.5, 2.0, 5.0, 10.0)

    Surface(
        color = MaterialTheme.colorScheme.surface,
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
        ) {
            // Category Scrollable Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Filter Drawer Trigger Button
                IconButton(
                    onClick = onOpenFilterDrawer,
                    modifier = Modifier
                        .size(24.dp)
                        .background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(6.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(6.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Filters",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(13.dp)
                    )
                }

                // All Categories Chip
                FilterChip(
                    selected = selectedCategory == null,
                    onClick = { onCategorySelect(null) },
                    label = { Text("All Skills", fontSize = 9.5.sp, fontWeight = FontWeight.Bold) },
                    modifier = Modifier.height(24.dp),
                    shape = RoundedCornerShape(6.dp),
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.onSurface,
                        selectedLabelColor = MaterialTheme.colorScheme.surface,
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = FilterChipDefaults.filterChipBorder(
                        enabled = true,
                        selected = selectedCategory == null,
                        borderColor = MaterialTheme.colorScheme.outline,
                        selectedBorderColor = MaterialTheme.colorScheme.onSurface
                    )
                )

                // Individual Category Chips
                categories.forEach { category ->
                    val isSelected = selectedCategory?.equals(category, ignoreCase = true) == true
                    FilterChip(
                        selected = isSelected,
                        onClick = { onCategorySelect(if (isSelected) null else category) },
                        label = { Text(category, fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold) },
                        modifier = Modifier.height(24.dp),
                        shape = RoundedCornerShape(6.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = SignalBlue,
                            selectedLabelColor = Color.White,
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = isSelected,
                            borderColor = MaterialTheme.colorScheme.outline,
                            selectedBorderColor = SignalBlue
                        )
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 3.dp),
                thickness = 1.dp,
                color = Slate100
            )

            // Radius & Verification Quick Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Radius Options
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Radius:",
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate500
                    )
                    radiusOptions.forEach { r ->
                        val isSelected = selectedRadius == r
                        Surface(
                            onClick = { onRadiusSelect(r) },
                            shape = RoundedCornerShape(4.dp),
                            color = if (isSelected) SignalBlueLight else MaterialTheme.colorScheme.surfaceVariant,
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) SignalBlue else MaterialTheme.colorScheme.outline
                            )
                        ) {
                            Text(
                                text = "${if (r % 1.0 == 0.0) r.toInt() else r} mi",
                                fontSize = 9.sp,
                                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.SemiBold,
                                color = if (isSelected) SignalBlueDark else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                            )
                        }
                    }
                }

                // Verified Toggle Chip
                Surface(
                    onClick = onToggleVerified,
                    shape = RoundedCornerShape(4.dp),
                    color = if (onlyVerified) IndustrialEmeraldLight else MaterialTheme.colorScheme.surfaceVariant,
                    border = androidx.compose.foundation.BorderStroke(
                        1.dp,
                        if (onlyVerified) IndustrialEmerald else MaterialTheme.colorScheme.outline
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.VerifiedUser,
                            contentDescription = "Verified",
                            tint = if (onlyVerified) IndustrialEmerald else Slate500,
                            modifier = Modifier.size(11.dp)
                        )
                        Text(
                            text = "Verified Only",
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (onlyVerified) IndustrialEmeraldDark else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
