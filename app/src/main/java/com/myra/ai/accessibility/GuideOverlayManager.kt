package com.myra.ai.accessibility

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Path
import android.graphics.Rect
import android.view.Gravity
import android.view.View
import android.view.WindowManager

class GuideOverlayView(
    context: Context,
    private var targetRect: Rect,
    private var instructionText: String
) : View(context) {

    private val boxPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#00E676") // Bright green
        style = Paint.Style.STROKE
        strokeWidth = 10f
    }

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#3300E676") // Semi-transparent green
        style = Paint.Style.FILL
    }

    private val arrowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFD600") // Yellow arrow
        style = Paint.Style.FILL
    }

    private val textBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000") // Dark background for label
        style = Paint.Style.FILL
    }

    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 42f
        isFakeBoldText = true
    }

    fun updateTarget(bounds: Rect, instruction: String) {
        this.targetRect = bounds
        this.instructionText = instruction
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (targetRect.isEmpty) return

        // 1. Draw target highlight box & semi-transparent overlay
        canvas.drawRect(targetRect, fillPaint)
        canvas.drawRect(targetRect, boxPaint)

        // 2. Draw pointing arrow above target
        val arrowPath = Path().apply {
            val cx = targetRect.centerX().toFloat()
            val topY = (targetRect.top - 60).toFloat().coerceAtLeast(100f)
            moveTo(cx, topY + 40f)
            lineTo(cx - 30f, topY)
            lineTo(cx + 30f, topY)
            close()
        }
        canvas.drawPath(arrowPath, arrowPaint)

        // 3. Draw instruction banner above arrow
        if (instructionText.isNotBlank()) {
            val textWidth = textPaint.measureText(instructionText)
            val textX = (targetRect.centerX() - textWidth / 2f).coerceAtLeast(20f)
            val textY = (targetRect.top - 90).toFloat().coerceAtLeast(80f)

            val bgRect = Rect(
                (textX - 16f).toInt(),
                (textY - 44f).toInt(),
                (textX + textWidth + 16f).toInt(),
                (textY + 12f).toInt()
            )
            canvas.drawRect(bgRect, textBgPaint)
            canvas.drawText(instructionText, textX, textY, textPaint)
        }
    }
}

object GuideOverlayManager {

    private var overlayView: GuideOverlayView? = null
    private var windowManager: WindowManager? = null

    fun showHighlight(service: MyraAccessibilityService, bounds: Rect, instruction: String) {
        try {
            val wm = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            if (overlayView == null) {
                val params = WindowManager.LayoutParams(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                            WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                    PixelFormat.TRANSLUCENT
                ).apply {
                    gravity = Gravity.TOP or Gravity.START
                }

                val view = GuideOverlayView(service, bounds, instruction)
                wm.addView(view, params)
                overlayView = view
            } else {
                overlayView?.updateTarget(bounds, instruction)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun clearHighlight() {
        try {
            overlayView?.let { view ->
                windowManager?.removeView(view)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            overlayView = null
            windowManager = null
        }
    }
}
