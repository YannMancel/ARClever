package com.mancel.yann.arclever.rendering

import android.opengl.GLES20.*
import android.opengl.GLES30.GL_RG
import android.opengl.GLES30.GL_RG8
import com.google.ar.core.Frame
import com.google.ar.core.exceptions.NotYetAvailableException

/**
 * Created by Yann MANCEL on 13/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.rendering
 *
 * Handle the creation and update of a GPU texture.
 */
class Texture {

    // FIELDS --------------------------------------------------------------------------------------

    var _textureId = -1
        private set
    var _width = -1
        private set
    var _height = -1
        private set

    // METHODS -------------------------------------------------------------------------------------

    // -- GlThread --

    /**
     * Creates and initializes the texture. This method needs to be called on a thread with a EGL
     * context attached.
     * @see [android.opengl.GLSurfaceView.Renderer.onSurfaceCreated]
     */
    fun createOnGlThread() {
        val textureIdArray = IntArray(1)
        glGenTextures(1, textureIdArray, 0)
        this._textureId = textureIdArray.first()

        glBindTexture(GL_TEXTURE_2D, this._textureId)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
    }

    /**
     * Updates the texture with the content from [Frame.acquireDepthImage],
     * which provides an image in DEPTH16 format,
     * representing each pixel as a depth measurement in millimeters.
     * This method needs to be called on a thread with a EGL context attached.
     */
    fun updateWithDepthImageOnGlThread(frame: Frame) {
        try {
            val depthImage = frame.acquireDepthImage()

            this._width = depthImage.width
            this._height = depthImage.height

            glBindTexture(GL_TEXTURE_2D, this._textureId)
            glTexImage2D(
                GL_TEXTURE_2D,
                0,
                GL_RG8,
                this._width,
                this._height,
                0,
                GL_RG,
                GL_UNSIGNED_BYTE,
                depthImage.planes[0].buffer)

            depthImage.close()
        } catch (e: NotYetAvailableException) {
            // This normally means that depth data is not available yet.
            // This is normal so we will not spam the logcat with this.
        }
    }
}