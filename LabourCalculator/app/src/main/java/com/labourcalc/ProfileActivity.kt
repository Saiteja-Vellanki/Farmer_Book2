package com.labourcalc

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.Rect
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.File

class ProfileActivity : AppCompatActivity() {

    private lateinit var photoView: ImageView

    private val photoPicker =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        profilePhotoFile(this).outputStream().use { out ->
                            input.copyTo(out)
                        }
                    }
                    loadPhoto()
                    Toast.makeText(this, getString(R.string.photo_updated), Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(this, "Could not save photo", Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_profile)
        findViewById<android.view.View>(R.id.headerProfile).padBelowStatusBar()

        photoView = findViewById(R.id.profilePhoto)
        loadPhoto()

        findViewById<TextView>(R.id.btnChangePhoto).setOnClickListener {
            photoPicker.launch("image/*")
        }

        val nameView = findViewById<TextView>(R.id.tvOwnerName)
        nameView.text = SetupManager.userName(this)
        findViewById<TextView>(R.id.btnEditName).setOnClickListener {
            val input = EditText(this).apply {
                setText(SetupManager.userName(this@ProfileActivity))
                setPadding(48, 32, 48, 32)
            }
            AlertDialog.Builder(this)
                .setTitle(R.string.edit_owner)
                .setView(input)
                .setPositiveButton(R.string.save) { _, _ ->
                    val n = input.text.toString().trim()
                    if (n.isNotBlank()) {
                        SetupManager.saveActivation(this, n)
                        nameView.text = n
                    }
                }
                .setNegativeButton(R.string.cancel, null)
                .show()
        }

        findViewById<TextView>(R.id.tvDeviceCode).text =
            SetupActivity.deviceCode(SetupManager.deviceId(this))

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "-" }
        findViewById<TextView>(R.id.tvAppVersion).text = "Farmer Book v$version"

        findViewById<TextView>(R.id.tvAdmin).text = "+91 96661 44894"

        updateLanguageRow()
        findViewById<TextView>(R.id.btnEditLanguage).setOnClickListener { showLanguageDialog() }
        findViewById<TextView>(R.id.tvLanguage).setOnClickListener { showLanguageDialog() }
    }

    private val languages = listOf(
        "English" to "en",
        "हिंदी" to "hi",
        "తెలుగు" to "te",
        "தமிழ்" to "ta",
        "ಕನ್ನಡ" to "kn",
        "മലയാളം" to "ml"
    )

    private fun updateLanguageRow() {
        val current = AppCompatDelegate.getApplicationLocales().toLanguageTags()
            .split(",").firstOrNull()?.substringBefore("-") ?: ""
        val name = languages.find { it.second == current }?.first ?: "English"
        findViewById<TextView>(R.id.tvLanguage).text = name
    }

    private fun showLanguageDialog() {
        val names = languages.map { it.first }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle(R.string.choose_language)
            .setItems(names) { _, which ->
                AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(languages[which].second)
                )
                // App screens recreate automatically in the selected language
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun loadPhoto() {
        val f = profilePhotoFile(this)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let {
                photoView.setImageBitmap(circleCrop(it))
            }
        }
    }

    companion object {
        fun profilePhotoFile(context: android.content.Context): File =
            File(context.filesDir, "profile.jpg")

        /** Crops any bitmap into a circle for round profile display. */
        fun circleCrop(src: Bitmap): Bitmap {
            val size = minOf(src.width, src.height)
            val x = (src.width - size) / 2
            val y = (src.height - size) / 2
            val squared = Bitmap.createBitmap(src, x, y, size, size)
            val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(out)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val r = size / 2f
            canvas.drawCircle(r, r, r, paint)
            paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_IN)
            canvas.drawBitmap(squared, Rect(0, 0, size, size), Rect(0, 0, size, size), paint)
            return out
        }
    }
}
