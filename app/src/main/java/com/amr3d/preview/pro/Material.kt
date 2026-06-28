package com.amr3d.preview.pro

enum class Material(val color: FloatArray) {
    PLA(floatArrayOf(0.2f, 0.6f, 1.0f)), // 0 - بلاستيك أزرق
    METAL(floatArrayOf(0.7f, 0.7f, 0.8f)), // 1 - معدن نيون فضي
    WOOD(floatArrayOf(0.55f, 0.35f, 0.15f)), // 2 - خشب كلاسيك بني
    MARBLE(floatArrayOf(0.9f, 0.9f, 0.85f)), // 3 - رخام فاخر أبيض
    BRONZE(floatArrayOf(0.8f, 0.5f, 0.2f)), // 4 - برونز مأكسد
    CARBON(floatArrayOf(0.1f, 0.1f, 0.12f)), // 5 - ألياف كربون أسود
    RESIN(floatArrayOf(0.8f