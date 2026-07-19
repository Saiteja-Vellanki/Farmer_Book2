package com.labourcalc

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class FertItem(var name: String = "", var qty: Double = 0.0, var unit: String = "kg")

data class FertRecord(
    var id: Long = System.currentTimeMillis(),
    var date: String = "",
    var items: MutableList<FertItem> = mutableListOf()
)

data class FertSection(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var records: MutableList<FertRecord> = mutableListOf()
)

data class FertPlace(
    var id: Long = System.currentTimeMillis(),
    var name: String = "",
    var acres: Double = 0.0,
    var sections: MutableList<FertSection> = mutableListOf()
)

object FertStore {
    private const val PREFS = "fert_store"
    private const val KEY = "places"

    fun load(context: Context): MutableList<FertPlace> {
        val json = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY, "[]") ?: "[]"
        val out = mutableListOf<FertPlace>()
        try {
            val arr = JSONArray(json)
            for (i in 0 until arr.length()) {
                val p = arr.getJSONObject(i)
                val place = FertPlace(
                    id = p.optLong("id"),
                    name = p.optString("name"),
                    acres = p.optDouble("acres", 0.0)
                )
                val secs = p.optJSONArray("sections") ?: JSONArray()
                for (j in 0 until secs.length()) {
                    val s = secs.getJSONObject(j)
                    val sec = FertSection(id = s.optLong("id"), name = s.optString("name"))
                    val recs = s.optJSONArray("records") ?: JSONArray()
                    for (k in 0 until recs.length()) {
                        val r = recs.getJSONObject(k)
                        val rec = FertRecord(id = r.optLong("id"), date = r.optString("date"))
                        val items = r.optJSONArray("items") ?: JSONArray()
                        for (m in 0 until items.length()) {
                            val it = items.getJSONObject(m)
                            rec.items.add(
                                FertItem(
                                    it.optString("n"), it.optDouble("q", 0.0), it.optString("u", "kg")
                                )
                            )
                        }
                        sec.records.add(rec)
                    }
                    place.sections.add(sec)
                }
                out.add(place)
            }
        } catch (e: Exception) { }
        return out
    }

    fun save(context: Context, places: List<FertPlace>) {
        val arr = JSONArray()
        for (p in places) {
            val secs = JSONArray()
            for (s in p.sections) {
                val recs = JSONArray()
                for (r in s.records) {
                    val items = JSONArray()
                    for (it in r.items) {
                        items.put(JSONObject().put("n", it.name).put("q", it.qty).put("u", it.unit))
                    }
                    recs.put(
                        JSONObject().put("id", r.id).put("date", r.date).put("items", items)
                    )
                }
                secs.put(JSONObject().put("id", s.id).put("name", s.name).put("records", recs))
            }
            arr.put(
                JSONObject().put("id", p.id).put("name", p.name)
                    .put("acres", p.acres).put("sections", secs)
            )
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY, arr.toString()).apply()
    }
}
