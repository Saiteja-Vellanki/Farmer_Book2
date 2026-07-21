package com.labourcalc

import android.app.DatePickerDialog
import android.os.Bundle
import android.text.InputType
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.view.View
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
    private val units = listOf("kg", "gms", "liters", "ml")

    private val place: FertPlace? get() = places.find { it.id == placeId }
    private val section: FertSection? get() = place?.sections?.find { it.id == sectionId }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.fertHeaderBox).padBelowStatusBar()

        placeId = intent.getLongExtra("placeId", 0)
        sectionId = intent.getLongExtra("sectionId", 0)
        places = FertStore.load(this, mode)
        if (section == null) { finish(); return }

        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        adapter = FertAdapter(rows(),
            onClick = { pos -> sortedRecords().getOrNull(pos)?.let { showRecordDialog(it) } },
            onLongClick = { pos -> deleteOptions(pos) })
        rv.adapter = adapter

        val fab = findViewById<ExtendedFloatingActionButton>(R.id.fertFab)
        fab.liftAboveNavBar()
        fab.text = when (mode) {
            "sale" -> getString(R.string.add_sale_btn)
            "spray" -> getString(R.string.add_spraying)
            else -> getString(R.string.add_fertigation)
        }
        fab.setOnClickListener { showRecordDialog(null) }

        updateHeader()
    }

    private fun totalSales(): Double =
        section?.records?.sumOf { r -> r.items.sumOf { it.qty * it.price } } ?: 0.0

    private fun updateHeader() {
        val header = findViewById<TextView>(R.id.tvFertHeader)
        val chipsRow = findViewById<LinearLayout>(R.id.fertChipsRow)
        when (mode) {
            "sale" -> {
                header.text = getString(R.string.sales_title, place!!.name)
                chipsRow.visibility = View.VISIBLE
                findViewById<TextView>(R.id.chipFertA).text =
                    getString(R.string.n_sales_chip, section?.records?.size ?: 0)
                findViewById<TextView>(R.id.chipFertB).text =
                    getString(R.string.total_sales_chip, "%.0f".format(totalSales()))
            }
            "spray" -> {
                header.text = getString(R.string.spray_header, place!!.name)
                chipsRow.visibility = View.GONE
            }
            else -> {
                header.text = "📍 ${place!!.name}"
                chipsRow.visibility = View.GONE
            }
        }
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
            if (mode == "sale") {
                val total = r.items.sumOf { it.qty * it.price }
                val buyerTxt = if (r.buyer.isNotBlank()) "  •  🧑 ${r.buyer}" else ""
                val title = "📅 ${r.date}$buyerTxt   ₹${"%.0f".format(total)}"
                val sub = r.items.joinToString("\n") {
                    "• ${it.name}: ${"%.1f".format(it.qty)} kg × ₹${"%.0f".format(it.price)} = ₹${"%.0f".format(it.qty * it.price)}"
                }
                Pair(title, sub)
            } else {
                val future = dateMillis(r.date) > todayStart
                val title = if (future) "📅 ${r.date}  " + getString(R.string.upcoming) else "📅 ${r.date}"
                val sub = r.items.joinToString("\n") { "• ${it.name}: ${it.qty} ${it.unit}" }
                Pair(title, sub)
            }
        }
    }

    private fun refreshList() {
        adapter.rows = rows()
        adapter.notifyDataSetChanged()
        updateHeader()
    }

    private fun showRecordDialog(existing: FertRecord?) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(48, 24, 48, 8)
        }

        val dateField = EditText(this).apply {
            hint = getString(R.string.hint_date)
            isFocusable = false
            isClickable = true
            setText(if (existing != null && existing.date.isNotBlank()) existing.date
                    else fmt.format(Calendar.getInstance().time))
            setOnClickListener {
                val cal = Calendar.getInstance()
                DatePickerDialog(this@FertRecordsActivity, { _, y, m, d ->
                    setText(String.format(Locale.US, "%02d/%02d/%04d", d, m + 1, y))
                }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
            }
        }
        container.addView(dateField)

        val buyerField = if (mode == "sale") {
            EditText(this).apply {
                hint = getString(R.string.hint_buyer)
                setText(existing?.buyer ?: "")
            }
        } else null
        buyerField?.let { container.addView(it) }

        val rowsHolder = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        container.addView(rowsHolder)

        fun addItemRow(item: FertItem?) {
            val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
            val name = EditText(this).apply {
                hint = when (mode) {
                    "sale" -> getString(R.string.hint_item)
                    "spray" -> getString(R.string.hint_chemical)
                    else -> getString(R.string.hint_fertilizer)
                }
                setText(item?.name ?: "")
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.1f)
            }
            val qty = EditText(this).apply {
                hint = if (mode == "sale") getString(R.string.hint_kgs) else getString(R.string.hint_qty)
                inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                if (item != null && item.qty > 0) setText(item.qty.toString())
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.55f)
            }
            row.addView(name)
            row.addView(qty)
            if (mode == "sale") {
                val price = EditText(this).apply {
                    hint = getString(R.string.hint_price)
                    inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
                    if (item != null && item.price > 0) setText(item.price.toString())
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f)
                }
                row.addView(price)
            } else {
                val unit = Spinner(this).apply {
                    adapter = ArrayAdapter(
                        this@FertRecordsActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        units
                    )
                    val idx = units.indexOf(item?.unit ?: "kg")
                    setSelection(if (idx >= 0) idx else 0)
                    layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 0.65f)
                }
                row.addView(unit)
            }
            rowsHolder.addView(row)
        }

        if (existing != null && existing.items.isNotEmpty()) {
            existing.items.forEach { addItemRow(it) }
        } else {
            addItemRow(null)
        }

        val addBtn = Button(this).apply {
            text = when (mode) {
                "sale" -> getString(R.string.add_item_btn)
                "spray" -> getString(R.string.add_chemical_btn)
                else -> getString(R.string.add_fertilizer_btn)
            }
            setOnClickListener { addItemRow(null) }
        }
        container.addView(addBtn)

        val titleWord = when (mode) {
            "sale" -> getString(R.string.word_sale)
            "spray" -> getString(R.string.word_spray)
            else -> getString(R.string.word_fert)
        }
        AlertDialog.Builder(this)
            .setTitle(if (existing == null) getString(R.string.add_x, titleWord) else getString(R.string.edit_x, titleWord))
            .setView(container)
            .setPositiveButton(R.string.save) { _, _ ->
                val items = mutableListOf<FertItem>()
                for (i in 0 until rowsHolder.childCount) {
                    val row = rowsHolder.getChildAt(i) as LinearLayout
                    val n = (row.getChildAt(0) as EditText).text.toString().trim()
                    val q = (row.getChildAt(1) as EditText).text.toString().toDoubleOrNull() ?: 0.0
                    if (n.isBlank()) continue
                    if (mode == "sale") {
                        val pr = (row.getChildAt(2) as EditText).text.toString().toDoubleOrNull() ?: 0.0
                        items.add(FertItem(n, q, "kg", pr))
                    } else {
                        val u = (row.getChildAt(2) as Spinner).selectedItem.toString()
                        items.add(FertItem(n, q, u))
                    }
                }
                if (items.isEmpty()) {
                    Toast.makeText(this, getString(R.string.at_least_one), Toast.LENGTH_SHORT).show()
                } else {
                    val date = dateField.text.toString().trim()
                        .ifBlank { fmt.format(Calendar.getInstance().time) }
                    val buyer = buyerField?.text?.toString()?.trim() ?: ""
                    if (existing == null) {
                        section?.records?.add(FertRecord(date = date, buyer = buyer, items = items))
                    } else {
                        existing.date = date
                        existing.buyer = buyer
                        existing.items = items
                    }
                    FertStore.save(this, mode, places)
                    refreshList()
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun deleteOptions(pos: Int) {
        AlertDialog.Builder(this)
            .setItems(arrayOf(getString(R.string.delete_this_record), getString(R.string.select_multiple))) { _, which ->
                if (which == 0) confirmDelete(pos) else multiDeleteDialog()
            }
            .show()
    }

    private fun multiDeleteDialog() {
        val recs = sortedRecords()
        if (recs.isEmpty()) return
        val labels = recs.map { r ->
            if (mode == "sale")
                "${r.date}  ₹${"%.0f".format(r.items.sumOf { it.qty * it.price })}"
            else
                "${r.date}  " + getString(R.string.n_items, r.items.size)
        }.toTypedArray()
        val checked = BooleanArray(labels.size)
        AlertDialog.Builder(this)
            .setTitle(R.string.select_records)
            .setMultiChoiceItems(labels, checked) { _, i, b -> checked[i] = b }
            .setPositiveButton(R.string.delete) { _, _ ->
                val ids = recs.filterIndexed { i, _ -> checked[i] }.map { it.id }
                if (ids.isEmpty()) return@setPositiveButton
                AlertDialog.Builder(this)
                    .setTitle(getString(R.string.delete_n_records, ids.size))
                    .setPositiveButton(R.string.yes_delete) { _, _ ->
                        section?.records?.removeAll { it.id in ids }
                        FertStore.save(this, mode, places)
                        refreshList()
                    }
                    .setNegativeButton(R.string.cancel, null)
                    .show()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun confirmDelete(pos: Int) {
        val rec = sortedRecords().getOrNull(pos) ?: return
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.delete_record_q, rec.date))
            .setPositiveButton(R.string.delete) { _, _ ->
                section?.records?.removeAll { it.id == rec.id }
                FertStore.save(this, mode, places)
                refreshList()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }
}
