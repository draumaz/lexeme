package io.github.draumaz.lexeme.data

import android.content.Context
import androidx.core.content.edit
import org.json.JSONArray

/** Recent lookups, newest first, in SharedPreferences. Small enough that a JSON array is plenty. */
class HistoryStore(context: Context) {
    private val prefs = context.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun load(): List<String> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        List(arr.length()) { arr.getString(it) }
    }.getOrDefault(emptyList())

    fun save(items: List<String>) {
        prefs.edit { putString(KEY, JSONArray(items).toString()) }
    }

    companion object {
        private const val KEY = "recent"
        const val LIMIT = 24

        /** Moves [title] to the front, dropping duplicates and the overflow. */
        fun push(items: List<String>, title: String): List<String> =
            (listOf(title) + items.filter { it != title }).take(LIMIT)
    }
}
