package com.safex.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Local alert stored in Room.
 * Deleted after the user reviews (Report / Mark safe).
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey
    val id: String,                // UUID string
    val createdAt: Long,           // epoch millis
    val type: String,              // "notification" | "gallery" | "manual"
    val riskLevel: String,         // "HIGH" | "MEDIUM" | "LOW"
    val category: String,          // e.g. "investment", "phishing", "unknown"
    val tacticsJson: String,       // JSON array string, e.g. ["urgency","impersonation"]
    val snippetRedacted: String,   // max ~500 chars, redacted preview
    val extractedUrl: String?,     // nullable — only if a link/QR was detected
    val headline: String?          // nullable — may be filled later by Gemini
)
