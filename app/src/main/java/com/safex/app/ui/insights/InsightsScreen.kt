package com.safex.app.ui.insights

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.lifecycle.viewmodel.compose.viewModel
import com.safex.app.R
import com.safex.app.data.AlertRepository
import com.safex.app.data.NewsArticleEntity
import com.safex.app.data.NewsRepository
import com.safex.app.data.NewsResult
import com.safex.app.data.UserPrefs
import com.safex.app.data.MlKitTranslator
import com.safex.app.data.local.SafeXDatabase
import com.safex.app.data.local.CategoryCount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // 1. ViewModels / Repositories
    val db = SafeXDatabase.getInstance(context)
    val alertRepo = AlertRepository.getInstance(db)
    val insightsRepo = com.safex.app.data.InsightsRepository.getInstance() // Use Singleton
    val insightsViewModel: InsightsViewModel = viewModel(
        factory = InsightsViewModel.Factory(alertRepo, insightsRepo)
    )
    val uiState by insightsViewModel.uiState.collectAsState()

    // 2. News State
    val newsRepo = remember { NewsRepository(context) }
    var isNewsRefreshing by remember { mutableStateOf(false) }
    var newsError by remember { mutableStateOf<String?>(null) }
    
    // observeNews now streams the English database directly
    val rawNewsArticles by newsRepo.observeNews().collectAsState(initial = emptyList())
    var translatedArticles by remember { mutableStateOf<List<NewsArticleEntity>>(emptyList()) }
    var isTranslating by remember { mutableStateOf(false) }

    val prefs = remember { UserPrefs(context) }
    val userLang by prefs.languageTag.collectAsState(initial = "en")
    val currentLang = if (userLang.isNotBlank()) userLang else "en"

    LaunchedEffect(rawNewsArticles, currentLang) {
        if (rawNewsArticles.isEmpty()) {
            translatedArticles = emptyList()
            return@LaunchedEffect
        }
        isTranslating = true
        withContext(Dispatchers.IO) {
            if (currentLang == "en") {
                translatedArticles = rawNewsArticles
            } else {
                translatedArticles = rawNewsArticles.map { article ->
                    val tTitle = MlKitTranslator.translate(article.title, currentLang)
                    val tSummary = article.summary?.let { MlKitTranslator.translate(it, currentLang) }
                    val tTips = article.warningsAndTips?.let { MlKitTranslator.translate(it, currentLang) }
                    
                    article.copy(
                        title = tTitle,
                        summary = tSummary,
                        warningsAndTips = tTips
                    )
                }
            }
        }
        isTranslating = false
    }

    fun fetchNews(force: Boolean = false) {
        scope.launch {
            newsRepo.getNews(force).collect { result ->
                when (result) {
                    is NewsResult.Loading -> {
                        isNewsRefreshing = true
                        newsError = null
                    }
                    is NewsResult.Success -> {
                        isNewsRefreshing = false
                        newsError = null
                    }
                    is NewsResult.Error -> {
                        isNewsRefreshing = false
                        newsError = result.message
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchNews()
        insightsViewModel.refreshCommunityInsights()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(), 
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.tab_insights),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = androidx.compose.ui.graphics.Color.Black
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // --- 2. Education ---
            item { EducationSection() }

                // --- 3. News Section (Header only) ---
                item {
                    NewsHeaderSection(
                        errorMsg = newsError,
                        retry = { fetchNews(true) }
                    )
                }

                // --- 4. News List ---
                if (translatedArticles.isEmpty() && (!isNewsRefreshing && !isTranslating)) {
                    item {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 32.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "You're all caught up!",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Button(onClick = { 
                                fetchNews(force = true)
                                insightsViewModel.refreshCommunityInsights()
                            }) {
                                Text("Refresh News")
                            }
                        }
                    }
                } else if (isTranslating || isNewsRefreshing) {
                    item {
                        Box(
                            modifier = Modifier.fillMaxWidth().height(100.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        }
                    }
                } else {
                    items(translatedArticles, key = { it.url }) { article ->
                        NewsArticleCard(article) {
                            // 1. Mark as read (will remove from list)
                            scope.launch {
                                newsRepo.markArticleAsRead(article.url)
                            }
                            // 2. Open URL
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(article.url))
                            context.startActivity(intent)
                        }
                    }
            }
        }
    }
}

// ── Sub-components ──

@Composable
fun EducationSection() {
    val tips = listOf(
        Tip(stringResource(R.string.tip_otp_title), stringResource(R.string.tip_otp_desc)),
        Tip(stringResource(R.string.tip_slow_title), stringResource(R.string.tip_slow_desc)),
        Tip(stringResource(R.string.tip_verify_title), stringResource(R.string.tip_verify_desc)),
        Tip(stringResource(R.string.tip_click_title), stringResource(R.string.tip_click_desc))
    )

    Column {
        Text(
            text = stringResource(R.string.safety_tips_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(8.dp))

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(tips) { tip ->
                TipCard(tip)
            }
        }
    }
}

@Composable
fun NewsHeaderSection(
    errorMsg: String?,
    retry: () -> Unit
) {
    Column {
        Text(
            text = stringResource(R.string.news_title),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold
        )
        
        // Safety disclaimer
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.4f)
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)
        ) {
            Text(
                text = stringResource(R.string.news_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer,
                modifier = Modifier.padding(12.dp)
            )
        }

        if (errorMsg != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = stringResource(R.string.news_failed),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = retry) { Text(stringResource(R.string.news_retry)) }
            }
        }
    }
}

@Composable
fun TipCard(tip: Tip) {
    Card(
        modifier = Modifier.width(200.dp).height(130.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Icon(Icons.Default.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.height(8.dp))
            Text(tip.title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(tip.content, style = MaterialTheme.typography.bodySmall, maxLines = 3, lineHeight = MaterialTheme.typography.bodySmall.lineHeight)
        }
    }
}

@Composable
fun NewsArticleCard(article: NewsArticleEntity, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(
                text = article.title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
            )
            
            if (!article.summary.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = article.summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
            }
            
            if (!article.warningsAndTips.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(12.dp))
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.05f)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp).fillMaxWidth()) {
                        Text(
                            text = stringResource(R.string.safety_tips_title),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = article.warningsAndTips,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "${article.domain} • ${formatSeenDate(article.seenDate)}",
                    style = MaterialTheme.typography.labelSmall, 
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // Optional: Icon indicating external link
                Icon(
                    painter = painterResource(id = android.R.drawable.ic_menu_view), // Using system icon or similar
                    contentDescription = "Read source",
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

private fun formatSeenDate(raw: String): String {
    if (raw.length < 8) return raw
    return "${raw.substring(0, 4)}-${raw.substring(4, 6)}-${raw.substring(6, 8)}"
}

data class Tip(val title: String, val content: String)
