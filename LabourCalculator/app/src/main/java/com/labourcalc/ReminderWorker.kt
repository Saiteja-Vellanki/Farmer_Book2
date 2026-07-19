package com.labourcalc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Worker
import androidx.work.WorkerParameters

class ReminderWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val unpaid = LabourStore.load(applicationContext).filter { !it.isPaid && it.total > 0 }
        if (unpaid.isEmpty()) return Result.success()

        val totalDue = unpaid.sumOf { it.balance }
        val names = unpaid.joinToString(", ") { "${it.place} ${it.date} (₹${"%.0f".format(it.balance)})" }

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Payment Reminders",
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }

        val intent = Intent(applicationContext, MainActivity::class.java)
        val pi = PendingIntent.getActivity(
            applicationContext, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Labour payments pending: ₹${"%.0f".format(totalDue)}")
            .setContentText(names)
            .setStyle(NotificationCompat.BigTextStyle().bigText(names))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()

        nm.notify(1001, notif)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "labour_reminders"
        const val WORK_NAME = "labour_payment_reminder"
    }
}
