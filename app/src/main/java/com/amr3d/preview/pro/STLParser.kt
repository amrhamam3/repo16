package com.amr3d.preview.pro

import android.content.Context
import android.net.Uri
import java.io.BufferedInputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Represents the parsed geometry of an STL file.
 * vertices: flat array of x,y,z per vertex
 * normals: flat array of nx,ny,nz per vertex (one normal per triangle, repeated for each of its 3 vertices)
 * triangleCount: number of triangles
 */
data class STLModel(
    val vertices: FloatArray,
    val normals: FloatArray,
    val triangleCount: Int,
    val minBounds: FloatArray, // [minX, minY, minZ]
    val maxBounds: FloatArray, // [maxX, maxY, maxZ]
    val isWatertightHint: Boolean // basic heuristic, not a full manifold check
)

class STLParseException(message: String) : Exception(message)

object STLParser {

    private const val MAX_FILE_SIZE = 2_000_000_000L // 2 GB limit
    private const val CHUNK_SIZE = 4_000_000 // Read 4MB chunks

    /**
     * Entry point: detects ASCII vs Binary STL and parses accordingly.
     * Uses streaming for large files to avoid OutOfMemoryError.
     */
    fun parse(context: Context, uri: Uri): STLModel {
        val resolver = context.contentResolver

        val fileSize: Long = resolver.query(uri, arrayOf(android.provider.OpenableColumns.SIZE),
            null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val idx = cursor.getColumnIndex(android.provider.OpenableColumns.SIZE)
                if (idx >= 0 &&!cursor.isNull(idx)) cursor.getLong(idx) else -1L
            } else -1L
        }?: -1L

        val actualSize = if (fileSize > 0) fileSize else
            resolver.openInputStream(uri)?.use { stream ->
                var count = 0L; val buf = ByteArray(8192)
                var n = stream.read(buf)
                while (n >= 0) { count += n; n = stream.read(buf) }
                count
            }?: throw STLParseException("تعذر فتح الملف")

        if (actualSize == 0L) {
            throw STLParseException("الملف فارغ")
        }

        if (actualSize > MAX_FILE_SIZE) {
            throw STLParseException("حجم الملف كبير جداً (أكثر من 2 GB). الملف غير مدعوم")
        }

        val headerBytes = ByteArray(minOf(512, actualSize.toInt()))
        resolver.openInputStream(uri)?.use { stream ->
            stream.read(headerBytes)
        }?: throw STLParseException("تعذر قراءة الملف")

        return if (isAsciiSTL(headerBytes, actualSize)) {
            parseAsciiStreaming(context, uri)
        } else {
            parseBinaryOptimized(context, uri, actualSize)
        }
    }

    private fun isAsciiSTL(headerBytes: ByteArray, fileSize: Long): Boolean {
        val header = String(headerBytes, Charsets.US_ASCII).trim()

        if (!header.lowercase().startsWith("solid")) {
            return false
        }

        if (fileSize >= 84) {
            try {
                val triCountFromHeader = ByteBuffer.wrap(headerBytes, 80, 4)
                   .order(ByteOrder.LITTLE_ENDIAN).int
                val expectedBinarySize = 84L + (triCountFromHeader.toLong() * 50L)
                if (expectedBinarySize == fileSize) {
                    return false
                }
            } catch (e: Exception) {
                // If parsing fails, assume ASCII
            }
        }

        val sample = String(headerBytes, Charsets.US_ASCII)
        return sample.contains("facet", ignoreCase = true)
    }

    private fun parseBinaryOptimized(context: Context, uri: Uri, fileSize: Long): STLModel {
        if (fileSize < 84) {
            throw STLParseException("ملف STL (Binary) تالف أو غير مكتمل")
        }

        val resolver = context.contentResolver
        val headerBuffer = ByteArray(84)

        resolver.openInputStream(uri)?.use { stream ->
            stream.read(headerBuffer)
        }?: throw STLParseException("تعذر قراءة الملف")

        val triangleCount = ByteBuffer.wrap(headerBuffer, 80, 4)
           .order(ByteOrder.LITTLE_ENDIAN).int

        val expectedSize = 84L + (triangleCount.toLong() * 50L)
        if (expectedSize > fileSize) {
            throw STLParseException(
                "عدد المثلثات في الملف ($triangleCount) لا يتطابق مع حجم الملف — الملف قد يكون تالفًا"
            )
        }
        if (triangleCount <= 0) {
            throw STLParseException("الملف لا يحتوي على أي مثلثات صالحة")
        }

        val vertices = FloatArray(triangleCount * 3 * 3)
        val normals = FloatArray(triangleCount * 3 * 3)

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        var vIdx = 0
        val triangleBytes = ByteArray(50)

        resolver.openInputStream(uri)?.use { stream ->
            stream.skip(84)

            for (t in 0 until triangleCount) {
                if (stream.read(triangleBytes)!= 50) {
                    throw STLParseException("ملف STL تالف - لا يمكن قراءة المثلث رقم $t")
                }

                val buffer = ByteBuffer.wrap(triangleBytes).order(ByteOrder.LITTLE_ENDIAN)

                val nx = buffer.float
                val ny = buffer.float
                val nz = buffer.float

                for (v in 0 until 3) {
                    val x = buffer.float
                    val y = buffer.float
                    val z = buffer.float

                    vertices[vIdx] = x
                    vertices[vIdx + 1] = y
                    vertices[vIdx + 2] = z

                    normals[vIdx] = nx
                    normals[vIdx + 1] = ny
                    normals[vIdx + 2] = nz

                    vIdx += 3

                    if (x < minX) minX = x
                    if (y < minY) minY = y
                    if (z < minZ) minZ = z
                    if (x > maxX) maxX = x
                    if (y > maxY) maxY = y
                    if (z > maxZ) maxZ = z
                }

                buffer.short
            }
        }?: throw STLParseException("تعذر قراءة الملف")

        return STLModel(
            vertices = vertices,
            normals = normals,
            triangleCount = triangleCount,
            minBounds = floatArrayOf(minX, minY, minZ),
            maxBounds = floatArrayOf(maxX, maxY, maxZ),
            isWatertightHint = (triangleCount % 2 == 0)
        )
    }

    private fun parseAsciiStreaming(context: Context, uri: Uri): STLModel {
        val resolver = context.contentResolver
        // ✅ التصحيح: شلت triangleCount من هنا لأنه مش متعرف لسه
        val vertexList = ArrayList<Float>()
        val normalList = ArrayList<Float>()

        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        var curNx = 0f
        var curNy = 0f
        var curNz = 0f
        var triangleCount = 0
        var vertsInCurrentFacet = 0

        resolver.openInputStream(uri)?.use { rawStream ->
            val bufferedStream = BufferedInputStream(rawStream, 8192)
            val reader = bufferedStream.bufferedReader()

            reader.use { lineReader ->
                lineReader.forEachLine { rawLine ->
                    val line = rawLine.trim()
                    when {
                        line.startsWith("facet normal", ignoreCase = true) -> {
                            val parts = line.split(Regex("\\s+"))
                            if (parts.size >= 5) {
                                try {
                                    curNx = parts[2].toFloat()
                                    curNy = parts[3].toFloat()
                                    curNz = parts[4].toFloat()
                                } catch (e: NumberFormatException) {
                                    curNx = 0f
                                    curNy = 0f
                                    curNz = 0f
                                }
                            }
                            vertsInCurrentFacet = 0
                        }
                        line.startsWith("vertex", ignoreCase = true) -> {
                            val parts = line.split(Regex("\\s+"))
                            if (parts.size >= 4) {
                                try {
                                    val x = parts[1].toFloat()
                                    val y = parts[2].toFloat()
                                    val z = parts[3].toFloat()

                                    vertexList.add(x)
                                    vertexList.add(y)
                                    vertexList.add(z)
                                    normalList.add(curNx)
                                    normalList.add(curNy)
                                    normalList.add(curNz)

                                    if (x < minX) minX = x
                                    if (y < minY) minY = y
                                    if (z < minZ) minZ = z
                                    if (x > maxX) maxX = x
                                    if (y > maxY) maxY = y
                                    if (z > maxZ) maxZ = z

                                    vertsInCurrentFacet++

                                } catch (e: NumberFormatException) {
                                    throw STLParseException("قيمة غير صالحة في الملف عند: $line")
                                }
                            }
                        }
                        line.startsWith("endfacet", ignoreCase = true) -> {
                            if (vertsInCurrentFacet == 3) {
                                triangleCount++
                            }
                        }
                    }
                }
            }
        }?: throw STLParseException("تعذر قراءة الملف")

        if (triangleCount == 0) {
            throw STLParseException("لم يتم العثور على أي مثلثات صالحة في ملف الـ ASCII STL")
        }

        return STLModel(
            vertices = vertexList.toFloatArray(),
            normals = normalList.toFloatArray(),
            triangleCount = triangleCount,
            minBounds = floatArrayOf(minX, minY, minZ),
            maxBounds = floatArrayOf(maxX, maxY, maxZ),
            isWatertightHint = (triangleCount % 2 == 0)
        )
    }
}
