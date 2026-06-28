package com.amr3d.preview.pro

import android.content.Context
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class GLViewerView(context: Context, attrs: AttributeSet? = null) : GLSurfaceView(context, attrs) {

    private val renderer: STLRenderer
    private val scaleDetector: ScaleGestureDetector
    
    private var previousX = 0f
    private var previousY = 0f
    private val touchSlop = 10f

    init {
        setEGLContextClientVersion(2)
        renderer = STLRenderer()
        setRenderer(renderer)
        renderMode = RENDERMODE_CONTINUOUSLY
        scaleDetector = ScaleGestureDetector(context, ScaleListener())
    }

    fun setModel(model: STLModel) {
        queueEvent { renderer.setModel(model) }
    }

    fun clearMeasurementPoints() {
        queueEvent { renderer.clearMeasurementPoints() }
    }

    fun addMeasurementPoint(point: FloatArray) {
        queueEvent { renderer.addMeasurementPoint(point) }
    }

    val measurementPoints: List<FloatArray>
        get() = renderer.measurementPoints

    fun setCurrentMaterial(material: Material) {
        queueEvent { renderer.setCurrentMaterial(material) }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        
        val x = event.x
        val y = event.y

        when (event.action) {
            MotionEvent.ACTION_MOVE -> {
                if (event.pointerCount == 1) {
                    val dx = x - previousX
                    val dy = y - previousY
                    
                    if (dx > touchSlop || dy > touchSlop) {
                        queueEvent {
                            renderer.rotationY += dx * 0.5f
                            renderer.rotationX += dy * 0.5f
                            renderer.rotationX = renderer.rotationX.coerceIn(-90f, 90f)
                        }
                    }
                } else if (event.pointerCount == 2) {
                    queueEvent {
                        renderer.panX += (x - previousX) * 0.01f / renderer.scaleFactor
                        renderer.panY -= (y - previousY) * 0.01f / renderer.scaleFactor
                    }
                }
            }
        }
        previousX = x
        previousY = y
        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            queueEvent {
                renderer.scaleFactor *= detector.scaleFactor
                renderer.scaleFactor = renderer.scaleFactor.coerceIn(0.1f, 10f)
            }
            return true
        }
    }
}