package com.safex.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "news_read_history")
data class NewsReadHistoryEntity(
    @PrimaryKey
    val url: String,
    val readAt: Long
)
