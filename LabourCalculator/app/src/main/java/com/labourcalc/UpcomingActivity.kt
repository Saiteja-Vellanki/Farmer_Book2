package com.labourcalc

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.floatingactionbutton.ExtendedFloatingActionButton
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class UpcomingActivity : AppCompatActivity() {

    private val fmt = SimpleDateFormat("dd/MM/yyyy", Locale.US)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fert_list)
        findViewById<android.view.View>(R.id.tvFertHeader).padBelowStatusBar()
        findViewById<TextView>(R.id.tvFertHeader).text = getString(R.string.upcoming_title)
        findViewById<View>(R.id.fertChipsRow)?.visibility = View.GONE
        findViewById<ExtendedFloatingActionButton>(R.id.fertFab).visibility = View.GONE

        val rows = buildRows()
        val rv = findViewById<RecyclerView>(R.id.fertRecycler)
        rv.layoutManager = LinearLayoutManager(this)
        rv.adapter = FertAdapter(
            if (rows.isEmpty()) listOf(Pair(getString(R.string.no_upcoming), "")) else rows,
            onClick = { }, onLongClick = { })
    }

    private fun buildRows(): List<Pair<String, String>> {
        val todayStart = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        data class Ev(val time: Long, val title: String, val sub: String)
        val events = mutableListOf<Ev>()
        for (mode in listOf("fert", "spray")) {
            val tag = if (mode == "spray") "🧪" else "🌱"
            for (p in FertStore.load(this, mode)) {
                for (sec in p.sections) {
                    for (r in sec.records) {
                        val t = try { fmt.parse(r.date)?.time ?: 0L } catch (e: Exception) { 0L }
                        if (t >= todayStart) {
                            var sub = r.items.joinToString("\n") { "• ${it.name}: ${it.qty} ${it.unit}" }
                            if (r.note.isNotBlank()) sub += "\n📝 ${r.note}"
                            events.add(Ev(t, "$tag ${p.name}  📅 ${r.date}", sub))
                        }
                    }
                }
            }
        }
        return events.sortedBy { it.time }.map { Pair(it.title, it.sub) }
    }
}
