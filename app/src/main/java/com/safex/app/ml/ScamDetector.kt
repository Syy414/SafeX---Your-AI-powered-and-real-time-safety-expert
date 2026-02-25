package com.safex.app.ml

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.text.Normalizer

private const val TAG = "SafeX-ScamDetector"

data class ModelConfig(
    val seqLen: Int,
    val threshold: Float,
    val padIndex: Int,
    val unkIndex: Int,
)

/**
 * Level-2 on-device scam detector.
 * Char-CNN TFLite model trained on EN/MS/ZH scam text.
 *
 * Input:  int32[1, seqLen]
 * Output: float32[1, 1]  (scam probability 0..1)
 *
 * Usage:
 *   val detector = ScamDetector(context)
 *   if (detector.isAvailable && detector.predictIsScam(text)) { … }
 */
class ScamDetector(ctx: Context) {

    var isAvailable: Boolean = false
        private set

    var initError: String? = null
        private set

    private var cfg: ModelConfig = ModelConfig(512, 0.35f, 0, 1)
    private var vocab: Map<String, Int> = emptyMap()
    private var tflite: Interpreter? = null

    init {
        try {
            // ── 1. Load model config ──────────────────────────────────
            val cfgJson = ctx.assets.open("models/model_config.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            val cfgObj = JSONObject(cfgJson)
            cfg = ModelConfig(
                seqLen    = cfgObj.getInt("seq_len"),
                threshold = cfgObj.getDouble("threshold").toFloat(),
                padIndex  = cfgObj.getInt("pad_index"),
                unkIndex  = cfgObj.getInt("unk_index"),
            )

            // ── 2. Load vocab ─────────────────────────────────────────
            val vocabJson = ctx.assets.open("models/char_vocab.json")
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
            vocab = loadVocab(vocabJson)

            // ── 3. Load TFLite model bytes into a ByteBuffer ──────────
            // Reading as bytes then wrapping avoids memory-mapping issues on
            // some emulators when assets are compressed.
            val modelBytes = ctx.assets.open("models/safex_charcnn_dynamic.tflite")
                .readBytes()
            val modelBuffer = ByteBuffer.allocateDirect(modelBytes.size)
                .apply {
                    order(ByteOrder.nativeOrder())
                    put(modelBytes)
                    rewind()
                }

            // ── 4. Build interpreter ──────────────────────────────────
            val opts = Interpreter.Options().apply {
                numThreads = 2   // 2 threads is safe on low-end devices
            }
            tflite = Interpreter(modelBuffer, opts)
            isAvailable = true
            Log.i(TAG, "ScamDetector ready. seqLen=${cfg.seqLen} threshold=${cfg.threshold} vocabSize=${vocab.size}")

        } catch (e: Exception) {
            val msg = e.message ?: e.toString()
            Log.e(TAG, "ScamDetector failed to load — Level 2 disabled: $msg", e)
            initError = msg
            isAvailable = false
        }
    }

    // ── Vocab loading ─────────────────────────────────────────────────
    private fun loadVocab(jsonText: String): Map<String, Int> {
        val arr = JSONArray(jsonText)
        val map = HashMap<String, Int>(arr.length())
        for (i in 0 until arr.length()) {
            map[arr.getString(i)] = i
        }
        return map
    }

    // ── Preprocessing (MUST match training pipeline exactly) ──────────
    // Ported from the Python normalize_new() used during Kaggle training.

    companion object {
        // Hard safety regexes (same order as training pipeline)
        private val RE_URL = Regex("""(?i)\b(?:https?://|www\.)?[a-zA-Z0-9-]+\.[a-zA-Z]{2,}(?:/[^\s]*)?""")
        private val RE_EMAIL = Regex("""(?i)\b[A-Z0-9._%+-]+@[A-Z0-9.-]+\.[A-Z]{2,}\b""")
        private val RE_PHONE = Regex("""(?<!\w)(?:\+?\d[\d\-\s]{6,}\d)(?!\w)""")
        private val RE_LONG_DIGITS = Regex("""\b\d{8,}\b""")
        private val RE_MONEY = Regex("""(?i)\bRM\s*\d+(?:\.\d{1,2})?\b""")

        // Contextual OTP detection
        private val RE_OTP_CTX = Regex("""(?i)\b(otp|tac|code|verification|verify|kod|pin|验证码|驗證碼)\b""")
        private val RE_OTP_VAL = Regex("""\b\d{4,8}\b""")

        // Contextual REF detection
        private val RE_REF_CTX = Regex("""(?i)\b(ref|reference|txn|transaction|receipt|resit|rujukan)\b""")

        // Organisation name → placeholder (matches training distribution)
        private val BANKS = listOf(
            "maybank", "cimb", "public bank", "rhb", "hong leong",
            "ambank", "bank islam", "bsn", "uob", "ocbc"
        )
        private val TELCOS = listOf("celcom", "digi", "maxis", "unifi", "tm", "yes")
        private val COURIERS = listOf(
            "j&t", "jt", "poslaju", "gdex", "dhl",
            "ninja van", "flash", "shopee express", "spx"
        )
    }

    private fun normalizeForModel(s: String): String {
        // 1. Unicode NFKC
        var x = Normalizer.normalize(s, Normalizer.Form.NFKC)
        // 2. Collapse whitespace
        x = x.replace(Regex("\\s+"), " ").trim()

        // 3. Hard safety normalizations (same order as training)
        x = RE_URL.replace(x, "<URL>")
        x = RE_EMAIL.replace(x, "<EMAIL>")
        x = RE_PHONE.replace(x, "<PHONE>")
        x = RE_LONG_DIGITS.replace(x, "<NUM>")
        x = RE_MONEY.replace(x, "RM <AMOUNT>")

        // 4. Map bank/telco/courier names to placeholders
        var lower = x.lowercase()
        for (b in BANKS) {
            if (b in lower) {
                x = x.replace(Regex("(?i)${Regex.escape(b)}"), "<BANK>")
                lower = x.lowercase()
            }
        }
        for (t in TELCOS) {
            if (t in lower) {
                x = x.replace(Regex("(?i)${Regex.escape(t)}"), "<TELCO>")
                lower = x.lowercase()
            }
        }
        for (c in COURIERS) {
            if (c in lower) {
                x = x.replace(Regex("(?i)${Regex.escape(c)}"), "<COURIER>")
                lower = x.lowercase()
            }
        }

        // 5. Replace 4–8 digit codes as <OTP> only when OTP context exists
        if (RE_OTP_CTX.containsMatchIn(x)) {
            x = RE_OTP_VAL.replace(x, "<OTP>")
        }

        // 6. Replace short ref numbers as <NUM> only if REF context exists
        if (RE_REF_CTX.containsMatchIn(x)) {
            x = Regex("""\b\d{4,8}\b""").replace(x, "<NUM>")
        }

        Log.d(TAG, "Normalized for model: $x")
        return x
    }

    private fun toCharIds(text: String): IntArray {
        val x = normalizeForModel(text)
        val ids = IntArray(cfg.seqLen) { cfg.padIndex }
        val chars = x.toCharArray()
        val n = minOf(chars.size, cfg.seqLen)
        for (i in 0 until n) {
            ids[i] = vocab[chars[i].toString()] ?: cfg.unkIndex
        }
        return ids
    }

    // ── Inference ─────────────────────────────────────────────────────

    /** Returns scam probability in [0, 1]. Returns -1f if model unavailable. */
    fun predictScore(text: String): Float {
        val interpreter = tflite ?: return -1f
        return try {
            val ids = toCharIds(text)
            // TFLite expects int32 — wrap as Array<IntArray>
            val input = arrayOf(ids)              // shape [1, seqLen]
            val output = Array(1) { FloatArray(1) } // shape [1, 1]
            interpreter.run(input, output)
            output[0][0]
        } catch (e: Exception) {
            Log.e(TAG, "Inference error", e)
            -1f
        }
    }

    /** True if score ≥ threshold. False if model unavailable. */
    fun predictIsScam(text: String): Boolean {
        if (!isAvailable) return false
        val score = predictScore(text)
        if (score < 0f) return false
        Log.d(TAG, "TFLite score=%.4f threshold=${cfg.threshold} isScam=${score >= cfg.threshold}".format(score))
        return score >= cfg.threshold
    }

    fun threshold(): Float = cfg.threshold
    fun seqLen(): Int = cfg.seqLen
}
