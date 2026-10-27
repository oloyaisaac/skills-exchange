package com.example.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.example.data.db.JobEntity
import com.example.ui.components.InteractiveMapView.MapWithFallbackFallback
import com.example.ui.theme.SkillsExchangeTheme

@Composable
fun ActiveJobScreen(
    job: JobEntity,
    selectedProvider: ServiceProviderEntity? = null,
    onProviderSelected: ((String) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    skillsExchangeTheme {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .horizontalPadding(16.dp),
            verticalArrangement = Arrangement.Start,
            horizontalArrangement = Arrangement.Start
        ) {
            JobDetailsSection(job = job)

            Spacer(modifier = Modifier.height(16.dp))

            <MapWithFallbackFallback
                modifier = Modifier.fillMaxWidth()
            ) { showMap ->
                if (showMap) {
                    // MapView rendered by MapWithFallbackFallback when API key is present
                    // (handled inside the composable via isMapsApiKeyPresent check)
                } else {
                    // MAP-003 fallback UI: show message and actions when Maps is unavailable
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "Map location is currently unavailable.",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium,
                            color = Slate700,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { /* open COMM-001 masked chat with worker */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SkillsExchangeTheme.colors.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Contact Worker",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkillsExchangeTheme.colors.onPrimary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { /* proceed without map */ },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SkillsExchangeTheme.colors.primary
                            ),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                        ) {
                            Text(
                                text = "Continue Without Map",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = SkillsExchangeTheme.colors.onPrimary
                            )
                        }
                    }
                }
            }
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = SkillsExchangeTheme.colors.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Continue Without Map",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkillsExchangeTheme.colors.onPrimary
                    )
                }
            } else {
                Button(
                    onClick = {},
                    colors = ButtonDefaults.buttonColors(containerColor = SkillsExchangeTheme.colors.primary),
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                ) {
                    Text(
                        text = "Contact Worker",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = SkillsExchangeTheme.colors.onPrimary
                    )
                }
            }
        }
    }
}

@Composable
private fun JobDetailsSection(job: JobEntity) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.Elevation.dp4,
        shape = RoundedCornerShape(8.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            horizontalAlignment = Arrangement.Start
        ) {
            Text(
                text = job.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = SkillsExchangeTheme.colors.primary
            )

            Text(
                text = job.description,
                fontSize = 14.sp,
                color = Slate900,
                style = MaterialTheme.typography.body2
            )

            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Chip(
                    label = Text(text = job.category ?: "Uncategorized", fontSize = 12.sp),
                    filterChipDefaults = ChipDefaults.copy(selectionIndicator = { false })
                )
                Text(
                    text = job.status?.name ?: "Pending",
                    fontSize = 12.sp,
                    color = Slate600
                )
            }
        }
    }
}