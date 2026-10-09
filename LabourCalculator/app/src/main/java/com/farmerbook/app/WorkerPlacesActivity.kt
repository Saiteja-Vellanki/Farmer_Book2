package com.farmerbook.app

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class WorkerPlacesActivity : AppCompatActivity() {

    private lateinit var placeNames: List<String>
    private lateinit var allLabours: MutableList<Labour>
    private lateinit var adapter: FertAdapter

    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val restored = SetupManager.importExcel(this, uri)
                if (restored.isEmpty()) {
                    Toast.makeText(this, getString(R.string.no_entries_file), Toast.LENGTH_LONG).show()
                } else {
                    val added = mergeImported(restored)
                    LabourStore.save(this, allLabours)
                    loadData()
                    Toast.makeText(this, getString(R.string.imported_n, added), Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<View>(R.id.fertHeaderBox).padBelowStatusBar()

        val header = findViewById<TextView>(R.id.tvFertHeader)
        header.text = getString(R.string.worker_places_title)
        header.setOnLongClickListener { openImportPicker(); true }

        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FertAdapter(emptyList(),
            onClick = { pos -> openPlace(placeNames[pos]) },
            onLongClick = { pos -> confirmDelete(placeNames[pos]) })
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fertFab)
        fab.liftAboveNavBar()
        fab.text = getString(R.string.add_place)
        fab.setOnClickListener { addPlaceDialog() }

        loadData()

        if (placeNames.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle(R.string.restore_title)
                .setMessage(R.string.restore_msg)
                .setPositiveButton(R.string.choose_file) { _, _ -> openImportPicker() }
                .setNegativeButton(R.string.start_fresh, null)
                .show()
        }
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun openImportPicker() {
        importPicker.launch(arrayOf("application/vnd.ms-excel", "application/octet-stream", "*/*"))
    }

    /** Avoids adding exact duplicate rows if the same backup is imported twice. */
    private fun mergeImported(restored: List<Labour>): Int {
        var added = 0
        for (r in restored) {
            val dup = allLabours.any {
                it.date == r.date && it.place == r.place &&
                        it.workers == r.workers && it.costPerWorker == r.costPerWorker
            }
            if (!dup) {
                allLabours.add(r)
                added++
            }
        }
        return added
    }

    private fun loadData() {
        allLabours = LabourStore.load(this)
        val stored = WorkerPlaceStore.load(this).map { it.name }
        val fromEntries = allLabours.map { it.place }.filter { it.isNotBlank() }
        placeNames = (stored + fromEntries).distinct()

        val chipsRow = findViewById<LinearLayout>(R.id.fertChipsRow)
        chipsRow.visibility = View.VISIBLE
        val due = allLabours.filter { !it.isPaid }.sumOf { it.balance }
        val paidCount = allLabours.count { it.isPaid }
        findViewById<TextView>(R.id.chipFertA).text = getString(R.string.chip_entries, allLabours.size)
        findViewById<TextView>(R.id.chipFertB).text = getString(R.string.chip_paid, paidCount)
        findViewById<TextView>(R.id.chipFertC).text = getString(R.string.chip_due, "%.0f".format(due))

        adapter.rows = rows()
        adapter.notifyDataSetChanged()
    }

    private fun rows() = placeNames.map { name ->
        val entries = allLabours.filter { it.place == name }
        val due = entries.filter { !it.isPaid }.sumOf { it.balance }
        Pair("📍 $name", getString(R.string.sub_worker_entries, entries.size, "%.0f".format(due)))
    }

    private fun openPlace(name: String) {
        startActivity(Intent(this, WorkerEntryActivity::class.java).putExtra("placeName", name))
    }

    private fun addPlaceDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_fert_place, null)
        val inPlace = v.findViewById<EditText>(R.id.inFertPlace)
        val inAcres = v.findViewById<EditText>(R.id.inFertAcres)
        AlertDialog.Builder(this)
            .setTitle(R.string.add_place)
            .setMessage(R.string.place_locked)
            .setView(v)
            .setPositiveButton(R.string.save) { _, _ ->
                val name = inPlace.text.toString().trim()
                val acres = inAcres.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isBlank() || placeNames.contains(name)) {
                    Toast.makeText(this, getString(R.string.place_name_required), Toast.LENGTH_SHORT).show()
                } else {
                    val places = WorkerPlaceStore.load(this)
                    places.add(WorkerPlace(name = name, acres = acres))
                    WorkerPlaceStore.save(this, places)
                    loadData()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_q, name))
            .setMessage(R.string.delete_place_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                val places = WorkerPlaceStore.load(this)
                places.removeAll { it.name == name }
                WorkerPlaceStore.save(this, places)

                val labours = LabourStore.load(this).toMutableList()
                labours.removeAll { it.place == name }
                LabourStore.save(this, labours)

                loadData()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
