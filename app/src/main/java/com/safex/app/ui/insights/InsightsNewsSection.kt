package com.safex.app.ui.insights

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.safex.app.R
import com.safex.app.data.NewsArticleEntity
import com.safex.app.data.NewsRepository
import com.safex.app.data.NewsResult
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsNewsSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repo = remember { NewsRepository(context) }
    val scope = rememberCoroutineScope()

    var selectedRegion by remember { mutableStateOf("MY") }
    var isRefreshing by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }

    // Observe cached articles reactively
    val articles by repo.observeNews(selectedRegion)
        .collectAsState(initial = emptyList())

    // Trigger fetch on region change or first load
    fun fetchNews(force: Boolean = false) {
        scope.launch {
            repo.getNews(selectedRegion, force).collect { result ->
                when (result) {
                    is NewsResult.Loading -> {
                        isRefreshing = true
                        errorMsg = null
                    }
                    is NewsResult.Success -> {
                        isRefreshing = false
                        errorMsg = null
                    }
                    is NewsResult.Error -> {
                        isRefreshing = false
                        errorMsg = result.message
                    }
                }
            }
        }
    }

    LaunchedEffect(selectedRegion) {
        fetchNews()
    }

    Column(modifier = modifier.fillMaxWidth()) {
        // ---- Section header ----
        Text(
            text = stringResource(R.string.news_title),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        // ---- Safety disclaimer ----
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(12.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = stringResource(R.string.news_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Region chips ----
        Row(
            modifier = Modifier.padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            FilterChip(
                selected = selectedRegion == "MY",
                onClick = { selectedRegion = "MY" },
                label = { Text(stringResource(R.string.news_malaysia)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
            FilterChip(
                selected = selectedRegion == "GLOBAL",
                onClick = { selectedRegion = "GLOBAL" },
                label = { Text(stringResource(R.string.news_global)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer
                )
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ---- Error banner ----
        if (errorMsg != null) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                ),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.news_error),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { fetchNews(force = true) }) {
                        Text("Retry")
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // ---- Pull-to-refresh article list ----
        PullToRefreshBox(
            isRefreshing = isRefreshing,
            onRefresh = { fetchNews(force = true) },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            if (articles.isEmpty() && !isRefreshing) {
                // Empty state
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.news_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(articles, key = { it.url }) { article ->
                        NewsArticleCard(article) {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                            context.startActivity(intent)
                        }
                    }
                }
            }
        }
    }
}


/**
 * GDELT seendate is like "20260213T103000Z". Format to "2026-02-13".
 */
private fun formatSeenDate(raw: String): String {
    if (raw.length < 8) return raw
    return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
}
