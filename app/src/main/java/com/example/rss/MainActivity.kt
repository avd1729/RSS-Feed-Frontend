package com.example.rss

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup


data class RssItem(val title: String, val link: String, val description: String, val categories: List<String>, val content: String)

val RSS_FEED_URLS = listOf(
    "https://www.theverge.com/rss/index.xml",
    "https://feeds.bbci.co.uk/news/rss.xml"
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val rssItemsState = mutableStateOf<List<RssItem>>(emptyList())
        val isLoading = mutableStateOf(true)
        val selectedCategory = mutableStateOf<String?>(null)

        lifecycleScope.launch(Dispatchers.IO) {
            val items = fetchAllRssFeeds(RSS_FEED_URLS)
            withContext(Dispatchers.Main) {
                rssItemsState.value = items
                isLoading.value = false
            }
        }

        setContent {
            RssReaderApp(rssItemsState.value, isLoading.value, selectedCategory)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RssReaderApp(items: List<RssItem>, isLoading: Boolean, selectedCategory: MutableState<String?>) {
    val categories = items.flatMap { it.categories }.distinct()
    val filteredItems = if (selectedCategory.value == null) items else items.filter { it.categories.contains(selectedCategory.value) }
    var selectedItem by remember { mutableStateOf<RssItem?>(null) }

    MaterialTheme {
        Scaffold(
            topBar = { CenterAlignedTopAppBar(title = { Text("Newsly") }) }
        ) { padding ->
            Column(
                modifier = Modifier.padding(padding).fillMaxSize(),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                DropdownMenu(selectedCategory, categories)

                if (isLoading) {
                    CircularProgressIndicator(modifier = Modifier.fillMaxSize())
                } else {
                    if (selectedItem != null) {
                        NewsDetailView(selectedItem!!) { selectedItem = null }
                    } else {
                        LazyColumn {
                            items(filteredItems) { item ->
                                RssItemView(item) { selectedItem = item }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DropdownMenu(selectedCategory: MutableState<String?>, categories: List<String>) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = Modifier.padding(8.dp)) {
        Button(onClick = { expanded = true }) {
            Text(text = selectedCategory.value ?: "Select Category")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = {
                selectedCategory.value = null
                expanded = false
            })
            categories.forEach { category ->
                DropdownMenuItem(text = { Text(category) }, onClick = {
                    selectedCategory.value = category
                    expanded = false
                })
            }
        }
    }
}

@Composable
fun RssItemView(item: RssItem, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp).clickable { onClick() },
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.description, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun NewsDetailView(item: RssItem, onBack: () -> Unit) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier
            .padding(16.dp)
            .fillMaxSize()
    ) {
        item {
            Text(text = item.title, style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = item.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                ElevatedButton(
                    onClick = { onBack() },
                    modifier = Modifier.weight(1f).padding(end = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.ArrowBack, contentDescription = "Back")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Back")
                }

                ElevatedButton(
                    onClick = { shareNews(context, item.link) },
                    modifier = Modifier.weight(1f).padding(start = 8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Share, contentDescription = "Share")
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Share")
                }
            }
        }
    }
}


// Function to share news link
fun shareNews(context: android.content.Context, link: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, "Check this out: $link")
    }
    context.startActivity(Intent.createChooser(intent, "Share via"))
}


// Fetch multiple RSS feeds concurrently
suspend fun fetchAllRssFeeds(urls: List<String>): List<RssItem> {
    val allItems = mutableListOf<RssItem>()
    allItems.addAll(fetchRssFeed(urls[0]))
    return allItems
}

// Fetch a single RSS feed
suspend fun fetchRssFeed(url: String): List<RssItem> {
    return try {
        val doc = Jsoup.connect(url)
            .userAgent("Mozilla/5.0")
            .followRedirects(true)
            .get()

        val items = doc.select("item, entry")
        items.map {
            RssItem(
                title = it.select("title").text(),
                link = it.select("link").text(),
                description = Jsoup.parse(it.select("description, summary").text()).text(),
                categories = it.select("category").map { category ->
                    category.attr("term").ifEmpty { category.text() } // Extract 'term' attribute if available
                },
                content = Jsoup.parse(it.select("content").text()).text()
            )
        }
    } catch (e: Exception) {
        Log.e("RSS", "Failed to load RSS from $url: ${e.message}", e)
        emptyList()
    }
}

