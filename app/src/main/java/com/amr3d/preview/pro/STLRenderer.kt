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
    private val tempMatrix = FloatArray(16)

    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var triangleCount = 0
    private var program = 0

    private var uMVPMatrix = 0
    private var aPosition = 0
    private var aNormal = 0
    private var uColor = 0
    private var uLightDir = 0

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 aPosition;
        attribute vec3 aNormal;
        varying vec3 vNormal;
        void main() {
            gl_Position = uMVPMatrix * aPosition;
            vNormal = aNormal;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec3 vNormal;
        uniform vec3 uColor;
        uniform vec3 uLightDir;
        void main() {
            vec3 norm = normalize(vNormal);
            float diff = max(dot(norm, normalize(uLightDir)), 0.0);
            vec3 ambient = uColor * 0.3;
            vec3 diffuse = uColor * diff * 0.7;
            gl_FragColor = vec4(ambient + diffuse, 1.0);
        }
    """.trimIndent()

    // ── Public API called from GLViewerView ──────────────────────────────────

    fun setModel(model: STLModel) {
        this.model = model
        uploadModelToGPU(model)
    }

    fun clearMeasurementPoints() {
        measurementPoints.clear()
    }

    fun addMeasurementPoint(point: FloatArray) {
        measurementPoints.add(point)
    }

    fun setCurrentMaterial(material: Material) {
        currentMaterial = material
    }

    // ── GLSurfaceView.Renderer ───────────────────────────────────────────────

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.12f, 0.12f, 0.14f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        program = buildProgram(vertexShaderCode, fragmentShaderCode)

        uMVPMatrix = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        aPosition  = GLES20.glGetAttribLocation(program,  "aPosition")
        aNormal    = GLES20.glGetAttribLocation(program,  "aNormal")
        uColor     = GLES20.glGetUniformLocation(program, "uColor")
        uLightDir  = GLES20.glGetUniformLocation(program, "uLightDir")

        // Re-upload model if one was set before surface creation
        model?.let { uploadModelToGPU(it) }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        val ratio = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 2f, 200f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        if (triangleCount == 0 || vertexBuffer == null) return

        // View matrix
        Matrix.setLookAtM(viewMatrix, 0,
            0f, 0f, 5f,
            0f, 0f, 0f,
            0f, 1f, 0f)

        // Model matrix: scale → rotate → translate (pan)
        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.translateM(modelMatrix, 0, panX, panY, 0f)
        Matrix.scaleM(modelMatrix, 0, scaleFactor, scaleFactor, scaleFactor)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)

        // MVP
        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        GLES20.glUseProgram(program)

        GLES20.glUniformMatrix4fv(uMVPMatrix, 1, false, mvpMatrix, 0)

        val color = currentMaterial.color
        GLES20.glUniform3f(uColor, color[0], color[1], color[2])
        GLES20.glUniform3f(uLightDir, 1f, 1f, 1f)

        // Vertices
        vertexBuffer!!.position(0)
        GLES20.glEnableVertexAttribArray(aPosition)
        GLES20.glVertexAttribPointer(aPosition, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer)

        // Normals
        normalBuffer?.let { nb ->
            nb.position(0)
            GLES20.glEnableVertexAttribArray(aNormal)
            GLES20.glVertexAttribPointer(aNormal, 3, GLES20.GL_FLOAT, false, 0, nb)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangleCount * 3)

        GLES20.glDisableVertexAttribArray(aPosition)
        GLES20.glDisableVertexAttribArray(aNormal)
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun uploadModelToGPU(model: STLModel) {
        triangleCount = model.triangleCount

        // Center the model
        val cx = (model.minBounds[0] + model.maxBounds[0]) / 2f
        val cy = (model.minBounds[1] + model.maxBounds[1]) / 2f
        val cz = (model.minBounds[2] + model.maxBounds[2]) / 2f

        val dx = model.maxBounds[0] - model.minBounds[0]
        val dy = model.maxBounds[1] - model.minBounds[1]
        val dz = model.maxBounds[2] - model.minBounds[2]
        val maxDim = maxOf(dx, dy, dz).coerceAtLeast(0.001f)
        val autoScale = 2.0f / maxDim

        val centered = FloatArray(model.vertices.size)
        var i = 0
        while (i < model.vertices.size) {
            centered[i]     = (model.vertices[i]     - cx) * autoScale
            centered[i + 1] = (model.vertices[i + 1] - cy) * autoScale
            centered[i + 2] = (model.vertices[i + 2] - cz) * autoScale
            i += 3
        }

        vertexBuffer = ByteBuffer
            .allocateDirect(centered.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(centered); position(0) }

        normalBuffer = ByteBuffer
            .allocateDirect(model.normals.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply { put(model.normals); position(0) }
    }

    private fun buildProgram(vertexSrc: String, fragmentSrc: String): Int {
        val vs = compileShader(GLES20.GL_VERTEX_SHADER, vertexSrc)
        val fs = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSrc)
        return GLES20.glCreateProgram().also { prog ->
            GLES20.glAttachShader(prog, vs)
            GLES20.glAttachShader(prog, fs)
            GLES20.glLinkProgram(prog)
        }
    }

    private fun compileShader(type: Int, src: String): Int {
        return GLES20.glCreateShader(type).also { shader ->
            GLES20.glShaderSource(shader, src)
            GLES20.glCompileShader(shader)
        }
    }
}
