package com.safex.app.guardian

/**
 * On-device triage engine for scam detection.
 *
 * Interface so we can swap HeuristicTriageEngine for TFLiteTriageEngine later.
 */
interface TriageEngine {
    fun analyze(text: String): TriageResult
}

data class TriageResult(
    val riskLevel: String,        // HIGH | MEDIUM | LOW
    val riskProbability: Float,   // 0..1
    val tactics: List<String>,
    val category: String,         // coarse guess
    val headline: String,
    val containsUrl: Boolean
)

/**
 * Keyword-heuristic triage. Fast, no model download needed.
 *
 * Scoring: each matched keyword group adds weight.
 * Link presence adds extra weight.
 * Threshold: >=0.6 → HIGH, >=0.35 → MEDIUM, else LOW.
 */
class HeuristicTriageEngine : TriageEngine {

    companion object {
        // ── keyword dictionaries ──────────────────────────────────────────
        private val URGENCY = setOf(
            "segera", "urgent", "immediately", "now", "act now",
            "hurry", "limited time", "expires today", "last chance",
            "deadline", "within 24 hours", "dalam 24 jam",
            "cepat", "sekarang juga", "jangan tunggu", "masa terhad"
        )

        private val AUTHORITY = setOf(
            "bank negara", "pdrm", "polis", "police", "lhdn",
            "inland revenue", "court", "mahkamah", "customs",
            "kastam", "officer", "pegawai", "customer service",
            "your account", "akaun anda", "verification required",
            "pengesahan diperlukan", "official", "rasmi"
        )

        private val MONEY = setOf(
            "transfer", "rm", "myr", "ringgit", "payment", "bayaran",
            "bayar", "pay", "send money", "wire", "top up", "reload",
            "investment", "pelaburan", "guaranteed returns",
            "pulangan dijamin", "profit", "keuntungan", "commission",
            "komisyen", "withdraw", "pengeluaran"
        )

        private val THREATS = setOf(
            "blocked", "disekat", "suspended", "digantung",
            "arrested", "ditangkap", "warrant", "waran",
            "legal action", "tindakan undang", "fine", "denda",
            "penalti", "penalty", "freeze", "beku", "locked",
            "dikunci", "terminated", "ditamatkan"
        )

        private val VERIFICATION = setOf(
            "verify", "sahkan", "confirm", "pengesahan",
            "otp", "pin", "tac", "password", "kata laluan",
            "click here", "klik sini", "tap here", "tekan sini",
            "update your", "kemaskini", "log in", "masuk",
            "ssn", "ic number", "nombor ic", "identity"
        )

        private val GREED = setOf(
            "congratulations", "tahniah", "winner", "pemenang",
            "won", "menang", "prize", "hadiah", "reward", "ganjaran",
            "free", "percuma", "bonus", "lucky", "bertuah",
            "selected", "terpilih", "exclusive", "eksklusif"
        )

        private val URL_REGEX = Regex(
            """(https?://[^\s]+|www\.[^\s]+|bit\.ly/[^\s]+|tinyurl\.com/[^\s]+)""",
            RegexOption.IGNORE_CASE
        )

        // category mapping
        private val CATEGORY_MAP = mapOf(
            "urgency" to "phishing",
            "authority" to "impersonation",
            "money" to "investment",
            "threats" to "impersonation",
            "verification" to "phishing",
            "greed" to "lottery_scam"
        )
    }

    override fun analyze(text: String): TriageResult {
        val lower = text.lowercase()
        val matched = mutableMapOf<String, Int>()

        fun score(group: String, keywords: Set<String>) {
            val count = keywords.count { lower.contains(it) }
            if (count > 0) matched[group] = count
        }

        score("urgency", URGENCY)
        score("authority", AUTHORITY)
        score("money", MONEY)
        score("threats", THREATS)
        score("verification", VERIFICATION)
        score("greed", GREED)

        val containsUrl = URL_REGEX.containsMatchIn(text)

        // weighted probability
        val rawScore = matched.entries.sumOf { (_, count) -> count.coerceAtMost(3) }
        val urlBonus = if (containsUrl) 2 else 0
        val groupBonus = matched.size  // more distinct groups → higher risk

        val total = rawScore + urlBonus + groupBonus
        // normalize: 12 is a "max practical" score
        val probability = (total / 12f).coerceIn(0f, 1f)

        val riskLevel = when {
            probability >= 0.6f -> "HIGH"
            probability >= 0.35f -> "MEDIUM"
            else -> "LOW"
        }

        val tactics = matched.keys.toList()
        val dominantGroup = matched.maxByOrNull { it.value }?.key
        val category = dominantGroup?.let { CATEGORY_MAP[it] } ?: "unknown"

        val headline = when (riskLevel) {
            "HIGH" -> buildHeadline(tactics, containsUrl)
            "MEDIUM" -> "Possible suspicious message detected"
            else -> "Low risk message"
        }

        return TriageResult(
            riskLevel = riskLevel,
            riskProbability = probability,
            tactics = tactics,
            category = category,
            headline = headline,
            containsUrl = containsUrl
        )
    }

    private fun buildHeadline(tactics: List<String>, hasUrl: Boolean): String {
        val base = when {
            "authority" in tactics && "money" in tactics ->
                "Potential impersonation scam with payment request"
            "authority" in tactics ->
                "Possible authority impersonation detected"
            "money" in tactics && "greed" in tactics ->
                "Potential investment or prize scam"
            "money" in tactics ->
                "Suspicious payment or money request"
            "greed" in tactics ->
                "Potential prize or reward scam"
            "verification" in tactics ->
                "Suspicious verification or phishing attempt"
            else ->
                "High-risk scam indicators detected"
        }
        return if (hasUrl) "$base — contains link, verify in Home Scan" else base
    }
}
