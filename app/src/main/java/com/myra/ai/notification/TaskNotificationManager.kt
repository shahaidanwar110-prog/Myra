package com.myra.ai.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.myra.ai.MainActivity
import com.myra.ai.R

class TaskStopReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_STOP_TASK) {
            onStopTaskRequested?.invoke()
        }
    }

    companion object {
        const val ACTION_STOP_TASK = "com.myra.ai.ACTION_STOP_TASK"
        var onStopTaskRequested: (() -> Unit)? = null
    }
}

class TaskNotificationManager(private val context: Context) {

    private val notificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    init {
        createNotificationChannel()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Task Execution Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows status of ongoing Myra actions and provides a Stop control."
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    fun showAssistantOverlayNotification(description: String = "Assistant Overlay active on screen") {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, TaskStopReceiver::class.java).apply {
            action = TaskStopReceiver.ACTION_STOP_TASK
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Myra Screen Assistant Active")
            .setContentText(description)
            .setSmallIcon(R.drawable.ic_myra_logo)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "STOP ASSISTANT",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun showTaskRunningNotification(taskDescription: String) {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPendingIntent = PendingIntent.getActivity(
            context,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(context, TaskStopReceiver::class.java).apply {
            action = TaskStopReceiver.ACTION_STOP_TASK
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Myra AI Running Task")
            .setContentText(taskDescription)
            .setSmallIcon(R.drawable.ic_myra_logo)
            .setOngoing(true)
            .setContentIntent(contentPendingIntent)
            .addAction(
                android.R.drawable.ic_delete,
                "STOP TASK",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()

        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    fun clearNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    companion object {
        const val CHANNEL_ID = "myra_task_channel"
        const val NOTIFICATION_ID = 1001
    }
}
