package com.farmerbook.app

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
    private const val REL_DIR = "Farmer_Book"

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

    private const val URI_CACHE_PREFS = "excel_uri_cache"

    private fun cachedUri(context: Context, fileName: String): String? =
        context.getSharedPreferences(URI_CACHE_PREFS, Context.MODE_PRIVATE)
            .getString(fileName, null)

    private fun cacheUri(context: Context, fileName: String, uri: String) {
        context.getSharedPreferences(URI_CACHE_PREFS, Context.MODE_PRIVATE)
            .edit().putString(fileName, uri).apply()
    }

    /** OutputStream for Documents/Farmer_Book/<fileName>.
     *  API 29+: MediaStore (no permission). API <=28: direct file (WRITE permission).
     *
     *  Reuses the exact same MediaStore entry on every call by remembering its URI
     *  after the first successful write, rather than re-searching for a matching
     *  file by path+name each time. Path-matching across RELATIVE_PATH values is
     *  inconsistently normalized on some Android versions/OEM skins, which can
     *  cause a search to silently miss the existing file - and when that happens,
     *  MediaStore doesn't error, it just creates "labour_data (1).xls" and so on,
     *  forever. Remembering the URI directly sidesteps that fragility entirely:
     *  once we have a working reference, we never need to search again. */
    private fun excelOutputStream(context: Context, fileName: String): OutputStream {
        if (Build.VERSION.SDK_INT >= 29) {
            val resolver = context.contentResolver
            val collection = MediaStore.Files.getContentUri("external")

            // Fast path: reuse the exact URI that worked last time.
            cachedUri(context, fileName)?.let { cached ->
                try {
                    return resolver.openOutputStream(Uri.parse(cached), "wt")!!
                } catch (e: Exception) {
                    // File was moved/deleted/inaccessible - fall through and re-resolve it below.
                }
            }

            val relPath = Environment.DIRECTORY_DOCUMENTS + "/" + REL_DIR + "/"

            // Match by DISPLAY_NAME, then confirm folder by a tolerant *contains*
            // check on RELATIVE_PATH rather than exact equality - some Android
            // versions/OEM skins normalize slashes or casing differently, and an
            // exact-match query can silently miss a file that genuinely exists,
            // which is what causes MediaStore to insert a "(1)" duplicate instead
            // of reusing it. A loose match finds the real file regardless of how
            // that device happens to have stored the path string.
            resolver.query(
                collection,
                arrayOf(MediaStore.MediaColumns._ID, MediaStore.MediaColumns.RELATIVE_PATH),
                MediaStore.MediaColumns.DISPLAY_NAME + "=?",
                arrayOf(fileName), null
            )?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val pathCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.RELATIVE_PATH)
                while (c.moveToNext()) {
                    val path = c.getString(pathCol) ?: ""
                    if (!path.contains(REL_DIR, ignoreCase = true)) continue
                    val uri = ContentUris.withAppendedId(collection, c.getLong(idCol))
                    try {
                        val out = resolver.openOutputStream(uri, "wt")
                        if (out != null) {
                            cacheUri(context, fileName, uri.toString())
                            return out
                        }
                    } catch (e: Exception) {
                        // Can't write to this match - keep checking other rows, if any.
                    }
                }
            }

            val cv = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/vnd.ms-excel")
                put(MediaStore.MediaColumns.RELATIVE_PATH, relPath)
            }
            val uri = resolver.insert(collection, cv)
                ?: throw IllegalStateException("Cannot create Excel file")
            cacheUri(context, fileName, uri.toString())
            return resolver.openOutputStream(uri, "wt")
                ?: throw IllegalStateException("Cannot open Excel file")
        } else {
            val dir = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
                REL_DIR
            )
            if (!dir.exists()) dir.mkdirs()
            return FileOutputStream(File(dir, fileName))
        }
    }

    fun exportExcel(context: Context, labours: List<Labour>) {
        excelOutputStream(context, "labour_data.xls").use { out ->
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

    /** Writes fertigation, spraying, or sale data to its own Excel file. */
    fun exportFertExcel(context: Context, mode: String, places: List<FertPlace>) {
        val fileName = when (mode) {
            "spray" -> "spraying_data.xls"
            "sale" -> "sale_data.xls"
            else -> "fertigation_data.xls"
        }
        excelOutputStream(context, fileName).use { out ->
            val wb = Workbook.createWorkbook(out)
            if (mode == "sale") {
                val sheet = wb.createSheet("Sale Data", 0)
                val headers = listOf("Place", "Date", "Buyer", "Item", "Qty", "Unit", "Price", "Amount")
                headers.forEachIndexed { c, h -> sheet.addCell(Label(c, 0, h)) }
                var r = 1
                var grand = 0.0
                for (p in places) for (sec in p.sections) for (rec in sec.records) for (item in rec.items) {
                    val amount = item.qty * item.price
                    grand += amount
                    sheet.addCell(Label(0, r, p.name))
                    sheet.addCell(Label(1, r, rec.date))
                    sheet.addCell(Label(2, r, rec.buyer))
                    sheet.addCell(Label(3, r, item.name))
                    sheet.addCell(jxl.write.Number(4, r, item.qty))
                    sheet.addCell(Label(5, r, item.unit))
                    sheet.addCell(jxl.write.Number(6, r, item.price))
                    sheet.addCell(jxl.write.Number(7, r, amount))
                    r++
                }
                sheet.addCell(Label(6, r + 1, "TOTAL"))
                sheet.addCell(jxl.write.Number(7, r + 1, grand))
            } else {
                val sheetName = if (mode == "spray") "Spraying Data" else "Fertigation Data"
                val sheet = wb.createSheet(sheetName, 0)
                val itemLabel = if (mode == "spray") "Chemical" else "Fertilizer"
                val headers = listOf("Place", "Acres", "Section", "Date", itemLabel, "Quantity", "Unit", "Note")
                headers.forEachIndexed { c, h -> sheet.addCell(Label(c, 0, h)) }
                var r = 1
                for (p in places) {
                    for (sec in p.sections) {
                        for (rec in sec.records) {
                            for (item in rec.items) {
                                sheet.addCell(Label(0, r, p.name))
                                sheet.addCell(jxl.write.Number(1, r, p.acres))
                                sheet.addCell(Label(2, r, sec.name))
                                sheet.addCell(Label(3, r, rec.date))
                                sheet.addCell(Label(4, r, item.name))
                                sheet.addCell(jxl.write.Number(5, r, item.qty))
                                sheet.addCell(Label(6, r, item.unit))
                                sheet.addCell(Label(7, r, rec.note))
                                r++
                            }
                        }
                    }
                }
            }
            wb.write()
            wb.close()
        }
    }

    /** Restore fertigation/spraying/sale data from a user-picked .xls backup
     *  (Storage Access Framework). Rebuilds the Place → Section → Record → Item
     *  hierarchy by grouping rows back together. Used for recovery after a
     *  reinstall, new device, or package-name change where app-private data
     *  (SharedPreferences) is lost but the Excel mirror in Documents survives. */
    fun importFertExcel(context: Context, mode: String, uri: Uri): MutableList<FertPlace> {
        val places = mutableListOf<FertPlace>()
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val wb = Workbook.getWorkbook(input)
                val sheet = wb.getSheet(0)

                fun findOrCreatePlace(name: String, acres: Double): FertPlace {
                    places.find { it.name == name }?.let { return it }
                    val p = FertPlace(name = name, acres = acres)
                    places.add(p)
                    return p
                }
                fun findOrCreateSection(p: FertPlace, name: String): FertSection {
                    p.sections.find { it.name == name }?.let { return it }
                    val s = FertSection(name = name)
                    p.sections.add(s)
                    return s
                }
                fun findOrCreateRecord(s: FertSection, date: String, buyer: String): FertRecord {
                    s.records.find { it.date == date && it.buyer == buyer }?.let { return it }
                    val r = FertRecord(date = date, buyer = buyer)
                    s.records.add(r)
                    return r
                }

                for (r in 1 until sheet.rows) {
                    fun cell(c: Int): String =
                        if (c < sheet.columns) sheet.getCell(c, r).contents.trim() else ""

                    if (mode == "sale") {
                        val placeName = cell(0)
                        val date = cell(1)
                        if (placeName.isBlank() || date.isBlank()) continue // skip blank/TOTAL row
                        val buyer = cell(2)
                        val place = findOrCreatePlace(placeName, 0.0)
                        val section = findOrCreateSection(place, "Main")
                        val record = findOrCreateRecord(section, date, buyer)
                        val qty = cell(4).toDoubleOrNull() ?: 0.0
                        val price = cell(6).toDoubleOrNull() ?: 0.0
                        record.items.add(FertItem(cell(3), qty, cell(5).ifBlank { "kgs" }, price))
                    } else {
                        val placeName = cell(0)
                        if (placeName.isBlank()) continue
                        val acres = cell(1).toDoubleOrNull() ?: 0.0
                        val sectionName = cell(2).ifBlank { "Main" }
                        val date = cell(3)
                        val place = findOrCreatePlace(placeName, acres)
                        val section = findOrCreateSection(place, sectionName)
                        val record = findOrCreateRecord(section, date, "")
                        if (record.note.isBlank()) record.note = cell(7)
                        val qty = cell(5).toDoubleOrNull() ?: 0.0
                        record.items.add(FertItem(cell(4), qty, cell(6).ifBlank { "kg" }))
                    }
                }
                wb.close()
            }
        } catch (e: Exception) {
            // unreadable file - return whatever parsed so far
        }
        return places
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
