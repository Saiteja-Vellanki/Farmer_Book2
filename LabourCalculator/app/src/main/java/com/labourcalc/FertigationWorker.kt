package com.labourcalc

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.TimeUnit

class FertigationWorker(context: Context, params: WorkerParameters) : Worker(context, params) {

    override fun doWork(): Result {
        val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        val upcoming = mutableListOf<String>()
        for (mode in listOf("fert", "spray")) {
            val tag = if (mode == "spray") "🧪" else "🌱"
            for (p in FertStore.load(applicationContext, mode)) {
                for (s in p.sections) {
                    for (r in s.records) {
                        val t = try { fmt.parse(r.date)?.time ?: 0L } catch (e: Exception) { 0L }
                        if (t >= todayStart) {
                            val ferts = r.items.joinToString(", ") { "${it.name} ${it.qty}${it.unit}" }
                            upcoming.add("$tag ${p.name} › ${s.name}: ${r.date} ($ferts)")
                        }
                    }
                }
            }
        }
        if (upcoming.isEmpty()) return Result.success()

        val nm = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Fertigation Schedule", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val pi = PendingIntent.getActivity(
            applicationContext, 1,
            Intent(applicationContext, FertPlacesActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val text = upcoming.joinToString("\n")
        val notif = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("🌾 Farm schedule today (${upcoming.size})")
            .setContentText(upcoming.first())
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .build()
        nm.notify(2002, notif)
        return Result.success()
    }

    companion object {
        const val CHANNEL_ID = "fertigation_reminders"
        const val WORK_NAME = "fertigation_morning_6am"

        /** Daily reminder at ~6:00 AM about today's and upcoming fertigation dates. */
        fun schedule(context: Context) {
            val now = Calendar.getInstance()
            val next6 = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (before(now) || timeInMillis == now.timeInMillis) add(Calendar.DAY_OF_YEAR, 1)
            }
            val delay = next6.timeInMillis - now.timeInMillis
            val request = PeriodicWorkRequestBuilder<FertigationWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(delay, TimeUnit.MILLISECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, request
            )
        }
    }
}
