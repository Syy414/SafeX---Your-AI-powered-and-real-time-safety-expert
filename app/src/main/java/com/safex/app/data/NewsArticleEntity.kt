package com.safex.app.data

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_articles")
data class NewsArticleEntity(
    @PrimaryKey
    val url: String,
    val title: String,
    @ColumnInfo(name = "domain") val domain: String? = null,
    @ColumnInfo(name = "imageUrl") val imageUrl: String? = null,
    @ColumnInfo(name = "seenDate") val seenDate: String,
    @ColumnInfo(name = "region") val region: String,
    @ColumnInfo(name = "cachedAt") val cachedAt: Long,
    @ColumnInfo(name = "summary") val summary: String? = null,
    @ColumnInfo(name = "warningsAndTips") val warningsAndTips: String? = null
)
