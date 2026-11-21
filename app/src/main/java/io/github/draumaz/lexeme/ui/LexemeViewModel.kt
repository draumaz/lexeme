package io.github.draumaz.lexeme.ui

import android.app.Application
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.draumaz.lexeme.data.CategoryPage
import io.github.draumaz.lexeme.data.CategoryResult
import io.github.draumaz.lexeme.data.Entry
import io.github.draumaz.lexeme.data.MemberKind
import io.github.draumaz.lexeme.data.isCategoryTitle
import io.github.draumaz.lexeme.data.titleParts
import io.github.draumaz.lexeme.data.HistoryStore
import io.github.draumaz.lexeme.data.LookupResult
import io.github.draumaz.lexeme.data.WiktionaryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface PageState {
    data object Loading : PageState
    data class Loaded(val entry: Entry) : PageState

    /**
     * A category page. [loading] is the member list fetching its next batch, if any;
     * [failed] the one whose last batch didn't arrive, so the list can offer a retry.
     */
    data class Category(
        val category: CategoryPage,
        val loading: MemberKind? = null,
        val failed: MemberKind? = null,
    ) : PageState
    data class Missing(val suggestions: List<String>) : PageState
    data class Failed(val message: String) : PageState
}

/** A namespaced title without its prefix: `Proto-Germanic/watōr`, `English nouns`. */
private fun bareName(title: String): String = title.substringAfter(':').trim()

/**
 * Puts titles that really start with [typed] (in full, or by their bare name) ahead of the
 * search engine's looser matches, keeping each group in its order: with namespaces in play
 * it can rank a near-miss like `protogermanica` above `Reconstruction:Proto-Germanic/watōr`.
 */
internal fun prefixMatchesFirst(typed: String, items: List<String>): List<String> =
    items.sortedBy { item ->
        !(item.startsWith(typed, ignoreCase = true) ||
            (titleParts(item).namespace != null && bareName(item).startsWith(typed, ignoreCase = true)))
    }

data class Suggestions(val query: String, val items: List<String>) {
    /**
     * What Enter should open for [typed]: an exact match if there is one, so typing a real
     * word never jumps elsewhere, then a namespaced page whose bare name matches (typing
     * `English nouns` opens the category), otherwise the top suggestion. Null if these are stale.
     */
    fun pickFor(typed: String): String? {
        val t = typed.trim()
        if (t.isEmpty() || t != query) return null
        return items.firstOrNull { it == t }
            ?: items.firstOrNull { item -> titleParts(item).let { it.namespace != null && bareName(item).equals(t, ignoreCase = true) } }
            ?: items.firstOrNull()
    }
}

/** One screen in the back stack. Ids only grow, which is how transitions tell push from pop. */
data class Page(val id: Long, val title: String, val fragment: String?, val state: PageState)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LexemeViewModel(app: Application) : AndroidViewModel(app) {
    private val api = WiktionaryApi()
    private val historyStore = HistoryStore(app)
    private val cache = LruCache<String, Entry>(16)
    /** Category pages as last seen, members loaded so far included. */
    private val categoryCache = LruCache<String, CategoryPage>(8)
    private val loadJobs = HashMap<Long, Job>()
    private val moreJobs = HashMap<Long, Job>()
    private var nextId = 1L

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _recent = MutableStateFlow(historyStore.load())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    val query = MutableStateFlow("")

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot snackbar messages. */
    val messages = _messages.receiveAsFlow()

    /**
     * Type-ahead titles, tagged with the query they answer: they arrive debounced, so the UI
     * only treats them as current (highlight, Enter picks the top one) once they match the field.
     */
    val suggestions: StateFlow<Suggestions> = query
        .debounce(200)
        .distinctUntilChanged()
        .mapLatest { q ->
            val trimmed = q.trim()
            if (trimmed.isEmpty()) Suggestions("", emptyList()) else try {
                Suggestions(trimmed, prefixMatchesFirst(trimmed, api.suggest(trimmed)))
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                Suggestions(trimmed, emptyList())
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Suggestions("", emptyList()))

    fun open(title: String, fragment: String? = null) {
        val t = title.trim()
        if (t.isEmpty()) return
        if (isCategoryTitle(t)) return openCategory(t)
        val cached = cache.get(t)
        val page = Page(nextId++, t, fragment, cached?.let { PageState.Loaded(it) } ?: PageState.Loading)
        _pages.update { it + page }
        query.value = ""
        // A cache hit may be a redirect ("Water" for "water"): history keeps the real title.
        if (cached != null) remember(cached.title) else load(page)
    }

    private fun openCategory(title: String) {
        val cached = categoryCache.get(title)
        val page = Page(nextId++, title, null, cached?.let { PageState.Category(it) } ?: PageState.Loading)
        _pages.update { it + page }
        query.value = ""
        if (cached != null) remember(cached.title) else load(page)
    }

    fun retry(page: Page) {
        loadJobs.remove(page.id)?.cancel()
        replace(page.id) { it.copy(state = PageState.Loading) }
        load(page)
    }

    private fun load(page: Page) {
        if (isCategoryTitle(page.title)) return loadCategory(page)
        loadJobs.remove(page.id)?.cancel()
        lateinit var job: Job
        job = viewModelScope.launch {
            try {
                val state = try {
                    when (val result = api.lookup(page.title)) {
                        is LookupResult.Found -> {
                            cache.put(page.title, result.entry)
                            cache.put(result.entry.title, result.entry)
                            remember(result.entry.title)
                            PageState.Loaded(result.entry)
                        }
                        is LookupResult.Missing -> PageState.Missing(result.suggestions)
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PageState.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach Wiktionary")
                }
                replace(page.id) { it.copy(state = state) }
            } finally {
                if (loadJobs[page.id] === job) loadJobs.remove(page.id)
            }
        }
        loadJobs[page.id] = job
    }

    private fun loadCategory(page: Page) {
        loadJobs.remove(page.id)?.cancel()
        lateinit var job: Job
        job = viewModelScope.launch {
            try {
                val state = try {
                    when (val result = api.category(page.title)) {
                        is CategoryResult.Found -> {
                            categoryCache.put(page.title, result.category)
                            categoryCache.put(result.category.title, result.category)
                            remember(result.category.title)
                            PageState.Category(result.category)
                        }
                        CategoryResult.Missing -> PageState.Missing(emptyList())
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    PageState.Failed(e.message?.takeIf { it.isNotBlank() } ?: "Couldn't reach Wiktionary")
                }
                replace(page.id) { it.copy(state = state) }
            } finally {
                if (loadJobs[page.id] === job) loadJobs.remove(page.id)
            }
        }
        loadJobs[page.id] = job
    }

    /** Fetches the next batch of [kind] members for a category page; one batch at a time. */
    fun loadMore(pageId: Long, kind: MemberKind) {
        val state = _pages.value.firstOrNull { it.id == pageId }?.state as? PageState.Category ?: return
        if (state.loading != null) return
        val members = state.category.members(kind)
        val from = members.next ?: return
        replace(pageId) { it.copy(state = state.copy(loading = kind, failed = null)) }
        moreJobs.remove(pageId)?.cancel()
        lateinit var job: Job
        job = viewModelScope.launch {
            try {
                val more = try {
                    api.categoryMembers(state.category.title, kind, from)
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    null
                }
                replace(pageId) { page ->
                    val current = page.state as? PageState.Category ?: return@replace page
                    if (more == null) return@replace page.copy(state = current.copy(loading = null, failed = kind))
                    val category = current.category.let {
                        when (kind) {
                            MemberKind.Subcategories -> it.copy(subcategories = it.subcategories + more)
                            MemberKind.Pages -> it.copy(pages = it.pages + more)
                        }
                    }
                    categoryCache.put(page.title, category)
                    categoryCache.put(category.title, category)
                    page.copy(state = current.copy(category = category, loading = null, failed = null))
                }
            } finally {
                if (moreJobs[pageId] === job) moreJobs.remove(pageId)
            }
        }
        moreJobs[pageId] = job
    }

    private fun replace(id: Long, transform: (Page) -> Page) {
        _pages.update { pages -> pages.map { if (it.id == id) transform(it) else it } }
    }

    private fun remember(title: String) {
        _recent.update { HistoryStore.push(it, title) }
        historyStore.save(_recent.value)
    }

    /** Pops one page; nothing when already home. */
    fun back() {
        val popped = _pages.value.lastOrNull() ?: return
        loadJobs.remove(popped.id)?.cancel()
        moreJobs.remove(popped.id)?.cancel()
        _pages.update { it.dropLast(1) }
    }

    fun home() {
        loadJobs.values.forEach { it.cancel() }
        loadJobs.clear()
        moreJobs.values.forEach { it.cancel() }
        moreJobs.clear()
        _pages.value = emptyList()
    }

    fun clearHistory() {
        _recent.value = emptyList()
        historyStore.save(emptyList())
    }
}
