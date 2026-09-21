package com.myra.ai.ui.components

import android.graphics.Matrix
import android.graphics.SweepGradient
import android.os.Build
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas

private const val AGSL_EDGE_LIGHTING_SHADER = """
    uniform vec2 iResolution;
    uniform float iTime;
    uniform float iIntensity;

    half4 main(in vec2 fragCoord) {
        vec2 uv = fragCoord / iResolution;

        // Edge distance (0 at outer edge, 1 at center)
        float edgeDistX = min(uv.x, 1.0 - uv.x);
        float edgeDistY = min(uv.y, 1.0 - uv.y);
        float edgeDist = min(edgeDistX, edgeDistY);

        // Border width scaling
        float borderWidth = 0.025 * iIntensity;
        if (edgeDist > borderWidth) {
            return half4(0.0, 0.0, 0.0, 0.0);
        }

        float alpha = smoothstep(borderWidth, 0.0, edgeDist);

        // Flowing multi-color blend along borders (purple, cyan, pink, gold, green)
        float t = iTime * 2.5 + (uv.x + uv.y) * 5.0;
        vec3 purple = vec3(0.48, 0.22, 0.92);
        vec3 cyan   = vec3(0.0, 0.83, 1.0);
        vec3 pink   = vec3(0.92, 0.22, 0.65);
        vec3 gold   = vec3(1.0, 0.84, 0.0);
        vec3 green  = vec3(0.06, 0.72, 0.51);

        float factor1 = 0.5 + 0.5 * sin(t);
        float factor2 = 0.5 + 0.5 * cos(t * 0.7);

        vec3 col = mix(mix(purple, cyan, factor1), mix(pink, mix(gold, green, factor2), sin(t * 0.5) * 0.5 + 0.5), factor2);
        return half4(col * alpha * iIntensity, alpha * 0.9 * iIntensity);
    }
"""

@Composable
fun FlowingEdgeLightingContainer(
    isListening: Boolean,
    isSpeaking: Boolean,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(modifier = modifier.fillMaxSize()) {
        content()

        if (isListening || isSpeaking) {
            val infiniteTransition = rememberInfiniteTransition(label = "edge_lighting")
            val animTime by infiniteTransition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2000, easing = LinearEasing),
                    repeatMode = RepeatMode.Restart
                ),
                label = "animTime"
            )

            val intensity = if (isSpeaking) 1.3f else 0.85f

            val agslEdgeShader = remember {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    try {
                        android.graphics.RuntimeShader(AGSL_EDGE_LIGHTING_SHADER)
                    } catch (e: Exception) {
                        null
                    }
                } else null
            }

            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && agslEdgeShader != null) {
                    try {
                        agslEdgeShader.setFloatUniform("iResolution", w, h)
                        agslEdgeShader.setFloatUniform("iTime", animTime * 10f)
                        agslEdgeShader.setFloatUniform("iIntensity", intensity)

                        drawIntoCanvas { canvas ->
                            val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                                shader = agslEdgeShader
                            }
                            canvas.nativeCanvas.drawRect(0f, 0f, w, h, paint)
                        }
                        return@Canvas
                    } catch (e: Exception) {
                        // Fallback
                    }
                }

                // Fallback for older Android versions (< 13)
                val strokeW = 20f * intensity
                val colors = intArrayOf(
                    android.graphics.Color.parseColor("#7C3AED"), // Purple
                    android.graphics.Color.parseColor("#06B6D4"), // Cyan
                    android.graphics.Color.parseColor("#EC4899"), // Pink
                    android.graphics.Color.parseColor("#FFD700"), // Gold
                    android.graphics.Color.parseColor("#10B981"), // Green
                    android.graphics.Color.parseColor("#7C3AED")  // Loop back
                )

                val sweep = SweepGradient(w / 2f, h / 2f, colors, null)
                val matrix = Matrix().apply {
                    setRotate(animTime * 360f, w / 2f, h / 2f)
                }
                sweep.setLocalMatrix(matrix)

                drawIntoCanvas { canvas ->
                    val borderPaint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
                        shader = sweep
                        style = android.graphics.Paint.Style.STROKE
                        strokeWidth = strokeW
                        alpha = if (isSpeaking) 240 else 180
                    }
                    canvas.nativeCanvas.drawRect(strokeW / 2f, strokeW / 2f, w - strokeW / 2f, h - strokeW / 2f, borderPaint)
                }
            }
        }
    }
}
