package com.example.assignment_3.notifications

import android.Manifest
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import com.example.assignment_3.MainActivity
import com.example.assignment_3.R
import com.example.assignment_3.data.local.entity.Assessment
import com.example.assignment_3.ui.util.formatDueDateTime

class NotificationHelper(private val context: Context) {

    private val manager = NotificationManagerCompat.from(context)

    /** Safe to call on every launch: re-creating an existing channel is a no-op. */
    fun createChannels() {
        val channel = NotificationChannelCompat.Builder(CHANNEL_DEADLINES, NotificationManagerCompat.IMPORTANCE_HIGH)
            .setName("Deadline reminders")
            .setDescription("Reminders before an assessment is due")
            .build()
        manager.createNotificationChannel(channel)
    }

    fun canPostNotifications(): Boolean {
        val permissionGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED
        return permissionGranted && manager.areNotificationsEnabled()
    }

    fun showDeadlineReminder(assessment: Assessment, unitCode: String) {
        if (!canPostNotifications()) return

        // Explicit component plus a data URI: Navigation matches the URI against the
        // AssessmentDetail deep link without exporting an intent filter to other apps.
        val intent = Intent(Intent.ACTION_VIEW, deepLinkFor(assessment.id).toUri(), context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            context,
            assessment.id.toInt(),
            intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val title = if (unitCode.isBlank()) assessment.title else "$unitCode · ${assessment.title}"
        val notification = NotificationCompat.Builder(context, CHANNEL_DEADLINES)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText("Due ${formatDueDateTime(assessment.dueAt)} · ${assessment.weighting}% of the unit")
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            manager.notify(assessment.id.toInt(), notification)
        } catch (_: SecurityException) {
            // Permission revoked between the check and the post; nothing useful to do.
        }
    }

    companion object {
        const val CHANNEL_DEADLINES = "deadline_reminders"
        const val DEEP_LINK_BASE = "studyhive://assessment"
        fun deepLinkFor(assessmentId: Long) = "$DEEP_LINK_BASE/$assessmentId"
    }
}
