package io.github.draumaz.lexeme.data

import io.github.draumaz.lexeme.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttp
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * Wikimedia's User-Agent policy asks clients to name themselves and give contact info. Without
 * the contact URL, its edge treats OkHttp traffic as an anonymous bot and some IPs get HTTP 403
 * ("Too many requests").
 */
val USER_AGENT =
    "Lexeme/${BuildConfig.VERSION_NAME} (https://github.com/draumaz/lexeme) okhttp/${OkHttp.VERSION}"

sealed interface LookupResult {
    data class Found(val entry: Entry) : LookupResult
    data class Missing(val suggestions: List<String>) : LookupResult
}

class WiktionaryApi(val client: OkHttpClient = defaultClient()) {

    private val base = "https://en.wiktionary.org/w/api.php".toHttpUrl()

    /**
     * Fetches and parses [title]. Wiktionary titles are case-sensitive, so a miss retries the
     * lower- and upper-cased first letter before falling back to search suggestions.
     */
    suspend fun lookup(title: String): LookupResult = withContext(Dispatchers.IO) {
        val candidates = listOf(
            title,
            title.replaceFirstChar { it.lowercaseChar() },
            title.replaceFirstChar { it.titlecaseChar() },
        ).distinct()
        for (candidate in candidates) {
            val parsed = fetchParsed(candidate) ?: continue
            val entry = withContext(Dispatchers.Default) { WiktionaryParser.parse(parsed.first, parsed.second) }
            return@withContext LookupResult.Found(entry)
        }
        LookupResult.Missing(runCatching { suggest(title, 12) }.getOrDefault(emptyList()).filter { it != title })
    }

    /** Returns the resolved title and HTML, or null if the page doesn't exist. */
    private fun fetchParsed(title: String): Pair<String, String>? {
        val url = base.newBuilder()
            .addQueryParameter("action", "parse")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("prop", "text")
            .addQueryParameter("disableeditsection", "1")
            .addQueryParameter("disabletoc", "1")
            .addQueryParameter("page", title)
            .build()
        val json = getJson(url)
        json.optJSONObject("error")?.let { error ->
            if (error.optString("code") == "missingtitle" || error.optString("code") == "invalidtitle") return null
            throw IOException(error.optString("info", "Wiktionary returned an error"))
        }
        val parse = json.getJSONObject("parse")
        return parse.getString("title") to parse.getString("text")
    }

    /** Prefix search for the type-ahead list. */
    suspend fun suggest(prefix: String, limit: Int = 8): List<String> = withContext(Dispatchers.IO) {
        if (prefix.isBlank()) return@withContext emptyList()
        val url = base.newBuilder()
            .addQueryParameter("action", "opensearch")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("namespace", "0")
            .addQueryParameter("limit", limit.toString())
            .addQueryParameter("search", prefix)
            .build()
        val body = get(url)
        val names = JSONArray(body).optJSONArray(1) ?: return@withContext emptyList()
        List(names.length()) { names.getString(it) }
    }

    /** A random main-namespace page, for the "surprise me" button. */
    suspend fun randomTitle(): String = withContext(Dispatchers.IO) {
        val url = base.newBuilder()
            .addQueryParameter("action", "query")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("list", "random")
            .addQueryParameter("rnnamespace", "0")
            .addQueryParameter("rnlimit", "1")
            .build()
        getJson(url).getJSONObject("query").getJSONArray("random").getJSONObject(0).getString("title")
    }

    private fun getJson(url: HttpUrl) = JSONObject(get(url))

    private fun get(url: HttpUrl): String {
        val request = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            return response.body.string()
        }
    }

    companion object {
        /**
         * One client for the whole app (API and image loading), so they share a connection
         * pool and the TLS handshake to Wikimedia is paid once.
         */
        fun defaultClient(): OkHttpClient = shared

        private val shared: OkHttpClient by lazy { build() }

        private fun build(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .build()
    }
}
