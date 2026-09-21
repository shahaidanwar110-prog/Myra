package com.myra.ai.util

import android.content.Context
import android.os.PowerManager

object WakeLockHelper {
    private var wakeLock: PowerManager.WakeLock? = null

    @Synchronized
    fun acquireWakeLock(context: Context, timeoutMs: Long = 60000L) {
        try {
            if (wakeLock == null || wakeLock?.isHeld == false) {
                val powerManager = context.applicationContext.getSystemService(Context.POWER_SERVICE) as? PowerManager
                @Suppress("DEPRECATION")
                wakeLock = powerManager?.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ON_AFTER_RELEASE,
                    "MyraAI:TaskWakeLock"
                )
                wakeLock?.acquire(timeoutMs)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    @Synchronized
    fun releaseWakeLock() {
        try {
            if (wakeLock != null && wakeLock?.isHeld == true) {
                wakeLock?.release()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            wakeLock = null
        }
    }
}
