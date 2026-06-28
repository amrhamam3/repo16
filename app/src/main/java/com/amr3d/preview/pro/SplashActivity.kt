package com.amr3d.preview.pro

import android.animation.*
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.animation.*
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class SplashActivity : AppCompatActivity() {

    private val SPLASH_DURATION = 5500L
    private val mainHandler = Handler(Looper.getMainLooper())
    private val runningAnimators = mutableListOf<Animator>()
    
    private val navigateRunnable = Runnable { navigateToMain() }
    private val rootViewRunnable = Runnable { executeFinalFadeOut() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // الدخول السريع إذا فتح من ملف خارجي (واتساب أو مدير الملفات) وتأمين الـ Intent
        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            val fileUri = intent.data
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = fileUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
            startActivity(mainIntent)
            finish()
            return
        }

        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val titleText = findViewById<TextView>(R.id.splashTitle)
        val devText = findViewById<TextView>(R.id.splashDev)
        val progressBar = findViewById<ProgressBar>(R.id.splashProgress)
        val progressText = findViewById<TextView>(R.id.splashProgressText)
        val glowLine = findViewById<View>(R.id.splashGlowLine)

        // صبغ شريط التحميل السميك بالبرتقالي الدافئ المضيء المستوحى من شعارك
        progressBar.progressDrawable?.setColorFilter(
            android.graphics.Color.parseColor("#FF9800"), 
            android.graphics.PorterDuff.Mode.SRC_IN
        )
        // صبغ خط الليزر الماسح بالبرتقالي النيون المتوهج
        glowLine.setBackgroundColor(android.graphics.Color.parseColor("#FFAC33"))

        // ضبط الحالات المبدئية للتلاشي والدخول
        listOf(logo, titleText, devText, progressBar, progressText, glowLine).forEach { it.alpha = 0f }
        logo.scaleX = 0.2f; logo.scaleY = 0.2f
        titleText.translationY = 80f
        devText.translationY = 60f

        // 1. دخول الشعار بنمط انفجار نيون دراماتيكي (Sci-Fi Warp Entrance)
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1.0f).setDuration(1200),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.2f, 1.15f, 1.0f).setDuration(1200),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.2f, 1.15f, 1.0f).setDuration(1200)
            )
            interpolator = OvershootInterpolator(1.4f)
            startDelay = 200
            trackAndStart()
        }

        // 2. تفعيل نبض التمدد والوميض اللانهائي على الشعار الفاخر (Infinite Neon Loop)
        mainHandler.postDelayed({
            val pulseAnim = ValueAnimator.ofFloat(0.92f, 1.05f).apply {
                duration = 1600
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
                addUpdateListener {
                    val scale = it.animatedValue as Float
                    logo.scaleX = scale
                    logo.scaleY = scale
                    logo.alpha = 0.85f + (scale - 0.92f) * 0.8f
                }
            }
            pulseAnim.trackAndStart()
        }, 1400)

        // 3. تأثير الطفو والجاذبية السينمائي للشعار في المنتصف
        mainHandler.postDelayed({
            val floatLogo = ObjectAnimator.ofFloat(logo, "translationY", -12f, 12f).apply {
                duration = 2200
                repeatCount = ValueAnimator.INFINITE
                repeatMode = ValueAnimator.REVERSE
                interpolator = AccelerateDecelerateInterpolator()
            }
            floatLogo.trackAndStart()
        }, 1400)

        // 4. حركية خط الليزر البرتقالي الماسح (Laser Glow Line Scan)
        ObjectAnimator.ofFloat(glowLine, "alpha", 0f, 0.8f, 0.4f, 1f).apply {
            duration = 800; startDelay = 800; trackAndStart()
        }
        ObjectAnimator.ofFloat(glowLine, "scaleX", 0f, 1f).apply {
            duration = 1200; startDelay = 800
            interpolator = DecelerateInterpolator(2.0f)
            trackAndStart()
        }

        // 5. صعود ووميض النصوص باللون العاجي الذهبي الفخم
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleText, "alpha", 0f, 1f).setDuration(800),
                ObjectAnimator.ofFloat(titleText, "translationY", 80f, 0f).setDuration(800)
            )
            interpolator = OvershootInterpolator(1.8f)
            startDelay = 1000
            trackAndStart()
        }
        
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(devText, "alpha", 0f, 0.8f).setDuration(700),
                ObjectAnimator.ofFloat(devText, "translationY", 60f, 0f).setDuration(700)
            )
            interpolator = DecelerateInterpolator()
            startDelay = 1500
            trackAndStart()
        }

        // 6. شريط التحميل السميك والنسبة المئوية معاً بتأثير التلاشي الظاهري
        ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).apply {
            duration = 400; startDelay = 1600; trackAndStart()
        }
        ObjectAnimator.ofFloat(progressText, "alpha", 0f, 1f).apply {
            duration = 400; startDelay = 1600; trackAndStart()
        }

        // تحريك العداد الرياضي وتحديث الرقم والشريط معاً ديناميكياً حتى %100
        ValueAnimator.ofInt(0, 100).apply {
            duration = SPLASH_DURATION - 2000
            startDelay = 1600
            interpolator = PathInterpolator(0.4f, 0.0f, 0.2f, 1.0f)
            addUpdateListener { animator ->
                val progressValue = animator.animatedValue as Int
                progressBar.progress = progressValue
                progressText.text = "$progressValue%"
            }
            trackAndStart()
        }

        // وميض النيون البرتقالي التكراري اللانهائي لشريط التحميل والنص معاً
        ValueAnimator.ofFloat(0.6f, 1.0f).apply {
            duration = 750
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { animator ->
                val alphaValue = animator.animatedValue as Float
                progressBar.alpha = alphaValue
                progressText.alpha = alphaValue
            }
            startDelay = 1800
            trackAndStart()
        }

        // جدولة الخروج التلاشي والسينمائي للواجهة الرئيسية
        mainHandler.postDelayed(rootViewRunnable, SPLASH_DURATION)
    }

    private fun Animator.trackAndStart() {
        runningAnimators.add(this)
        this.start()
    }

    private fun executeFinalFadeOut() {
        val rootView = findViewById<View>(android.R.id.content)
        ObjectAnimator.ofFloat(rootView, "alpha", 1f, 0f).apply {
            duration = 600
            interpolator = AccelerateInterpolator()
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mainHandler.post(navigateRunnable)
                }
            })
            start()
        }
    }

    private fun navigateToMain() {
        if (!isFinishing) {
            startActivity(Intent(this, MainActivity::class.java))
            overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
            finish()
        }
    }

    override fun onDestroy() {
        // تنظيف الـ Lifecycle لحماية معالج الهاتف ومنع الـ Memory Leaks والانهيارات تماماً
        mainHandler.removeCallbacksAndMessages(null)
        for (animator in runningAnimators) {
            if (animator.isRunning) animator.cancel()
        }
        runningAnimators.clear()
        super.onDestroy()
    }
}
