package co.urbi.android.urbiscan.core

import android.view.Surface

class FrameMetadata(
        val width: Int, val height: Int, val rotation: Int, val facing: Int
) {

    fun getConvertedRotation(): Int {
        when (rotation) {
            0 -> return Surface.ROTATION_0
            90 -> return Surface.ROTATION_90
            180 -> return Surface.ROTATION_180
            270 -> return Surface.ROTATION_270
        }
        return Surface.ROTATION_0
    }

    /** Builder of [co.urbi.android.urbiscan.core.FrameMetadata].  */
    class Builder {
        private var width = 0
        private var height = 0
        private var rotation = 0
        private var cameraFacing = 0
        fun setWidth(width: Int): Builder {
            this.width = width
            return this
        }

        fun setHeight(height: Int): Builder {
            this.height = height
            return this
        }

        fun setRotation(rotation: Int): Builder {
            this.rotation = rotation
            return this
        }

        fun setCameraFacing(facing: Int): Builder {
            cameraFacing = facing
            return this
        }

        fun build(): FrameMetadata {
            return FrameMetadata(width, height, rotation, cameraFacing)
        }
    }

}