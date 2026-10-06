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

class FertPlacesActivity : AppCompatActivity() {

    private lateinit var places: MutableList<FertPlace>
    private lateinit var adapter: FertAdapter
    private val mode: String by lazy { intent.getStringExtra("mode") ?: "fert" }
    private val titlePrefix: String
        get() = when (mode) {
            "spray" -> getString(R.string.prefix_spray)
            "sale" -> getString(R.string.prefix_sale)
            else -> getString(R.string.prefix_fert)
        }

    private val importPicker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val restored = SetupManager.importFertExcel(this, mode, uri)
                if (restored.isEmpty()) {
                    Toast.makeText(this, getString(R.string.no_entries_file), Toast.LENGTH_LONG).show()
                } else {
                    val addedPlaces = mergeImported(restored)
                    FertStore.save(this, mode, places)
                    adapter.rows = rows()
                    adapter.notifyDataSetChanged()
                    updateChips()
                    Toast.makeText(this, getString(R.string.imported_n, addedPlaces), Toast.LENGTH_LONG).show()
                }
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.fertHeaderBox).padBelowStatusBar()

        val header = findViewById<TextView>(R.id.tvFertHeader)
        header.text = getString(R.string.places_title, titlePrefix)
        header.setOnLongClickListener { openImportPicker(); true }

        places = FertStore.load(this, mode)
        updateChips()

        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FertAdapter(rows(),
            onClick = { pos -> openPlace(pos) },
            onLongClick = { pos -> confirmDelete(pos) })
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fertFab)
        fab.liftAboveNavBar()
        fab.text = getString(R.string.add_place)
        fab.setOnClickListener { addPlaceDialog() }

        if (places.isEmpty()) {
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
        places = FertStore.load(this, mode)
        adapter.rows = rows()
        adapter.notifyDataSetChanged()
        updateChips()
    }

    private fun openImportPicker() {
        importPicker.launch(arrayOf("application/vnd.ms-excel", "application/octet-stream", "*/*"))
    }

    /** Merges imported places into the current list: new places are added
     *  whole; for places that already exist (by name), only genuinely new
     *  sections/records are merged in, so importing the same backup twice
     *  never creates duplicates. Returns how many places were touched. */
    private fun mergeImported(imported: List<FertPlace>): Int {
        var touched = 0
        for (ip in imported) {
            val existing = places.find { it.name == ip.name }
            if (existing == null) {
                places.add(ip)
                touched++
                continue
            }
            var changed = false
            for (isec in ip.sections) {
                val esec = existing.sections.find { it.name == isec.name }
                if (esec == null) {
                    existing.sections.add(isec)
                    changed = true
                    continue
                }
                for (irec in isec.records) {
                    val dup = esec.records.any { it.date == irec.date && it.buyer == irec.buyer }
                    if (!dup) {
                        esec.records.add(irec)
                        changed = true
                    }
                }
            }
            if (changed) touched++
        }
        return touched
    }

    private fun updateChips() {
        val chipsRow = findViewById<LinearLayout>(R.id.fertChipsRow)
        if (mode == "sale") {
            val grand = places.sumOf { p ->
                p.sections.sumOf { s -> s.records.sumOf { r -> r.items.sumOf { it.qty * it.price } } }
            }
            val count = places.sumOf { p -> p.sections.sumOf { s -> s.records.size } }
            chipsRow.visibility = View.VISIBLE
            findViewById<TextView>(R.id.chipFertA).text = "🧾 $count sales"
            findViewById<TextView>(R.id.chipFertB).text = "💰 Total ₹${"%.0f".format(grand)}"
        } else {
            chipsRow.visibility = View.GONE
        }
    }

    private fun openPlace(pos: Int) {
        val p = places[pos]
        // Places go straight to records for every mode - no separate Sections screen
        if (p.sections.isEmpty()) {
            p.sections.add(FertSection(name = "Main"))
            FertStore.save(this, mode, places)
        }
        startActivity(
            Intent(this, FertRecordsActivity::class.java)
                .putExtra("placeId", p.id)
                .putExtra("sectionId", p.sections[0].id)
                .putExtra("mode", mode)
        )
    }

    private fun rows() = places.map { p ->
        val sub = when (mode) {
            "spray" -> getString(R.string.sub_spray, p.acres.toString(), p.sections.sumOf { s -> s.records.size })
            "fert" -> getString(R.string.sub_fert_records, p.acres.toString(), p.sections.sumOf { s -> s.records.size })
            "sale" -> {
                val total = p.sections.sumOf { s -> s.records.sumOf { r -> r.items.sumOf { it.qty * it.price } } }
                getString(R.string.sub_sale, p.sections.sumOf { s -> s.records.size }, "%.0f".format(total))
            }
            else -> getString(R.string.sub_fert_records, p.acres.toString(), p.sections.sumOf { s -> s.records.size })
        }
        Pair("📍 ${p.name}", sub)
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
                if (name.isBlank()) {
                    Toast.makeText(this, getString(R.string.place_name_required), Toast.LENGTH_SHORT).show()
                } else {
                    places.add(FertPlace(name = name, acres = acres))
                    FertStore.save(this, mode, places)
                    adapter.rows = rows()
                    adapter.notifyDataSetChanged()
                    updateChips()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_q, places[pos].name))
            .setMessage(R.string.delete_place_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                places.removeAt(pos)
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
                updateChips()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
