package com.kosh.android.util

import com.kosh.android.data.model.FilterMode

/**
 * 4x5 colour matrices (Android's ColorMatrix layout, row-major: R', G', B', A' each as
 * a*R + b*G + c*B + d*A + e with e in 0..255). The same arrays drive the on-screen preview and the exported PDF,
 * so what the user previews is what they get.
 */
object FilterMatrices {

    /** Null means "leave the pixels alone". */
    fun forMode(mode: FilterMode): FloatArray? = when (mode) {
        FilterMode.ORIGINAL -> null
        FilterMode.COLOR -> contrast(1.12f, 4f)
        FilterMode.DOCUMENT -> multiply(saturation(0.55f), contrast(1.55f, 12f))
        FilterMode.GRAYSCALE -> saturation(0f)
        FilterMode.BLACK_AND_WHITE -> multiply(saturation(0f), contrast(9f, -10f))
        FilterMode.LIGHTEN -> contrast(0.92f, 38f)
        FilterMode.VIVID -> multiply(saturation(1.6f), contrast(1.1f, 0f))
        FilterMode.SEPIA -> floatArrayOf(
            0.393f, 0.769f, 0.189f, 0f, 0f,
            0.349f, 0.686f, 0.168f, 0f, 0f,
            0.272f, 0.534f, 0.131f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
        FilterMode.INVERT -> floatArrayOf(
            -1f, 0f, 0f, 0f, 255f,
            0f, -1f, 0f, 0f, 255f,
            0f, 0f, -1f, 0f, 255f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** out = c * in + 128 * (1 - c) + brightness, so 128 stays put and the rest is pushed apart. */
    fun contrast(c: Float, brightness: Float = 0f): FloatArray {
        val t = 128f * (1f - c) + brightness
        return floatArrayOf(
            c, 0f, 0f, 0f, t,
            0f, c, 0f, 0f, t,
            0f, 0f, c, 0f, t,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    fun saturation(s: Float): FloatArray {
        val r = 0.2126f; val g = 0.7152f; val b = 0.0722f
        val ir = (1 - s) * r; val ig = (1 - s) * g; val ib = (1 - s) * b
        return floatArrayOf(
            ir + s, ig, ib, 0f, 0f,
            ir, ig + s, ib, 0f, 0f,
            ir, ig, ib + s, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        )
    }

    /** Returns the matrix that applies [first] and then [second]. */
    fun multiply(first: FloatArray, second: FloatArray): FloatArray {
        val out = FloatArray(20)
        for (row in 0 until 4) {
            for (col in 0 until 5) {
                var v = 0f
                for (k in 0 until 4) v += second[row * 5 + k] * first[k * 5 + col]
                if (col == 4) v += second[row * 5 + 4]
                out[row * 5 + col] = v
            }
        }
        return out
    }

    /** Applies a matrix to one RGB pixel (0..255 per channel), clamping the result. Used by tests. */
    fun apply(m: FloatArray, r: Int, g: Int, b: Int): Triple<Int, Int, Int> {
        fun ch(row: Int) = (m[row * 5] * r + m[row * 5 + 1] * g + m[row * 5 + 2] * b + m[row * 5 + 4]).toInt().coerceIn(0, 255)
        return Triple(ch(0), ch(1), ch(2))
    }
}
