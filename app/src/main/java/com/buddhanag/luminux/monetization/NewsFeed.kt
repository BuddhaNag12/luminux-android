package com.buddhanag.luminux.monetization

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL

@Serializable
data class NewsArticle(val title: String, val source: String, val url: String, val image: String? = null, val publishedAt: String? = null)

/** What the news proxy (Server/news-worker) returns. `enabled` is the remote switch for the whole panel. */
@Serializable
data class NewsResponse(val enabled: Boolean, val articles: List<NewsArticle>, val attribution: String? = null)

/**
 * Headlines for the hub's news panel, from Luminux's own proxy so the provider's key stays off the phone (NewsFeed.swift).
 * Parked: the panel appears only once `luminux.newsUrl` is set and the proxy's switch is on.
 */
class NewsFeed(
    private val prefs: DataStore<Preferences>,
    private val endpoint: String?,
    private val get: suspend (String) -> Pair<Int, String> = ::httpGet,
    private val now: () -> Long = System::currentTimeMillis,
) {
    enum class Phase { Idle, Loading, Loaded, Failed }

    data class State(
        val articles: List<NewsArticle> = emptyList(),
        val attribution: String? = null,
        val phase: Phase = Phase.Idle,
        val isEnabled: Boolean = false,
    )

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state
    private var fetchedAt: Long? = null

    /** The switch is remembered between launches, so the panel doesn't pop in on the left after the hub is laid out. */
    suspend fun load() {
        _state.update { it.copy(isEnabled = endpoint != null && prefs.data.first()[ENABLED] == true) }
    }

    suspend fun refreshIfStale() {
        if (fetchedAt?.let { now() - it < REFRESH_INTERVAL_MS } == true) return
        refresh()
    }

    suspend fun refresh() {
        val url = endpoint ?: return
        if (_state.value.phase == Phase.Loading) return
        if (_state.value.articles.isEmpty()) _state.update { it.copy(phase = Phase.Loading) }
        try {
            val (code, body) = get(url)
            check(code == 200) { "HTTP $code" }
            val feed = decode(body)
            prefs.edit { it[ENABLED] = feed.enabled }
            _state.value = State(feed.articles, feed.attribution, Phase.Loaded, feed.enabled)
            fetchedAt = now()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            // Keep the last headlines on screen; only an empty panel shows the error.
            _state.update { it.copy(phase = if (it.articles.isEmpty()) Phase.Failed else Phase.Loaded) }
        }
    }

    companion object {
        val ENABLED = booleanPreferencesKey("newsEnabled")
        /** The proxy caches for this long too, so fetching sooner only returns the same headlines. */
        const val REFRESH_INTERVAL_MS = 15 * 60_000L
        private val json = Json { ignoreUnknownKeys = true }
        fun decode(body: String) = json.decodeFromString<NewsResponse>(body)
    }
}

private suspend fun httpGet(url: String): Pair<Int, String> = withContext(Dispatchers.IO) {
    val connection = URL(url).openConnection() as HttpURLConnection
    try {
        connection.connectTimeout = 15_000
        connection.readTimeout = 15_000
        val code = connection.responseCode
        code to if (code == 200) connection.inputStream.bufferedReader().use { it.readText() } else ""
    } finally {
        connection.disconnect()
    }
}
