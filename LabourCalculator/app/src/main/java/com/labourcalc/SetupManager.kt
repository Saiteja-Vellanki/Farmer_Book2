package com.labourcalc

import android.annotation.SuppressLint
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.provider.Settings
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import jxl.Workbook
import jxl.write.Label

object SetupManager {

    private const val PREFS = "setup_prefs"
    private const val KEY_ACTIVATED = "activated"
    private const val KEY_NAME = "user_name"
    private const val EXCEL_NAME = "labour_data.xls"
    private const val REL_DIR = "worker_data"

    @SuppressLint("HardwareIds")
    fun deviceId(context: Context): String =
        Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID) ?: "unknown"

    fun isActivated(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getBoolean(KEY_ACTIVATED, false)

    fun saveActivation(context: Context, name: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putBoolean(KEY_ACTIVATED, true)
            .putString(KEY_NAME, name.trim())
            .apply()
    }

    fun userName(context: Context): String =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_NAME, "") ?: ""

    /** OutputStream for Documents/worker_data/labour_data.xls.
     *  API 29+: MediaStore (no permission). API <=28: direct file (WRITE permission). */
    private fun excelOutputStream(context: Context): OutputStream {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")
            val relPath = Environment.DIRECTORY_DOCUMENTS + "/" + REL_DIR

            val sel = MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " +
                    MediaStore.MediaColumns.DISPLAY_NAME + "=?"
            resolver.query(
                collection, arrayOf(MediaStore.MediaColumns._ID),
                sel, arrayOf("$relPath/", EXCEL_NAME), null
            )?.use { c ->
                if (c.moveToFirst()) {
                    val uri = ContentUris.withAppendedId(collection, c.getLong(0))
                    resolver.openOutputStream(uri, "wt")?.let { return it }
                }
            }
            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, EXCEL_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-excel")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
            val uri = resolver.insert(collection, cv)
                ?: throw IllegalStateException("Cannot create Excel file")
            return resolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Cannot open Excel file")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                REL_DIR
            )
            if (!dir.exists()) dir.mkdirs()
            return FileOutputStream(File(dir, EXCEL_NAME))
        }
    }

    fun exportExcel(context: Context, labours: List<Labour>) {
        excelOutputStream(context).use { out ->
            val wb = Workbook.createWorkbook(out)
            val sheet = wb.createSheet("Labour Data", 0)
            val headers = listOf(
                "Date", "Place/Site", "No. of Workers", "Cost per Worker",
                "Total", "Amount Paid", "Balance", "Status", "Note"
            )
            headers.forEachIndexed { c, h -> sheet.addCell(Label(c, 0, h)) }
            labours.forEachIndexed { i, l ->
                val r = i + 1
                sheet.addCell(Label(0, r, l.date))
                sheet.addCell(Label(1, r, l.place))
                sheet.addCell(jxl.write.Number(2, r, l.workers.toDouble()))
                sheet.addCell(jxl.write.Number(3, r, l.costPerWorker))
                sheet.addCell(jxl.write.Number(4, r, l.total))
                sheet.addCell(jxl.write.Number(5, r, l.amountPaid))
                sheet.addCell(jxl.write.Number(6, r, l.balance))
                sheet.addCell(Label(7, r, if (l.isPaid) "PAID" else "DUE"))
                sheet.addCell(Label(8, r, l.note))
            }
            wb.write()
            wb.close()
        }
    }

    /** Restore entries from a user-picked .xls file (Storage Access Framework). */
    fun importExcel(context: Context, uri: Uri): MutableList<Labour> {
        val list = mutableListOf<Labour>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val wb = Workbook.getWorkbook(input)
                val sheet = wb.getSheet(0)
                for (r in 1 until sheet.rows) {
                    fun cell(c: Int): String =
                        if (c < sheet.columns) sheet.getCell(c, r).contents.trim() else ""
                    val place = cell(1)
                    if (place.isBlank() && cell(0).isBlank()) continue
                    list.add(
                        Labour(
                            id = System.currentTimeMillis() + r,
                            date = cell(0),
                            place = place,
                            workers = cell(2).toDoubleOrNull()?.toInt() ?: 0,
                            costPerWorker = cell(3).toDoubleOrNull() ?: 0.0,
                            note = cell(8),
                            amountPaid = cell(5).toDoubleOrNull() ?: 0.0
                        )
                    )
                }
                wb.close()
            }
        } catch (e: Exception) {
            // unreadable file - return whatever parsed
        }
        return list
    }
}
