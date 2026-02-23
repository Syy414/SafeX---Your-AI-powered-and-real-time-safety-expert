package com.safex.app

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.safex.app.guardian.HybridTriageEngine
import com.safex.app.guardian.HeuristicTriageEngine
import com.safex.app.ml.ScamDetector
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ModelThresholdTest {

    private lateinit var context: Context
    private lateinit var triageEngine: HybridTriageEngine
    private lateinit var heuristicEngine: HeuristicTriageEngine
    private lateinit var mlEngine: ScamDetector

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        mlEngine = ScamDetector(context)
        heuristicEngine = HeuristicTriageEngine()
        triageEngine = HybridTriageEngine(context)
        // Ensure model is loaded before testing
        mlEngine.predictScore("Test message to load model")
    }

    @Test
    fun testMessages() = runBlocking {
        val messages = listOf(
            "Bro easy job, just like & follow. Earn RM80/day. Register here: https://t.me/taskbonusMY. Withdraw after complete 3 tasks.",
            "Celcom: Bil anda RM128.70 telah dijana. Bayar sebelum 25/02. Semak dalam aplikasi Celcom Life.",
            "Maybank: Your TAC is 482913. Do not share this code with anyone. Valid for 5 minutes.",
            "Telegram security: suspicious login. Enter code 483920 to verify. If not you, secure now at www.telegram-safe-check.com",
            "PosLaju: bungkusan anda ditahan. Bayar caj kastam RM2.00 di www.poslaju-my.com/track/123",
            "【J&T快递】您的包裹已到达分拨中心，运单号：882731992. 请留意派送通知。",
            "Your WhatsApp account has been flagged for violations and will be automatically suspended in 12 hours. Please log in to the official website: https://whtasapp-a.com to have the restrictions lifted!",
            "Akaun WhatsApp anda telah ditandai kerana pelanggaran dan akan digantung secara automatik dalam masa 12 jam. Sila log masuk ke laman web rasmi: https://whtasapp-a.com untuk membatalkan sekatan tersebut!",
            "您的WhatsApp账号涉嫌违规，12小时后将被自动注销，请登录官网：https://whtasapp-a.com 解除限制！",
            "Your Maybank account is on hold. Verify now: https://maybank-secure-login.com or call +60 12-345 6789. TAC: 928173",
            "CIMB Alert: Card purchase RM35.90 at MYDIN. Ref: 123456. If not you, call CIMB hotline."
        )

        for ((index, msg) in messages.withIndex()) {
            val tScore = mlEngine.predictScore(msg)
            val (hScore, tactics) = heuristicEngine.scoreText(msg)
            val combined = (hScore * 0.20f) + (tScore * 0.80f)
            
            println("=== MESSAGE \${index + 1} ===")
            println("Text: \$msg")
            println("TFLite Score: \$tScore -> contributes \${tScore * 0.80f}")
            println("Heuristic Score: \$hScore -> contributes \${hScore * 0.20f}")
            println("Tactics Matched: \$tactics")
            println("COMBINED FINAL SCORE: \$combined")
            println("EXCEEDS 0.30 THRESHOLD? \${combined >= 0.30f}")
            println()
        }
    }
}
