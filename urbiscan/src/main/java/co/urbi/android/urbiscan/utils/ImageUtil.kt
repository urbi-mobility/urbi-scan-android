package co.urbi.android.urbiscan.utils

import android.graphics.Bitmap
import android.graphics.Rect
import androidx.camera.core.AspectRatio
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ImageUtil {

    companion object {

        const val RATIO_4_3_VALUE = 4.0 / 3.0
        const val RATIO_16_9_VALUE = 16.0 / 9.0
        const val FILENAME = "yyyy-MM-dd-HH-mm-ss-SSS"
        const val PHOTO_EXTENSION = ".jpg"
        const val LEFT_MARGIN = 40
        const val RIGHT_MARGIN = 40
        const val TOP_MARGIN = 60
        const val BOTTOM_MARGIN = 70
        const val MAX_HEIGHT = 270.0
        const val MAX_WIDTH = 240.0
        const val RATIO_STANDARD = 0.75

        fun getRatio(measure: Int, reference: Double): Double {
            return reference / measure
        }

        /**
         *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
         *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
         *
         *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
         *  of preview ratio to one of the provided values.
         *
         *  @param width - preview width
         *  @param height - preview height
         *  @return suitable aspect ratio
         */
        fun aspectRatio(width: Int, height: Int): Int {
            val previewRatio = max(width, height).toDouble() / min(width, height)
            if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
                return AspectRatio.RATIO_4_3
            }
            return AspectRatio.RATIO_16_9
        }
        fun createFile(baseFolder: File, format: String, extension: String) =
            File(
                baseFolder,
                SimpleDateFormat(format, Locale.getDefault())
                    .format(System.currentTimeMillis()) + extension
            )
    }
}

fun Bitmap.fixOrientation(): Float {
    return if (width> height)
        90f
    else
        0f
}

fun Rect.getPhotoLeft(): Int {
    return if (left - ImageUtil.LEFT_MARGIN < 0)
        0
    else (left - ImageUtil.LEFT_MARGIN)
}

fun Rect.getPhotoTop(max: Int): Int {
    return if (top + ImageUtil.TOP_MARGIN > max)
        max
    else top + ImageUtil.TOP_MARGIN
}

fun Rect.getPhotoWidth(max: Int): Int {
    return if (width() + ImageUtil.LEFT_MARGIN + ImageUtil.RIGHT_MARGIN > max)
        max
    else width() + ImageUtil.LEFT_MARGIN + ImageUtil.RIGHT_MARGIN
}

fun Rect.getPhotoHeight(max: Int): Int {
    return if (height() + ImageUtil.TOP_MARGIN + ImageUtil.BOTTOM_MARGIN > max)
        max
    else height() + ImageUtil.TOP_MARGIN + ImageUtil.BOTTOM_MARGIN
}
