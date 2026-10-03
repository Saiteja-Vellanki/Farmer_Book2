package com.farmerbook.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WorkerPlace(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var acres: Double = 0.0
)

/** Places created up front via "+ Add Place" (before any labour entry exists there).
 *  Places that already have labour entries are derived from LabourStore directly,
 *  so a place always shows up whether it was pre-created or just typed on an entry. */
object WorkerPlaceStore {
    private const val PREFS = "worker_place_store"
    private const val KEY = "places"

    fun load(context: Context): MutableList<WorkerPlace> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val out = mutableListOf<WorkerPlace>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(WorkerPlace(o.optLong("id"), o.optString("name"), o.optDouble("acres", 0.0)))
            }
        } catch (e: Exception) { }
        return out
    }

    fun save(context: Context, places: List<WorkerPlace>) {
        val arr = JSONArray()
        for (p in places) {
            arr.put(JSONObject().put("id", p.id).put("name", p.name).put("acres", p.acres))
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
