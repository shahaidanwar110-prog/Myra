package com.myra.ai.voice

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentLinkedQueue

class LiveAudioEngine(
    private val context: Context,
    private val scope: CoroutineScope
) {
    companion object {
        const val SAMPLE_RATE_RECORD = 16000
        const val SAMPLE_RATE_PLAYBACK = 24000
    }

    private var audioRecord: AudioRecord? = null
    private var audioTrack: AudioTrack? = null

    private var recordJob: Job? = null
    private var playbackJob: Job? = null

    private val audioQueue = ConcurrentLinkedQueue<ByteArray>()

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying

    @Volatile
    var isMuted: Boolean = false

    var onAudioChunkCaptured: ((ByteArray) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording() {
        if (_isRecording.value) return

        val minBufSize = AudioRecord.getMinBufferSize(
            SAMPLE_RATE_RECORD,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = Math.max(minBufSize, SAMPLE_RATE_RECORD * 2 * 1) // 1 second buffer

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                SAMPLE_RATE_RECORD,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                // Fallback to MIC if VOICE_RECOGNITION fails
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    SAMPLE_RATE_RECORD,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )
            }

            audioRecord?.startRecording()
            _isRecording.value = true

            recordJob = scope.launch(Dispatchers.IO) {
                val readBuffer = ByteArray(1024) // 512 samples = 32ms chunks
                while (isActive && _isRecording.value) {
                    val bytesRead = audioRecord?.read(readBuffer, 0, readBuffer.size) ?: -1
                    if (bytesRead > 0) {
                        // Echo/feedback prevention: skip sending microphone audio while Myra is speaking
                        if (!isMuted && !_isPlaying.value) {
                            val chunk = readBuffer.copyOf(bytesRead)
                            onAudioChunkCaptured?.invoke(chunk)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            _isRecording.value = false
        }
    }

    fun stopRecording() {
        _isRecording.value = false
        recordJob?.cancel()
        recordJob = null
        try {
            audioRecord?.stop()
            audioRecord?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioRecord = null
    }

    fun initPlayback() {
        if (audioTrack != null) return

        val minBufSize = AudioTrack.getMinBufferSize(
            SAMPLE_RATE_PLAYBACK,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )

        val bufferSize = Math.max(minBufSize, SAMPLE_RATE_PLAYBACK * 2 * 2)

        audioTrack = AudioTrack(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                .build(),
            AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(SAMPLE_RATE_PLAYBACK)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build(),
            bufferSize,
            AudioTrack.MODE_STREAM,
            android.media.AudioManager.AUDIO_SESSION_ID_GENERATE
        )

        try {
            audioTrack?.play()
        } catch (e: Exception) {
            // Ignore
        }

        playbackJob = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val chunk = audioQueue.poll()
                if (chunk != null) {
                    _isPlaying.value = true
                    audioTrack?.write(chunk, 0, chunk.size)
                } else {
                    if (_isPlaying.value && audioQueue.isEmpty()) {
                        // Small buffer drain delay
                        kotlinx.coroutines.delay(100L)
                        if (audioQueue.isEmpty()) {
                            _isPlaying.value = false
                        }
                    } else {
                        kotlinx.coroutines.delay(20L)
                    }
                }
            }
        }
    }

    fun playPcmChunk(pcmData: ByteArray) {
        if (audioTrack == null) {
            initPlayback()
        }
        audioQueue.add(pcmData)
        _isPlaying.value = true
    }

    fun clearPlaybackQueue() {
        audioQueue.clear()
        _isPlaying.value = false
        try {
            audioTrack?.pause()
            audioTrack?.flush()
            audioTrack?.play()
        } catch (e: Exception) {
            // Ignore
        }
    }

    fun stopPlayback() {
        audioQueue.clear()
        _isPlaying.value = false
        playbackJob?.cancel()
        playbackJob = null
        try {
            audioTrack?.stop()
            audioTrack?.release()
        } catch (e: Exception) {
            // Ignore
        }
        audioTrack = null
    }

    fun stopAll() {
        stopRecording()
        stopPlayback()
    }
}
