package org.koitharu.kotatsu.local.util

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.koitharu.kotatsu.R

object ConversionNotification {
    private const val CHANNEL_ID = "pdf_convert"

    fun ensureChannel(context: Context) {
        val mgr = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (mgr.getNotificationChannel(CHANNEL_ID) == null) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                context.getString(R.string.pdf_conversion),
                NotificationManager.IMPORTANCE_LOW,
            )
            mgr.createNotificationChannel(channel)
        }
    }

    fun showProgress(context: Context, id: Int, title: String, progress: Int) {
        ensureChannel(context)
        val n = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_file_zip)
            .setContentTitle(title)
            .setOnlyAlertOnce(true)
            .setProgress(100, progress, false)
            .setOngoing(progress in 0 until 100)
            .build()
        NotificationManagerCompat.from(context).notify(id, n)
    }

    fun cancel(context: Context, id: Int) {
        NotificationManagerCompat.from(context).cancel(id)
    }
}
