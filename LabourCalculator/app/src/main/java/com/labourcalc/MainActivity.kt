package com.labourcalc

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!SetupManager.isActivated(this)) {
            startActivity(Intent(this, SetupActivity::class.java))
            finish()
            return
        }

        setContentView(R.layout.activity_main)
        findViewById<android.view.View>(R.id.headerHome).padBelowStatusBar()

        findViewById<TextView>(R.id.tvWelcome).text =
            "Welcome to ${SetupManager.userName(this)}"

        findViewById<CardView>(R.id.cardWorker).setOnClickListener {
            startActivity(Intent(this, WorkerEntryActivity::class.java))
        }
        findViewById<CardView>(R.id.cardFert).setOnClickListener {
            startActivity(
                Intent(this, FertPlacesActivity::class.java).putExtra("mode", "fert")
            )
        }
        findViewById<CardView>(R.id.cardSpray).setOnClickListener {
            startActivity(
                Intent(this, FertPlacesActivity::class.java).putExtra("mode", "spray")
            )
        }

        if (Build.VERSION.SDK_INT >= 33) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val labourReminder = PeriodicWorkRequestBuilder<ReminderWorker>(6, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            ReminderWorker.WORK_NAME, ExistingPeriodicWorkPolicy.KEEP, labourReminder
        )
        FertigationWorker.schedule(this)
    }

}
