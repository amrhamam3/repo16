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
    val measurementPoints = CopyOnWriteArrayList<FloatArray>()
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