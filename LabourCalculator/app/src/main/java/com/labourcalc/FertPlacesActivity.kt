package com.labourcalc

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.fertHeaderBox).padBelowStatusBar()

        findViewById<TextView>(R.id.tvFertHeader).text = getString(R.string.places_title, titlePrefix)
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
    }

    override fun onResume() {
        super.onResume()
        places = FertStore.load(this, mode)
        adapter.rows = rows()
        adapter.notifyDataSetChanged()
        updateChips()
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
        if (mode == "spray" || mode == "sale") {
            // Spraying and Sales have no sections - go straight to records
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
        } else {
            startActivity(
                Intent(this, FertSectionsActivity::class.java)
                    .putExtra("placeId", p.id)
                    .putExtra("mode", mode)
            )
        }
    }

    private fun rows() = places.map { p ->
        val sub = when (mode) {
            "spray" -> getString(R.string.sub_spray, p.acres.toString(), p.sections.sumOf { s -> s.records.size })
            "sale" -> {
                val total = p.sections.sumOf { s -> s.records.sumOf { r -> r.items.sumOf { it.qty * it.price } } }
                getString(R.string.sub_sale, p.sections.sumOf { s -> s.records.size }, "%.0f".format(total))
            }
            else -> getString(R.string.sub_fert, p.acres.toString(), p.sections.size)
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
