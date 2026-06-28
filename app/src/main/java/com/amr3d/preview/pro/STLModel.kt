package com.amr3d.preview.pro

data class STLModel(
    val vertices: FloatArray,
    val normals: FloatArray,
    val triangleCount: Int,
    val minBounds: FloatArray,
    val maxBounds: FloatArray,
    val isWatertightHint: Boolean
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is STLModel) return false
        return triangleCount == other.triangleCount &&
                vertices.contentEquals(other.vertices)
    }

    override fun hashCode(): Int {
        return 31 * triangleCount + vertices.contentHashCode()
    }
}
