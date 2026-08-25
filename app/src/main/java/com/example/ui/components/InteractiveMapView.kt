package com.example.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.db.ServiceProviderEntity
import com.example.ui.theme.*
import com.example.util.CurrencyRegion
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun InteractiveMapView(
    providers: List<ServiceProviderEntity>,
    selectedProvider: ServiceProviderEntity?,
    onSelectProvider: (ServiceProviderEntity) -> Unit,
    radiusMiles: Double,
    activeRegion: CurrencyRegion = CurrencyRegion.currentRegion,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFEBF1F5))
            .border(1.dp, Slate300)
    ) {
        // Interactive Canvas Map Surface
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(providers) {
                    detectTapGestures { tapOffset ->
                        // Calculate tap proximity to provider pins
                        val width = size.width.toFloat()
                        val height = size.height.toFloat()
                        val centerX = width / 2f
                        val centerY = height / 2f

                        var closestProvider: ServiceProviderEntity? = null
                        var minDistance = Float.MAX_VALUE

                        providers.forEachIndexed { index, provider ->
                            val angle = (index * (360f / providers.size.coerceAtLeast(1))) * (Math.PI / 180f)
                            val distPx = (provider.distanceMiles / 5.0) * (width.coerceAtMost(height) * 0.35f)
                            val pinX = (centerX + cos(angle) * distPx).toFloat()
                            val pinY = (centerY + sin(angle) * distPx).toFloat()

                            val dx = tapOffset.x - pinX
                            val dy = tapOffset.y - pinY
                            val d = kotlin.math.sqrt(dx * dx + dy * dy)
                            if (d < 60f && d < minDistance) {
                                minDistance = d
                                closestProvider = provider
                            }
                        }

                        closestProvider?.let { onSelectProvider(it) }
                    }
                }
        ) {
            val width = size.width
            val height = size.height
            val center = Offset(width / 2f, height / 2f)
            val maxRadiusPx = width.coerceAtMost(height) * 0.38f

            // Draw Background Grid Blocks
            val gridStep = 60f
            var x = 0f
            while (x < width) {
                drawLine(
                    color = Color(0xFFDFE6ED),
                    start = Offset(x, 0f),
                    end = Offset(x, height),
                    strokeWidth = 1f
                )
                x += gridStep
            }
            var y = 0f
            while (y < height) {
                drawLine(
                    color = Color(0xFFDFE6ED),
                    start = Offset(0f, y),
                    end = Offset(width, y),
                    strokeWidth = 1f
                )
                y += gridStep
            }

            // Draw River Feature
            val riverPath = Path().apply {
                moveTo(0f, height * 0.2f)
                cubicTo(width * 0.3f, height * 0.1f, width * 0.6f, height * 0.4f, width, height * 0.35f)
                lineTo(width, height * 0.42f)
                cubicTo(width * 0.6f, height * 0.47f, width * 0.3f, height * 0.17f, 0f, height * 0.27f)
                close()
            }
            drawPath(path = riverPath, color = Color(0xFFBAE6FD).copy(alpha = 0.6f))

            // Draw Radial Distance Proximity Rings
            listOf(0.5, 2.0, 5.0).forEach { rMiles ->
                val ringRadiusPx = (rMiles / 5.0) * maxRadiusPx
                drawCircle(
                    color = if (rMiles == radiusMiles) SignalBlue else Slate400.copy(alpha = 0.4f),
                    radius = ringRadiusPx.toFloat(),
                    center = center,
                    style = Stroke(
                        width = if (rMiles == radiusMiles) 2.5f else 1f,
                        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(12f, 8f), 0f)
                    )
                )
            }

            // Draw Center User Location Beacon
            drawCircle(color = SignalBlue.copy(alpha = pulseAlpha * 0.4f), radius = 24f, center = center)
            drawCircle(color = SignalBlue, radius = 8f, center = center)
            drawCircle(color = Color.White, radius = 4f, center = center)

            // Draw Provider Pins
            providers.forEachIndexed { index, provider ->
                val angle = (index * (360f / providers.size.coerceAtLeast(1))) * (Math.PI / 180f)
                val distPx = (provider.distanceMiles / 5.0) * maxRadiusPx
                val pinX = (center.x + cos(angle) * distPx).toFloat()
                val pinY = (center.y + sin(angle) * distPx).toFloat()
                val pinOffset = Offset(pinX, pinY)

                val isSelected = selectedProvider?.id == provider.id

                // Selection glow pulse
                if (isSelected) {
                    drawCircle(
                        color = SignalBlue.copy(alpha = pulseAlpha),
                        radius = 28f,
                        center = pinOffset
                    )
                }

                // Pin Outer Circle
                drawCircle(
                    color = if (isSelected) Slate900 else SignalBlue,
                    radius = if (isSelected) 18f else 14f,
                    center = pinOffset
                )
                // Pin Inner Core
                drawCircle(
                    color = if (provider.isVerifiedResident) IndustrialEmerald else Color.White,
                    radius = if (isSelected) 8f else 6f,
                    center = pinOffset
                )
            }
        }

        // Selected Provider Callout Card Overlay
        selectedProvider?.let { provider ->
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp)
                    .fillMaxWidth(0.9f)
                    .shadow(8.dp, RoundedCornerShape(8.dp)),
                shape = RoundedCornerShape(8.dp),
                color = Color.White,
                border = androidx.compose.foundation.BorderStroke(1.5.dp, SignalBlue)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = provider.name,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                color = Slate900
                            )
                            if (provider.isVerifiedResident) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Verified",
                                    tint = IndustrialEmerald,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "${provider.tagline} • ${provider.neighborhood}",
                            fontSize = 11.sp,
                            color = Slate600
                        )

                        Row(
                            modifier = Modifier.padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Star,
                                    contentDescription = "Rating",
                                    tint = AmberUrgent,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = " ${provider.rating}",
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Slate900
                                )
                            }
                            Text(
                                text = "${provider.distanceMiles} mi away",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = SignalBlue
                            )
                            Text(
                                text = "${CurrencyRegion.format(provider.hourlyRate, activeRegion)}/hr",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.ExtraBold,
                                color = IndustrialEmeraldDark
                            )
                        }
                    }

                    Button(
                        onClick = { onSelectProvider(provider) },
                        colors = ButtonDefaults.buttonColors(containerColor = SignalBlue),
                        shape = RoundedCornerShape(6.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "View Profile",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Radial Filter Legend Badge Top Left
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopStart),
            shape = RoundedCornerShape(6.dp),
            color = Slate900.copy(alpha = 0.9f),
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(SignalBlue)
                )
                Text(
                    text = "Proximity Filter: $radiusMiles mi • ${providers.size} Nearby Specialists",
                    fontSize = 11.sp,
                    color = Color.White,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }

        // Floating Center GPS Button Top Right
        Surface(
            modifier = Modifier
                .padding(12.dp)
                .align(Alignment.TopEnd),
            shape = RoundedCornerShape(6.dp),
            color = Color.White,
            border = androidx.compose.foundation.BorderStroke(1.dp, Slate300),
            shadowElevation = 4.dp
        ) {
            Row(
                modifier = Modifier
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.LocationOn,
                    contentDescription = "GPS Active",
                    tint = SignalBlue,
                    modifier = Modifier.size(14.dp)
                )
                Text(
                    text = "GPS Centered",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Slate800
                )
            }
        }
    }
}
