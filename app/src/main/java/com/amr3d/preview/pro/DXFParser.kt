package com.amr3d.preview.pro

import android.content.Context
import android.net.Uri
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

object DXFParser {

    private data class DxfPair(val code: Int, val value: String)

    fun parse(context: Context, uri: Uri): STLModel {
        val text = context.contentResolver.openInputStream(uri)?.use {
            it.bufferedReader(Charsets.ISO_8859_1).readText()
        } ?: throw STLParseException("تعذر فتح ملف DXF")

        val rawLines = text.lines()
        val pairs = mutableListOf<DxfPair>()
        var idx = 0
        while (idx < rawLines.size - 1) {
            val code = rawLines[idx].trim().toIntOrNull()
            val value = rawLines[idx + 1].trim()
            if (code != null) pairs.add(DxfPair(code, value))
            idx += 2
        }

        // إيجاد الـ ENTITIES section
        var entStart = -1
        var entEnd = pairs.size
        for (k in pairs.indices) {
            if (pairs[k].code == 2 && pairs[k].value == "ENTITIES") entStart = k
            if (entStart > 0 && pairs[k].code == 0 && pairs[k].value == "ENDSEC" && k > entStart) {
                entEnd = k
                break
            }
        }
        if (entStart < 0) throw STLParseException("لم يتم العثور على قسم ENTITIES")

        val vertices = mutableListOf<Float>()
        val normals = mutableListOf<Float>()
        var minX = Float.MAX_VALUE; var maxX = -Float.MAX_VALUE
        var minY = Float.MAX_VALUE; var maxY = -Float.MAX_VALUE

        var pos = entStart
        while (pos < entEnd) {
            val pair = pairs[pos]
            when {
                pair.code == 0 && pair.value == "LINE" -> {
                    pos++
                    var x1 = 0f; var y1 = 0f; var x2 = 0f; var y2 = 0f
                    while (pos < entEnd && pairs[pos].code != 0) {
                        when (pairs[pos].code) {
                            10 -> x1 = pairs[pos].value.toFloatOrNull() ?: 0f
                            20 -> y1 = pairs[pos].value.toFloatOrNull() ?: 0f
                            11 -> x2 = pairs[pos].value.toFloatOrNull() ?: 0f
                            21 -> y2 = pairs[pos].value.toFloatOrNull() ?: 0f
                        }
                        pos++
                    }
                    addLine(vertices, normals, x1, y1, x2, y2)
                    minX = minOf(minX, x1, x2); maxX = maxOf(maxX, x1, x2)
                    minY = minOf(minY, y1, y2); maxY = maxOf(maxY, y1, y2)
                }

                pair.code == 0 && pair.value == "LWPOLYLINE" -> {
                    pos++
                    val pts = mutableListOf<Pair<Float, Float>>()
                    var closed = false
                    var cx = 0f; var cy = 0f; var hasX = false
                    while (pos < entEnd && pairs[pos].code != 0) {
                        when (pairs[pos].code) {
                            70 -> closed = (pairs[pos].value.trim().toIntOrNull() ?: 0) and 1 != 0
                            10 -> { cx = pairs[pos].value.toFloatOrNull() ?: 0f; hasX = true }
                            20 -> {
                                cy = pairs[pos].value.toFloatOrNull() ?: 0f
                                if (hasX) { pts.add(Pair(cx, cy)); hasX = false }
                            }
                        }
                        pos++
                    }
                    for (k in 0 until pts.size - 1) {
                        addLine(vertices, normals, pts[k].first, pts[k].second, pts[k+1].first, pts[k+1].second)
                        minX = minOf(minX, pts[k].first, pts[k+1].first)
                        maxX = maxOf(maxX, pts[k].first, pts[k+1].first)
                        minY = minOf(minY, pts[k].second, pts[k+1].second)
                        maxY = maxOf(maxY, pts[k].second, pts[k+1].second)
                    }
                    if (closed && pts.size > 1) {
                        addLine(vertices, normals, pts.last().first, pts.last().second, pts.first().first, pts.first().second)
                    }
                }

                pair.code == 0 && pair.value == "POLYLINE" -> {
                    pos++
                    var closed = false
                    val pts = mutableListOf<Pair<Float, Float>>()
                    while (pos < entEnd && !(pairs[pos].code == 0 && pairs[pos].value == "SEQEND")) {
                        if (pairs[pos].code == 70) {
                            closed = (pairs[pos].value.trim().toIntOrNull() ?: 0) and 1 != 0
                            pos++
                        } else if (pairs[pos].code == 0 && pairs[pos].value == "VERTEX") {
                            pos++
                            var vx = 0f; var vy = 0f
                            while (pos < entEnd && pairs[pos].code != 0) {
                                when (pairs[pos].code) {
                                    10 -> vx = pairs[pos].value.toFloatOrNull() ?: 0f
                                    20 -> vy = pairs[pos].value.toFloatOrNull() ?: 0f
                                }
                                pos++
                            }
                            pts.add(Pair(vx, vy))
                        } else {
                            pos++
                        }
                    }
                    for (k in 0 until pts.size - 1) {
                        addLine(vertices, normals, pts[k].first, pts[k].second, pts[k+1].first, pts[k+1].second)
                        minX = minOf(minX, pts[k].first, pts[k+1].first)
                        maxX = maxOf(maxX, pts[k].first, pts[k+1].first)
                        minY = minOf(minY, pts[k].second, pts[k+1].second)
                        maxY = maxOf(maxY, pts[k].second, pts[k+1].second)
                    }
                    if (closed && pts.size > 1) {
                        addLine(vertices, normals, pts.last().first, pts.last().second, pts.first().first, pts.first().second)
                    }
                    // تخطي SEQEND
                    if (pos < entEnd) pos++
                }

                pair.code == 0 && pair.value == "CIRCLE" -> {
                    pos++
                    var cx = 0f; var cy = 0f; var r = 0f
                    while (pos < entEnd && pairs[pos].code != 0) {
                        when (pairs[pos].code) {
                            10 -> cx = pairs[pos].value.toFloatOrNull() ?: 0f
                            20 -> cy = pairs[pos].value.toFloatOrNull() ?: 0f
                            40 -> r = pairs[pos].value.toFloatOrNull() ?: 0f
                        }
                        pos++
                    }
                    if (r > 0f) {
                        addArc(vertices, normals, cx, cy, r, 0f, 360f)
                        minX = minOf(minX, cx - r); maxX = maxOf(maxX, cx + r)
                        minY = minOf(minY, cy - r); maxY = maxOf(maxY, cy + r)
                    }
                }

                pair.code == 0 && pair.value == "ARC" -> {
                    pos++
                    var cx = 0f; var cy = 0f; var r = 0f; var startA = 0f; var endA = 360f
                    while (pos < entEnd && pairs[pos].code != 0) {
                        when (pairs[pos].code) {
                            10 -> cx = pairs[pos].value.toFloatOrNull() ?: 0f
                            20 -> cy = pairs[pos].value.toFloatOrNull() ?: 0f
                            40 -> r = pairs[pos].value.toFloatOrNull() ?: 0f
                            50 -> startA = pairs[pos].value.toFloatOrNull() ?: 0f
                            51 -> endA = pairs[pos].value.toFloatOrNull() ?: 360f
                        }
                        pos++
                    }
                    if (r > 0f) {
                        addArc(vertices, normals, cx, cy, r, startA, endA)
                        minX = minOf(minX, cx - r); maxX = maxOf(maxX, cx + r)
                        minY = minOf(minY, cy - r); maxY = maxOf(maxY, cy + r)
                    }
                }

                else -> pos++
            }
        }

        if (vertices.isEmpty()) throw STLParseException("لم يتم العثور على عناصر قابلة للعرض في ملف DXF")

        val vArray = vertices.toFloatArray()
        val nArray = normals.toFloatArray()
        return STLModel(
            vertices = vArray,
            normals = nArray,
            triangleCount = vArray.size / 9,
            minBounds = floatArrayOf(minX, minY, -1f),
            maxBounds = floatArrayOf(maxX, maxY, 1f),
            isWatertightHint = false
        )
    }

    private fun addLine(verts: MutableList<Float>, norms: MutableList<Float>,
                        x1: Float, y1: Float, x2: Float, y2: Float) {
        val dx = x2 - x1; val dy = y2 - y1
        val len = sqrt(dx * dx + dy * dy).takeIf { it > 0f } ?: return
        val thickness = maxOf(len * 0.015f, 1f)
        val nx = -dy / len * thickness; val ny = dx / len * thickness
        verts.addAll(listOf(x1+nx, y1+ny, 0f, x1-nx, y1-ny, 0f, x2+nx, y2+ny, 0f))
        repeat(3) { norms.addAll(listOf(0f, 0f, 1f)) }
        verts.addAll(listOf(x2+nx, y2+ny, 0f, x1-nx, y1-ny, 0f, x2-nx, y2-ny, 0f))
        repeat(3) { norms.addAll(listOf(0f, 0f, 1f)) }
    }

    private fun addArc(verts: MutableList<Float>, norms: MutableList<Float>,
                       cx: Float, cy: Float, r: Float, startDeg: Float, endDeg: Float) {
        val segments = 64
        var end = endDeg
        if (end <= startDeg) end += 360f
        val totalAngle = end - startDeg
        val thickness = maxOf(r * 0.015f, 1f)
        val r1 = r - thickness; val r2 = r + thickness
        for (s in 0 until segments) {
            val a1 = Math.toRadians((startDeg + s * totalAngle / segments).toDouble()).toFloat()
            val a2 = Math.toRadians((startDeg + (s + 1) * totalAngle / segments).toDouble()).toFloat()
            verts.addAll(listOf(cx+r1*cos(a1), cy+r1*sin(a1), 0f, cx+r2*cos(a1), cy+r2*sin(a1), 0f, cx+r1*cos(a2), cy+r1*sin(a2), 0f))
            repeat(3) { norms.addAll(listOf(0f, 0f, 1f)) }
            verts.addAll(listOf(cx+r2*cos(a1), cy+r2*sin(a1), 0f, cx+r2*cos(a2), cy+r2*sin(a2), 0f, cx+r1*cos(a2), cy+r1*sin(a2), 0f))
            repeat(3) { norms.addAll(listOf(0f, 0f, 1f)) }
        }
    }
}
