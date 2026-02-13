package com.safex.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticleEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    val domain: String,
    val imageUrl: String?,
    val seenDate: String,      // GDELT seendate string e.g. "20260213T103000Z"
    val region: String,        // "MY" | "GLOBAL"
    val cachedAt: Long         // epoch millis when we cached this
)
