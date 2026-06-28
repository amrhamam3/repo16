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

    private val SPLASH_DURATION = 5000L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // لو التطبيق اتفتح من واتساب أو مدير ملفات (ACTION_VIEW)
        // ادخل على MainActivity مباشرة بدون Splash
        if (intent?.action == Intent.ACTION_VIEW && intent?.data != null) {
            val fileUri = intent.data
            val mainIntent = Intent(this, MainActivity::class.java).apply {
                action = Intent.ACTION_VIEW
                data = fileUri
                flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            }
            startActivity(mainIntent)
            finish()
            return
        }

        // لو من الأيقونة: عرض الـ Splash كامل
        setContentView(R.layout.activity_splash)

        val logo = findViewById<ImageView>(R.id.splashLogo)
        val titleText = findViewById<TextView>(R.id.splashTitle)
        val devText = findViewById<TextView>(R.id.splashDev)
        val progressBar = findViewById<ProgressBar>(R.id.splashProgress)
        val glowLine = findViewById<View>(R.id.splashGlowLine)

        // كل العناصر شفافة في البداية
        listOf(logo, titleText, devText, progressBar, glowLine).forEach {
            it.alpha = 0f
        }
        logo.scaleX = 0.5f; logo.scaleY = 0.5f
        titleText.translationY = 50f
        devText.translationY = 40f

        // اللوجو - Neon flash entrance
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(logo, "alpha", 0f, 1.2f, 1f).setDuration(1000),
                ObjectAnimator.ofFloat(logo, "scaleX", 0.5f, 1.08f, 1f).setDuration(1000),
                ObjectAnimator.ofFloat(logo, "scaleY", 0.5f, 1.08f, 1f).setDuration(1000)
            )
            interpolator = DecelerateInterpolator(2.5f)
            startDelay = 300
            start()
        }

        // Neon glow pulse على اللوجو
        Handler(Looper.getMainLooper()).postDelayed({
            ObjectAnimator.ofFloat(logo, "alpha", 1f, 0.7f, 1f, 0.85f, 1f).apply {
                duration = 600
                interpolator = AccelerateDecelerateInterpolator()
                start()
            }
        }, 1000)

        // الخط النيون
        ObjectAnimator.ofFloat(glowLine, "alpha", 0f, 1f).apply {
            duration = 400; startDelay = 900; start()
        }
        ObjectAnimator.ofFloat(glowLine, "scaleX", 0f, 1f).apply {
            duration = 500
            startDelay = 900
            interpolator = AccelerateDecelerateInterpolator()
            start()
        }

        // النصوص
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(titleText, "alpha", 0f, 1f).setDuration(600),
                ObjectAnimator.ofFloat(titleText, "translationY", 50f, 0f).setDuration(600)
            )
            interpolator = OvershootInterpolator(1.5f)
            startDelay = 1200
            start()
        }
        AnimatorSet().apply {
            playTogether(
                ObjectAnimator.ofFloat(devText, "alpha", 0f, 1f).setDuration(500),
                ObjectAnimator.ofFloat(devText, "translationY", 40f, 0f).setDuration(500)
            )
            interpolator = DecelerateInterpolator()
            startDelay = 1600
            start()
        }

        // Progress Bar
        ObjectAnimator.ofFloat(progressBar, "alpha", 0f, 1f).apply {
            duration = 300; startDelay = 1800; start()
        }
        ValueAnimator.ofInt(0, 100).apply {
            duration = SPLASH_DURATION - 500
            startDelay = 1800
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener { progressBar.progress = it.animatedValue as Int }
            start()
        }

        // Neon pulse على شريط التحميل
        ValueAnimator.ofFloat(0.7f, 1f).apply {
            duration = 800
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.REVERSE
            addUpdateListener { progressBar.alpha = it.animatedValue as Float }
            startDelay = 2000
            start()
        }

        // انتقل للـ MainActivity بعد 5 ثواني
        Handler(Looper.getMainLooper()).postDelayed({
            val rootView = findViewById<View>(android.R.id.content)
            ObjectAnimator.ofFloat(rootView, "alpha", 1f, 0f).apply {
                duration = 500
                interpolator = AccelerateInterpolator()
                start()
            }
            Handler(Looper.getMainLooper()).postDelayed({
                startActivity(Intent(this, MainActivity::class.java))
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out)
                finish()
            }, 500)
        }, SPLASH_DURATION)
    }
}
