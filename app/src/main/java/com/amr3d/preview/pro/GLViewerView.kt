package com.amr3d.preview.pro

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.hypot

/**
 * Touch gestures:
 * - One finger drag      -> rotate model
 * - Two finger pinch     -> zoom
 * - Two finger drag      -> pan (safeguarded)
 * - Two finger twist     -> rotate (helps reach awkward orientations)
 * - Single tap           -> measurement point picking
 */
class GLViewerView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    val stlRenderer = STLRenderer()

    private var previousX = 0f
    private var previousY = 0f
    private var previousSpan = 0f
    private var previousAngle = 0f
    private var lastTouchCount = 0
    private var moved = false

    var onSingleTap: ((Float, Float) -> Unit)? = null

    init {
        setEGLContextClientVersion(2)
        setRenderer(stlRenderer)
        renderMode = RENDERMODE_CONTINUOUSLY
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                previousX = event.x
                previousY = event.y
                moved = false
                lastTouchCount = 1
            }

            MotionEvent.ACTION_POINTER_DOWN -> {
                lastTouchCount = event.pointerCount
                previousX = averageX(event)
                previousY = averageY(event)
                previousSpan = currentSpan(event)
                previousAngle = currentAngle(event)
            }

            MotionEvent.ACTION_MOVE -> {
                val curX = averageX(event)
                val curY = averageY(event)
                val dx = curX - previousX
                val dy = curY - previousY

                if (abs(dx) > 1f || abs(dy) > 1f) moved = true

                if (event.pointerCount >= 2) {
                    // 1. Zoom via pinch
                    val curSpan = currentSpan(event)
                    if (previousSpan > 10f && curSpan > 10f) {
                        val spanRatio = curSpan / previousSpan
                        stlRenderer.scaleFactor = (stlRenderer.scaleFactor * spanRatio).coerceIn(0.1f, 12f)
                    }
                    previousSpan = curSpan

                    // 2. Detect Twist
                    val curAngle = currentAngle(event)
                    val angleDelta = curAngle - previousAngle

                    // Normalize angle delta to [-180, 180]
                    val normAngle = when {
                        angleDelta > 180f -> angleDelta - 360f
                        angleDelta < -180f -> angleDelta + 360f
                        else -> angleDelta
                    }

                    // التعديل: تفعيل الالتواء والدوران فقط إذا كانت الحركة واضحة وقوية لمنع التداخل العشوائي مع السحب (Pan)
                    if (abs(normAngle) > 0.8f) { 
                        stlRenderer.rotationY += normAngle * 1.2f
                        previousAngle = curAngle
                    } else {
                        // تفادي تراكم الفروقات الصغيرة جداً
                        previousAngle = curAngle
                    }

                    // 3. Two-finger pan
                    // توازن قيم التحجيم وسرعة التحريك الأفقي والعمودي بناءً على معدل التكبير الحالي للمجسم
                    val panSensitivity = 0.002f / (stlRenderer.scaleFactor.coerceAtLeast(0.5f))
                    stlRenderer.panX += dx * panSensitivity
                    stlRenderer.panY -= dy * panSensitivity

                } else if (event.pointerCount == 1 && lastTouchCount == 1) {
                    // One finger rotate
                    stlRenderer.rotationY += dx * 0.4f
                    stlRenderer.rotationX += dy * 0.4f
                    stlRenderer.rotationX = stlRenderer.rotationX.coerceIn(-90f, 90f)
                }

                previousX = curX
                previousY = curY
            }

            MotionEvent.ACTION_POINTER_UP -> {
                // إصلاح خطأ القفزة العنيفة (Jumping Bug Fixing):
                // عند رفع أحد الأصابع، نحتاج لإعادة تعيين نقطة الارتكاز السابقة فوراً لتطابق وضعية الإصبع المتبقي المحدثة
                val pointerIndex = event.actionIndex
                var remainingIndex = 0
                if (pointerIndex == 0) remainingIndex = 1
                
                if (event.pointerCount > 2) {
                    lastTouchCount = event.pointerCount - 1
                    // إعادة الحساب بالاعتماد على الأصابع المتبقية فقط لتفادي تصفير القيم المفاجئ
                    previousX = averageXExcept(event, pointerIndex)
                    previousY = averageYExcept(event, pointerIndex)
                } else {
                    lastTouchCount = 1
                    // إذا كان المتبقي إصبع واحد، ننتقل مباشرة لإحداثياته الحقيقية دون توسط حسابي مضلل
                    previousX = event.getX(remainingIndex)
                    previousY = event.getY(remainingIndex)
                }
                previousSpan = 0f
                previousAngle = 0f
            }

            MotionEvent.ACTION_UP -> {
                if (!moved && lastTouchCount == 1) {
                    onSingleTap?.invoke(event.x, event.y)
                }
                lastTouchCount = 0
            }
        }
        return true
    }

    private fun currentSpan(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(0) - event.getX(1)
        val dy = event.getY(0) - event.getY(1)
        return hypot(dx, dy)
    }

    private fun currentAngle(event: MotionEvent): Float {
        if (event.pointerCount < 2) return 0f
        val dx = event.getX(1) - event.getX(0)
        val dy = event.getY(1) - event.getY(0)
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble())).toFloat()
    }

    private fun averageX(event: MotionEvent): Float {
        var total = 0f
        for (i in 0 until event.pointerCount) total += event.getX(i)
        return total / event.pointerCount
    }

    private fun averageY(event: MotionEvent): Float {
        var total = 0f
        for (i in 0 until event.pointerCount) total += event.getY(i)
        return total / event.pointerCount
    }

    // دوال مساعدة لمنع قفزات الرؤية عند بقاء أكثر من إصبع
    private fun averageXExcept(event: MotionEvent, skipIndex: Int): Float {
        var total = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == skipIndex) continue
            total += event.getX(i)
            count++
        }
        return if (count > 0) total / count else event.x
    }

    private fun averageYExcept(event: MotionEvent, skipIndex: Int): Float {
        var total = 0f
        var count = 0
        for (i in 0 until event.pointerCount) {
            if (i == skipIndex) continue
            total += event.getY(i)
            count++
        }
        return if (count > 0) total / count else event.y
    }
}
