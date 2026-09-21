package com.myra.ai.accessibility

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.*
import android.os.Build
import android.view.*
import android.widget.*
import com.myra.ai.R
import kotlin.math.abs

enum class OrbState { IDLE, LISTENING, THINKING, SPEAKING }

class FloatingOrbView(context: Context) : View(context) {
    private val avatarBitmap: Bitmap? by lazy {
        try {
            BitmapFactory.decodeResource(resources, R.drawable.myra_avatar)
        } catch (e: Exception) {
            null
        }
    }

    private var currentState = OrbState.IDLE

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

    fun setState(state: OrbState) {
        if (currentState != state) {
            currentState = state
            when (state) {
                OrbState.LISTENING -> {
                    borderPaint.color = Color.parseColor("#06B6D4") // Cyan
                    animator.duration = 800
                }
                OrbState.THINKING -> {
                    borderPaint.color = Color.parseColor("#A855F7") // Purple/Violet
                    animator.duration = 600
                }
                OrbState.SPEAKING -> {
                    borderPaint.color = Color.parseColor("#EC4899") // Pink
                    animator.duration = 1000
                }
                OrbState.IDLE -> {
                    borderPaint.color = Color.parseColor("#FFD700") // Gold
                    animator.duration = 1500
                }
            }
            invalidate()
        }
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        animator.cancel()
    }

    private val agslOrbShaderString = """
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

            float angle = iTime * 0.4;
            mat2 rot = mat2(cos(angle), -sin(angle), sin(angle), cos(angle));
            vec2 rotUV = rot * uv;

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

            float rim = pow(1.0 - max(0.0, normal.z), 2.5);
            vec3 rimColor = mix(cyan, gold, sin(iTime) * 0.5 + 0.5) * rim * 1.8;

            vec3 lightDir = normalize(vec3(cos(iTime * 0.8) * 0.6 - 0.3, sin(iTime * 0.6) * 0.6 - 0.4, 0.8));
            vec3 viewDir = vec3(0.0, 0.0, 1.0);
            vec3 reflectDir = reflect(-lightDir, normal);
            float spec = pow(max(0.0, dot(viewDir, reflectDir)), 20.0);
            vec3 specColor = vec3(1.0, 0.98, 0.9) * spec * 1.4;

            vec3 finalCol = col * 0.85 + rimColor + specColor;
            float alpha = smoothstep(0.48, 0.44, r);
            return half4(finalCol * alpha * iPulse, alpha * 0.95 * iPulse);
        }
    """.trimIndent()

    private var orbRuntimeShader: Any? = null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            try {
                orbRuntimeShader = RuntimeShader(agslOrbShaderString)
            } catch (e: Exception) {
                orbRuntimeShader = null
            }
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseRadius = (width.coerceAtMost(height) / 2f) - 12f
        if (baseRadius <= 0) return

        val glowColor1 = when (currentState) {
            OrbState.LISTENING -> Color.parseColor("#A006B6D4")
            OrbState.THINKING -> Color.parseColor("#A0A855F7")
            OrbState.SPEAKING -> Color.parseColor("#A0EC4899")
            OrbState.IDLE -> Color.parseColor("#808A2BE2")
        }

        val glowColor2 = when (currentState) {
            OrbState.LISTENING -> Color.parseColor("#4038BDF8")
            OrbState.THINKING -> Color.parseColor("#40C084FC")
            OrbState.SPEAKING -> Color.parseColor("#40F472B6")
            OrbState.IDLE -> Color.parseColor("#40FFD700")
        }

        // Outer radial glow halo
        val glowRadius = baseRadius * pulseRadius
        glowPaint.shader = RadialGradient(
            cx, cy, glowRadius.coerceAtLeast(1f),
            intArrayOf(glowColor1, glowColor2, Color.TRANSPARENT),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP
        )
        canvas.drawCircle(cx, cy, glowRadius, glowPaint)

        // Draw 3D AGSL Orb or Layered Fallback
        val w = width.toFloat()
        val h = height.toFloat()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && orbRuntimeShader != null) {
            try {
                val shader = orbRuntimeShader as RuntimeShader
                shader.setFloatUniform("iResolution", w, h)
                shader.setFloatUniform("iTime", (System.currentTimeMillis() % 100000) / 1000f)
                shader.setFloatUniform("iPulse", pulseRadius)
                val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.shader = shader
                }
                canvas.drawCircle(cx, cy, baseRadius, paint)
                return
            } catch (e: Exception) {
                // Fallback
            }
        }

        // Fallback for older versions
        val plasmaPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            shader = RadialGradient(
                cx - baseRadius * 0.2f, cy - baseRadius * 0.2f, baseRadius * 1.2f,
                intArrayOf(
                    Color.parseColor("#06B6D4"),
                    Color.parseColor("#7C3AED"),
                    Color.parseColor("#EC4899"),
                    Color.parseColor("#FFD700")
                ),
                floatArrayOf(0f, 0.4f, 0.7f, 1f),
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawCircle(cx, cy, baseRadius, plasmaPaint)
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
    private var orbView: FloatingOrbView? = null
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

            // 2. Floating orb overlay (draggable) with size setting (Small 100dp, Medium 130dp, Large 160dp default)
            val storage = com.myra.ai.data.SecureStorage(context)
            val orbDp = when (storage.getOrbSize()) {
                "small" -> 100
                "medium" -> 130
                else -> 160
            }
            val orbSize = (orbDp * context.resources.displayMetrics.density).toInt()
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
        val state = when {
            isListening -> OrbState.LISTENING
            isSpeaking -> OrbState.SPEAKING
            else -> OrbState.IDLE
        }
        orbView?.setState(state)
    }

    fun updateOverlayState(isListening: Boolean, isThinking: Boolean, isSpeaking: Boolean) {
        edgeLightingView?.updateState(isListening || isThinking, isSpeaking)
        val state = when {
            isListening -> OrbState.LISTENING
            isThinking -> OrbState.THINKING
            isSpeaking -> OrbState.SPEAKING
            else -> OrbState.IDLE
        }
        orbView?.setState(state)
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
