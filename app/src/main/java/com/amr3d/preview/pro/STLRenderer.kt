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

    // --- Shaders مع دعم اتجاه الإضاءة ---
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
        uniform int uMaterial;  // 0=plastic 1=metal 2=wood 3=marble 4=bronze 5=carbon 6=resin

        // دالة noise بسيطة للـ procedural textures
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

            // ===== بلاستيك =====
            if (uMaterial == 0) {
                ambientStr = 0.45;
                shininess = 24.0;
                specStrength = 0.3;
                finalColor = uColor.rgb;
            }
            // ===== معدن =====
            else if (uMaterial == 1) {
                float metalNoise = noise(pos * 8.0) * 0.08;
                finalColor = uColor.rgb + vec3(metalNoise);
                shininess = 128.0;
                specStrength = 1.2;
                ambientStr = 0.3;
            }
            // ===== خشب =====
            else if (uMaterial == 2) {
                float grain = fbm(vec3(pos.x * 12.0, pos.y * 0.8, pos.z * 12.0));
                float rings = sin((length(pos.xz) * 18.0) + grain * 6.0) * 0.5 + 0.5;
                vec3 woodLight = vec3(0.76, 0.52, 0.22);
                vec3 woodDark  = vec3(0.42, 0.24, 0.08);
                finalColor = mix(woodDark, woodLight, rings) * (uColor.rgb * 1.8);
                shininess = 12.0;
                specStrength = 0.15;
                ambientStr = 0.55;
            }
            // ===== رخام =====
            else if (uMaterial == 3) {
                float veins = fbm(pos * 3.0 + vec3(0.5));
                float veinPattern = sin(veins * 12.0 + pos.x * 4.0) * 0.5 + 0.5;
                vec3 marbleBase = vec3(0.90, 0.88, 0.85);
                vec3 marbleVein = vec3(0.55, 0.52, 0.50);
                finalColor = mix(marbleBase, marbleVein, veinPattern * 0.6) * uColor.rgb * 1.6;
                shininess = 64.0;
                specStrength = 0.8;
                ambientStr = 0.5;
            }
            // ===== نحاس/برونز =====
            else if (uMaterial == 4) {
                float tarnish = noise(pos * 6.0) * 0.15;
                float patina = fbm(pos * 4.0) * 0.2;
                vec3 bronzeBase = vec3(0.80, 0.50, 0.20);
                vec3 bronzePatina = vec3(0.30, 0.55, 0.40);
                finalColor = mix(bronzeBase, bronzePatina, patina) + vec3(tarnish * 0.1, 0.0, 0.0);
                finalColor *= uColor.rgb * 1.5;
                shininess = 80.0;
                specStrength = 0.9;
                ambientStr = 0.4;
            }
            // ===== كربون =====
            else if (uMaterial == 5) {
                float weave = mod(pos.x * 20.0, 1.0) * mod(pos.y * 20.0, 1.0);
                float diag1 = mod((pos.x + pos.y) * 14.0, 1.0);
                float diag2 = mod((pos.x - pos.y) * 14.0, 1.0);
                float pattern = min(diag1, diag2) * 0.3 + weave * 0.1;
                vec3 carbonDark = vec3(0.06, 0.06, 0.08);
                vec3 carbonLight = vec3(0.18, 0.18, 0.22);
                finalColor = mix(carbonDark, carbonLight, pattern) * uColor.rgb * 2.0;
                shininess = 96.0;
                specStrength = 1.0;
                ambientStr = 0.25;
            }
            // ===== ريزن =====
            else if (uMaterial == 6) {
                float sub = fbm(pos * 5.0) * 0.3;
                vec3 resinColor = mix(uColor.rgb, vec3(1.0, 0.95, 0.80), 0.4);
                finalColor = resinColor + vec3(sub * 0.1, sub * 0.08, 0.0);
                shininess = 48.0;
                specStrength = 0.7;
                ambientStr = 0.6;
            }

            // إضاءة موحدة
            vec3 ambient  = finalColor * ambientStr;
            float diff    = max(dot(normal, lightDir), 0.0);
            vec3 diffuse  = finalColor * diff * 0.75;
            vec3 reflDir  = reflect(-lightDir, normal);
            float spec    = pow(max(dot(viewDir, reflDir), 0.0), shininess);
            vec3 specular = vec3(1.0) * spec * specStrength;
            float rim     = pow(max(1.0 - dot(normal, viewDir), 0.0), 2.0) * 0.25;

            vec3 result = ambient + diffuse + specular + finalColor * rim;
            gl_FragColor = vec4(result, uColor.a);
        }
    """.trimIndent()

    private val lineVertexShaderCode = """
        uniform mat4 uMVPMatrix;
        attribute vec4 vPosition;
        void main() {
            gl_Position = uMVPMatrix * vPosition;
            gl_PointSize = 14.0;
        }
    """.trimIndent()

    private val lineFragmentShaderCode = """
        precision mediump float;
        uniform vec4 uColor;
        void main() {
            gl_FragColor = uColor;
        }
    """.trimIndent()

    private var meshProgram = 0
    private var lineProgram = 0

    // CPU-side buffers (nulled after upload to GPU)
    private var vertexBuffer: FloatBuffer? = null
    private var normalBuffer: FloatBuffer? = null
    private var wireframeBuffer: FloatBuffer? = null
    private var wireframeVertexCount = 0
    private var vertexCountToDraw = 0

    // VBO handles — data lives on GPU after upload
    private val vboIds = IntArray(3) // [0]=vertex [1]=normal [2]=wireframe
    private var vboReady = false
    private var pendingModel: STLModel? = null

    @Volatile var wireframeMode = false

    private val mvpMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val modelMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(16)
    private val tempMatrix = FloatArray(16)

    @Volatile var rotationX = -25f
    @Volatile var rotationY = 35f
    @Volatile var scaleFactor = 1f
    @Volatile var panX = 0f
    @Volatile var panY = 0f

    // اتجاه الإضاءة - قابل للتغيير من الـ slider
    @Volatile
    var lightAngle = 45f
        set(value) {
            field = ((value % 360f) + 360f) % 360f
        } // زاوية الإضاءة من 0 إلى 360

    private var modelCenter = floatArrayOf(0f, 0f, 0f)
    private var modelRadius = 1f

    // CopyOnWriteArrayList بدل ArrayList - thread-safe
    private val measurementPoints = CopyOnWriteArrayList<FloatArray>()

    private var surfaceWidth = 0
    private var surfaceHeight = 0

    var modelColor = floatArrayOf(0.45f, 0.75f, 0.95f, 1.0f)

    fun setModelColor(r: Float, g: Float, b: Float) { modelColor = floatArrayOf(r, g, b, 1.0f) }

    // نظام المواد
    enum class Material(val id: Int, val nameAr: String, val defaultColor: FloatArray) {
        PLASTIC(0, "بلاستيك", floatArrayOf(0.45f, 0.75f, 0.95f)),
        METAL  (1, "معدن",    floatArrayOf(0.75f, 0.75f, 0.80f)),
        WOOD   (2, "خشب",     floatArrayOf(0.60f, 0.40f, 0.20f)),
        MARBLE (3, "رخام",    floatArrayOf(0.85f, 0.82f, 0.80f)),
        BRONZE (4, "نحاس",    floatArrayOf(0.80f, 0.50f, 0.20f)),
        CARBON (5, "كربون",   floatArrayOf(0.15f, 0.15f, 0.18f)),
        RESIN  (6, "ريزن",    floatArrayOf(0.90f, 0.75f, 0.50f))
    }

    @Volatile var currentMaterial = Material.PLASTIC

    fun setMaterial(material: Material) {
        currentMaterial = material
        setModelColor(material.defaultColor[0], material.defaultColor[1], material.defaultColor[2])
    }
    fun getCurrentModelMatrix(): FloatArray = modelMatrix.copyOf()
    fun getCurrentViewMatrix(): FloatArray = viewMatrix.copyOf()
    fun getCurrentProjectionMatrix(): FloatArray = projectionMatrix.copyOf()
    fun getSurfaceWidth(): Int = surfaceWidth
    fun getSurfaceHeight(): Int = surfaceHeight

    private var currentModel: STLModel? = null
    fun getModel(): STLModel? = currentModel

    fun setModel(model: STLModel) {
        currentModel = model
        // If GL context ready, upload now; else queue for onSurfaceCreated
        if (vboIds[0] != 0) {
            uploadModelToGPU(model)
        } else {
            pendingModel = model
            // Keep CPU buffers as fallback until VBOs are ready
            val verts = model.vertices
            val norms = model.normals
            vertexBuffer = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(verts); position(0) }
            normalBuffer = ByteBuffer.allocateDirect(norms.size * 4).order(ByteOrder.nativeOrder())
                .asFloatBuffer().apply { put(norms); position(0) }
            vertexCountToDraw = verts.size / 3
        }

        modelCenter = floatArrayOf(
            (model.minBounds[0] + model.maxBounds[0]) / 2f,
            (model.minBounds[1] + model.maxBounds[1]) / 2f,
            (model.minBounds[2] + model.maxBounds[2]) / 2f
        )
        val dx = model.maxBounds[0] - model.minBounds[0]
        val dy = model.maxBounds[1] - model.minBounds[1]
        val dz = model.maxBounds[2] - model.minBounds[2]
        modelRadius = (maxOf(dx, dy, dz) / 2f).let { if (it <= 0f) 1f else it }

        rotationX = -25f; rotationY = 35f; scaleFactor = 1f; panX = 0f; panY = 0f
        measurementPoints.clear()
        updateProjection()
    }


    /** Uploads model geometry to GPU VBOs and frees CPU copies. Called on GL thread. */
    private fun uploadModelToGPU(model: STLModel) {
        val verts = model.vertices
        val norms = model.normals

        // Upload vertex positions
        val vb = ByteBuffer.allocateDirect(verts.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(verts); position(0) }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[0])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, verts.size * 4, vb, GLES20.GL_STATIC_DRAW)

        // Upload normals
        val nb = ByteBuffer.allocateDirect(norms.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(norms); position(0) }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[1])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, norms.size * 4, nb, GLES20.GL_STATIC_DRAW)

        vertexCountToDraw = verts.size / 3

        // Build wireframe with LOD: for meshes >500K triangles, sample every 4th triangle
        val triCount = vertexCountToDraw / 3
        val wireStep = when {
            triCount > 2_000_000 -> 8
            triCount > 500_000   -> 4
            triCount > 200_000   -> 2
            else                 -> 1
        }
        val sampledTris = (triCount + wireStep - 1) / wireStep
        val wireData = FloatArray(sampledTris * 6 * 3)
        var wIdx = 0; var vSrc = 0
        var t = 0
        while (t < triCount) {
            val ax = verts[vSrc]; val ay = verts[vSrc+1]; val az = verts[vSrc+2]
            val bx = verts[vSrc+3]; val by = verts[vSrc+4]; val bz = verts[vSrc+5]
            val cx = verts[vSrc+6]; val cy = verts[vSrc+7]; val cz = verts[vSrc+8]
            if (wIdx + 18 <= wireData.size) {
                wireData[wIdx++]=ax; wireData[wIdx++]=ay; wireData[wIdx++]=az
                wireData[wIdx++]=bx; wireData[wIdx++]=by; wireData[wIdx++]=bz
                wireData[wIdx++]=bx; wireData[wIdx++]=by; wireData[wIdx++]=bz
                wireData[wIdx++]=cx; wireData[wIdx++]=cy; wireData[wIdx++]=cz
                wireData[wIdx++]=cx; wireData[wIdx++]=cy; wireData[wIdx++]=cz
                wireData[wIdx++]=ax; wireData[wIdx++]=ay; wireData[wIdx++]=az
            }
            t += wireStep; vSrc += wireStep * 9
        }
        val wb = ByteBuffer.allocateDirect(wIdx * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(wireData, 0, wIdx); position(0) }
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[2])
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, wIdx * 4, wb, GLES20.GL_STATIC_DRAW)
        wireframeVertexCount = wIdx / 3

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        // Free CPU copies — data is now on GPU
        vertexBuffer = null
        normalBuffer = null
        wireframeBuffer = null
        vboReady = true
    }

    fun addMeasurementPoint(point: FloatArray) {
        measurementPoints.add(point)
        if (measurementPoints.size > 2) measurementPoints.removeAt(0)
    }

    fun clearMeasurementPoints() { measurementPoints.clear() }
    fun getMeasurementPoints(): List<FloatArray> = measurementPoints.toList()

    override fun onSurfaceCreated(unused: GL10?, config: EGLConfig?) {
        updateClearColor()
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        meshProgram = createProgram(vertexShaderCode, fragmentShaderCode)
        lineProgram = createProgram(lineVertexShaderCode, lineFragmentShaderCode)
        // Generate VBO handles
        GLES20.glGenBuffers(3, vboIds, 0)
        // Upload any model that was loaded before GL context was ready
        pendingModel?.let { uploadModelToGPU(it); pendingModel = null }
    }

    var bgColor = floatArrayOf(0.10f, 0.11f, 0.13f)
    fun setBackgroundColor(r: Float, g: Float, b: Float) { bgColor = floatArrayOf(r, g, b); updateClearColor() }
    private fun updateClearColor() { GLES20.glClearColor(bgColor[0], bgColor[1], bgColor[2], 1f) }

    override fun onSurfaceChanged(unused: GL10?, width: Int, height: Int) {
        surfaceWidth = width; surfaceHeight = height
        GLES20.glViewport(0, 0, width, height)
        updateProjection()
    }

    fun updateProjection() {
        if (surfaceWidth == 0 || surfaceHeight == 0) return
        val ratio = surfaceWidth.toFloat() / surfaceHeight.toFloat()
        val safeRadius = if (modelRadius > 0f) modelRadius else 1f
        val orthoHalf = safeRadius * 1.4f / scaleFactor
        val near = -safeRadius * 10f
        val far = safeRadius * 10f
        Matrix.orthoM(projectionMatrix, 0,
            -orthoHalf * ratio, orthoHalf * ratio,
            -orthoHalf, orthoHalf, near, far)
    }

    override fun onDrawFrame(unused: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)
        updateClearColor()
        if ((!vboReady && vertexBuffer == null) || vertexCountToDraw == 0) return
        // Upload pending model now that we're on the GL thread
        pendingModel?.let { uploadModelToGPU(it); pendingModel = null }

        updateProjection()

        val camDistance = (if (modelRadius > 0f) modelRadius else 1f) * 5f
        val panScale = (if (modelRadius > 0f) modelRadius else 1f) * 1.4f / scaleFactor

        Matrix.setIdentityM(modelMatrix, 0)
        Matrix.rotateM(modelMatrix, 0, rotationX, 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, rotationY, 0f, 1f, 0f)
        Matrix.translateM(modelMatrix, 0, -modelCenter[0], -modelCenter[1], -modelCenter[2])

        Matrix.setIdentityM(viewMatrix, 0)
        Matrix.translateM(viewMatrix, 0, panX * panScale, panY * panScale, -camDistance)

        Matrix.multiplyMM(tempMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projectionMatrix, 0, tempMatrix, 0)

        Matrix.invertM(normalMatrix, 0, modelMatrix, 0)
        Matrix.transposeM(normalMatrix, 0, normalMatrix, 0)

        drawMesh()

        val pts = measurementPoints.toList() // snapshot آمن
        if (pts.isNotEmpty()) drawMeasurementOverlay(pts)
    }

    private fun drawMesh() {
        if (wireframeMode) drawWireframe() else drawSolidMesh()
    }

    fun captureFrame(width: Int, height: Int): android.graphics.Bitmap {
        val buffer = ByteBuffer.allocateDirect(width * height * 4).order(ByteOrder.nativeOrder())
        GLES20.glReadPixels(0, 0, width, height, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, buffer)
        val bitmap = android.graphics.Bitmap.createBitmap(width, height, android.graphics.Bitmap.Config.ARGB_8888)
        buffer.rewind(); bitmap.copyPixelsFromBuffer(buffer)
        val matrix = android.graphics.Matrix().apply { postScale(1f, -1f) }
        return android.graphics.Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true)
    }

    private fun drawWireframe() {
        if (wireframeVertexCount == 0) return
        GLES20.glUseProgram(lineProgram)
        val positionHandle = GLES20.glGetAttribLocation(lineProgram, "vPosition")
        val mvpHandle = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(lineProgram, "uColor")
        GLES20.glEnableVertexAttribArray(positionHandle)
        if (vboReady) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[2])
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
        } else {
            val buf = wireframeBuffer ?: return
            buf.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, buf)
        }
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniform4f(colorHandle, modelColor[0], modelColor[1], modelColor[2], 1f)
        GLES20.glLineWidth(1.5f)
        GLES20.glDrawArrays(GLES20.GL_LINES, 0, wireframeVertexCount)
        GLES20.glDisableVertexAttribArray(positionHandle)
        if (vboReady) GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawSolidMesh() {
        GLES20.glUseProgram(meshProgram)
        val positionHandle = GLES20.glGetAttribLocation(meshProgram, "vPosition")
        val normalHandle = GLES20.glGetAttribLocation(meshProgram, "vNormal")
        val mvpHandle = GLES20.glGetUniformLocation(meshProgram, "uMVPMatrix")
        val normalMatrixHandle = GLES20.glGetUniformLocation(meshProgram, "uNormalMatrix")
        val colorHandle = GLES20.glGetUniformLocation(meshProgram, "uColor")
        val lightDirHandle = GLES20.glGetUniformLocation(meshProgram, "uLightDir")
        val materialHandle = GLES20.glGetUniformLocation(meshProgram, "uMaterial")

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glEnableVertexAttribArray(normalHandle)
        if (vboReady) {
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[0])
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vboIds[1])
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, 0)
            GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
        } else {
            vertexBuffer?.position(0)
            GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, vertexBuffer ?: return)
            normalBuffer?.position(0)
            GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, normalBuffer ?: return)
        }

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(normalMatrixHandle, 1, false, normalMatrix, 0)
        GLES20.glUniform4fv(colorHandle, 1, modelColor, 0)
        GLES20.glUniform1i(materialHandle, currentMaterial.id)

        // حساب اتجاه الإضاءة من الزاوية
        val angleRad = Math.toRadians(lightAngle.toDouble()).toFloat()
        val lx = kotlin.math.cos(angleRad) * 0.7f
        val ly = 0.7f
        val lz = kotlin.math.sin(angleRad) * 0.7f
        GLES20.glUniform3f(lightDirHandle, lx, ly, lz)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, vertexCountToDraw)
        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        if (vboReady) GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)
    }

    private fun drawMeasurementOverlay(pts: List<FloatArray>) {
        GLES20.glUseProgram(lineProgram)
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)

        val positionHandle = GLES20.glGetAttribLocation(lineProgram, "vPosition")
        val mvpHandle = GLES20.glGetUniformLocation(lineProgram, "uMVPMatrix")
        val colorHandle = GLES20.glGetUniformLocation(lineProgram, "uColor")

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glEnableVertexAttribArray(positionHandle)

        val flat = FloatArray(pts.size * 3)
        pts.forEachIndexed { i, p ->
            flat[i * 3] = p[0]; flat[i * 3 + 1] = p[1]; flat[i * 3 + 2] = p[2]
        }
        val fb = ByteBuffer.allocateDirect(flat.size * 4).order(ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(flat); position(0) }

        GLES20.glVertexAttribPointer(positionHandle, 3, GLES20.GL_FLOAT, false, 0, fb)
        GLES20.glUniform4f(colorHandle, 1f, 0.75f, 0.1f, 1f)
        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, pts.size)

        if (pts.size == 2) {
            GLES20.glUniform4f(colorHandle, 1f, 0.85f, 0.2f, 1f)
            GLES20.glLineWidth(4f)
            GLES20.glDrawArrays(GLES20.GL_LINES, 0, 2)
        }

        GLES20.glDisableVertexAttribArray(positionHandle)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
    }

    private fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val v = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val f = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        return GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, v)
            GLES20.glAttachShader(it, f)
            GLES20.glLinkProgram(it)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] == 0) {
                val log = GLES20.glGetProgramInfoLog(it)
                GLES20.glDeleteProgram(it)
                throw RuntimeException("Program link failed: $log")
            }
        }
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, shaderCode)
            GLES20.glCompileShader(it)
            val compileStatus = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, compileStatus, 0)
            if (compileStatus[0] == 0) {
                val log = GLES20.glGetShaderInfoLog(it)
                GLES20.glDeleteShader(it)
                throw RuntimeException("Shader compile failed: $log")
            }
        }
    }
}
