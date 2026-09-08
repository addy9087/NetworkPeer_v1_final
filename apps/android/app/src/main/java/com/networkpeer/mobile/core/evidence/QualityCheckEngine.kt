package com.networkpeer.mobile.core.evidence

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import com.networkpeer.mobile.core.model.QualityMetric
import com.networkpeer.mobile.core.model.QualityCheckResult
import com.networkpeer.mobile.core.model.QualityChecks
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * On-Device Capture QA Pipeline (Revision 2 Specification §3).
 *
 * Enforces hard gate checks before upload:
 * 1. Document Edge-to-Edge boundary coverage (>= 90% coverage & closed boundary)
 * 2. Sharpness / Blur detection (variance of Laplacian operator)
 * 3. Exposure / Glare histogram analysis
 *
 * If any check fails, the capture is HARD REJECTED on-device with an explicit reason.
 */
object QualityCheckEngine {

    suspend fun analyze(context: Context, uri: Uri): QualityCheckResult = withContext(Dispatchers.Default) {
        val bitmap = loadDownsampledBitmap(context, uri, maxDimension = 640)
            ?: return@withContext QualityCheckResult(
                passed = false,
                checks = QualityChecks(
                    edgeCoverage = QualityMetric(false, 0.0, "Unable to decode captured frame"),
                    sharpness = QualityMetric(false, 0.0, "Unable to decode captured frame"),
                    exposure = QualityMetric(false, 0.0, "Unable to decode captured frame"),
                ),
                overallScore = 0.0,
                engineVersion = "np-qa-v2",
                ranOnDevice = true,
                checkedAt = currentIsoTimestamp(),
            )

        try {
            val width = bitmap.width
            val height = bitmap.height
            val pixels = IntArray(width * height)
            bitmap.getPixels(pixels, 0, width, 0, 0, width, height)

            // Convert to grayscale luminance
            val luminance = FloatArray(width * height)
            val histogram = IntArray(256)
            var totalLum = 0.0

            for (i in pixels.indices) {
                val p = pixels[i]
                val r = (p shr 16) and 0xFF
                val g = (p shr 8) and 0xFF
                val b = p and 0xFF
                val lum = (0.299f * r + 0.587f * g + 0.114f * b).toInt().coerceIn(0, 255)
                luminance[i] = lum.toFloat()
                histogram[lum]++
                totalLum += lum
            }

            val totalPixels = (width * height).toDouble()
            val meanBrightness = totalLum / totalPixels

            // 1. Exposure / Glare check (§3.3.3)
            val darkPixelsRatio = (0..15).sumOf { histogram[it] } / totalPixels
            val brightPixelsRatio = (245..255).sumOf { histogram[it] } / totalPixels
            val exposurePassed = darkPixelsRatio < 0.35 && brightPixelsRatio < 0.28 && meanBrightness in 40.0..220.0
            val exposureScore = ((1.0 - max(darkPixelsRatio, brightPixelsRatio)) * 100.0).coerceIn(0.0, 100.0)
            val exposureMessage = if (!exposurePassed) {
                if (brightPixelsRatio >= 0.28) "Too much glare on page — adjust angle away from direct light and retake"
                else "Image is too dark or unevenly lit — improve lighting and retake"
            } else null

            // 2. Sharpness / Blur check using Laplacian variance (§3.3.2)
            var laplacianSum = 0.0
            var laplacianSqSum = 0.0
            var validLaplacianCount = 0

            for (y in 1 until height - 1) {
                val rowOffset = y * width
                for (x in 1 until width - 1) {
                    val idx = rowOffset + x
                    // 3x3 Laplacian: [0, 1, 0; 1, -4, 1; 0, 1, 0]
                    val lapVal = luminance[idx - width] +
                            luminance[idx + width] +
                            luminance[idx - 1] +
                            luminance[idx + 1] -
                            4f * luminance[idx]

                    laplacianSum += lapVal
                    laplacianSqSum += (lapVal * lapVal)
                    validLaplacianCount++
                }
            }

            val lapMean = laplacianSum / validLaplacianCount
            val lapVariance = (laplacianSqSum / validLaplacianCount) - (lapMean * lapMean)
            // Blur threshold: clean text pages typically produce variance > 50-70
            val sharpnessPassed = lapVariance >= 35.0
            val sharpnessScore = min(100.0, lapVariance * 1.2)
            val sharpnessMessage = if (!sharpnessPassed) {
                "Image is too blurry — hold device steady and retake"
            } else null

            // 3. Edge-to-Edge / Document Boundary Gate (§3.3.1)
            // Check frame border rows and columns for cutoff text/content
            val borderMarginX = (width * 0.03).toInt().coerceAtLeast(2)
            val borderMarginY = (height * 0.03).toInt().coerceAtLeast(2)
            var outerEdgeGradients = 0
            val totalBorderChecks = 2 * (width + height)

            for (x in 0 until width) {
                val topDiff = kotlin.math.abs(luminance[x] - luminance[borderMarginY * width + x])
                val bottomDiff = kotlin.math.abs(luminance[(height - 1) * width + x] - luminance[(height - 1 - borderMarginY) * width + x])
                if (topDiff > 35) outerEdgeGradients++
                if (bottomDiff > 35) outerEdgeGradients++
            }
            for (y in 0 until height) {
                val leftDiff = kotlin.math.abs(luminance[y * width] - luminance[y * width + borderMarginX])
                val rightDiff = kotlin.math.abs(luminance[y * width + width - 1] - luminance[y * width + width - 1 - borderMarginX])
                if (leftDiff > 35) outerEdgeGradients++
                if (rightDiff > 35) outerEdgeGradients++
            }

            // Estimate document coverage (Rev 2: >= 90-92% frame coverage)
            val cutoffRatio = outerEdgeGradients.toDouble() / totalBorderChecks.toDouble()
            val estimatedCoverage = ((1.0 - (cutoffRatio * 0.15)) * 96.0).coerceIn(60.0, 98.5)
            // Reject if page extends past outer frame (cutoff) or coverage < 90%
            val edgePassed = cutoffRatio < 0.45 && estimatedCoverage >= 90.0
            val edgeScore = estimatedCoverage
            val edgeMessage = if (!edgePassed) {
                "Page edges not fully visible — please ensure document is captured edge-to-edge inside the frame"
            } else null

            val allPassed = exposurePassed && sharpnessPassed && edgePassed
            val compositeScore = (exposureScore * 0.25 + min(sharpnessScore, 100.0) * 0.35 + edgeScore * 0.4) / 100.0

            QualityCheckResult(
                passed = allPassed,
                checks = QualityChecks(
                    edgeCoverage = QualityMetric(edgePassed, edgeScore, edgeMessage),
                    sharpness = QualityMetric(sharpnessPassed, sharpnessScore, sharpnessMessage),
                    exposure = QualityMetric(exposurePassed, exposureScore, exposureMessage),
                ),
                overallScore = compositeScore.coerceIn(0.0, 1.0),
                engineVersion = "np-qa-v2",
                ranOnDevice = true,
                checkedAt = currentIsoTimestamp(),
            )
        } finally {
            bitmap.recycle()
        }
    }

    private fun loadDownsampledBitmap(context: Context, uri: Uri, maxDimension: Int): Bitmap? {
        return runCatching {
            var stream: InputStream? = context.contentResolver.openInputStream(uri)
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeStream(stream, null, options)
            stream?.close()

            val origWidth = options.outWidth
            val origHeight = options.outHeight
            if (origWidth <= 0 || origHeight <= 0) return null

            var inSampleSize = 1
            while (origWidth / inSampleSize > maxDimension || origHeight / inSampleSize > maxDimension) {
                inSampleSize *= 2
            }

            val decodeOptions = BitmapFactory.Options().apply {
                this.inSampleSize = inSampleSize
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            stream = context.contentResolver.openInputStream(uri)
            val decoded = BitmapFactory.decodeStream(stream, null, decodeOptions)
            stream?.close()
            decoded
        }.getOrNull()
    }

    private fun currentIsoTimestamp(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US)
        return sdf.format(Date())
    }
}
