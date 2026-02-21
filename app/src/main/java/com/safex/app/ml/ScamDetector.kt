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
            Log.e(TAG, "ScamDetector failed to load — Level 2 disabled", e)
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

    // ── Preprocessing (must match training pipeline exactly) ──────────

    private fun normalizeForModel(s: String): String {
        // 1. Unicode NFKC
        var x = Normalizer.normalize(s, Normalizer.Form.NFKC)
        // 2. Collapse whitespace
        x = x.replace(Regex("\\s+"), " ").trim()
        // 3. URL → <URL>
        x = x.replace(Regex("(?i)\\b(?:https?://|www\\.)\\S+"), "<URL>")
        // 4. 8+ consecutive digits → <NUM>
        x = x.replace(Regex("\\b\\d{8,}\\b"), "<NUM>")
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
