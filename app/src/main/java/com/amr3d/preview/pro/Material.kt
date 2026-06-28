package com.amr3d.preview.pro

enum class Material(val color: FloatArray) {
    PLA(floatArrayOf(0.2f, 0.6f, 1.0f)),
    METAL(floatArrayOf(0.7f, 0.7f, 0.8f)),
    WOOD(floatArrayOf(0.55f, 0.35f, 0.15f)),
    MARBLE(floatArrayOf(0.9f, 0.9f, 0.85f)),
    BRONZE(floatArrayOf(0.8f, 0.5f, 0.2f)),
    CARBON(floatArrayOf(0.1f, 0.1f, 0.12f)),
    RESIN(floatArrayOf(0.8f, 0.9f, 1.0f))
}