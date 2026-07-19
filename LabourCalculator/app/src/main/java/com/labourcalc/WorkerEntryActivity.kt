package com.labourcalc

import android.Manifest
import android.app.DatePickerDialog
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
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

    private lateinit var labours: MutableList<Labour>
    private lateinit var adapter: LabourAdapter
    private lateinit var chipTotal: TextView
    private lateinit var chipPaid: TextView
    private lateinit var chipDue: TextView

    private val dateFmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    private val legacyStoragePermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val restored = SetupManager.importExcel(this, uri)
                if (restored.isEmpty()) {
                    Toast.makeText(this, "No entries found in that file", Toast.LENGTH_LONG).show()
                } else {
                    labours.addAll(restored)
                    Toast.makeText(this, "Imported ${restored.size} entries ✔", Toast.LENGTH_LONG).show()
                    refresh()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_worker)
        findViewById<android.view.View>(R.id.headerWorker).padBelowStatusBar()

        labours = LabourStore.load(this)

        val header = findViewById<TextView>(R.id.tvWorkerHeader)
        header.setOnLongClickListener { openImportPicker(); true }

        chipTotal = findViewById(R.id.chipTotal)
        chipPaid = findViewById(R.id.chipPaid)
        chipDue = findViewById(R.id.chipDue)

        val rv = findViewById<RecyclerView>(R.id.recycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = LabourAdapter(
            labours,
            onEdit = { showDialog(it) },
            onMarkPaid = { markPaid(it) },
            onDelete = { confirmDelete(it) }
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

        if (labours.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("Restore data?")
                .setMessage("If you have a previous labour_data.xls backup in Documents/Farmer_Book, you can restore it now.")
                .setPositiveButton("Choose file") { _, _ -> openImportPicker() }
                .setNegativeButton("Start fresh", null)
                .show()
        }

        if (intent.getBooleanExtra("openAdd", false)) {
            showDialog(null)
        }
    }

    private fun openImportPicker() {
        importPicker.launch(arrayOf("application/vnd.ms-excel", "application/octet-stream", "*/*"))
    }

    private fun dateMillis(l: Labour): Long = try {
        dateFmt.parse(l.date)?.time ?: 0L
    } catch (e: Exception) {
        0L
    }

    private fun sortEntries() {
        labours.sortWith(Comparator { a, b ->
            when {
                a.isPaid != b.isPaid -> if (a.isPaid) 1 else -1
                a.isPaid -> dateMillis(b).compareTo(dateMillis(a))
                else -> dateMillis(a).compareTo(dateMillis(b))
            }
        })
    }

    private fun refresh() {
        sortEntries()
        adapter.notifyDataSetChanged()
        val due = labours.filter { !it.isPaid }.sumOf { it.balance }
        val paidCount = labours.count { it.isPaid }
        chipTotal.text = "📋 ${labours.size}"
        chipPaid.text = "✅ $paidCount Paid"
        chipDue.text = "⏳ Due ₹${"%.0f".format(due)}"
        LabourStore.save(this, labours)
        val snapshot = labours.toList()
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

    private fun confirmDelete(l: Labour) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${l.place} (${l.date})?")
            .setPositiveButton("Delete") { _, _ ->
                labours.remove(l); refresh()
            }
            .setNegativeButton("Cancel", null)
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
            place.setText(it.place)
            workers.setText(it.workers.toString())
            cost.setText(it.costPerWorker.toString())
            note.setText(it.note)
            paid.setText(it.amountPaid.toString())
        }

        AlertDialog.Builder(this)
            .setTitle(if (existing == null) "Add Entry" else "Edit Entry")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val l = existing ?: Labour().also { labours.add(it) }
                l.date = date.text.toString().trim().ifBlank { today() }
                l.place = place.text.toString().trim()
                l.workers = workers.text.toString().toIntOrNull() ?: 0
                l.costPerWorker = cost.text.toString().toDoubleOrNull() ?: 0.0
                l.note = note.text.toString().trim()
                l.amountPaid = paid.text.toString().toDoubleOrNull() ?: 0.0
                refresh()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
