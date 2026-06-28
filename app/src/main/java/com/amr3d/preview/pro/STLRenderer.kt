package com.amr3d.preview.pro

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.util.concurrent.CopyOnWriteArrayList
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class STLRenderer : GLSurfaceView.Renderer {

    @Volatile var scaleFactor = 1.0f
    @Volatile var rotationX = 0f
    @Volatile var rotationY = 0f
    @Volatile var panX = 0f
    @Volatile var panY = 0f

    private var model: STLModel? = null
    val measurementPoints = CopyOnWriteArrayList<FloatArray>()
    private var currentMaterial = Material.PLA

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)

    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var triangleCount = 0
    private var program = 0

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        varying vec3 fNormal;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            fNormal = vNormal;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec3