package com.example.rss

import android.os.Bundle
import android.widget.Toast
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
import org.jsoup.Jsoup
import java.net.URL

// Data class for RSS items
data class RssItem(val title: String, val link: String, val description: String)

class MainActivity : ComponentActivity() {
    private val rssUrl = "http://10.0.2.2:8080/rss" // Change if hosted externally

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        var rssItems by mutableStateOf<List<RssItem>>(emptyList())

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                rssItems = fetchRssFeed(rssUrl)
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Error loading feed", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setContent {
            RssReaderApp(rssItems)
        }
    }
}

// UI
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssReaderApp(items: List<RssItem>) {
    MaterialTheme {
        Scaffold(topBar = { TopAppBar(title = { Text("RSS Reader") }) }) { padding ->
            LazyColumn(modifier = Modifier.padding(padding)) {
                items(items) { item ->
                    RssItemView(item)
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

// Function to fetch RSS feed
fun fetchRssFeed(url: String): List<RssItem> {
    val doc = Jsoup.connect(url).get()
    val items = doc.select("item")
    return items.map {
        RssItem(
            title = it.select("title").text(),
            link = it.select("link").text(),
            description = it.select("description").text()
        )
    }
}
