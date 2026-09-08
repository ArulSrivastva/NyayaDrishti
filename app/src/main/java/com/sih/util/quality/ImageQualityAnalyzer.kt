package com.sih.util.quality

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.Log
import com.sih.network.dto.DefectRegion
import com.sih.network.dto.ImageQualityResult
import com.sih.network.dto.QualityStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

object ImageQualityAnalyzer {

    private const val TAG = "ImageQualityAnalyzer"

    suspend fun analyze(context: Context, imageUri: Uri): ImageQualityResult = withContext(Dispatchers.Default) {
        try {
            var input: InputStream? = context.contentResolver.openInputStream(imageUri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(input, null, options)
            input?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight

            if (origWidth <= 0 || origHeight <= 0) {
                return@withContext ImageQualityResult(
                    qualityScore = 0.0f,
                    overallStatus = QualityStatus.REJECT,
                    rejectionReason = "Invalid or unreadable image file."
                )
            }

            // Downscale to ~480px for fast sub-50ms CPU analysis
            val sampleSize = calculateInSampleSize(origWidth, origHeight, 480, 480)
            val decodeOptions = BitmapFactory.Options().apply { inSampleSize = sampleSize }
            input = context.contentResolver.openInputStream(imageUri)
            val bitmap = BitmapFactory.decodeStream(input, null, decodeOptions)
            input?.close()

            if (bitmap == null) {
                return@withContext ImageQualityResult(
                    qualityScore = 0.0f,
                    overallStatus = QualityStatus.REJECT,
                    rejectionReason = "Failed to decode image bitmap."
                )
            }

            try {
                return@withContext analyzeBitmap(bitmap, origWidth, origHeight)
            } finally {
                bitmap.recycle()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error analyzing image quality for $imageUri", e)
            return@withContext ImageQualityResult(
                qualityScore = 0.50f,
                overallStatus = QualityStatus.WARNING,
                rejectionReason = "Image quality could not be automatically validated (${e.message ?: "Unknown error"}). Manual review required."
            )
        }
    }

    fun analyzeBitmap(bitmap: Bitmap, originalWidth: Int = bitmap.width, originalHeight: Int = bitmap.height): ImageQualityResult {
        val width = bitmap.width
        val height = bitmap.height
        val totalPixels = width * height

        // 1. Resolution Check (Continuous scaling based on dimensions)
        val minDim = min(originalWidth, originalHeight)
        val origTotalPx = originalWidth * originalHeight
        val (resScore, resStatus) = when {
            origTotalPx < 320 * 240 -> Pair(0.35f, QualityStatus.REJECT)
            minDim < 540 -> Pair((0.65f + (minDim / 540f) * 0.12f).coerceIn(0.50f, 0.77f), QualityStatus.WARNING)
            minDim >= 1080 -> Pair((0.92f + (minDim - 1080) / 18000f).coerceAtMost(0.99f), QualityStatus.ACCEPT)
            else -> Pair(0.82f + ((minDim - 540f) / 540f) * 0.10f, QualityStatus.ACCEPT)
        }

        // 2. Grayscale, Luminance & True Specular Glare Analysis
        val pixels = IntArray(totalPixels)
        bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

        var saturatedGlarePixels = 0
        var minLuma = 255
        var maxLuma = 0
        val gray = FloatArray(totalPixels)

        // 4x4 Grid for localized defect bounding boxes
        val gridCols = 4
        val gridRows = 4
        val cellW = width / gridCols
        val cellH = height / gridRows
        val gridGlareCounts = IntArray(gridCols * gridRows)

        for (y in 0 until height) {
            val rowOffset = y * width
            val gy = (y / cellH).coerceIn(0, gridRows - 1)
            for (x in 0 until width) {
                val idx = rowOffset + x
                val c = pixels[idx]
                val r = Color.red(c)
                val g = Color.green(c)
                val b = Color.blue(c)

                // Luminance
                val luma = (0.299f * r + 0.587f * g + 0.114f * b).toInt()
                gray[idx] = luma.toFloat()

                if (luma < minLuma) minLuma = luma
                if (luma > maxLuma) maxLuma = luma

                // True specular glare: washed-out hotspot where color channels are saturated >= 252
                // (excludes standard white packaging like nutrition table/paper which averages 235-248)
                if (luma >= 252 && r >= 250 && g >= 250 && b >= 250) {
                    saturatedGlarePixels++
                    val gx = (x / cellW).coerceIn(0, gridCols - 1)
                    gridGlareCounts[gy * gridCols + gx]++
                }
            }
        }

        val glareRatio = saturatedGlarePixels.toFloat() / totalPixels.toFloat()
        val defectRegions = mutableListOf<DefectRegion>()

        // Check which grid cells have high localized glare (> 28% of a specific quadrant)
        val cellArea = cellW * cellH
        for (gy in 0 until gridRows) {
            for (gx in 0 until gridCols) {
                val count = gridGlareCounts[gy * gridCols + gx]
                val cellGlareRatio = count.toFloat() / cellArea.toFloat()
                if (cellGlareRatio > 0.28f) { // Severe localized hotspot occluding text in this quadrant
                    val x1 = (gx * cellW.toFloat() / width) * 100f
                    val y1 = (gy * cellH.toFloat() / height) * 100f
                    val x2 = ((gx + 1) * cellW.toFloat() / width) * 100f
                    val y2 = ((gy + 1) * cellH.toFloat() / height) * 100f
                    val regionName = when {
                        gy == 0 -> "Top"
                        gy == gridRows - 1 -> "Bottom"
                        else -> "Center"
                    } + " " + when {
                        gx == 0 -> "Left"
                        gx == gridCols - 1 -> "Right"
                        else -> "Center"
                    }
                    defectRegions.add(
                        DefectRegion(
                            type = "GLARE",
                            bbox = listOf(x1, y1, x2, y2),
                            description = "Localized specular hotspot in $regionName quadrant"
                        )
                    )
                }
            }
        }

        // Continuous glare score based on actual specular reflection ratio
        val glareScore = (1.0f - (glareRatio * 3.2f)).coerceIn(0.20f, 0.99f)
        val glareStatus = when {
            glareRatio > 0.16f -> QualityStatus.REJECT
            glareRatio > 0.06f || defectRegions.isNotEmpty() -> QualityStatus.WARNING
            else -> QualityStatus.ACCEPT
        }

        // 3. Blur Detection using Discrete Laplacian Operator
        var lapSum = 0.0
        var lapSqSum = 0.0
        var lapCount = 0

        for (y in 1 until height - 1) {
            val rowOffset = y * width
            for (x in 1 until width - 1) {
                val center = gray[rowOffset + x]
                val left = gray[rowOffset + x - 1]
                val right = gray[rowOffset + x + 1]
                val up = gray[rowOffset - width + x]
                val down = gray[rowOffset + width + x]

                val lap = (left + right + up + down - 4f * center)
                lapSum += lap
                lapSqSum += (lap * lap)
                lapCount++
            }
        }

        val lapMean = if (lapCount > 0) lapSum / lapCount else 0.0
        val lapVariance = if (lapCount > 0) (lapSqSum / lapCount) - (lapMean * lapMean) else 0.0

        // Adjusted variance thresholds for 480px sampled bitmaps
        val blurStatus = when {
            lapVariance < 35.0 -> QualityStatus.REJECT
            lapVariance < 75.0 -> QualityStatus.WARNING
            else -> QualityStatus.ACCEPT
        }

        val blurScore = when {
            lapVariance < 35.0 -> (0.20f + (lapVariance / 35.0 * 0.25).toFloat()).coerceIn(0.15f, 0.45f)
            lapVariance < 75.0 -> (0.55f + ((lapVariance - 35.0) / 40.0 * 0.20).toFloat()).coerceIn(0.55f, 0.75f)
            else -> {
                val norm = (lapVariance - 75.0) / (lapVariance + 100.0)
                (0.78f + norm * 0.20f).toFloat().coerceIn(0.78f, 0.98f)
            }
        }

        // 4. Text Contrast / Dynamic Range
        val dynamicRange = maxLuma - minLuma
        val (textVisScore, textVisStatus) = when {
            dynamicRange < 40 -> Pair(0.30f, QualityStatus.REJECT)
            dynamicRange < 85 -> Pair(0.65f + (dynamicRange / 85f) * 0.10f, QualityStatus.WARNING)
            else -> {
                val norm = (dynamicRange - 85f) / 170f
                Pair(0.84f + norm * 0.14f, QualityStatus.ACCEPT)
            }
        }

        // 5. Determine Overall Quality & Rejection Reason
        var overallStatus = QualityStatus.ACCEPT
        var rejectionReason: String? = null

        if (blurStatus == QualityStatus.REJECT) {
            overallStatus = QualityStatus.REJECT
            rejectionReason = "Image is too blurry for legal text verification. Camera motion or loss of focus."
        } else if (glareStatus == QualityStatus.REJECT) {
            overallStatus = QualityStatus.REJECT
            rejectionReason = "Severe specular reflection/glare occluding packaging declarations."
        } else if (resStatus == QualityStatus.REJECT) {
            overallStatus = QualityStatus.REJECT
            rejectionReason = "Image resolution is too low to verify mandatory font height."
        } else if (textVisStatus == QualityStatus.REJECT) {
            overallStatus = QualityStatus.REJECT
            rejectionReason = "Flat surface or low contrast detected. No legible text visible."
        } else if (blurStatus == QualityStatus.WARNING || glareStatus == QualityStatus.WARNING || resStatus == QualityStatus.WARNING) {
            overallStatus = QualityStatus.WARNING
            rejectionReason = when {
                glareStatus == QualityStatus.WARNING -> "Localized reflection hotspot detected on packaging panel."
                blurStatus == QualityStatus.WARNING -> "Mild soft-focus detected. Hold camera steady."
                else -> "Sub-optimal lighting or resolution."
            }
        }

        // Weighted continuous overall quality index (50% weight on Blur)
        var overallQualityScore = (
            0.50f * blurScore +
            0.25f * glareScore +
            0.15f * resScore +
            0.10f * textVisScore
        ).coerceIn(0.15f, 0.99f)

        // Severely cap quality score if image is rejected for blur, glare, or low contrast
        if (overallStatus == QualityStatus.REJECT) {
            overallQualityScore = minOf(overallQualityScore, 0.25f)
        } else if (overallStatus == QualityStatus.WARNING) {
            overallQualityScore = minOf(overallQualityScore, 0.65f)
        }

        return ImageQualityResult(
            qualityScore = overallQualityScore,
            blurScore = blurScore,
            glareScore = glareScore,
            resolutionScore = resScore,
            orientationScore = 1.0f,
            textVisibilityScore = textVisScore,
            blurStatus = blurStatus,
            glareStatus = glareStatus,
            resolutionStatus = resStatus,
            orientationStatus = QualityStatus.ACCEPT,
            textVisibilityStatus = textVisStatus,
            overallStatus = overallStatus,
            rejectionReason = rejectionReason,
            defectRegions = defectRegions
        )
    }

    suspend fun analyzePreCapture(context: Context, imageUri: Uri): com.sih.model.QualityGateResult = withContext(Dispatchers.Default) {
        val baseResult = analyze(context, imageUri)
        val regionScores = mutableMapOf<String, Float>()
        
        // Priority regions mapping from defect regions
        val hasBottomGlare = baseResult.defectRegions.any { it.description.contains("Bottom", ignoreCase = true) }
        val hasTopGlare = baseResult.defectRegions.any { it.description.contains("Top", ignoreCase = true) }
        val hasCenterGlare = baseResult.defectRegions.any { it.description.contains("Center", ignoreCase = true) }

        regionScores["MRP_REGION"] = if (hasBottomGlare || hasCenterGlare) (baseResult.glareScore * 0.7f).coerceIn(0.1f, 0.99f) else baseResult.glareScore
        regionScores["NET_QUANTITY_REGION"] = if (hasBottomGlare) (baseResult.glareScore * 0.75f).coerceIn(0.1f, 0.99f) else baseResult.glareScore
        regionScores["DATE_REGION"] = if (hasTopGlare || hasBottomGlare) (baseResult.glareScore * 0.8f).coerceIn(0.1f, 0.99f) else baseResult.glareScore
        regionScores["MANUFACTURER_REGION"] = if (hasCenterGlare) (baseResult.glareScore * 0.85f).coerceIn(0.1f, 0.99f) else baseResult.glareScore

        val warnings = mutableListOf<String>()
        val tier = when (baseResult.overallStatus) {
            QualityStatus.REJECT -> {
                warnings.add(baseResult.rejectionReason ?: "Image quality too low for statutory compliance verification.")
                com.sih.model.QualityTier.RED
            }
            QualityStatus.WARNING -> {
                if (hasBottomGlare) warnings.add("Moderate glare detected near possible MRP / Net Quantity region.")
                else if (hasTopGlare) warnings.add("Mild glare detected near date / crimp region.")
                else warnings.add(baseResult.rejectionReason ?: "Sub-optimal lighting or mild focus softness.")
                com.sih.model.QualityTier.AMBER
            }
            QualityStatus.ACCEPT -> com.sih.model.QualityTier.GREEN
        }

        com.sih.model.QualityGateResult(
            overallScore = baseResult.qualityScore,
            blurScore = baseResult.blurScore,
            glareScore = baseResult.glareScore,
            resolutionScore = baseResult.resolutionScore,
            orientationScore = baseResult.orientationScore,
            textVisibilityScore = null, // Set post-OCR
            status = tier,
            regionScores = regionScores,
            warnings = warnings,
            canOverride = tier == com.sih.model.QualityTier.AMBER
        )
    }

    fun evaluatePostOcrQuality(
        preCaptureResult: com.sih.model.QualityGateResult,
        detectedBlockCount: Int,
        totalTextLength: Int
    ): com.sih.model.QualityGateResult {
        val textVisibilityScore = when {
            totalTextLength == 0 -> 0.10f
            totalTextLength < 30 -> 0.45f
            detectedBlockCount < 3 -> 0.65f
            detectedBlockCount < 6 -> 0.82f
            else -> 0.95f
        }
        val warnings = preCaptureResult.warnings.toMutableList()
        if (textVisibilityScore < 0.50f && preCaptureResult.status != com.sih.model.QualityTier.RED) {
            warnings.add("Low text visibility: Very few characters detected despite acceptable resolution.")
        }
        return preCaptureResult.copy(
            textVisibilityScore = textVisibilityScore,
            warnings = warnings
        )
    }

    private fun calculateInSampleSize(width: Int, height: Int, reqWidth: Int, reqHeight: Int): Int {
        var inSampleSize = 1
        if (height > reqHeight || width > reqWidth) {
            val halfHeight = height / 2
            val halfWidth = width / 2
            while ((halfHeight / inSampleSize) >= reqHeight && (halfWidth / inSampleSize) >= reqWidth) {
                inSampleSize *= 2
            }
        }
        return inSampleSize
    }
}
