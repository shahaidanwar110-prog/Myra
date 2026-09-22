package com.myra.ai.ui.components

import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.dp
import com.myra.ai.ui.theme.*
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private const val AGSL_ORB_SHADER = """
    uniform vec2 iResolution;
    uniform float iTime;
    uniform float iPulse;

    half4 main(in vec2 fragCoord) {
        vec2 uv = (fragCoord - 0.5 * iResolution) / min(iResolution.x, iResolution.y);
        float r = length(uv);

        if (r > 0.48) {
            float edgeAlpha = smoothstep(0.50, 0.48, r);
            return half4(0.0, 0.0, 0.0, edgeAlpha * 0.05);
        }

        // 3D Sphere Normal & Depth
        float z = sqrt(0.25 - r * r);
        vec3 normal = normalize(vec3(uv.x, uv.y, z));

        // Slow rotation angle
        float angle = iTime * 0.4;
        mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
        vec2 rotUV = rot * uv;

        // Swirling Colored Plasma Inside
        float t = iTime * 1.2;
        float plasma1 = sin(rotUV.x * 10.0 + t) + cos(rotUV.y * 10.0 - t);
        float plasma2 = sin((rotUV.x + rotUV.y) * 8.0 + t * 1.4);

        vec3 purple = vec3(0.48, 0.22, 0.92);
        vec3 cyan   = vec3(0.0, 0.83, 1.0);
        vec3 pink   = vec3(0.92, 0.22, 0.65);
        vec3 gold   = vec3(1.0, 0.84, 0.0);
        vec3 green  = vec3(0.06, 0.72, 0.51);

        float factor1 = 0.5 + 0.5 * plasma1;
        float factor2 = 0.5 + 0.5 * plasma2;

        vec3 col = mix(mix(purple, cyan, factor1), mix(pink, mix(gold, green, factor2), sin(t * 0.8) * 0.5 + 0.5), factor2);

        // Bright Rim Light (Fresnel)
        float rim = pow(1.0 - max(0.0, normal.z), 2.5);
        vec3 rimColor = mix(cyan, gold, sin(iTime) * 0.5 + 0.5) * rim * 1.8;

        // Moving Specular Highlight
        vec3 lightDir = normalize(vec3(cos(iTime * 0.8) * 0.6 - 0.3, sin(iTime * 0.6) * 0.6 - 0.4, 0.8));
        vec3 viewDir = vec3(0.0, 0.0, 1.0);
        vec3 reflectDir = reflect(-lightDir, normal);
        float spec = pow(max(0.0, dot(viewDir, reflectDir)), 20.0);
        vec3 specColor = vec3(1.0, 0.98, 0.9) * spec * 1.4;

        // Combine Lighting & Swirl Plasma
        vec3 finalCol = col * 0.85 + rimColor + specColor;

        // Soft outer edge alpha blending
        float alpha = smoothstep(0.48, 0.44, r);
        return half4(finalCol * alpha * iPulse, alpha * 0.95 * iPulse);
    }
"""

private data class LightBubble(
    val id: Int,
    val xOffsetRatio: Float,
    val startProgress: Float,
    val radiusDp: Float,
    val color: Color
)

@Composable
fun GlowingOrbCenterpiece(
    isSpeaking: Boolean,
    centerpieceStyle: String = "orb",
    modifier: Modifier = Modifier
) {
    when (centerpieceStyle.lowercase()) {
        "character", "girl" -> {
            // Animated Myra Character Video (with Chroma Key & Cross-Fade)
            AnimatedCharacterCenterpiece(
                isSpeaking = isSpeaking,
                modifier = modifier
            )
        }
        "both" -> {
            // Both: 3D Glowing Orb in background with Animated Character Video layered on top!
            Box(
                modifier = modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                OrbGraphicContent(isSpeaking = isSpeaking)
                AnimatedCharacterCenterpiece(
                    isSpeaking = isSpeaking,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
        else -> {
            // Default 3D Glowing Orb Centerpiece
            OrbGraphicContent(
                isSpeaking = isSpeaking,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun OrbGraphicContent(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "orb_centerpiece")

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSpeaking) 1.15f else 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 500 else 1800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val timeAnim by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(12000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "timeAnim"
    )

    val lightBubbles = remember {
        val rand = Random(123)
        val colors = listOf(PrimaryPurple, SecondaryCyan, GoldPrimary, VioletAccent, Color(0xFFEC4899))
        List(12) { id ->
            LightBubble(
                id = id,
                xOffsetRatio = (rand.nextFloat() - 0.5f) * 1.2f,
                startProgress = rand.nextFloat(),
                radiusDp = rand.nextFloat() * 10f + 6f,
                color = colors[id % colors.size]
            )
        }
    }

    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.Center
    ) {
        // Rising light bubbles around orb while speaking / active
        Canvas(
            modifier = Modifier.size(280.dp)
        ) {
            val cx = size.width / 2f
            val cy = size.height / 2f
            val baseRadius = size.width * 0.35f

            lightBubbles.forEach { bubble ->
                val progress = (bubble.startProgress + timeAnim * (if (isSpeaking) 2.5f else 1.0f)) % 1.0f
                val bubbleY = cy + baseRadius * 0.2f - progress * baseRadius * 2.2f
                val bubbleX = cx + bubble.xOffsetRatio * baseRadius * (1f + progress * 0.5f)
                val alpha = (1.0f - progress).coerceIn(0f, 0.85f) * (if (isSpeaking) 0.9f else 0.4f)
                val rPx = bubble.radiusDp * density

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            bubble.color.copy(alpha = alpha),
                            bubble.color.copy(alpha = alpha * 0.2f),
                            Color.Transparent
                        ),
                        center = Offset(bubbleX, bubbleY),
                        radius = rPx
                    ),
                    center = Offset(bubbleX, bubbleY),
                    radius = rPx
                )
            }
        }

        // Outer pulse glow halo behind orb
        Box(
            modifier = Modifier
                .scale(pulseScale)
                .size(240.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            PrimaryPurple.copy(alpha = if (isSpeaking) 0.5f else 0.35f),
                            SecondaryCyan.copy(alpha = if (isSpeaking) 0.4f else 0.25f),
                            GoldPrimary.copy(alpha = 0.15f),
                            Color.Transparent
                        )
                    )
                )
        )

        // 3D Glowing Orb Canvas with AGSL Shader or Layered Fallback
        val agslShader = remember {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                try {
                    RuntimeShader(AGSL_ORB_SHADER)
                } catch (e: Exception) {
                    null
                }
            } else null
        }

        Canvas(
            modifier = Modifier
                .scale(pulseScale)
                .size(220.dp)
        ) {
            val w = size.width
            val h = size.height
            val cx = w / 2f
            val cy = h / 2f
            val radius = minOf(w, h) / 2f - 8f

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslShader != null) {
                try {
                    agslShader.setFloatUniform("iResolution", w, h)
                    agslShader.setFloatUniform("iTime", timeAnim * 12f)
                    agslShader.setFloatUniform("iPulse", if (isSpeaking) 1.15f else 1.0f)

                    drawIntoCanvas { canvas ->
                        val nativePaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                            shader = agslShader
                        }
                        canvas.nativeCanvas.drawCircle(cx, cy, radius, nativePaint)
                    }
                    return@Canvas
                } catch (e: Exception) {
                    // Fallback
                }
            }

            // Fallback for older Android versions (< 13)
            // 1. Plasma Core
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        SecondaryCyan,
                        PrimaryPurple,
                        VioletAccent,
                        Color(0xFFEC4899),
                        GoldPrimary
                    ),
                    center = Offset(cx - radius * 0.2f, cy - radius * 0.2f),
                    radius = radius
                ),
                center = Offset(cx, cy),
                radius = radius
            )

            // 2. Rotating Gradient Overlay
            val colors = intArrayOf(
                android.graphics.Color.parseColor("#7C3AED"),
                android.graphics.Color.parseColor("#06B6D4"),
                android.graphics.Color.parseColor("#EC4899"),
                android.graphics.Color.parseColor("#FFD700"),
                android.graphics.Color.parseColor("#7C3AED")
            )
            val sweepGrad = android.graphics.SweepGradient(cx, cy, colors, null)
            val matrix = android.graphics.Matrix().apply {
                setRotate(timeAnim * 360f, cx, cy)
            }
            sweepGrad.setLocalMatrix(matrix)

            drawIntoCanvas { canvas ->
                val sweepPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                    shader = sweepGrad
                    alpha = 160
                }
                canvas.nativeCanvas.drawCircle(cx, cy, radius * 0.95f, sweepPaint)
            }

            // 3. Bright Rim Light
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.Transparent,
                        GoldLight.copy(alpha = 0.3f),
                        GoldPrimary.copy(alpha = 0.7f)
                    ),
                    center = Offset(cx, cy),
                    radius = radius
                ),
                center = Offset(cx, cy),
                radius = radius
            )

            // 4. Moving Specular Highlight Dot
            val specX = cx - radius * 0.35f + cos(timeAnim * 2 * Math.PI.toFloat()) * radius * 0.15f
            val specY = cy - radius * 0.35f + sin(timeAnim * 2 * Math.PI.toFloat()) * radius * 0.15f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.9f),
                        Color.White.copy(alpha = 0.3f),
                        Color.Transparent
                    ),
                    center = Offset(specX, specY),
                    radius = radius * 0.35f
                ),
                center = Offset(specX, specY),
                radius = radius * 0.35f
            )
        }
    }
}
