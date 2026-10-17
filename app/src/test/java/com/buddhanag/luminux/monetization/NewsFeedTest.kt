package com.buddhanag.luminux.monetization

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.time.Instant

@OptIn(ExperimentalCoroutinesApi::class)
class NewsFeedTest {
    @get:Rule val tmp = TemporaryFolder()

    private val sample = """
        {"enabled":true,"attribution":"Headlines from GNews","extra":1,"articles":[
          {"title":"First","source":"The Paper","url":"https://example.com/1","publishedAt":"2026-10-08T09:30:00Z"},
          {"title":"Second","source":"Other","url":"https://example.com/2","image":"https://cdn.example.com/2.jpg"}
        ]}
    """.trimIndent()

    private fun fresh(): DataStore<Preferences> =
        PreferenceDataStoreFactory.create(scope = TestScope(UnconfinedTestDispatcher())) { tmp.newFile("s.preferences_pb") }

    @Test fun decodesTheProxyResponse() {
        val feed = NewsFeed.decode(sample)
        assertTrue(feed.enabled)
        assertEquals("Headlines from GNews", feed.attribution)
        assertEquals(listOf("First", "Second"), feed.articles.map { it.title })
        assertEquals(Instant.parse("2026-10-08T09:30:00Z"), Instant.parse(feed.articles[0].publishedAt))
        assertEquals("https://cdn.example.com/2.jpg", feed.articles[1].image)
        assertEquals(null, feed.articles[1].publishedAt)
    }

    @Test fun staysHiddenWithoutAProxy() = runTest {
        val prefs = fresh()
        prefs.edit { it[NewsFeed.ENABLED] = true }
        var calls = 0
        val feed = NewsFeed(prefs, endpoint = null, get = { calls++; 200 to sample })
        feed.load()
        feed.refresh()
        assertFalse(feed.state.value.isEnabled)
        assertTrue(feed.state.value.articles.isEmpty())
        assertEquals(0, calls)
    }

    @Test fun loadsHeadlinesAndRemembersTheSwitch() = runTest {
        val prefs = fresh()
        val feed = NewsFeed(prefs, "https://news.example", get = { 200 to sample })
        feed.load()
        assertFalse(feed.state.value.isEnabled)
        feed.refresh()
        assertEquals(NewsFeed.Phase.Loaded, feed.state.value.phase)
        assertEquals(2, feed.state.value.articles.size)
        val relaunched = NewsFeed(prefs, "https://news.example", get = { error("no fetch") })
        relaunched.load()
        assertTrue(relaunched.state.value.isEnabled)
    }

    @Test fun remoteSwitchTurnsThePanelOff() = runTest {
        val prefs = fresh()
        prefs.edit { it[NewsFeed.ENABLED] = true }
        val feed = NewsFeed(prefs, "https://news.example", get = { 200 to """{"enabled":false,"articles":[]}""" })
        feed.load()
        assertTrue(feed.state.value.isEnabled)
        feed.refresh()
        assertFalse(feed.state.value.isEnabled)
    }

    @Test fun keepsTheLastHeadlinesWhenOffline() = runTest {
        var online = true
        val feed = NewsFeed(fresh(), "https://news.example", get = { if (online) 200 to sample else throw IOException("offline") })
        feed.refresh()
        online = false
        feed.refresh()
        assertEquals(NewsFeed.Phase.Loaded, feed.state.value.phase)
        assertEquals(2, feed.state.value.articles.size)
    }

    @Test fun showsAnErrorWhenNothingHasLoaded() = runTest {
        val feed = NewsFeed(fresh(), "https://news.example", get = { 500 to "" })
        feed.refresh()
        assertEquals(NewsFeed.Phase.Failed, feed.state.value.phase)
        assertTrue(feed.state.value.articles.isEmpty())
    }

    @Test fun refreshesOnlyWhenStale() = runTest {
        var clock = 0L
        var calls = 0
        val feed = NewsFeed(fresh(), "https://news.example", get = { calls++; 200 to sample }, now = { clock })
        feed.refreshIfStale()
        clock += NewsFeed.REFRESH_INTERVAL_MS - 1
        feed.refreshIfStale()
        assertEquals(1, calls)
        clock += 1
        feed.refreshIfStale()
        assertEquals(2, calls)
    }

    @Test fun newsRowsInterleave() {
        val articles = (1..12).map { NewsArticle("t$it", "s", "https://example.com/$it") }
        val rows = newsRows(articles, listOf("ad1", "ad2", "ad3"))
        assertEquals(14, rows.size)
        assertEquals("ad1", rows[5])
        assertEquals("ad2", rows[11])
        assertEquals(listOf(5, 11), rows.indices.filter { rows[it] is String })
    }

    @Test fun nativeAdCount() {
        assertEquals(listOf(1, 1, 2, 3), listOf(0, 7, 10, 40).map(NewsAds::nativeAdCount))
    }
}
