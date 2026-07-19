package com.labourcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class Labour(
    var id: Long = System.currentTimeMillis(),
    var date: String = "",
    var place: String = "",
    var workers: Int = 0,
    var costPerWorker: Double = 0.0,
    var note: String = "",
    var amountPaid: Double = 0.0
) {
    val total: Double get() = workers * costPerWorker
    val balance: Double get() = total - amountPaid
    val isPaid: Boolean get() = balance <= 0.009 && total > 0
}

object LabourStore {
    private const val PREFS = "labour_store"
    private const val KEY = "labours"

    fun load(context: Context): MutableList<Labour> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val arr = JSONArray(json)
        val list = mutableListOf<Labour>()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            list.add(
                Labour(
                    id = o.optLong("id"),
                    date = o.optString("date"),
                    place = o.optString("place"),
                    workers = o.optInt("workers", 0),
                    costPerWorker = o.optDouble("cost", 0.0),
                    note = o.optString("note"),
                    amountPaid = o.optDouble("paid", 0.0)
                )
            )
        }
        return list
    }

    fun save(context: Context, list: List<Labour>) {
        val arr = JSONArray()
        for (l in list) {
            arr.put(
                JSONObject()
                    .put("id", l.id)
                    .put("date", l.date)
                    .put("place", l.place)
                    .put("workers", l.workers)
                    .put("cost", l.costPerWorker)
                    .put("note", l.note)
                    .put("paid", l.amountPaid)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
