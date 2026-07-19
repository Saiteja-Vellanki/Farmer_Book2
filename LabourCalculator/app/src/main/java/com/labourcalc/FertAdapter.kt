package com.labourcalc

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class FertAdapter(
    var rows: List<Pair<String, String>>,
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit
) : RecyclerView.Adapter<FertAdapter.VH>() {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val title: TextView = v.findViewById(R.id.tvFertTitle)
        val subtitle: TextView = v.findViewById(R.id.tvFertSub)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_fert, parent, false))

    override fun getItemCount() = rows.size

    override fun onBindViewHolder(h: VH, pos: Int) {
        h.title.text = rows[pos].first
        val sub = rows[pos].second
        if (sub.isBlank()) h.subtitle.visibility = View.GONE
        else {
            h.subtitle.visibility = View.VISIBLE
            h.subtitle.text = sub
        }
        h.itemView.setOnClickListener { onClick(h.bindingAdapterPosition) }
        h.itemView.setOnLongClickListener { onLongClick(h.bindingAdapterPosition); true }
    }
}
