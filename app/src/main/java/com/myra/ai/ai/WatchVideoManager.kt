package com.myra.ai.ai

import com.myra.ai.accessibility.MyraAccessibilityService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlin.coroutines.resume
import kotlin.coroutines.suspendCoroutine

class WatchVideoManager(private val aiProviderManager: AiProviderManager) {

    private var watchJob: Job? = null
    private val _isWatching = MutableStateFlow(false)
    val isWatching: StateFlow<Boolean> = _isWatching.asStateFlow()

    fun startWatching(
        scope: CoroutineScope,
        onFrameAnalyzed: (String) -> Unit
    ) {
        stopWatching()
        _isWatching.value = true

        watchJob = scope.launch {
            while (_isWatching.value) {
                val service = MyraAccessibilityService.getInstance()
                if (service != null) {
                    val bitmap = suspendCoroutine { continuation ->
                        service.captureScreenshot { bmp ->
                            continuation.resume(bmp)
                        }
                    }
                    val treeText = service.dumpNodeTreeText()
                    val prompt = "You are Myra watching a playing video or screen. Describe what is currently happening in 1-2 clear, concise sentences based on this frame and screen tree text."

                    val result = aiProviderManager.describeScreen(bitmap, treeText, prompt)
                    result.onSuccess { summary ->
                        if (_isWatching.value && summary.isNotBlank()) {
                            onFrameAnalyzed(summary)
                        }
                    }.onFailure { err ->
                        if (_isWatching.value) {
                            val msg = err.localizedMessage ?: "Could not analyze frame."
                            onFrameAnalyzed("Frame update: $msg")
                        }
                    }
                } else {
                    onFrameAnalyzed("Accessibility service is disabled. Enable Myra in Accessibility Settings to watch videos.")
                }

                delay(3000L)
            }
        }
    }

    fun stopWatching() {
        _isWatching.value = false
        watchJob?.cancel()
        watchJob = null
    }
}
