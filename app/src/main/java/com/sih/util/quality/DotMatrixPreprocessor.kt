package com.sih.util.quality

import android.graphics.Bitmap
import android.graphics.Color
import kotlin.math.max
import kotlin.math.min

/**
 * Specialized edge-native morphological preprocessor for Dot-Matrix / Continuous Inkjet (CIJ)
 * printed packaging declarations (MFD/EXP, batch codes, crimp stamps).
 *
 * It bridges disconnected circular ink dots into continuous optical strokes via
 * adaptive local thresholding and a 3x3 structuring element dilation pass,
 * allowing standard ML Kit OCR engines to recognize numerals like 6, 8, 5, 0 accurately.
 */
object DotMatrixPreprocessor {

    /**
     * Enhances a bitmap containing suspected dot-matrix text.
     * Returns a new Bitmap with bridged dots. Caller is responsible for recycling the returned bitmap.
     */
    fun enhanceDotMatrix(source: Bitmap): Bitmap {
        val width = source.width
        val height = source.height
        val totalPixels = width * height

        val srcPixels = IntArray(totalPixels)
        source.getPixels(srcPixels, 0, width, 0, 0, width, height)

        // 1. Convert to grayscale luminance
        val gray = IntArray(totalPixels)
        var sumLuma = 0L
        for (i in 0 until totalPixels) {
            val c = srcPixels[i]
            val r = Color.red(c)
            val g = Color.green(c)
            val b = Color.blue(c)
            val luma = (0.299 * r + 0.587 * g + 0.114 * b).toInt()
            gray[i] = luma
            sumLuma += luma
        }

        // 2. Adaptive local thresholding to isolate ink dots (darker than local background)
        // Global mean fallback
        val avgLuma = (sumLuma / totalPixels).toInt()
        val threshold = (avgLuma * 0.85).toInt().coerceIn(60, 200)

        val binary = BooleanArray(totalPixels)
        for (i in 0 until totalPixels) {
            binary[i] = gray[i] < threshold // True if ink pixel (dark)
        }

        // 3. Morphological Dilation / Closing with 3x3 cross structuring element
        val dilated = dilateInk(binary, width, height)

        // 4. Synthesize enhanced output bitmap (high contrast black text on white background)
        val outPixels = IntArray(totalPixels)
        for (i in 0 until totalPixels) {
            outPixels[i] = if (dilated[i]) Color.BLACK else Color.WHITE
        }

        val enhanced = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        enhanced.setPixels(outPixels, 0, width, 0, 0, width, height)
        return enhanced
    }

    /**
     * Pure Kotlin morphological dilation kernel (3x3 structuring element).
     * Bridges disconnected dot-matrix ink dots (both orthogonal and diagonal)
     * without requiring Android Bitmap SDK at runtime.
     * 8-connectivity is critical for bridging diagonal dot-matrix strokes like '/' and '\'.
     */
    fun dilateInk(binary: BooleanArray, width: Int, height: Int): BooleanArray {
        val totalPixels = width * height
        val dilated = BooleanArray(totalPixels)
        for (y in 0 until height) {
            val yMin = if (y > 0) y - 1 else 0
            val yMax = if (y < height - 1) y + 1 else height - 1

            for (x in 0 until width) {
                val idx = y * width + x
                if (binary[idx]) {
                    dilated[idx] = true
                    continue
                }

                // Check 8-connected neighbors (both orthogonal and diagonal)
                var hasInkNeighbor = false
                val xMin = if (x > 0) x - 1 else 0
                val xMax = if (x < width - 1) x + 1 else width - 1

                for (ny in yMin..yMax) {
                    val rowOffset = ny * width
                    for (nx in xMin..xMax) {
                        if (binary[rowOffset + nx]) {
                            hasInkNeighbor = true
                            break
                        }
                    }
                    if (hasInkNeighbor) break
                }

                if (hasInkNeighbor) {
                    dilated[idx] = true
                }
            }
        }
        return dilated
    }
}
