package io.github.draumaz.lexeme.data

import io.github.draumaz.lexeme.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
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

sealed interface CategoryResult {
    data class Found(val category: CategoryPage) : CategoryResult
    data object Missing : CategoryResult
}

/** Which members of a category to list. */
enum class MemberKind(internal val apiName: String) { Subcategories("subcat"), Pages("page") }

/** A parsed page: its resolved title, HTML, and visible categories. */
private class Parsed(val title: String, val html: String, val categories: List<String>)

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
            val entry = withContext(Dispatchers.Default) {
                WiktionaryParser.parse(parsed.title, parsed.html, parsed.categories)
            }
            return@withContext LookupResult.Found(entry)
        }
        LookupResult.Missing(runCatching { suggest(title, 12) }.getOrDefault(emptyList()).filter { it != title })
    }

    /** Returns the resolved title, HTML and categories, or null if the page doesn't exist. */
    private fun fetchParsed(title: String): Parsed? {
        val url = base.newBuilder()
            .addQueryParameter("action", "parse")
            .addQueryParameter("format", "json")
            .addQueryParameter("formatversion", "2")
            .addQueryParameter("redirects", "1")
            .addQueryParameter("prop", "text|categories")
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
        val categories = parse.optJSONArray("categories")?.let { list ->
            List(list.length()) { list.getJSONObject(it) }
                .filterNot { it.optBoolean("hidden") }
                .map { it.getString("category").replace('_', ' ') }
        }.orEmpty()
        return Parsed(parse.getString("title"), parse.getString("text"), categories)
    }

    /**
     * A category page: its description (if it has one; a category can exist through its members
     * alone), counts, and the first batch of subcategories and pages, fetched side by side.
     */
    suspend fun category(title: String): CategoryResult = coroutineScope {
        val name = title.trim().substring(CategoryPrefix.length).trim().replace('_', ' ')
        if (name.isEmpty()) return@coroutineScope CategoryResult.Missing
        val requested = CategoryPrefix + name
        val parsed = async(Dispatchers.IO) { fetchParsed(requested) }
        val info = async(Dispatchers.IO) { categoryInfo(requested) }
        val pages = async(Dispatchers.IO) { fetchMembers(requested, MemberKind.Pages, null) }
        val (resolved, counts, subcategories) = info.await()
        val page = parsed.await()
        val members = pages.await()
        if (page == null && counts == null && subcategories.titles.isEmpty() && members.titles.isEmpty()) {
            return@coroutineScope CategoryResult.Missing
        }
        val canonical = page?.title ?: resolved
        val (breadcrumbs, description) = page?.let {
            withContext(Dispatchers.Default) { WiktionaryParser.parseCategory(canonical, it.html) }
        } ?: (emptyList<Breadcrumb>() to emptyList())
        CategoryResult.Found(
            CategoryPage(
                title = canonical,
                breadcrumbs = breadcrumbs,
                description = description,
                parents = page?.categories.orEmpty(),
                pageCount = counts?.first ?: members.titles.size,
                subcategoryCount = counts?.second ?: subcategories.titles.size,
                subcategories = subcategories,
                pages = members,
            ),
        )
    }

    /** The normalised title, (pages, subcategories) counts if known, and the first subcategories. */
    private fun categoryInfo(title: String): Triple<String, Pair<Int, Int>?, CategoryMembers> {
        val url = membersUrl(title, MemberKind.Subcategories, null).newBuilder()
            .addQueryParameter("prop", "categoryinfo")
            .addQueryParameter("titles", title)
            .build()
        val json = getJson(url)
        val query = json.optJSONObject("query")
        val page = query?.optJSONArray("pages")?.optJSONObject(0)
        val counts = page?.optJSONObject("categoryinfo")?.let { it.optInt("pages") to it.optInt("subcats") }
        return Triple(page?.optString("title")?.ifBlank { null } ?: title, counts, membersOf(json))
    }

    /** The next batch of [kind] members of [title], continuing from [from]. */
    suspend fun categoryMembers(title: String, kind: MemberKind, from: String?): CategoryMembers =
        withContext(Dispatchers.IO) { fetchMembers(title, kind, from) }

    private fun fetchMembers(title: String, kind: MemberKind, from: String?): CategoryMembers =
        membersOf(getJson(membersUrl(title, kind, from)))

    private fun membersUrl(title: String, kind: MemberKind, from: String?): HttpUrl = base.newBuilder()
        .addQueryParameter("action", "query")
        .addQueryParameter("format", "json")
        .addQueryParameter("formatversion", "2")
        .addQueryParameter("list", "categorymembers")
        .addQueryParameter("cmtitle", title)
        .addQueryParameter("cmtype", kind.apiName)
        .addQueryParameter("cmprop", "title")
        .addQueryParameter("cmlimit", MemberBatch.toString())
        .apply { if (from != null) addQueryParameter("cmcontinue", from).addQueryParameter("continue", "-||") }
        .build()

    private fun membersOf(json: JSONObject): CategoryMembers {
        json.optJSONObject("error")?.let { throw IOException(it.optString("info", "Wiktionary returned an error")) }
        val list = json.optJSONObject("query")?.optJSONArray("categorymembers")
        val titles = list?.let { l -> List(l.length()) { l.getJSONObject(it).getString("title") } }.orEmpty()
        val next = json.optJSONObject("continue")?.optString("cmcontinue")?.ifBlank { null }
        return CategoryMembers(titles, next)
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
        /** Members fetched per request; the API's limit for ordinary clients is 500. */
        private const val MemberBatch = 200

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
