package com.myra.ai.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import android.widget.*
import com.myra.ai.R
import kotlin.math.abs

class FloatingOrbView(context: Context) : View(context) {
    private val avatarBitmap: Bitmap? by lazy {
        try {
            BitmapFactory.decodeResource(resources, R.drawable.myra_avatar)
        } catch (e: Exception) {
            null
        }
    }

    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 6f
        color = Color.parseColor("#FFD700") // Gold border
    }

    private var pulseRadius = 0f
    private val animator = ValueAnimator.ofFloat(0.85f, 1.15f).apply {
        duration = 1500
        repeatCount = ValueAnimator.INFINITE
        repeatMode = ValueAnimator.REVERSE
        addUpdateListener {
            pulseRadius = it.animatedValue as Float
            invalidate()
        }
    }

    init {
        animator.start()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 2f) - 16f
        if (baseRadius <= 0) return

        // Soft radial glow background
        val glowRadius = baseRadius * pulseRadius
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius.coerceAtLeast(1f),
            intArrayOf(
                Color.parseColor("#808A2BE2"), // Purple alpha
                Color.parseColor("#40FFD700"), // Gold alpha
                Color.TRANSPARENT
            ),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // Avatar circle
        val bmp = avatarBitmap
        if (bmp != null) {
            val srcRect = Rect(0, 0, bmp.width, bmp.height)
            val destRect = RectF(cx - baseRadius, cy - baseRadius, cx + baseRadius, cy + baseRadius)

            val path = Path().apply {
                addCircle(cx, cy, baseRadius, Path.Direction.CW)
            }
            canvas.save()
            canvas.clipPath(path)
            canvas.drawBitmap(bmp, srcRect, destRect, null)
            canvas.restore()
        } else {
            val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#7C3AED")
                style = Paint.Style.FILL
            }
            canvas.drawCircle(cx, cy, baseRadius, fillPaint)
        }

        // Gold border
        canvas.drawCircle(cx, cy, baseRadius, borderPaint)
    }
}

class EdgeLightingView(context: Context) : View(context) {
    private var isListening = false
    private var isSpeaking = false
    private var animTime = 0f

    private val animator = ValueAnimator.ofFloat(0f, 1f).apply {
        duration = 2000
        repeatCount = ValueAnimator.INFINITE
        addUpdateListener {
            animTime = it.animatedFraction
            invalidate()
        }
    }

    // AGSL Shader code for Android 13+ (API 33+)
    private val agslShaderString = """
        uniform vec2 iResolution;
        uniform float iTime;

        half4 main(in vec2 fragCoord) {
            vec2 uv = fragCoord / iResolution;

            // Edge distance (0 at center, 1 at edge)
            float edgeDistX = min(uv.x, 1.0 - uv.x);
            float edgeDistY = min(uv.y, 1.0 - uv.y);
            float edgeDist = min(edgeDistX, edgeDistY);

            // Border width
            float borderWidth = 0.035;
            if (edgeDist > borderWidth) {
                return half4(0.0, 0.0, 0.0, 0.0);
            }

            float alpha = smoothstep(borderWidth, 0.0, edgeDist);

            // Flowing colors: Purple, Cyan, Pink, Gold
            float t = iTime * 2.0 + (uv.x + uv.y) * 4.0;
            vec3 purple = vec3(0.48, 0.22, 0.92);
            vec3 cyan   = vec3(0.0, 0.83, 1.0);
            vec3 pink   = vec3(0.92, 0.22, 0.65);
            vec3 gold   = vec3(1.0, 0.84, 0.0);

            float factor1 = 0.5 + 0.5 * sin(t);
            float factor2 = 0.5 + 0.5 * cos(t * 0.7);

            vec3 col = mix(mix(purple, cyan, factor1), mix(pink, gold, factor2), sin(t * 0.5) * 0.5 + 0.5);
            return half4(col * alpha, alpha * 0.85);
        }
    """.trimIndent()

    private var runtimeShader: Any? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                runtimeShader = android.graphics.RuntimeShader(agslShaderString)
            } catch (e: Exception) {
                runtimeShader = null
            }
        }
        animator.start()
    }

    fun updateState(listening: Boolean, speaking: Boolean) {
        this.isListening = listening
        this.isSpeaking = speaking
        visibility = if (isListening || isSpeaking) VISIBLE else GONE
        invalidate()
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (!isListening && !isSpeaking) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && runtimeShader != null) {
            try {
                val shader = runtimeShader as android.graphics.RuntimeShader
                shader.setFloatUniform("iResolution", w, h)
                shader.setFloatUniform("iTime", animTime * 10f)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                }
                canvas.drawRect(0f, 0f, w, h, paint)
                return
            } catch (e: Exception) {
                // Fallback
            }
        }

        // Fallback for older Android versions (< 13) or shader error
        val borderWidth = 24f
        val colors = intArrayOf(
            Color.parseColor("#7C3AED"), // Purple
            Color.parseColor("#06B6D4"), // Cyan
            Color.parseColor("#EC4899"), // Pink
            Color.parseColor("#FFD700"), // Gold
            Color.parseColor("#7C3AED")  // Purple loop
        )

        val rotationAngle = animTime * 360f
        val matrix = Matrix().apply {
            setRotate(rotationAngle, w / 2f, h / 2f)
        }
        val sweepGradient = SweepGradient(w / 2f, h / 2f, colors, null)
        sweepGradient.setLocalMatrix(matrix)

        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = sweepGradient
            style = Paint.Style.STROKE
            strokeWidth = borderWidth
        }

        canvas.drawRect(borderWidth / 2f, borderWidth / 2f, w - borderWidth / 2f, h - borderWidth / 2f, borderPaint)
    }
}

object AssistantOverlayManager {

    private var windowManager: WindowManager? = null
    private var orbView: View? = null
    private var quickChatView: View? = null
    private var edgeLightingView: EdgeLightingView? = null

    private var orbParams: WindowManager.LayoutParams? = null

    var onStopOverlayRequested: (() -> Unit)? = null
    var onUserSubmitPrompt: ((String) -> Unit)? = null
    var onMicClickRequested: (() -> Unit)? = null

    private var chatHistoryText = StringBuilder()
    private var chatTextView: TextView? = null

    fun isOverlayActive(): Boolean = orbView != null

    fun showOverlay(context: Context) {
        if (orbView != null) return

        try {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager = wm

            // 1. Edge lighting overlay (non-touchable)
            val edgeParams = WindowManager.LayoutParams(
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

            val edgeView = EdgeLightingView(context)
            edgeView.visibility = View.GONE
            wm.addView(edgeView, edgeParams)
            edgeLightingView = edgeView

            // 2. Floating orb overlay (draggable)
            val orbSize = (72 * context.resources.displayMetrics.density).toInt()
            val params = WindowManager.LayoutParams(
                orbSize,
                orbSize,
                WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
                PixelFormat.TRANSLUCENT
            ).apply {
                gravity = Gravity.TOP or Gravity.START
                x = 100
                y = 300
            }
            orbParams = params

            val orb = FloatingOrbView(context)

            var initialX = 0
            var initialY = 0
            var initialTouchX = 0f
            var initialTouchY = 0f
            var isClick = true

            orb.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        initialX = params.x
                        initialY = params.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        isClick = true
                        true
                    }
                    MotionEvent.ACTION_MOVE -> {
                        val dx = (event.rawX - initialTouchX).toInt()
                        val dy = (event.rawY - initialTouchY).toInt()
                        if (abs(dx) > 10 || abs(dy) > 10) {
                            isClick = false
                        }
                        params.x = initialX + dx
                        params.y = initialY + dy
                        windowManager?.updateViewLayout(orbView, params)
                        true
                    }
                    MotionEvent.ACTION_UP -> {
                        if (isClick) {
                            toggleQuickChat(context)
                        }
                        true
                    }
                    else -> false
                }
            }

            wm.addView(orb, params)
            orbView = orb

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun toggleQuickChat(context: Context) {
        val wm = windowManager ?: return

        if (quickChatView != null) {
            try {
                wm.removeView(quickChatView)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            quickChatView = null
            return
        }

        // Create quick chat view layout
        val layout = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#EE12131A"))
            setPadding(32, 32, 32, 32)
        }

        // Header with Title & Stop Button
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val title = TextView(context).apply {
            text = "Myra Assistant"
            setTextColor(Color.parseColor("#FFD700"))
            textSize = 18f
            setTypeface(null, Typeface.BOLD)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val stopButton = Button(context).apply {
            text = "Stop"
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.parseColor("#DC2626")) // Red
            setOnClickListener {
                hideOverlay()
                onStopOverlayRequested?.invoke()
            }
        }

        header.addView(title)
        header.addView(stopButton)
        layout.addView(header)

        // Chat messages scrollable view
        val scrollView = ScrollView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
                1f
            ).apply {
                topMargin = 16
                bottomMargin = 16
            }
        }

        val chatTv = TextView(context).apply {
            text = if (chatHistoryText.isBlank()) "Myra overlay is ready. Ask anything or say 'look at my screen'." else chatHistoryText.toString()
            setTextColor(Color.WHITE)
            textSize = 14f
        }
        chatTextView = chatTv
        scrollView.addView(chatTv)
        layout.addView(scrollView)

        // Input row: Text Field + Mic + Send
        val inputRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }

        val editText = EditText(context).apply {
            hint = "Ask about screen..."
            setHintTextColor(Color.GRAY)
            setTextColor(Color.WHITE)
            textSize = 14f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }

        val micBtn = Button(context).apply {
            text = "🎤"
            textSize = 16f
            setOnClickListener {
                onMicClickRequested?.invoke()
            }
        }

        val sendBtn = Button(context).apply {
            text = "Send"
            setOnClickListener {
                val prompt = editText.text.toString().trim()
                if (prompt.isNotEmpty()) {
                    appendChatMessage("You: $prompt")
                    onUserSubmitPrompt?.invoke(prompt)
                    editText.setText("")
                }
            }
        }

        inputRow.addView(editText)
        inputRow.addView(micBtn)
        inputRow.addView(sendBtn)
        layout.addView(inputRow)

        val chatParams = WindowManager.LayoutParams(
            (context.resources.displayMetrics.widthPixels * 0.9).toInt(),
            (context.resources.displayMetrics.heightPixels * 0.45).toInt(),
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        wm.addView(layout, chatParams)
        quickChatView = layout
    }

    fun updateAudioState(isListening: Boolean, isSpeaking: Boolean) {
        edgeLightingView?.updateState(isListening, isSpeaking)
    }

    fun appendChatMessage(message: String) {
        if (chatHistoryText.isNotEmpty()) {
            chatHistoryText.append("\n\n")
        }
        chatHistoryText.append(message)
        chatTextView?.post {
            chatTextView?.text = chatHistoryText.toString()
        }
    }

    fun hideOverlay() {
        try {
            orbView?.let { windowManager?.removeView(it) }
            quickChatView?.let { windowManager?.removeView(it) }
            edgeLightingView?.let { windowManager?.removeView(it) }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            orbView = null
            quickChatView = null
            edgeLightingView = null
            windowManager = null
            chatTextView = null
            chatHistoryText.clear()
        }
    }
}
