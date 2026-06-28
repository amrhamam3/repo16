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
                vec3 woodLight = vec3(0.76, 0.52, 0.22); vec3 woodDark  = vec3(0.42, 0.24, 0.08);
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

            vec3 ambient  = finalColor * ambientStr;
            float diff    = max(dot(normal, lightDir), 0.0);
            vec3 diffuse  = finalColor * diff * 0.75;
            vec3 reflDir  = reflect(-