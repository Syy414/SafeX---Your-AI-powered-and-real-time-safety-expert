package com.safex.app.guardian

/**
 * On-device triage engine for scam detection.
 *
 * New architecture (parallel weighted scoring):
 *   Level 1 — HeuristicTriageEngine.scoreText()  → contributes 20% of combined score
 *   Level 2 — ScamDetector (TFLite)               → contributes 80% of combined score
 *   Combined ≥ 0.50 → proceed to Gemini → create alert + notification
 */
interface TriageEngine {
    suspend fun analyze(text: String): TriageResult
}

data class TriageResult(
    val riskLevel: String,           // HIGH | MEDIUM | LOW
    val riskProbability: Float,      // combined weighted score 0..1
    val heuristicScore: Float,       // unweighted heuristic score 0..1
    val tfliteScore: Float,          // unweighted tflite score 0..1
    val tactics: List<String>,
    val category: String,
    val headline: String,
    val containsUrl: Boolean,
    val geminiAnalysis: String? = null,
    val analysisLanguage: String? = null
)

/**
 * Heuristic scorer — contributes 20% of the final combined score.
 *
 * Supports EN/MS/ZH scam patterns. Every pattern requires COMPOUND signals
 * (both sides must match) to avoid false positives on legitimate content.
 *
 * Plus: typosquatting detection for known brand domains.
 *
 * Scored patterns (each produces a base score, final is clamped [0, 1]):
 *   1. Credential harvesting  — OTP/PIN/TAC/密码 AND action verb          → 0.70
 *   2. Money transfer         — transfer verb AND account reference        → 0.60
 *   3. Account threat         — your account/您的账号 AND blocked/frozen    → 0.55
 *   4. Authority coercion     — specific authority AND legal action        → 0.65
 *   5. Suspicious URL         — shortened domain OR suspicious TLD         → 0.45
 *   6. Typosquat URL          — domain within edit-distance 2 of brand     → 0.70
 */
class HeuristicTriageEngine : TriageEngine {

    companion object {
        // ── URL detection ──────────────────────────────────────────────
        private val URL_REGEX = Regex(
            """(https?://[^\s]+|www\.[^\s]+)""",
            RegexOption.IGNORE_CASE
        )
        private val SHORTENED_DOMAINS = setOf(
            "bit.ly", "tinyurl.com", "t.co", "goo.gl", "ow.ly", "is.gd",
            "short.link", "tiny.cc", "rb.gy", "cutt.ly", "shorturl.at",
            "s.id", "v.gd"
        )
        private val SUSPICIOUS_TLDS = listOf(
            ".tk", ".ml", ".ga", ".cf", ".gq", ".top", ".xyz", ".click",
            ".link", ".work", ".download", ".loan", ".win", ".bid"
        )

        // ── Typosquat: known brand domains (we check edit-distance to these) ─
        private val BRAND_DOMAINS = setOf(
            "whatsapp.com", "facebook.com", "instagram.com", "telegram.org",
            "maybank2u.com.my", "maybank.com", "cimbclicks.com.my", "cimb.com", 
            "rhbgroup.com", "pbebank.com", "publicbank.com.my", "publicbank.com",
            "hlb.com.my", "hlb.com", "hongleongbank.com", "hongleongbank.com.my",
            "bankislam.com", "affinbank.com", "ambank.com.my", "ambank.com", "bsn.com.my",
            "google.com", "apple.com", "microsoft.com", "paypal.com",
            "shopee.com", "shopee.com.my", "lazada.com", "lazada.com.my", "grab.com", "tiktok.com",
            "wechat.com", "alipay.com", "taobao.com", "jd.com",
            "pos.com.my", "poslaju.com.my", "jpj.gov.my", "kwsp.gov.my"
        )

        // ── Pattern 1: Credential harvesting ──────────────────────────
        // EN + MS + ZH credential tokens
        private val CREDENTIAL_TOKENS = setOf(
            // EN
            "otp", "tac", "pin number", "password", "security code",
            "ic number", "card number", "cvv", "verification",
            // MS
            "kata laluan", "kod keselamatan", "nombor ic", "nombor kad",
            "kod pengesahan", "nombor pin", "kad pengenalan",
            // ZH
            "密码", "验证码", "口令", "动态码", "身份证", "银行卡", "信用卡"
        )
        private val CREDENTIAL_ACTIONS = setOf(
            // EN
            "enter", "provide", "share", "key in", "send",
            "click", "log in", "login", "verify", "confirm",
            "update", "reset", "authenticate",
            // MS
            "masukkan", "berikan", "kongsi", "taip", "hantar",
            "klik", "log masuk", "kemaskini", "kemas kini", "mengesahkan", "sahkan",
            // ZH
            "输入", "提供", "填写", "登录", "登陆", "验证", "点击", "确认"
        )

        // ── Pattern 2: Money transfer coercion ────────────────────────
        private val TRANSFER_VERBS = setOf(
            // EN
            "transfer", "wire", "send money", "deposit", "pay",
            // MS
            "pindah", "hantar wang", "bank in", "bayar", "deposit", "kredit",
            // ZH
            "转账", "汇款", "打款", "转钱", "付款", "存入", "支付"
        )
        private val ACCOUNT_REFS = setOf(
            // EN
            "account", "bank", "acc no", "receive", "claim",
            // MS
            "akaun", "no. akaun", "terima", "tuntut", "menuntut", "penebusan",
            // ZH
            "账号", "账户", "收款", "对方", "指定", "领奖", "领取"
        )

        // ── Pattern 3: Account locked/frozen threat ────────────────────
        private val ACCOUNT_SUBJECT = setOf(
            // EN
            "account", "card", "wallet", "banking", "profile",
            // MS
            "akaun", "kad", "profil", "perbankan",
            // ZH
            "账号", "账户", "帐号", "钱包", "微信", "支付宝", "银行卡", "信用分"
        )
        private val ACCOUNT_STATUS = setOf(
            // EN
            "blocked", "suspended", "frozen", "locked", "deactivated",
            "terminated", "closed", "compromised",
            "unusual activity", "unauthorized", "restrict",
            // MS
            "sekat", "disekat", "gantung", "digantung", "beku", "dibeku", 
            "kunci", "dikunci", "tamat", "ditamatkan",
            "tutup", "ditutup", "tanpa kebenaran", "mencurigakan", "batal", "dibatal",
            // ZH
            "违规", "冻结", "锁定", "封禁", "注销",
            "暂停", "异常", "风险", "限制",
            "停用"
        )

        // ── Pattern 4: Authority + legal coercion ─────────────────────
        private val AUTHORITY_NAMES = setOf(
            // EN
            "police", "revenue", "customs", "immigration", "court",
            // MS
            "polis", "pdrm", "bank negara", "bnm", "badan kerajaan",
            "lembaga hasil", "lhdn", "mahkamah",
            "kastam", "imigresen", "sprm", "macc",
            "suruhanjaya komunikasi", "skmm", "mcmc",
            // ZH
            "公安", "刑侦", "警察", "检察院", "法院",
            "税务", "移民", "海关", "银监会", "反诈"
        )
        private val LEGAL_ACTIONS = setOf(
            // EN
            "arrest", "prosecut", "summons", "fine", "court", "tax evasion", "warrant", "illegal", "investigat",
            // MS
            "tangkap", "dakwa", "pendakwaan", "saman", "denda", "mahkamah", "waran", "jenayah", "siasat", "haram",
            // ZH
            "逮捕", "拘留", "传唤", "立案", "犯罪", "调查", "洗钱", "诈骗", "违法", "抓捕"
        )

        // ── Category map ───────────────────────────────────────────────
        private val CATEGORY_MAP = mapOf(
            "credential"     to "Phishing",
            "money_transfer" to "Investment Scam",
            "account_threat" to "Impersonation",
            "legal_threat"   to "Impersonation",
            "suspicious_url" to "Phishing",
            "typosquat_url"  to "Phishing"
        )
    }

    /**
     * Returns a normalised heuristic score in [0, 1] and the list of matched signal names.
     */
    fun scoreText(text: String): Pair<Float, List<String>> {
        val lower = text.lowercase()
        val matched = mutableListOf<String>()
        var raw = 0f

        // 1. Credential harvesting
        if (CREDENTIAL_TOKENS.any { lower.contains(it) } &&
            CREDENTIAL_ACTIONS.any { lower.contains(it) }) {
            raw += 0.70f
            matched.add("credential")
        }

        // 2. Money transfer to account
        if (TRANSFER_VERBS.any { lower.contains(it) } &&
            ACCOUNT_REFS.any { lower.contains(it) }) {
            raw += 0.60f
            matched.add("money_transfer")
        }

        // 3. Account locked/frozen threat
        if (ACCOUNT_SUBJECT.any { lower.contains(it) } &&
            ACCOUNT_STATUS.any { lower.contains(it) }) {
            raw += 0.55f
            matched.add("account_threat")
        }

        // 4. Authority + legal coercion
        if (AUTHORITY_NAMES.any { lower.contains(it) } &&
            LEGAL_ACTIONS.any { lower.contains(it) }) {
            raw += 0.65f
            matched.add("legal_threat")
        }

        // ── URL analysis ──────────────────────────────────────────────
        val urls = URL_REGEX.findAll(text).map { it.value.lowercase() }.toList()

        // 5. Shortened/suspicious TLD URLs
        val hasSuspiciousUrl = urls.any { url ->
            SHORTENED_DOMAINS.any { url.contains(it) } ||
            SUSPICIOUS_TLDS.any { tld ->
                url.substringBefore("?").endsWith(tld) ||
                url.substringBefore("?").contains("$tld/")
            }
        }
        if (hasSuspiciousUrl) {
            raw += 0.45f
            matched.add("suspicious_url")
        }

        // 6. Typosquat URL detection: domain within edit-distance 2 of a known brand
        if (!hasSuspiciousUrl && urls.isNotEmpty()) {
            val hasTyposquat = urls.any { url ->
                val domain = extractDomain(url)
                domain.isNotEmpty() && BRAND_DOMAINS.any { brand ->
                    isTyposquat(domain, brand)
                }
            }
            if (hasTyposquat) {
                raw += 0.70f
                matched.add("typosquat_url")
            }
        }

        return raw.coerceIn(0f, 1f) to matched
    }

    /** Returns true if ANY url is present (not just suspicious). */
    fun hasAnyUrl(text: String): Boolean = URL_REGEX.containsMatchIn(text)

    /** Standalone analyze() — used only when called independently. */
    override suspend fun analyze(text: String): TriageResult {
        val (score, tactics) = scoreText(text)
        val riskLevel = when {
            score >= 0.75f -> "HIGH"
            score >= 0.50f -> "MEDIUM"
            else           -> "LOW"
        }
        val category = tactics.firstOrNull()?.let { CATEGORY_MAP[it] } ?: "unknown"
        val language = java.util.Locale.getDefault().language
        val englishHeadline = buildHeadline(tactics, hasAnyUrl(text))
        val translatedHeadline = com.safex.app.data.MlKitTranslator.translate(englishHeadline, language)

        return TriageResult(
            riskLevel       = riskLevel,
            riskProbability = score,
            heuristicScore  = score,
            tfliteScore     = 0f,
            tactics         = tactics,
            category        = category,
            headline        = translatedHeadline,
            containsUrl     = hasAnyUrl(text),
            analysisLanguage = language
        )
    }

    fun buildHeadline(tactics: List<String>, hasUrl: Boolean): String {
        val base = when {
            "credential" in tactics && "account_threat" in tactics ->
                "Possible account takeover phishing attempt"
            "credential" in tactics ->
                "Suspicious credential harvesting attempt"
            "typosquat_url" in tactics ->
                "Message contains fake look-alike website"
            "money_transfer" in tactics ->
                "Suspicious money transfer request"
            "legal_threat" in tactics ->
                "Possible authority impersonation scam"
            "account_threat" in tactics ->
                "Suspicious account security threat"
            "suspicious_url" in tactics ->
                "Message contains suspicious shortened link"
            else -> "Suspicious message detected"
        }
        return if (hasUrl && "suspicious_url" !in tactics && "typosquat_url" !in tactics)
            "$base — verify any links in Home Scan"
        else base
    }

    // ── URL helpers ─────────────────────────────────────────────────

    /** Extract just the domain from a URL string. */
    private fun extractDomain(url: String): String {
        return try {
            val stripped = url
                .removePrefix("https://")
                .removePrefix("http://")
                .removePrefix("www.")
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore(":")
                .trim()
            stripped
        } catch (_: Exception) { "" }
    }

    /**
     * Returns true if [candidate] looks like a typosquat of [brand].
     *
     * Strategy: Levenshtein distance ≤ 2 between the base parts (before TLD).
     * Also checks if the candidate contains the brand name with extra chars inserted
     * (e.g. "whtasas-a.com" vs "whatsapp.com").
     */
    private fun isTyposquat(candidate: String, brand: String): Boolean {
        // Exact match = not a typosquat
        if (candidate == brand) return false

        val candBase = candidate.substringBeforeLast(".").replace("-", "")
        val brandBase = brand.substringBeforeLast(".").replace("-", "")

        // If bases are equal lengths within ±2 and edit distance ≤ 2
        if (kotlin.math.abs(candBase.length - brandBase.length) <= 3) {
            val dist = levenshtein(candBase, brandBase)
            if (dist in 1..2) return true
        }

        // Also check: does the candidate contain a rearranged/mutated version of the brand?
        // E.g. "whtasas" is a mangled "whatsapp"
        // Use character frequency similarity
        if (candBase.length >= 4 && brandBase.length >= 4) {
            val common = candBase.toSet().intersect(brandBase.toSet())
            val similarity = common.size.toFloat() / maxOf(candBase.toSet().size, brandBase.toSet().size)
            if (similarity >= 0.7f && levenshtein(candBase, brandBase) <= 3) return true
        }

        return false
    }

    /** Standard Levenshtein distance, capped at max 10 to avoid wasted CPU. */
    private fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        if (kotlin.math.abs(a.length - b.length) > 5) return 10

        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) {
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                dp[i][j] = minOf(dp[i-1][j] + 1, dp[i][j-1] + 1, dp[i-1][j-1] + cost)
            }
        }
        return dp[a.length][b.length]
    }
}
