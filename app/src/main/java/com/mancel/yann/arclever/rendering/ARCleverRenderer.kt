package com.mancel.yann.arclever.rendering

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.Log
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.mancel.yann.arclever.utils.DisplayListenerTools
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by Yann MANCEL on 11/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.rendering
 *
 * A class which implements [GLSurfaceView.Renderer]
 */
class ARCleverRenderer(
    private val _context: Context,
    private val _displayListener: DisplayListenerTools
) : GLSurfaceView.Renderer {

    // FIELDS --------------------------------------------------------------------------------------

    var _session: Session? = null

    private val _depthTexture = Texture()
    private val _backgroundRenderer = BackgroundRenderer()

    // METHODS -------------------------------------------------------------------------------------

    // -- GLSurfaceView.Renderer interface --

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set the background frame color
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f)

        // Prepare the rendering objects. This involves reading shaders,
        // so may throw an IOException.
        try {
            // Create the texture and pass it to ARCore session to be filled during update()
            this._depthTexture.createOnGlThread()
            this._backgroundRenderer.createOnGlThread(this._context, this._depthTexture._textureId)
        } catch (e: IOException) {
            Log.e(this.javaClass.simpleName, "Failed to read an asset file", e)
        }
    }

    override fun onDrawFrame(gl: GL10?) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // No Session of AR
        if (this._session == null) return

        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        this._displayListener.updateSessionIfNeeded(this._session!!)

        try {
            this._session!!.setCameraTextureName(this._backgroundRenderer._cameraTextureId)

            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera frame rate.
            val frame = this._session!!.update()
            val camera = frame.camera

//            if (frame.hasDisplayGeometryChanged() || calculateUVTransform) {
//                // The UV Transform represents the transformation between screenspace in normalized units
//                // and screenspace in units of pixels.  Having the size of each pixel is necessary in the
//                // virtual object shader, to perform kernel-based blur effects.
//                calculateUVTransform = false;
//                float[] transform = getTextureTransformMatrix(frame);
//                virtualObject.setUvTransformMatrix(transform);
//            }

            if (this._session!!.isDepthModeSupported(Config.DepthMode.AUTOMATIC)) {
                this._depthTexture.updateWithDepthImageOnGlThread(frame)
            }

            // Handle one tap per frame.
//            handleTap(frame, camera)

            // If frame is ready, render camera preview image to the GL surface.
            this._backgroundRenderer.draw(frame, false) //this._depthSettings.depthColorVisualizationEnabled())

//            // Keep the screen unlocked while tracking, but allow it to lock when tracking stops.
//            trackingStateHelper.updateKeepScreenOnFlag(camera.getTrackingState());
//
//            // If not tracking, don't draw 3D objects, show tracking failure reason instead.
//            if (camera.getTrackingState() == TrackingState.PAUSED) {
//                messageSnackbarHelper.showMessage(
//                    this, TrackingStateHelper.getTrackingFailureReasonString(camera));
//                return;
//            }

            // Get projection matrix.
            val projectionMatrix = FloatArray(16)
            camera.getProjectionMatrix(projectionMatrix, 0, 0.1f, 100.0f)

            // Get camera matrix and draw.
            val viewMatrix = FloatArray(16)
            camera.getViewMatrix(viewMatrix, 0)

            // Compute lighting from average intensity of the image.
            // The first three components are color scaling factors.
            // The last one is the average pixel intensity in gamma space.
            val colorCorrectionRgba = FloatArray(4)
            frame.lightEstimate.getColorCorrection(colorCorrectionRgba, 0)
        } catch ( t: Throwable) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(this.javaClass.simpleName, "Exception on the OpenGL thread", t)
        }
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        this._displayListener.onSurfaceChanged(width, height)
        GLES20.glViewport(0, 0, width, height)
    }

    // -- Shader --

    companion object {

        /**
         * Loads the shader
         * @param type          an [Int] that contains the type of shader
         * @param shaderCode    a [String] that contains the shader code
         * @return an [Int] tha corresponds to the shader
         */
        fun loadShader(type: Int, shaderCode: String) : Int {
            // Creates a vertex shader type (GLES20.GL_VERTEX_SHADER)
            // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
            return GLES20.glCreateShader(type).also { shader ->
                // Adds the source code to the shader and compile it
                GLES20.glShaderSource(shader, shaderCode)
                GLES20.glCompileShader(shader)
            }
        }
    }
}