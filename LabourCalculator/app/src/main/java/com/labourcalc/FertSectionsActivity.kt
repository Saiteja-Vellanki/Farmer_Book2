package com.labourcalc

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton

class FertSectionsActivity : AppCompatActivity() {

    private lateinit var places: MutableList<FertPlace>
    private val mode: String by lazy { intent.getStringExtra("mode") ?: "fert" }
    private var placeId: Long = 0
    private lateinit var adapter: FertAdapter

    private val place: FertPlace? get() = places.find { it.id == placeId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.tvFertHeader).padBelowStatusBar()

        placeId = intent.getLongExtra("placeId", 0)
        places = FertStore.load(this, mode)
        val p = place ?: run { finish(); return }

        findViewById<TextView>(R.id.tvFertHeader).text = "📍 ${p.name} — Sections"

        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FertAdapter(rows(),
            onClick = { pos ->
                startActivity(
                    Intent(this, FertRecordsActivity::class.java)
                        .putExtra("placeId", placeId)
                        .putExtra("sectionId", place!!.sections[pos].id)
                        .putExtra("mode", mode)
                )
            },
            onLongClick = { pos -> confirmDelete(pos) })
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fertFab)
        fab.liftAboveNavBar()
        fab.text = "Add Section"
        fab.setOnClickListener { addSection() }
    }

    override fun onResume() {
        super.onResume()
        places = FertStore.load(this, mode)
        if (place == null) { finish(); return }
        adapter.rows = rows()
        adapter.notifyDataSetChanged()
    }

    private fun rows() = place!!.sections.map {
        Pair(it.name, "${it.records.size} fertigation records")
    }

    private fun addSection() {
        val p = place ?: return
        val next = p.sections.size + 1
        AlertDialog.Builder(this)
            .setTitle("Add Section $next?")
            .setPositiveButton("Add") { _, _ ->
                p.sections.add(FertSection(name = "Section $next"))
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        val p = place ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete ${p.sections[pos].name}?")
            .setMessage("All fertigation records in this section will be deleted.")
            .setPositiveButton("Delete") { _, _ ->
                p.sections.removeAt(pos)
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
