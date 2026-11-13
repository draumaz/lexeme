package io.github.draumaz.lexeme.ui

import android.app.Application
import android.util.LruCache
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.draumaz.lexeme.data.Entry
import io.github.draumaz.lexeme.data.HistoryStore
import io.github.draumaz.lexeme.data.LookupResult
import io.github.draumaz.lexeme.data.WiktionaryApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.FlowPreview
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
    data class Missing(val suggestions: List<String>) : PageState
    data class Failed(val message: String) : PageState
}

data class Suggestions(val query: String, val items: List<String>) {
    /**
     * What Enter should open for [typed]: an exact match if there is one, so typing a real
     * word never jumps elsewhere, otherwise the top suggestion. Null if these are stale.
     */
    fun pickFor(typed: String): String? {
        val t = typed.trim()
        if (t.isEmpty() || t != query) return null
        return items.firstOrNull { it == t } ?: items.firstOrNull()
    }
}

/** One screen in the back stack. Ids only grow, which is how transitions tell push from pop. */
data class Page(val id: Long, val title: String, val fragment: String?, val state: PageState)

@OptIn(FlowPreview::class, kotlinx.coroutines.ExperimentalCoroutinesApi::class)
class LexemeViewModel(app: Application) : AndroidViewModel(app) {
    private val api = WiktionaryApi()
    private val historyStore = HistoryStore(app)
    private val cache = LruCache<String, Entry>(16)
    private var nextId = 1L

    private val _pages = MutableStateFlow<List<Page>>(emptyList())
    val pages: StateFlow<List<Page>> = _pages.asStateFlow()

    private val _recent = MutableStateFlow(historyStore.load())
    val recent: StateFlow<List<String>> = _recent.asStateFlow()

    val query = MutableStateFlow("")

    private val _messages = Channel<String>(Channel.BUFFERED)
    /** One-shot snackbar messages. */
    val messages = _messages.receiveAsFlow()

    private val _randomBusy = MutableStateFlow(false)
    val randomBusy: StateFlow<Boolean> = _randomBusy.asStateFlow()

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
                Suggestions(trimmed, api.suggest(trimmed))
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
        val cached = cache.get(t)
        val page = Page(nextId++, t, fragment, cached?.let { PageState.Loaded(it) } ?: PageState.Loading)
        _pages.update { it + page }
        query.value = ""
        // A cache hit may be a redirect ("Water" for "water"): history keeps the real title.
        if (cached != null) remember(cached.title) else load(page)
    }

    fun retry(page: Page) {
        replace(page.id) { it.copy(state = PageState.Loading) }
        load(page)
    }

    private fun load(page: Page) {
        viewModelScope.launch {
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
        }
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
        _pages.update { it.dropLast(1) }
    }

    fun home() {
        _pages.value = emptyList()
    }

    fun random() {
        if (_randomBusy.value) return
        _randomBusy.value = true
        viewModelScope.launch {
            try {
                open(api.randomTitle())
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                _messages.send("Couldn't reach Wiktionary")
            } finally {
                _randomBusy.value = false
            }
        }
    }

    fun clearHistory() {
        _recent.value = emptyList()
        historyStore.save(emptyList())
    }
}
