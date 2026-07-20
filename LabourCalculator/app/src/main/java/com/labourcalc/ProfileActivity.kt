package com.labourcalc

import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
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
                    Toast.makeText(this, "Photo updated ✔", Toast.LENGTH_SHORT).show()
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
                .setTitle("Edit owner name")
                .setView(input)
                .setPositiveButton("Save") { _, _ ->
                    val n = input.text.toString().trim()
                    if (n.isNotBlank()) {
                        SetupManager.saveActivation(this, n)
                        nameView.text = n
                    }
                }
                .setNegativeButton("Cancel", null)
                .show()
        }

        findViewById<TextView>(R.id.tvDeviceCode).text =
            SetupActivity.deviceCode(SetupManager.deviceId(this))

        val version = try {
            packageManager.getPackageInfo(packageName, 0).versionName
        } catch (e: Exception) { "-" }
        findViewById<TextView>(R.id.tvAppVersion).text = "Farmer Book v$version"

        findViewById<TextView>(R.id.tvAdmin).text = "+91 96669 77855"
    }

    private fun loadPhoto() {
        val f = profilePhotoFile(this)
        if (f.exists()) {
            BitmapFactory.decodeFile(f.absolutePath)?.let { photoView.setImageBitmap(it) }
        }
    }

    companion object {
        fun profilePhotoFile(context: android.content.Context): File =
            File(context.filesDir, "profile.jpg")
    }
}
