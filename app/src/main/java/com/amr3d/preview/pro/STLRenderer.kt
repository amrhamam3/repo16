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
    @Volatile var surfaceWidth = 0
    @Volatile var surfaceHeight = 0

    private var model: STLModel? = null
    private val measurementPoints = CopyOnWriteArrayList<FloatArray>()
    private var currentMaterial = Material.PLA

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)

    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var triangleCount = 0
    private var program = 0

    private val vertexShaderCode = """
        uniform mat4 uMVPMatrix;
        uniform mat4 uNormalMatrix;
        attribute vec4 vPosition;
        attribute vec3 vNormal;
        varying vec3 fNormal;
        varying vec3 fPosition;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            fNormal = normalize((uNormalMatrix * vec4(vNormal, 0.0)).xyz);
            fPosition = vPosition.xyz;
        }
    """.trimIndent()

    private val fragmentShaderCode = """
        precision mediump float;
        varying vec3 fNormal;
        varying vec3 fPosition;
        uniform vec4 uColor;
        uniform vec3 uLightDir;
        uniform int uMaterial;

        float hash(vec3 p) {
            p = fract(p * vec3(443.8975, 397.2973, 491.1871));
            p += dot(p.zxy, p.yxz + 19.19);
            return fract(p.x * p.y * p.z);
        }

        float noise(vec3 p) {
            vec3 i = floor(p);
            vec3 f = fract(p);
            f = f * f * (3.0 - 2.0 * f);
            return mix(
                mix(mix(hash(i), hash(i+vec3(1,0,0)), f.x),
                    mix(hash(i+vec3(0,1,0)), hash(i+vec3(1,1,0)), f.x), f.y),
                mix(mix(hash(i+vec3(0,0,1)), hash(i+vec3(1,0,1)), f.x),
                    mix(hash(i+vec3(0,1,1)), hash(i+vec3(1,1,1)), f.x), f.y),
                f.z);
        }

        float fbm(vec3 p) {
            float v = 0.0; float a = 0.5;
            for(int i=0; i<4; i++) { v += a*noise(p); p *= 2.0; a *= 0.5; }
            return v;
        }

        void main() {
            vec3 normal = normalize(fNormal);
            vec3 lightDir = normalize(uLightDir);
            vec3 viewDir = normalize(vec3(0.0, 0.0, 1.0) - fPosition * 0.01);
            vec3 pos = fPosition * 0.02;

            vec3 finalColor = uColor.rgb;
            float shininess = 32.0;
            float specStrength = 0.6;
            float ambientStr = 0.5;

            if (uMaterial == 0) {
                ambientStr = 0.45; shininess = 24.0; specStrength = 0.3; finalColor = uColor.rgb;
            } else if (uMaterial == 1) {
                float metalNoise = noise(pos * 8.0) * 0.08;
                finalColor = uColor.rgb + vec3(metalNoise);
                shininess = 128.0; specStrength = 1.2; ambientStr = 0.3;
            } else if (uMaterial == 2) {
                float grain = fbm(vec3(pos.x * 12.0, pos.y * 0.8, pos.z * 12.0));
                float rings = sin((length(pos.xz) * 18.0) + grain * 6.0) * 0.5 + 0.5;
                vec3 woodLight = vec3(0.76, 0.52, 0.22); vec3 woodDark = vec3(0.42, 0.24, 0.08);
                finalColor = mix(woodDark, woodLight, rings) * (uColor.rgb * 1.8);
                shininess = 12.0; specStrength = 0.15; ambientStr = 0.55;
            } else if (uMaterial == 3) {
                float veins = fbm(pos * 3.0 + vec3(0.5));
                float veinPattern = sin(veins * 12.0 + pos.x * 4.0) * 0.5 + 0.5;
                vec3 marbleBase = vec3(0.90, 0.88, 0.85); vec3 marbleVein = vec3(0.55, 0.52, 0.50);
                finalColor = mix(marbleBase, marbleVein, veinPattern * 0.6) * uColor.rgb * 1.6;
                shininess = 64.0; specStrength = 0.8; ambientStr = 0.5;
            } else if (uMaterial == 4) {
                float tarnish = noise(pos * 6.0) * 0.15; float patina = fbm(pos * 4.0) * 0.2;
                vec3 bronzeBase = vec3(0.80, 0.50, 0.20); vec3 bronzePatina = vec3(0.30, 0.55, 0.40);
                finalColor = mix(bronzeBase, bronzePatina, patina) + vec3(tarnish * 0.1, 0.0, 0.0);
                finalColor *= uColor.rgb * 1.5;
                shininess = 80.0; specStrength = 0.9; ambientStr = 0.4;
            } else if (uMaterial == 5) {
                float weave = mod(pos.x * 20.0, 1.0) * mod(pos.y * 20.0, 1.0);
                float diag1 = mod((pos.x + pos.y) * 14.0, 1.0); float diag2 = mod((pos.x - pos.y) * 14.0, 1.0);
                float pattern = min(diag1, diag2) * 0.3 + weave * 0.1;
                vec3 carbonDark = vec3(0.06, 0.06, 0.08); vec3 carbonLight = vec3(0.18, 0.18, 0.22);
                finalColor = mix(carbonDark, carbonLight, pattern) * uColor.rgb * 2.0;
                shininess = 96.0; specStrength = 1.0; ambientStr = 0.25;
            } else if (uMaterial == 6) {
                float sub = fbm(pos * 5.0) * 0.3;
                vec3 resinColor = mix(uColor.rgb, vec3(1.0, 0.95, 0.80), 0.4);
                finalColor = resinColor + vec3(sub * 0.1, sub * 0.08, 0.0);
                shininess = 48.0; specStrength = 0.7; ambientStr = 0.6;
            }

            vec3 ambient = finalColor * ambientStr;
            float diff = max(dot(normal, lightDir), 0.0);
            vec3 diffuse = finalColor * diff * 0.75;
            vec3 reflDir = reflect(-lightDir, normal);
            float spec = pow(max(dot(viewDir, reflDir), 0.0), shininess);
            vec3 specular = vec3(1.0) * spec * specStrength;
            gl_FragColor = vec4(ambient + diffuse + specular, uColor.a);
        }
    """.trimIndent()

    fun setModel(model: STLModel) {
        this.model = model
        setupBuffers()
    }

    fun setCurrentMaterial(material: Material) {
        currentMaterial = material
    }

    fun clearMeasurementPoints() {
        measurementPoints.clear()
    }

    fun addMeasurementPoint(point: FloatArray) {
        if (measurementPoints.size < 2) measurementPoints.add(point)
    }

    fun getModelMatrix(): FloatArray = modelMatrix.clone()
    fun getViewMatrix(): FloatArray = viewMatrix.clone()
    fun getProjectionMatrix(): FloatArray = projectionMatrix.clone()

    private fun setupBuffers() {
        model?.let {
            val vertices = it.vertices
            val normals = it.normals
            triangleCount = vertices.size / 9

            vertexBuffer = ByteBuffer.allocateDirect(vertices.size * 4)
               .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(vertices)
                    position(0)
                }

            normalBuffer = ByteBuffer.allocateDirect(normals.size * 4)
               .order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
                    put(normals)
                    position(0)
                }
        }
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glClearColor(0.1f, 0.1f, 0.12f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glEnable(GLES20.GL_CULL_FACE)

        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexShaderCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentShaderCode)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        val ratio: Float = width.toFloat() / height.toFloat()
        Matrix.frustumM(projectionMatrix, 0, -ratio, ratio, -1f, 1f, 3f, 100f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        GLES20.glUseProgram(program)

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.scaleM(modelMatrix, 0, scaleFactor, scaleFactor, scaleFactor)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, panX, panY, 0f)

        Matrix.setLookAtM(viewMatrix, 0, 0f, 5f, 0f, 1.0f, 0.0f)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, viewMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, mvpMatrix, 0, modelMatrix, 0)

        Matrix.invertM(normalMatrix, 0, modelMatrix, 0)
        Matrix.transposeM(normalMatrix, 0, normalMatrix, 0)

        val mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix")
        val normalMatrixHandle = GLES20.glGetUniformLocation(program, "uNormalMatrix")
        val colorHandle = GLES20.glGetUniformLocation(program, "uColor")
        val lightDirHandle = GLES20.glGetUniformLocation(program, "uLightDir")
        val materialHandle = GLES20.glGetUniformLocation(program, "uMaterial")
        val positionHandle = GLES20.glGetAttribLocation(program, "vPosition")
        val normalHandle = GLES20.glGetAttribLocation(program, "vNormal")

        GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normalMatrix, 0)
        GLES20.glUniform4f(colorHandle, currentMaterial.color[0], currentMaterial.color[1], currentMaterial.color[2], 1.0f)
        GLES20.glUniform3f(lightDirHandle, 0.5f, 0.7f, 1.0f)
        GLES20.glUniform1i(materialHandle, currentMaterial.ordinal)

        vertexBuffer?.let {
            GLES20.glEnableVertexAttribArray(positionHandle)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, it)
        }

        normalBuffer?.let {
            GLES20.glEnableVertexAttribArray(normalHandle)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, it)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, triangleCount * 3)
        GLES20.glDisableVertexAttribArray(positionHandle)