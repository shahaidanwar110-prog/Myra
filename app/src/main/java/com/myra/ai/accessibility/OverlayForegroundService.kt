package com.myra.ai.accessibility

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.myra.ai.MainActivity
import com.myra.ai.R

class OverlayForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == ACTION_STOP_OVERLAY) {
            stopOverlayService()
            return START_NOT_STICKY
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(this)) {
            val errMsg = "Cannot start overlay service: 'Display over other apps' permission is missing."
            com.myra.ai.util.DiagnosticsHelper.lastError = errMsg
            com.myra.ai.util.EventLogger.logEvent(
                eventType = "PERMISSION_DENIED",
                tag = "OverlayForegroundService",
                message = errMsg,
                reason = "SYSTEM_ALERT_WINDOW permission missing"
            )
            stopSelf()
            return START_NOT_STICKY
        }

        try {
            val notification = createNotification()
            startForeground(NOTIFICATION_ID, notification)

            val serviceInstance = MyraAccessibilityService.getInstance()
            if (serviceInstance != null) {
                AssistantOverlayManager.showOverlay(serviceInstance)
            } else {
                AssistantOverlayManager.showOverlay(this)
            }
        } catch (e: Exception) {
            val errMsg = "Failed to start overlay service: ${e.localizedMessage ?: e.message}"
            com.myra.ai.util.DiagnosticsHelper.lastError = errMsg
            com.myra.ai.util.EventLogger.logEvent(
                eventType = "SERVICE_ERROR",
                tag = "OverlayForegroundService",
                message = errMsg,
                reason = e.stackTraceToString()
            )
            stopSelf()
            return START_NOT_STICKY
        }

        return START_STICKY
    }

    private fun stopOverlayService() {
        AssistantOverlayManager.hideOverlay()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } else {
            @Suppress("DEPRECATION")
            stopForeground(true)
        }
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Myra Overlay Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps Myra floating overlay orb active on screen"
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java)
        val openAppPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, OverlayForegroundService::class.java).apply {
            action = ACTION_STOP_OVERLAY
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Myra Overlay Active")
            .setContentText("Myra floating orb is running on top of other apps.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(openAppPendingIntent)
            .setOngoing(true)
            .addAction(R.drawable.myra_avatar, "Stop", stopPendingIntent)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        AssistantOverlayManager.hideOverlay()
    }

    companion object {
        const val CHANNEL_ID = "myra_overlay_foreground_channel"
        const val NOTIFICATION_ID = 2001
        const val ACTION_STOP_OVERLAY = "com.myra.ai.ACTION_STOP_OVERLAY"

        fun start(context: Context) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !android.provider.Settings.canDrawOverlays(context)) {
                val errMsg = "Cannot start floating overlay: 'Display over other apps' permission is required."
                com.myra.ai.util.DiagnosticsHelper.lastError = errMsg
                com.myra.ai.util.EventLogger.logEvent(
                    eventType = "PERMISSION_DENIED",
                    tag = "OverlayForegroundService",
                    message = errMsg
                )
                return
            }

            try {
                val intent = Intent(context, OverlayForegroundService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                val errMsg = "Overlay service auto-start failed: ${e.localizedMessage ?: e.message}"
                com.myra.ai.util.DiagnosticsHelper.lastError = errMsg
                com.myra.ai.util.EventLogger.logEvent(
                    eventType = "SERVICE_ERROR",
                    tag = "OverlayForegroundService",
                    message = errMsg,
                    reason = e.stackTraceToString()
                )
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, OverlayForegroundService::class.java).apply {
                action = ACTION_STOP_OVERLAY
            }
            context.startService(intent)
            AssistantOverlayManager.hideOverlay()
        }
    }
}
