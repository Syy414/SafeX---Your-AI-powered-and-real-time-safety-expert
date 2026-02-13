package com.safex.app.scan

/**
 * On-device keyword heuristic triage for extracted text.
 * Matches urgency / impersonation / money / threat / verification patterns
 * and derives riskLevel + reasons.
 */
object TextTriageEngine {

    // ── pattern groups (lowercase matching) ──────────────────────────

    private val URGENCY = listOf(
        "act now", "immediately", "urgent", "expire", "limited time",
        "last chance", "hurry", "don't delay", "within 24 hours",
        "segera", "cepat", "sekarang juga"
    )

    private val IMPERSONATION = listOf(
        "bank negara", "pdrm", "polis", "lhdn", "kwsp", "epf",
        "maybank", "cimb", "public bank", "rhb", "hong leong",
        "pos laju", "j&t", "shopee", "lazada", "grab",
        "customer service", "official", "verify your identity",
        "pengesahan", "akaun anda"
    )

    private val MONEY_PRESSURE = listOf(
        "transfer", "pay", "payment", "deposit", "investment",
        "guaranteed return", "profit", "commission", "rm",
        "bayar", "wang", "duit", "keuntungan", "pelaburan",
        "bitcoin", "crypto", "forex", "trading"
    )

    private val THREATS = listOf(
        "account locked", "suspended", "frozen", "legal action",
        "warrant", "arrested", "blacklisted",
        "akaun disekat", "tindakan undang-undang", "ditangkap"
    )

    private val VERIFICATION = listOf(
        "verify", "confirm", "click here", "click this link",
        "tap here", "otp", "tac", "pin",
        "sahkan", "klik sini", "tekan sini"
    )

    // ── URL extraction ───────────────────────────────────────────────

    private val URL_REGEX = Regex(
        """(https?://[^\s<>"{}|\\^`\[\]]+)""",
        RegexOption.IGNORE_CASE
    )

    fun extractUrls(text: String): List<String> =
        URL_REGEX.findAll(text).map { it.value.trimEnd('.', ',', ')') }.toList()

    // ── triage entry point ───────────────────────────────────────────

    fun triage(text: String, scanType: ScanType): ScanResult {
        if (text.isBlank()) {
            return ScanResult(
                riskLevel = RiskLevel.SAFE,
                headline = "No text detected",
                reasons = listOf("No readable text was found in this content."),
                nextSteps = listOf("If you believe this is suspicious, try another scan method."),
                scanType = scanType,
                extractedText = ""
            )
        }

        val lower = text.lowercase()
        val reasons = mutableListOf<String>()

        if (matchesAny(lower, URGENCY))
            reasons += "⚠️ Urgency language detected (pressure to act fast)"
        if (matchesAny(lower, IMPERSONATION))
            reasons += "🏦 Possible impersonation of a known brand or authority"
        if (matchesAny(lower, MONEY_PRESSURE))
            reasons += "💰 Money / financial pressure detected"
        if (matchesAny(lower, THREATS))
            reasons += "🚨 Threatening language (account locked, legal action)"
        if (matchesAny(lower, VERIFICATION))
            reasons += "🔑 Suspicious verification / click-link request"

        val urls = extractUrls(text)
        if (urls.isNotEmpty())
            reasons += "🔗 Contains URL(s): suspicious link detected"

        val riskLevel = when {
            reasons.size >= 3 -> RiskLevel.HIGH
            reasons.size >= 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        val headline = when (riskLevel) {
            RiskLevel.HIGH -> "High risk — likely scam content"
            RiskLevel.MEDIUM -> "Medium risk — some suspicious patterns"
            RiskLevel.LOW -> "Low risk — looks mostly safe"
            RiskLevel.SAFE -> "Safe — no issues detected"
            RiskLevel.UNKNOWN -> "Unknown"
        }

        val nextSteps = when (riskLevel) {
            RiskLevel.HIGH -> listOf(
                "🚫 Do NOT click any links or transfer money",
                "🚫 Do NOT share your OTP / TAC / passwords",
                "📞 Call the real organization directly using their official number",
                "📝 Report this to the authorities (CCID / BNM)"
            )
            RiskLevel.MEDIUM -> listOf(
                "⚠️ Be cautious — verify independently before acting",
                "🔍 Search for the phone number or domain online",
                "🚫 Do NOT share personal details until you verify"
            )
            else -> listOf(
                "✅ Content appears safe, but always stay alert",
                "🔍 When in doubt, verify through official channels"
            )
        }

        return ScanResult(
            riskLevel = riskLevel,
            headline = headline,
            reasons = reasons.ifEmpty { listOf("No suspicious patterns detected.") },
            nextSteps = nextSteps,
            extractedUrl = urls.firstOrNull(),
            extractedText = text.take(500),
            scanType = scanType
        )
    }

    private fun matchesAny(text: String, keywords: List<String>): Boolean =
        keywords.any { text.contains(it) }
}
