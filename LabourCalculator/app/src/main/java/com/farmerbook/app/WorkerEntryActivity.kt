package com.farmerbook.app

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.concurrent.thread

class WorkerEntryActivity : AppCompatActivity() {

    private lateinit var allLabours: MutableList<Labour>
    private lateinit var adapter: LabourAdapter
    private lateinit var tabDue: TextView
    private lateinit var tabPaid: TextView
    private lateinit var tvTotalBanner: TextView

    private val placeName: String by lazy { intent.getStringExtra("placeName") ?: "" }
    private var showingPaid = false

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val restored = SetupManager.importExcel(this, uri)
                if (restored.isEmpty()) {
                    Toast.makeText(this, getString(R.string.no_entries_file), Toast.LENGTH_LONG).show()
                } else {
                    allLabours.addAll(restored)
                    Toast.makeText(this, getString(R.string.imported_n, restored.size), Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker)
        findViewById<View>(R.id.headerWorker).padBelowStatusBar()

        allLabours = LabourStore.load(this)

        val header = findViewById<TextView>(R.id.tvWorkerHeader)
        header.text = "📍 $placeName"
        header.setOnLongClickListener { openImportPicker(); true }

        tabDue = findViewById(R.id.tabDue)
        tabPaid = findViewById(R.id.tabPaid)
        tvTotalBanner = findViewById(R.id.tvTotalBanner)
        tabDue.setOnClickListener { showingPaid = false; refresh() }
        tabPaid.setOnClickListener { showingPaid = true; refresh() }

        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = LabourAdapter(
            emptyList(),
            onEdit = { showDialog(it) },
            onMarkPaid = { markPaid(it) },
            onDelete = { deleteOptions(it) }
        )
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fabAdd)
        fab.liftAboveNavBar()
        fab.setOnClickListener { showDialog(null) }

        if (Build.VERSION.SDK_INT <= 28 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            != PackageManager.PERMISSION_GRANTED
        ) {
            legacyStoragePermission.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }

        refresh()

        if (placeEntries().isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_msg)
                .setPositiveButton(R.string.choose_file) { _, _ -> openImportPicker() }
                .setNegativeButton(R.string.start_fresh, null)
                .show()
        }
    }

    private fun openImportPicker() {
        importPicker.launch(arrayOf("application/vnd.ms-excel", "application/octet-stream", "*/*"))
    }

    private fun placeEntries(): List<Labour> = allLabours.filter { it.place == placeName }

    private fun dateMillis(l: Labour): Long = try {
        dateFmt.parse(l.date)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    /** Updates the tab bar styling and the prominent total banner (due amount or
     *  paid amount shown at the very top, per the active tab). */
    private fun updateTabsAndBanner() {
        val dueList = placeEntries().filter { !it.isPaid }
        val paidList = placeEntries().filter { it.isPaid }
        tabDue.text = getString(R.string.tab_due, dueList.size)
        tabPaid.text = getString(R.string.tab_paid, paidList.size)

        if (showingPaid) {
            tabPaid.setBackgroundResource(R.drawable.bg_status_paid)
            tabPaid.setTextColor(0xFFFFFFFF.toInt())
            tabDue.background = null
            tabDue.setTextColor(0xFFFFEB3B.toInt())

            val totalPaid = paidList.sumOf { it.amountPaid }
            tvTotalBanner.text = getString(R.string.paid_total_banner, "%.0f".format(totalPaid))
            tvTotalBanner.setBackgroundResource(R.drawable.bg_status_paid)
            tvTotalBanner.setTextColor(0xFFFFFFFF.toInt())
        } else {
            tabDue.setBackgroundResource(R.drawable.bg_status_due)
            tabDue.setTextColor(0xFFFFFFFF.toInt())
            tabPaid.background = null
            tabPaid.setTextColor(0xFFC8E6C9.toInt())

            val totalDue = dueList.sumOf { it.balance }
            tvTotalBanner.text = getString(R.string.due_total_banner, "%.0f".format(totalDue))
            tvTotalBanner.setBackgroundResource(R.drawable.bg_status_due)
            tvTotalBanner.setTextColor(0xFFFFFFFF.toInt())
        }
    }

    private fun refresh() {
        updateTabsAndBanner()
        val visible = placeEntries()
            .filter { it.isPaid == showingPaid }
            .sortedByDescending { dateMillis(it) }
        adapter.items = visible
        adapter.notifyDataSetChanged()

        LabourStore.save(this, allLabours)
        val snapshot = allLabours.toList()
        thread {
            try {
                SetupManager.exportExcel(this, snapshot)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Excel save failed: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun markPaid(l: Labour) {
        l.amountPaid = l.total
        refresh()
    }

    private fun deleteOptions(l: Labour) {
        AlertDialog.Builder(this)
            .setItems(arrayOf(getString(R.string.delete_this_entry), getString(R.string.select_multiple))) { _, which ->
                if (which == 0) confirmDelete(l) else multiDeleteDialog()
            }
            .show()
    }

    private fun multiDeleteDialog() {
        val visible = placeEntries().filter { it.isPaid == showingPaid }.sortedByDescending { dateMillis(it) }
        if (visible.isEmpty()) return
        val labels = visible.map {
            "${it.date}  ₹${"%.0f".format(it.total)}"
        }.toTypedArray()
        val checked = BooleanArray(labels.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.select_entries)
            .setMultiChoiceItems(labels, checked) { _, i, b -> checked[i] = b }
            .setPositiveButton(R.string.delete) { _, _ ->
                val toRemove = visible.filterIndexed { i, _ -> checked[i] }
                if (toRemove.isEmpty()) return@setPositiveButton
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_n_entries, toRemove.size))
                    .setPositiveButton(R.string.yes_delete) { _, _ ->
                        allLabours.removeAll(toRemove)
                        refresh()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(l: Labour) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_q, "${l.place} (${l.date})"))
            .setPositiveButton(R.string.delete) { _, _ ->
                allLabours.remove(l); refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun today(): String = dateFmt.format(Calendar.getInstance().time)

    private fun showDialog(existing: Labour?) {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_labour, null)
        val date = v.findViewById<EditText>(R.id.inDate)
        val place = v.findViewById<EditText>(R.id.inPlace)
        val workers = v.findViewById<EditText>(R.id.inWorkers)
        val cost = v.findViewById<EditText>(R.id.inCost)
        val note = v.findViewById<EditText>(R.id.inNote)
        val paid = v.findViewById<EditText>(R.id.inPaid)

        // Place is inherited from this screen's place - hidden, never typed per-entry
        place.visibility = View.GONE

        date.setText(existing?.date?.ifBlank { today() } ?: today())
        date.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(
                this,
                { _, y, m, d ->
                    date.setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y))
                },
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)
            ).show()
        }

        existing?.let {
            workers.setText(it.workers.toString())
            cost.setText(it.costPerWorker.toString())
            note.setText(it.note)
            paid.setText(it.amountPaid.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) getString(R.string.add_entry) else getString(R.string.edit_entry))
            .setView(v)
            .setPositiveButton(R.string.save) { _, _ ->
                val l = existing ?: Labour().also { allLabours.add(it) }
                l.date = date.text.toString().trim().ifBlank { today() }
                l.place = placeName
                l.workers = workers.text.toString().toIntOrNull() ?: 0
                l.costPerWorker = cost.text.toString().toDoubleOrNull() ?: 0.0
                l.note = note.text.toString().trim()
                l.amountPaid = paid.text.toString().toDoubleOrNull() ?: 0.0
                if (existing == null) showingPaid = l.isPaid
                refresh()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
