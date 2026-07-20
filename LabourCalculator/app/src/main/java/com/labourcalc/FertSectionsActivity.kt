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
        findViewById<android.view.View>(R.id.fertHeaderBox).padBelowStatusBar()

        placeId = intent.getLongExtra("placeId", 0)
        places = FertStore.load(this, mode)
        val p = place ?: run { finish(); return }

        findViewById<TextView>(R.id.tvFertHeader).text = getString(R.string.sections_title, p.name)

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
        fab.text = getString(R.string.add_section)
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
        Pair(it.name, getString(R.string.n_fert_records, it.records.size))
    }

    private fun addSection() {
        val p = place ?: return
        val next = p.sections.size + 1
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.add_section_q, next))
            .setPositiveButton(R.string.add_section) { _, _ ->
                p.sections.add(FertSection(name = getString(R.string.section_n, next)))
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        val p = place ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_q, p.sections[pos].name))
            .setMessage(R.string.delete_section_msg)
            .setPositiveButton(R.string.delete) { _, _ ->
                p.sections.removeAt(pos)
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
