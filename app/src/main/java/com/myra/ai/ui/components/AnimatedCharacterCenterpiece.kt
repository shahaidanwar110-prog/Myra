package com.myra.ai.ui.components

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.SurfaceTexture
import android.net.Uri
import android.opengl.*
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.view.Surface
import android.view.TextureView
import androidx.annotation.OptIn
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.myra.ai.R
import com.myra.ai.ui.theme.GoldPrimary
import com.myra.ai.ui.theme.PrimaryPurple
import com.myra.ai.ui.theme.VioletAccent
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

private const val TAG = "AnimatedCharacterView"

@OptIn(UnstableApi::class)
@Composable
fun AnimatedCharacterCenterpiece(
    isSpeaking: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var isIdleError by remember { mutableStateOf(false) }
    var isTalkError by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "character_pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1.0f,
        targetValue = if (isSpeaking) 1.12f else 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(if (isSpeaking) 600 else 2000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    // Cross-fade animation factor: 0.0f = full idle, 1.0f = full talk
    val crossFadeAlpha by animateFloatAsState(
        targetValue = if (isSpeaking) 1.0f else 0.0f,
        animationSpec = tween(durationMillis = 350, easing = LinearOutSlowInEasing),
        label = "crossFadeAlpha"
    )

    // ExoPlayer for idle clip
    val idlePlayer = remember {
        try {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse("asset:///myra_idle.mp4"))
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Idle player error", error)
                        isIdleError = true
                    }
                })
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create idle player", e)
            isIdleError = true
            null
        }
    }

    // ExoPlayer for talk clip
    val talkPlayer = remember {
        try {
            ExoPlayer.Builder(context).build().apply {
                val mediaItem = MediaItem.fromUri(Uri.parse("asset:///myra_talk.mp4"))
                setMediaItem(mediaItem)
                repeatMode = Player.REPEAT_MODE_ONE
                volume = 0f
                addListener(object : Player.Listener {
                    override fun onPlayerError(error: PlaybackException) {
                        Log.e(TAG, "Talk player error", error)
                        isTalkError = true
                    }
                })
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to create talk player", e)
            isTalkError = true
            null
        }
    }

    // Lifecycle observer to pause players when backgrounded and resume when active
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    idlePlayer?.pause()
                    talkPlayer?.pause()
                }
                Lifecycle.Event.ON_RESUME -> {
                    idlePlayer?.play()
                    talkPlayer?.play()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            idlePlayer?.release()
            talkPlayer?.release()
        }
    }

    Box(
        modifier = modifier
            .scale(pulseScale)
            .fillMaxWidth()
            .height(300.dp),
        contentAlignment = Alignment.Center
    ) {
        // Soft radial glowing aura background behind character (ethereal glow)
        Box(
            modifier = Modifier
                .size(260.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            GoldPrimary.copy(alpha = if (isSpeaking) 0.50f else 0.32f),
                            PrimaryPurple.copy(alpha = if (isSpeaking) 0.40f else 0.25f),
                            VioletAccent.copy(alpha = 0.18f),
                            Color.Transparent
                        )
                    )
                )
        )

        if (isIdleError || isTalkError || idlePlayer == null || talkPlayer == null) {
            // Fallback to static girl image if video clips fail to load
            Image(
                painter = painterResource(id = R.drawable.myra_logo),
                contentDescription = "Myra Character",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxHeight()
                    .padding(8.dp)
            )
        } else {
            // Cross-fading chroma-key video layers rendered large and unobstructed
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .aspectRatio(16f / 9f),
                contentAlignment = Alignment.Center
            ) {
                // Idle Video Clip (Alpha = 1 - crossFadeAlpha)
                AndroidView(
                    factory = { ctx ->
                        ChromaKeyTextureView(ctx, idlePlayer)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = (1.0f - crossFadeAlpha).coerceIn(0f, 1f) }
                )

                // Talk Video Clip (Alpha = crossFadeAlpha)
                AndroidView(
                    factory = { ctx ->
                        ChromaKeyTextureView(ctx, talkPlayer)
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = crossFadeAlpha.coerceIn(0f, 1f) }
                )
            }
        }
    }
}

/**
 * Custom TextureView rendering ExoPlayer video with an OpenGL ES 2.0 Chroma-Key Shader.
 * Removes solid green background with soft smoothstep alpha edge and green spill suppression.
 */
private class ChromaKeyTextureView(
    context: Context,
    private val player: ExoPlayer
) : TextureView(context), TextureView.SurfaceTextureListener {

    private var renderThread: HandlerThread? = null
    private var renderHandler: Handler? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        val thread = HandlerThread("ChromaKeyGLRenderThread").apply { start() }
        renderThread = thread
        val handler = Handler(thread.looper)
        renderHandler = handler

        handler.post {
            initGL(surface, width, height)
        }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderHandler?.post {
            releaseGL()
        }
        renderThread?.quitSafely()
        renderThread = null
        renderHandler = null
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}

    private var eglDisplay: EGLDisplay? = null
    private var eglContext: EGLContext? = null
    private var eglSurface: EGLSurface? = null

    private var textureId: Int = 0
    private var videoSurfaceTexture: SurfaceTexture? = null
    private var videoSurface: Surface? = null

    private var program: Int = 0
    private var aPositionHandle: Int = 0
    private var aTextureCoordHandle: Int = 0
    private var uSTMatrixHandle: Int = 0
    private var uTextureHandle: Int = 0

    private val stMatrix = FloatArray(16)

    private val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                -1.0f, -1.0f,
                 1.0f, -1.0f,
                -1.0f,  1.0f,
                 1.0f,  1.0f
            ))
            position(0)
        }

    private val textureBuffer: FloatBuffer = ByteBuffer.allocateDirect(8 * 4)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer()
        .apply {
            put(floatArrayOf(
                0.0f, 0.0f,
                1.0f, 0.0f,
                0.0f, 1.0f,
                1.0f, 1.0f
            ))
            position(0)
        }

    private fun initGL(surfaceTexture: SurfaceTexture, width: Int, height: Int) {
        try {
            val display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY)
            eglDisplay = display
            val version = IntArray(2)
            EGL14.eglInitialize(display, version, 0, version, 1)

            val configAttribs = intArrayOf(
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_DEPTH_SIZE, 0,
                EGL14.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            EGL14.eglChooseConfig(display, configAttribs, 0, configs, 0, 1, numConfigs, 0)
            val config = configs[0]

            val contextAttribs = intArrayOf(
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
            )
            val context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT, contextAttribs, 0)
            eglContext = context

            val surfaceAttribs = intArrayOf(EGL14.EGL_NONE)
            val surface = EGL14.eglCreateWindowSurface(display, config, surfaceTexture, surfaceAttribs, 0)
            eglSurface = surface

            EGL14.eglMakeCurrent(display, surface, surface, context)

            program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            if (program == 0) return

            aPositionHandle = GLES20.glGetAttribLocation(program, "aPosition")
            aTextureCoordHandle = GLES20.glGetAttribLocation(program, "aTextureCoord")
            uSTMatrixHandle = GLES20.glGetUniformLocation(program, "uSTMatrix")
            uTextureHandle = GLES20.glGetUniformLocation(program, "sTexture")

            val textures = IntArray(1)
            GLES20.glGenTextures(1, textures, 0)
            textureId = textures[0]

            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameterf(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR.toFloat())
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)

            val vTex = SurfaceTexture(textureId).apply {
                setDefaultBufferSize(width, height)
                setOnFrameAvailableListener {
                    renderHandler?.post {
                        drawFrame(width, height)
                    }
                }
            }
            videoSurfaceTexture = vTex
            val vSurf = Surface(vTex)
            videoSurface = vSurf

            val mainHandler = Handler(android.os.Looper.getMainLooper())
            mainHandler.post {
                try {
                    player.setVideoSurface(vSurf)
                } catch (e: Exception) {
                    Log.e(TAG, "Error setting player surface", e)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "GL Initialization failed", e)
        }
    }

    private fun drawFrame(width: Int, height: Int) {
        val display = eglDisplay ?: return
        val surface = eglSurface ?: return
        val vTex = videoSurfaceTexture ?: return

        try {
            vTex.updateTexImage()
            vTex.getTransformMatrix(stMatrix)

            GLES20.glViewport(0, 0, width, height)
            GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f)
            GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            GLES20.glUseProgram(program)

            vertexBuffer.position(0)
            GLES20.glVertexAttribPointer(aPositionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)
            GLES20.glEnableVertexAttribArray(aPositionHandle)

            textureBuffer.position(0)
            GLES20.glVertexAttribPointer(aTextureCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer)
            GLES20.glEnableVertexAttribArray(aTextureCoordHandle)

            GLES20.glUniformMatrix4fv(uSTMatrixHandle, 1, false, stMatrix, 0)

            GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId)
            GLES20.glUniform1i(uTextureHandle, 0)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

            EGL14.eglSwapBuffers(display, surface)
        } catch (e: Exception) {
            Log.e(TAG, "Error drawing GL frame", e)
        }
    }

    private fun releaseGL() {
        try {
            videoSurface?.release()
            videoSurface = null
            videoSurfaceTexture?.release()
            videoSurfaceTexture = null

            if (eglDisplay != null && eglSurface != null) {
                EGL14.eglDestroySurface(eglDisplay, eglSurface)
                eglSurface = null
            }
            if (eglDisplay != null && eglContext != null) {
                EGL14.eglDestroyContext(eglDisplay, eglContext)
                eglContext = null
            }
            if (eglDisplay != null) {
                EGL14.eglTerminate(eglDisplay)
                eglDisplay = null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing GL resources", e)
        }
    }

    private fun createProgram(vertexSource: String, fragmentSource: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource)
        if (vertexShader == 0) return 0
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource)
        if (fragmentShader == 0) return 0

        var prog = GLES20.glCreateProgram()
        if (prog != 0) {
            GLES20.glAttachShader(prog, vertexShader)
            GLES20.glAttachShader(prog, fragmentShader)
            GLES20.glLinkProgram(prog)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e(TAG, "Could not link program: ${GLES20.glGetProgramInfoLog(prog)}")
                GLES20.glDeleteProgram(prog)
                prog = 0
            }
        }
        return prog
    }

    private fun loadShader(shaderType: Int, source: String): Int {
        var shader = GLES20.glCreateShader(shaderType)
        if (shader != 0) {
            GLES20.glShaderSource(shader, source)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e(TAG, "Could not compile shader $shaderType: ${GLES20.glGetShaderInfoLog(shader)}")
                GLES20.glDeleteShader(shader)
                shader = 0
            }
        }
        return shader
    }

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec4 aPosition;
            attribute vec4 aTextureCoord;
            varying vec2 vTextureCoord;
            uniform mat4 uSTMatrix;
            void main() {
                gl_Position = aPosition;
                vTextureCoord = (uSTMatrix * aTextureCoord).xy;
            }
        """

        private const val FRAGMENT_SHADER = """
            #extension GL_OES_EGL_image_external : require
            precision mediump float;
            varying vec2 vTextureCoord;
            uniform samplerExternalOES sTexture;

            void main() {
                vec4 color = texture2D(sTexture, vTextureCoord);

                // Green chroma keying
                float maxRB = max(color.r, color.b);
                float greenDiff = color.g - maxRB;

                // Smoothstep alpha threshold for anti-aliased edges
                float alpha = 1.0 - smoothstep(0.10, 0.32, greenDiff);

                // Green spill suppression on edges
                if (greenDiff > 0.0) {
                    color.g = maxRB + greenDiff * (1.0 - alpha);
                }

                color.a = alpha;
                color.rgb *= alpha; // Premultiplied alpha blending

                gl_FragColor = color;
            }
        """
    }
}
