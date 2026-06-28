    /**
     * Streaming ASCII parser to handle large ASCII files without loading entire file into memory.
     */
    private fun parseAsciiStreaming(context: Context, uri: Uri): STLModel {
        val resolver = context.contentResolver
        
        var triangleCount = 0
        var minX = Float.MAX_VALUE
        var minY = Float.MAX_VALUE
        var minZ = Float.MAX_VALUE
        var maxX = -Float.MAX_VALUE
        var maxY = -Float.MAX_VALUE
        var maxZ = -Float.MAX_VALUE

        // نقلنا المتغير للأعلى، واستخدمنا حجم افتراضي مبدئي لعدم معرفة العدد مسبقاً في ملفات ASCII
        val vertexList = ArrayList<Float>(1_000_000)
        val normalList = ArrayList<Float>(1_000_000)

        var curNx = 0f
        var curNy = 0f
        var curNz = 0f

        resolver.openInputStream(uri)?.use { inputStream ->
            val reader = BufferedInputStream(inputStream).bufferedReader()
            var vertexCountInTriangle = 0

            reader.forEachLine { originalLine ->
                val line = originalLine.trim().lowercase()
                
                if (line.startsWith("facet normal")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 5) {
                        curNx = parts[2].toFloatOrNull() ?: 0f
                        curNy = parts[3].toFloatOrNull() ?: 0f
                        curNz = parts[4].toFloatOrNull() ?: 0f
                    }
                    vertexCountInTriangle = 0
                } else if (line.startsWith("vertex")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size >= 4) {
                        val x = parts[1].toFloatOrNull() ?: 0f
                        val y = parts[2].toFloatOrNull() ?: 0f
                        val z = parts[3].toFloatOrNull() ?: 0f

                        vertexList.add(x)
                        vertexList.add(y)
                        vertexList.add(z)

                        normalList.add(curNx)
                        normalList.add(curNy)
                        normalList.add(curNz)

                        vertexCountInTriangle++

                        // تحديث الحدود الإحداثية Bounds
                        if (x < minX) minX = x
                        if (y < minY) minY = y
                        if (z < minZ) minZ = z
                        if (x > maxX) maxX = x
                        if (y > maxY) maxY = y
                        if (z > maxZ) maxZ = z
                    }
                } else if (line.startsWith("endfacet")) {
                    if (vertexCountInTriangle == 3) {
                        triangleCount++
                    } else {
                        // تنظيف آخر مثلث إذا كان تالفاً وغير مكتمل الأضلاع
                        repeat(vertexCountInTriangle * 3) {
                            if (vertexList.isNotEmpty()) vertexList.removeAt(vertexList.size - 1)
                            if (normalList.isNotEmpty()) normalList.removeAt(normalList.size - 1)
                        }
                    }
                }
            }
        } ?: throw STLParseException("تعذر قراءة ملف ASCII")

        if (triangleCount == 0) {
            throw STLParseException("ملف ASCII لا يحتوي على أي مجسمات أو مثلثات صالحة")
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
