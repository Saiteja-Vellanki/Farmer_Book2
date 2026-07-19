package com.labourcalc

import android.app.DatePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class FertRecordsActivity : AppCompatActivity() {

    private lateinit var places: MutableList<FertPlace>
    private val mode: String by lazy { intent.getStringExtra("mode") ?: "fert" }
    private var placeId: Long = 0
    private var sectionId: Long = 0
    private lateinit var adapter: FertAdapter
    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    private val place: FertPlace? get() = places.find { it.id == placeId }
    private val section: FertSection? get() = place?.sections?.find { it.id == sectionId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.tvFertHeader).padBelowStatusBar()

        placeId = intent.getLongExtra("placeId", 0)
        sectionId = intent.getLongExtra("sectionId", 0)
        places = FertStore.load(this, mode)
        val s = section ?: run { finish(); return }

        findViewById<TextView>(R.id.tvFertHeader).text =
            if (mode == "spray") "🧪 ${place!!.name} — Spraying"
            else "📍 ${place!!.name} › ${s.name}"

        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FertAdapter(rows(),
            onClick = { },
            onLongClick = { pos -> confirmDelete(pos) })
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fertFab)
        fab.liftAboveNavBar()
        fab.text = if (mode == "spray") "Add Spraying" else "Add Fertigation"
        fab.setOnClickListener { addRecordDialog() }
    }

    private fun dateMillis(d: String): Long =
        try { fmt.parse(d)?.time ?: 0L } catch (e: Exception) { 0L }

    private fun sortedRecords(): List<FertRecord> =
        section?.records?.sortedBy { dateMillis(it.date) } ?: emptyList()

    private fun rows(): List<Pair<String, String>> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        return sortedRecords().map { r ->
            val future = dateMillis(r.date) > todayStart
            val title = if (future) "📅 ${r.date}  ⏳ upcoming" else "📅 ${r.date}"
            val sub = r.items.joinToString("\n") { "• ${it.name}: ${it.qty} ${it.unit}" }
            Pair(title, sub)
        }
    }

    private fun addRecordDialog() {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val dateField = EditText(this).apply {
            hint = "Date (tap to pick) *"
            isFocusable = false
            isClickable = true
            setText(fmt.format(Calendar.getInstance().time))
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@FertRecordsActivity, { _, y, m, d ->
                    setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        container.addView(dateField)

        val rowsHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(rowsHolder)

        fun addFertRow() {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val name = EditText(this).apply {
                hint = if (mode == "spray") "Chemical name" else "Fertilizer name"
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.2f)
            }
            val qty = EditText(this).apply {
                hint = "Qty"
                inputType = android.text.InputType.TYPE_CLASS_NUMBER or
                        android.text.InputType.TYPE_NUMBER_FLAG_DECIMAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.6f)
            }
            val unit = Spinner(this).apply {
                adapter = ArrayAdapter(
                    this@FertRecordsActivity,
                    android.R.layout.simple_spinner_dropdown_item,
                    listOf("kg", "liters")
                )
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.7f)
            }
            row.addView(name); row.addView(qty); row.addView(unit)
            rowsHolder.addView(row)
        }
        addFertRow()

        val addBtn = Button(this).apply {
            text = if (mode == "spray") "+ Add chemical" else "+ Add fertilizer"
            setOnClickListener { addFertRow() }
        }
        container.addView(addBtn)

        AlertDialog.Builder(this)
            .setTitle(if (mode == "spray") "Add Spraying Record" else "Add Fertigation Record")
            .setMessage("Note: record cannot be changed after saving.")
            .setView(container)
            .setPositiveButton("Save") { _, _ ->
                val rec = FertRecord(date = dateField.text.toString().trim())
                for (i in 0 until rowsHolder.childCount) {
                    val row = rowsHolder.getChildAt(i) as LinearLayout
                    val n = (row.getChildAt(0) as EditText).text.toString().trim()
                    val q = (row.getChildAt(1) as EditText).text.toString().toDoubleOrNull() ?: 0.0
                    val u = (row.getChildAt(2) as Spinner).selectedItem.toString()
                    if (n.isNotBlank()) rec.items.add(FertItem(n, q, u))
                }
                if (rec.items.isEmpty()) {
                    Toast.makeText(this, if (mode == "spray") "Add at least one chemical" else "Add at least one fertilizer", Toast.LENGTH_SHORT).show()
                } else {
                    section?.records?.add(rec)
                    FertStore.save(this, mode, places)
                    adapter.rows = rows()
                    adapter.notifyDataSetChanged()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        val rec = sortedRecords().getOrNull(pos) ?: return
        AlertDialog.Builder(this)
            .setTitle("Delete record of ${rec.date}?")
            .setPositiveButton("Delete") { _, _ ->
                section?.records?.removeAll { it.id == rec.id }
                FertStore.save(this, mode, places)
                adapter.rows = rows()
                adapter.notifyDataSetChanged()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}
