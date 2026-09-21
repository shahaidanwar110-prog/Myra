package com.myra.ai.ui.components

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import com.myra.ai.ui.theme.*
import java.io.File
import kotlin.random.Random

private data class Bubble(
    val id: Int,
    val xRatio: Float,
    var startYOffsetRatio: Float,
    val radiusDp: Float,
    val speed: Float,
    val color: Color,
    val baseAlpha: Float,
    val pulseSpeed: Int
)

@Composable
fun AnimatedWallpaperBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    var wallpaperBitmap by remember { mutableStateOf<Bitmap?>(null) }

    // Check for myra_wallpaper.png in repo root or sdcard/app storage
    LaunchedEffect(Unit) {
        val candidates = listOf(
            File("myra_wallpaper.png"),
            File("/sdcard/myra_wallpaper.png"),
            File("/data/local/tmp/myra_wallpaper.png")
        )
        for (file in candidates) {
            if (file.exists() && file.canRead()) {
                try {
                    val bmp = BitmapFactory.decodeFile(file.absolutePath)
                    if (bmp != null) {
                        wallpaperBitmap = bmp
                        break
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        val currentBmp = wallpaperBitmap
        if (currentBmp != null) {
            // Render custom wallpaper image behind a subtle midnight translucent overlay
            Image(
                bitmap = currentBmp.asImageBitmap(),
                contentDescription = "Myra Custom Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(
                                Color(0xBB0B0B12),
                                Color(0xDD12101F),
                                Color(0xF20B0B12)
                            )
                        )
                    )
            )
        } else {
            // Render deep midnight gradient with slowly drifting colored aurora and soft floating bubbles
            val infiniteTransition = rememberInfiniteTransition(label = "wallpaper_anim")

            // Aurora drift animations
            val time1 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(24000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "auroraTime1"
            )

            val time2 by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(18000, easing = LinearEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "auroraTime2"
            )

            val pulseAlpha by infiniteTransition.animateFloat(
                initialValue = 0.6f,
                targetValue = 1.0f,
                animationSpec = infiniteRepeatable(
                    animation = tween(4000, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "pulseAlpha"
            )

            // Random seed for stable bubbles
            val bubbles = remember {
                val rand = Random(42)
                val colors = listOf(
                    PrimaryPurple,
                    SecondaryCyan,
                    VioletAccent,
                    GoldPrimary,
                    Color(0xFFEC4899) // Pink
                )
                List(16) { id ->
                    Bubble(
                        id = id,
                        xRatio = rand.nextFloat(),
                        startYOffsetRatio = rand.nextFloat(),
                        radiusDp = rand.nextFloat() * 24f + 12f,
                        speed = rand.nextFloat() * 0.12f + 0.05f,
                        color = colors[id % colors.size],
                        baseAlpha = rand.nextFloat() * 0.15f + 0.08f,
                        pulseSpeed = rand.nextInt(2000, 5000)
                    )
                }
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                // 1. Base deep midnight background gradient
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF090910),
                            Color(0xFF130F21),
                            Color(0xFF0D0B16),
                            Color(0xFF06060B)
                        )
                    ),
                    size = size
                )

                // 2. Drifting Aurora Blob 1 (Purple/Violet)
                val aurora1X = w * (0.3f + 0.35f * kotlin.math.sin(time1 * 2 * Math.PI.toFloat()))
                val aurora1Y = h * (0.25f + 0.2f * kotlin.math.cos(time1 * 2 * Math.PI.toFloat()))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            PrimaryPurple.copy(alpha = 0.28f * pulseAlpha),
                            VioletAccent.copy(alpha = 0.15f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = Offset(aurora1X, aurora1Y),
                        radius = w * 0.75f
                    ),
                    center = Offset(aurora1X, aurora1Y),
                    radius = w * 0.75f
                )

                // 3. Drifting Aurora Blob 2 (Cyan/Pink)
                val aurora2X = w * (0.7f - 0.35f * kotlin.math.cos(time2 * 2 * Math.PI.toFloat()))
                val aurora2Y = h * (0.65f + 0.25f * kotlin.math.sin(time2 * 2 * Math.PI.toFloat()))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            SecondaryCyan.copy(alpha = 0.22f * pulseAlpha),
                            Color(0xFFEC4899).copy(alpha = 0.14f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = Offset(aurora2X, aurora2Y),
                        radius = w * 0.70f
                    ),
                    center = Offset(aurora2X, aurora2Y),
                    radius = w * 0.70f
                )

                // 4. Drifting Aurora Blob 3 (Gold Glow)
                val aurora3X = w * (0.5f + 0.2f * kotlin.math.sin((time1 + 0.5f) * 2 * Math.PI.toFloat()))
                val aurora3Y = h * (0.85f - 0.2f * kotlin.math.cos((time2 + 0.3f) * 2 * Math.PI.toFloat()))
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = 0.16f * pulseAlpha),
                            Color.Transparent
                        ),
                        center = Offset(aurora3X, aurora3Y),
                        radius = w * 0.55f
                    ),
                    center = Offset(aurora3X, aurora3Y),
                    radius = w * 0.55f
                )

                // 5. Soft floating translucent bubbles
                bubbles.forEach { b ->
                    val totalProgress = (b.startYOffsetRatio + time1 * b.speed * 8f) % 1.0f
                    val bubbleY = h * (1.05f - totalProgress * 1.15f)
                    val swayX = w * (b.xRatio + 0.04f * kotlin.math.sin((time1 * 6f + b.id)))
                    val radiusPx = b.radiusDp * density

                    // Pulse multiplier
                    val bubblePulse = 0.7f + 0.3f * kotlin.math.sin((time1 * 10f + b.id))
                    val currentAlpha = (b.baseAlpha * bubblePulse).coerceIn(0.02f, 0.35f)

                    drawCircle(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                b.color.copy(alpha = currentAlpha),
                                b.color.copy(alpha = currentAlpha * 0.3f),
                                Color.Transparent
                            ),
                            center = Offset(swayX, bubbleY),
                            radius = radiusPx
                        ),
                        center = Offset(swayX, bubbleY),
                        radius = radiusPx
                    )
                }
            }
        }

        content()
    }
}
