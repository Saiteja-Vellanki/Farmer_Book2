package com.labourcalc

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class SetupActivity : AppCompatActivity() {

    companion object {
        private const val SECRET = "AbhiLabour@2026#Secret"
        private const val MASTER_REVIEW_CODE = "593217"
        const val ADMIN_WHATSAPP = "919666977855"

        fun fnv1a(s: String): Int {
            var h = 0x811C9DC5.toInt()
            for (ch in s) {
                h = h xor ch.code
                h *= 16777619
            }
            return h
        }

        fun deviceCode(androidId: String): String =
            String.format("%08X", fnv1a(androidId))

        fun activationCode(devCode: String): String {
            val n = (fnv1a(devCode + SECRET).toLong() and 0xFFFFFFFFL) % 900000L + 100000L
            return n.toString()
        }
    }

    private lateinit var inName: EditText
    private lateinit var inCode: EditText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_setup)
        findViewById<android.view.View>(R.id.setupRoot).padBelowStatusBar()

        inName = findViewById(R.id.inName)
        inCode = findViewById(R.id.inOtp)

        val devCode = deviceCode(SetupManager.deviceId(this))
        findViewById<TextView>(R.id.tvDeviceCode).text = devCode
        findViewById<TextView>(R.id.tvOtpInfo).text = getString(R.string.setup_instructions)

        findViewById<Button>(R.id.btnShareWa).setOnClickListener { shareOnWhatsApp(devCode) }
        findViewById<Button>(R.id.btnVerify).setOnClickListener { verify(devCode) }
    }

    private fun shareOnWhatsApp(devCode: String) {
        val name = inName.text.toString().trim()
        val msg = "Farmer Book activation request 🙏\nName: ${if (name.isBlank()) "-" else name}\nDevice Code: $devCode\nPlease send my Activation Code."
        try {
            val url = "https://wa.me/$ADMIN_WHATSAPP?text=" + Uri.encode(msg)
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            Toast.makeText(this, "WhatsApp not found on this phone", Toast.LENGTH_LONG).show()
        }
    }

    private fun verify(devCode: String) {
        val name = inName.text.toString().trim()
        if (name.isBlank()) {
            Toast.makeText(this, getString(R.string.name_required), Toast.LENGTH_SHORT).show()
            return
        }
        val entered = inCode.text.toString().trim()
        if (entered != activationCode(devCode) && entered != MASTER_REVIEW_CODE) {
            Toast.makeText(this, getString(R.string.wrong_code), Toast.LENGTH_SHORT).show()
            return
        }
        SetupManager.saveActivation(this, name)
        Toast.makeText(this, getString(R.string.login_success), Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java))
        finish()
    }
}
