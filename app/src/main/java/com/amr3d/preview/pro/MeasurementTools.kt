package com.amr3d.preview.pro

import kotlin.math.sqrt

/**
 * Display unit for measurements. STL files have no embedded unit — the raw numbers are
 * just floats — but the near-universal convention in CNC/3D-printing workflows is that
 * those numbers represent millimeters. We treat the raw file values as millimeters and
 * convert for display based on the user's selected unit.
 */
enum class MeasurementUnit(val label: String, val factorFromMm: Float) {
    MM("مم", 1f),
    CM("سم", 0.1f),
    INCH("بوصة", 1f / 25.4f)
}

/**
 * Holds computed measurement/inspection results for a loaded STL model.
 */
data class ModelInspectionReport(
    val triangleCount: Int,
    val vertexCount: Int,
    val width: Float,   // X extent, in the requested display unit
    val depth: Float,   // Y extent, in the requested display unit
    val height: Float,  // Z extent, in the requested display unit
    val approxVolume: Float,     // signed-volume method, valid for closed meshes
    val approxSurfaceArea: Float,
    val possiblyNotWatertight: Boolean,
    val unit: MeasurementUnit
)

object MeasurementTools {

    /**
     * Computes basic bounding-box dimensions, approximate volume (via the divergence/
     * signed-tetrahedron method — accurate only if the mesh is a closed/watertight solid),
     * and approximate surface area (sum of triangle areas). Raw STL coordinates are
     * assumed to be millimeters and converted to the requested display unit.
     */
    fun inspect(model: STLModel, unit: MeasurementUnit = MeasurementUnit.MM): ModelInspectionReport {
        val f = unit.factorFromMm
        val width = (model.maxBounds[0] - model.minBounds[0]) * f
        val depth = (model.maxBounds[1] - model.minBounds[1]) * f
        val height = (model.maxBounds[2] - model.minBounds[2]) * f

        var volumeSum = 0.0
        var areaSum = 0.0

        val v = model.vertices
        val safeLimit = v.size - 8 // التعديل لحماية الفهارس من الـ Crash

        var i = 0
        while (i < safeLimit) {
            val x1 = v[i];     val y1 = v[i + 1]; val z1 = v[i + 2]
            val x2 = v[i + 3]; val y2 = v[i + 4]; val z2 = v[i + 5]
            val x3 = v[i + 6]; val y3 = v[i + 7]; val z3 = v[i + 8]

            // حساب الحجم الموقّع الصافي للرباعي المكون مع نقطة الأصل
            volumeSum += signedTetraVolume(
                x1.toDouble(), y1.toDouble(), z1.toDouble(),
                x2.toDouble(), y2.toDouble(), z2.toDouble(),
                x3.toDouble(), y3.toDouble(), z3.toDouble()
            )

            areaSum += triangleArea(
                x1, y1, z1, x2, y2, z2, x3, y3, z3
            )

            i += 9
        }

        // إصلاح حساب الحجم الرياضي: أخذ القيمة المطلقة للمجموع الكلي للملف
        val volumeMm = abs(volumeSum).toFloat()
        val areaMm = areaSum.toFloat()
        
        // التحويل للوحدات المطلوبة بشكل تكعيبي ومربّع دقيق
        val volume = volumeMm * f * f * f
        val area = areaMm * f * f

        // تحسين كاشف الفجوات Heuristic: الفحص بمعدل تفاوت مرن لمنع التنبيهات الكاذبة في المجسمات الصغيرة جداً
        val possiblyNotWatertight = model.isWatertightHint || (volumeMm < 1e-4f && (width > 0.01f || depth > 0.01f || height > 0.01f))

        return ModelInspectionReport(
            triangleCount = model.triangleCount,
            vertexCount = model.vertices.size / 3,
            width = width,
            depth = depth,
            height = height,
            approxVolume = volume,
            approxSurfaceArea = area,
            possiblyNotWatertight = possiblyNotWatertight,
            unit = unit
        )
    }

    /**
     * الدالة الرياضية الدقيقة لحساب الحجم ثلاثي الأبعاد الموقّع
     */
    private fun signedTetraVolume(
        x1: Double, y1: Double, z1: Double,
        x2: Double, y2: Double, z2: Double,
        x3: Double, y3: Double, z3: Double
    ): Double {
        // المعادلة الرياضية الصحيحة والمحسنة عبر ضرب المصفوفة المحددة (Determinant) والقسمة على 6.0 إجمالياً
        return (x1 * y2 * z3 - x1 * y3 * z2 - x2 * y1 * z3 + x2 * y3 * z1 + x3 * y1 * z2 - x3 * y2 * z1) / 6.0
    }

    private fun triangleArea(
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float
    ): Float {
        val ux = x2 - x1; val uy = y2 - y1; val uz = z2 - z1
        val vx = x3 - x1; val vy = y3 - y1; val vz = z3 - z1

        val cx = uy * vz - uz * vy
        val cy = uz * vx - ux * vz
        val cz = ux * vy - uy * vx

        return 0.5f * sqrt(cx * cx + cy * cy + cz * cz)
    }

    private fun abs(d: Double): Double = if (d < 0) -d else d

    /**
     * Straight-line (Euclidean) distance between two points placed by the user.
     * Raw STL values are assumed to be millimeters; pass the desired display unit
     * to convert (e.g. MeasurementUnit.CM).
     */
    fun distanceBetween(p1: FloatArray, p2: FloatArray, unit: MeasurementUnit = MeasurementUnit.MM): Float {
        if (p1.size < 3 || p2.size < 3) return 0f // حماية أمنية لمنع الـ Crash في حال تمرير نقاط فارغة
        val dx = p2[0] - p1[0]
        val dy = p2[1] - p1[1]
        val dz = p2[2] - p1[2]
        val mm = sqrt(dx * dx + dy * dy + dz * dz)
        return mm * unit.factorFromMm
    }
}
