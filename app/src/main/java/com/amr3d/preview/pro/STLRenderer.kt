package com.amr3d.preview.pro

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class STLRenderer : GLSurfaceView.Renderer {
    @Volatile var scaleFactor = 1.0f
    @Volatile var rotationX = 0f
    @Volatile var rotationY = 0f

    private var triangleCount = 0
    private var vertexBuffer: FloatBuffer? = null
    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private var program = 0

    fun loadSTL(vertices: FloatArray) {
        triangleCount = vertices.size / 9
        vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
           .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                put(vertices)
                position(0)
            }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.12f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        
        val vs = "uniform mat4 uMVPMatrix; attribute vec4 vPosition; void main() { gl_Position = uMVPMatrix * vPosition; }"
        val fs = "precision mediump float; void main() { gl_FragColor = vec4(0.2, 0.6, 1.0, 1.0); }"
        
        val vShader = GLES20.glCreateShader(GLES20.GL_VERTEX_SHADER).also {
            GLES20.glShaderSource(it, vs)
            GLES20.glCompileShader(it)
        }
        val fShader = GLES20.glCreateShader(GLES20.GL_FRAGMENT_SHADER).also {
            GLES20.glShaderSource(it, fs)
            GLES20.glCompileShader(it)
        }
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vShader)
            GLES20.glAttachShader(it, fShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, scaleFactor, scaleFactor, scaleFactor)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 0f, 5f, 0f, 1f, 0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        val mvpHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val posHandle = GLES20.glGetAttribLocation(program, "vPosition")

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        
        vertexBuffer?.let {
            GLES20.glEnableVertexAttribArray(posHandle)
            GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, it)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangleCount * 3)
            GLES20.glDisableVertexAttribArray(posHandle)
        }
    }
}
