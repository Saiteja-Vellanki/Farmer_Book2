package com.labourcalc

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.widget.EditText
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
        get() = if (mode == "spray") "🧪 Spraying" else "🌱 Drip Fertigation"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.tvFertHeader).padBelowStatusBar()

        findViewById<TextView>(R.id.tvFertHeader).text = "$titlePrefix — Places"
        places = FertStore.load(this, mode)

        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FertAdapter(rows(),
            onClick = { pos -> openPlace(pos) },
            onLongClick = { pos -> confirmDelete(pos) })
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fertFab)
        fab.text = "Add Place"
        fab.setOnClickListener { addPlaceDialog() }
    }

    override fun onResume() {
        super.onResume()
        places = FertStore.load(this, mode)
        adapter.rows = rows()
        adapter.notifyDataSetChanged()
    }

    private fun openPlace(pos: Int) {
        val p = places[pos]
        if (mode == "spray") {
            // Spraying has no sections - go straight to records via a hidden default section
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

    private fun rows() = places.map {
        val sub = if (mode == "spray")
            "${it.acres} acres  •  ${it.sections.sumOf { s -> s.records.size }} spraying records"
        else
            "${it.acres} acres  •  ${it.sections.size} sections"
        Pair("📍 ${it.name}", sub)
    }

    private fun addPlaceDialog() {
        val v = LayoutInflater.from(this).inflate(R.layout.dialog_fert_place, null)
        val inPlace = v.findViewById<EditText>(R.id.inFertPlace)
        val inAcres = v.findViewById<EditText>(R.id.inFertAcres)
        AlertDialog.Builder(this)
            .setTitle("Add Place")
            .setMessage("Note: place details cannot be changed after saving.")
            .setView(v)
            .setPositiveButton("Save") { _, _ ->
                val name = inPlace.text.toString().trim()
                val acres = inAcres.text.toString().toDoubleOrNull() ?: 0.0
                if (name.isBlank()) {
                    Toast.makeText(this, "Place name required", Toast.LENGTH_SHORT).show()
                } else {
                    places.add(FertPlace(name = name, acres = acres))
                    FertStore.save(this, mode, places)
                    adapter.rows = rows()
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        AlertDialog.Builder(this)
            .setTitle("Delete ${places[pos].name}?")
            .setMessage("All sections and fertigation data of this place will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                places.removeAt(pos)
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
