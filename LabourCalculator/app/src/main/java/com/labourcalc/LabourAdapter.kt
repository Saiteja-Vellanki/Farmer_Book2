package com.labourcalc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class LabourAdapter(
    private val items: List<Labour>,
    private val onEdit: (Labour) -> Unit,
    private val onMarkPaid: (Labour) -> Unit,
    private val onDelete: (Labour) -> Unit
) : RecyclerView.Adapter<LabourAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val place: TextView = v.findViewById(R.id.tvPlace)
        val date: TextView = v.findViewById(R.id.tvDate)
        val calc: TextView = v.findViewById(R.id.tvCalc)
        val note: TextView = v.findViewById(R.id.tvNote)
        val total: TextView = v.findViewById(R.id.tvTotal)
        val paid: TextView = v.findViewById(R.id.tvPaid)
        val balance: TextView = v.findViewById(R.id.tvBalance)
        val status: TextView = v.findViewById(R.id.tvStatus)
        val btnPaid: Button = v.findViewById(R.id.btnMarkPaid)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_labour, parent, false))

    override fun getItemCount() = items.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        val l = items[pos]
        h.place.text = l.place
        h.date.text = "📅 ${l.date}"
        h.calc.text = "👷 ${l.workers} × ₹${"%.0f".format(l.costPerWorker)}/-"

        if (l.note.isNotBlank()) {
            h.note.visibility = View.VISIBLE
            h.note.text = "📝 ${l.note}"
        } else {
            h.note.visibility = View.GONE
        }

        h.total.text = "Total ₹${"%.0f".format(l.total)}"
        h.paid.text = "Paid ₹${"%.0f".format(l.amountPaid)}"
        h.balance.text = "Bal ₹${"%.0f".format(l.balance)}"

        if (l.isPaid) {
            h.status.text = "PAID ✔"
            h.status.setBackgroundResource(R.drawable.bg_status_paid)
            h.btnPaid.visibility = View.GONE
        } else {
            h.status.text = "DUE ₹${"%.0f".format(l.balance)}"
            h.status.setBackgroundResource(R.drawable.bg_status_due)
            h.btnPaid.visibility = View.VISIBLE
        }

        h.itemView.setOnClickListener { onEdit(l) }
        h.itemView.setOnLongClickListener { onDelete(l); true }
        h.btnPaid.setOnClickListener { onMarkPaid(l) }
    }
}
