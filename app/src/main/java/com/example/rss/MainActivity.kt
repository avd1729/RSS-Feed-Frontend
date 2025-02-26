package com.example.rss

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup

// Data class for RSS items
data class RssItem(val title: String, val link: String, val description: String)

// List of multiple RSS feed sources
val RSS_FEED_URLS = listOf(
    "https://feeds.bbci.co.uk/news/rss.xml",
    "https://rss.cnn.com/rss/edition.rss",
    "https://www.theverge.com/rss/index.xml"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rssItemsState = mutableStateOf<List<RssItem>>(emptyList())
        val isLoading = mutableStateOf(true)

        lifecycleScope.launch(Dispatchers.IO) {
            val items = fetchAllRssFeeds(RSS_FEED_URLS)
            withContext(Dispatchers.Main) {
                rssItemsState.value = items
                isLoading.value = false
            }
        }

        setContent {
            RssReaderApp(rssItemsState.value, isLoading.value)
        }
    }
}

// UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssReaderApp(items: List<RssItem>, isLoading: Boolean) {
    MaterialTheme {
        Scaffold(
            topBar = { TopAppBar(title = { Text("Multi-Source RSS Reader") }) }
        ) { padding ->
            Box(modifier = Modifier.padding(padding)) {
                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    LazyColumn {
                        items(items) { item ->
                            RssItemView(item)
                        }
                    }
                }
            }
        }
    }
}

// UI for an RSS item
@Composable
fun RssItemView(item: RssItem) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Fetch multiple RSS feeds concurrently
suspend fun fetchAllRssFeeds(urls: List<String>): List<RssItem> {
    val allItems = mutableListOf<RssItem>()
    urls.forEach { url ->
        allItems.addAll(fetchRssFeed(url))
    }
    return allItems
}

// Fetch a single RSS feed
suspend fun fetchRssFeed(url: String): List<RssItem> {
    return try {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0") // Bypass restrictions
            .followRedirects(true)    // Ensure redirects work
            .get()

        val items = doc.select("item, entry") // Support both RSS & Atom
        items.map {
            RssItem(
                title = it.select("title").text(),
                link = it.select("link").text(),
                description = Jsoup.parse(it.select("description, summary").text()).text()
            )
        }
    } catch (e: Exception) {
        Log.e("RSS", "Failed to load RSS from $url: ${e.message}", e)
        emptyList()
    }
}
