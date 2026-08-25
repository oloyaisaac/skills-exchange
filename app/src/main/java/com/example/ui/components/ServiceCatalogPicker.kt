package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ServiceTaxonomyCatalog
import com.example.data.db.ServiceTaxonomyItem
import com.example.ui.theme.*

/**
 * SKILL-003: 125-Service Catalog UI Selector for Worker Onboarding & Profile Editing
 * Enforces 1 Primary Skill + up to 2 Related Additional Skills within the same trade family.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServiceCatalogPickerModal(
    isOpen: Boolean,
    onDismiss: () -> Unit,
    initialPrimarySkill: String? = null,
    initialAdditionalSkills: List<String> = emptyList(),
    onConfirmSkills: (primarySkill: String, additionalSkills: List<String>) -> Unit
) {
    if (!isOpen) return

    var selectedCategory by remember { mutableStateOf("Construction") }
    var selectedPrimarySkill by remember { mutableStateOf<ServiceTaxonomyItem?>(initialPrimarySkill?.let { ServiceTaxonomyCatalog.findService(it) }) }
    val selectedAdditionalSkills = remember { mutableStateListOf<ServiceTaxonomyItem>().apply {
        initialAdditionalSkills.mapNotNull { ServiceTaxonomyCatalog.findService(it) }.forEach { add(it) }
    } }
    var validationError by remember { mutableStateOf<String?>(null) }

    val categories = listOf("Construction", "Cleaning", "Technology", "Automotive", "Personal")

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.White,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Select Your Trade Skills",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Slate900
                    )
                    Text(
                        text = "1 Primary Skill + up to 2 Related Skills (125-Service Catalog)",
                        fontSize = 12.sp,
                        color = Slate500
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Close", tint = Slate500)
                }
            }

            // Category Selector Tabs
            LazyRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                items(categories) { category ->
                    val isSelected = selectedCategory == category
                    Surface(
                        color = if (isSelected) Slate900 else Slate100,
                        contentColor = if (isSelected) Color.White else Slate700,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier.clickable { selectedCategory = category }
                    ) {
                        Text(
                            text = category,
                            fontSize = 13.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp)
                        )
                    }
                }
            }

            // Current Selection Preview Summary
            Surface(
                color = Slate50,
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "PRIMARY: ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SignalBlue
                        )
                        Text(
                            text = selectedPrimarySkill?.let { "${it.name} (${it.skillFamily})" } ?: "None selected",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = if (selectedPrimarySkill != null) Slate900 else Slate400
                        )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ADDITIONAL (${selectedAdditionalSkills.size}/2): ",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = ForestGreen
                        )
                        Text(
                            text = if (selectedAdditionalSkills.isNotEmpty()) {
                                selectedAdditionalSkills.joinToString(", ") { it.name }
                            } else "None (optional, up to 2)",
                            fontSize = 12.sp,
                            color = if (selectedAdditionalSkills.isNotEmpty()) Slate800 else Slate400
                        )
                    }
                }
            }

            if (validationError != null) {
                Surface(
                    color = RedDangerLight,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = RedDanger, modifier = Modifier.size(16.dp))
                        Text(text = validationError ?: "", fontSize = 12.sp, color = RedDanger)
                    }
                }
            }

            // Service Items List
            val categoryServices = ServiceTaxonomyCatalog.getServicesByCategory(selectedCategory)

            Text(
                text = "$selectedCategory Services (${categoryServices.size})",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = Slate700
            )

            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(categoryServices) { service ->
                    val isPrimary = selectedPrimarySkill?.id == service.id
                    val isAdditional = selectedAdditionalSkills.any { it.id == service.id }

                    Surface(
                        color = when {
                            isPrimary -> Blue50
                            isAdditional -> Emerald50
                            else -> Color.White
                        },
                        shape = RoundedCornerShape(10.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            when {
                                isPrimary -> SignalBlue
                                isAdditional -> ForestGreen
                                else -> Slate200
                            }
                        ),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(
                                        text = service.name,
                                        fontSize = 13.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Slate900
                                    )
                                    Surface(
                                        color = Slate100,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = service.skillFamily,
                                            fontSize = 10.sp,
                                            color = Slate600,
                                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "Qualification: ${service.qualExpected}",
                                    fontSize = 11.sp,
                                    color = Slate500
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                // Set Primary Button
                                Button(
                                    onClick = {
                                        selectedPrimarySkill = service
                                        selectedAdditionalSkills.removeAll { it.id == service.id }
                                        // Revalidate existing additional skills against new primary
                                        if (selectedPrimarySkill != null) {
                                            val res = ServiceTaxonomyCatalog.validateWorkerSkills(
                                                service.name,
                                                selectedAdditionalSkills.map { it.name }
                                            )
                                            validationError = if (!res.isValid) res.errorMessage else null
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isPrimary) SignalBlue else Slate100,
                                        contentColor = if (isPrimary) Color.White else Slate700
                                    ),
                                    shape = RoundedCornerShape(6.dp),
                                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Text(text = if (isPrimary) "★ Primary" else "Primary", fontSize = 11.sp)
                                }

                                // Toggle Additional Button (only available once primary is chosen)
                                if (!isPrimary) {
                                    OutlinedButton(
                                        onClick = {
                                            if (isAdditional) {
                                                selectedAdditionalSkills.removeAll { it.id == service.id }
                                                validationError = null
                                            } else {
                                                if (selectedPrimarySkill == null) {
                                                    validationError = "Please select a Primary Skill first."
                                                    return@OutlinedButton
                                                }
                                                if (selectedAdditionalSkills.size >= 2) {
                                                    validationError = "Maximum 2 additional skills allowed."
                                                    return@OutlinedButton
                                                }
                                                val candidateList = selectedAdditionalSkills.map { it.name } + service.name
                                                val res = ServiceTaxonomyCatalog.validateWorkerSkills(
                                                    selectedPrimarySkill!!.name,
                                                    candidateList
                                                )
                                                if (res.isValid) {
                                                    selectedAdditionalSkills.add(service)
                                                    validationError = null
                                                } else {
                                                    validationError = res.errorMessage
                                                }
                                            }
                                        },
                                        shape = RoundedCornerShape(6.dp),
                                        colors = ButtonDefaults.outlinedButtonColors(
                                            containerColor = if (isAdditional) Emerald100 else Color.Transparent,
                                            contentColor = if (isAdditional) ForestGreen else Slate600
                                        ),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                        modifier = Modifier.height(30.dp)
                                    ) {
                                        Text(text = if (isAdditional) "✓ Added" else "+ Add", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Confirm Button
            Button(
                onClick = {
                    val primary = selectedPrimarySkill
                    if (primary == null) {
                        validationError = "Please choose a Primary Skill before saving."
                        return@Button
                    }
                    val res = ServiceTaxonomyCatalog.validateWorkerSkills(
                        primary.name,
                        selectedAdditionalSkills.map { it.name }
                    )
                    if (!res.isValid) {
                        validationError = res.errorMessage
                        return@Button
                    }
                    onConfirmSkills(primary.name, selectedAdditionalSkills.map { it.name })
                    onDismiss()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(46.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Slate900)
            ) {
                Text(
                    text = "Confirm Skills Selection",
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
        }
    }
}
