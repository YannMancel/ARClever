package com.mancel.yann.arclever.rendering

import android.content.Context
import android.opengl.GLES11Ext
import android.opengl.GLES20
import com.google.ar.core.Coordinates2d
import com.google.ar.core.Frame
import com.google.ar.core.Session
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by Yann MANCEL on 13/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.rendering
 *
 * This class renders the AR background from camera feed. It creates and hosts the texture given to
 * ARCore to be filled with the camera image.
 */
class BackgroundRenderer {

    // FIELDS --------------------------------------------------------------------------------------

    private lateinit var _quadCoords: FloatBuffer
    private lateinit var _quadTexCoords: FloatBuffer

    private var _cameraProgram: Int = 0
    private var _cameraPositionAttrib: Int = 0
    private var _cameraTexCoordAttrib: Int = 0
    private var _cameraTextureUniform: Int = 0
    var _cameraTextureId: Int = -1
        private set

    private var _depthProgram: Int = 0
    private var _depthPositionAttrib: Int = 0
    private var _depthTexCoordAttrib: Int = 0
    private var _depthTextureUniform: Int = 0
    private var _depthTextureId: Int = -1

    private var _suppressTimestampZeroRendering = true

    companion object {
        /**
         * (-1, 1) ------- (1, 1)
         *   |    \           |
         *   |       \        |
         *   |          \     |
         *   |             \  |
         * (-1, -1) ------ (1, -1)
         * Ensure triangles are front-facing, to support glCullFace().
         * This quad will be drawn using GL_TRIANGLE_STRIP which draws two
         * triangles: v0->v1->v2, then v2->v1->v3.
         */
        private val QUAD_COORDS =
            floatArrayOf(-1.0F, -1.0F, +1.0F, -1.0F, -1.0F, +1.0F, +1.0F, +1.0F)
        private const val COORDS_PER_VERTEX = 2
        private const val FLOAT_SIZE = 4
        private const val TEXCOORDS_PER_VERTEX = 2

        private const val CAMERA_VERTEX_SHADER_NAME = "shaders/screenquad.vert"
        private const val CAMERA_FRAGMENT_SHADER_NAME = "shaders/screenquad.frag"

        private const val DEPTH_VISUALIZER_VERTEX_SHADER_NAME =
            "shaders/background_show_depth_color_visualization.vert"
        private const val DEPTH_VISUALIZER_FRAGMENT_SHADER_NAME =
            "shaders/background_show_depth_color_visualization.frag"
    }

    // METHODS -------------------------------------------------------------------------------------

    // -- GlThread --

    /**
     * Allocates and initializes OpenGL resources needed by the background renderer.
     * Must be called on the OpenGL thread,
     * typically in [android.opengl.GLSurfaceView.Renderer.onSurfaceCreated].
     * @param context Needed to access shader source.
     * @param depthTextureId a [Int] that contains the texture Id
     */
    @Throws(IOException::class)
    fun createOnGlThread(context: Context, depthTextureId: Int) {
        // Generate the background texture
        val textures = IntArray(1)
        GLES20.glGenTextures(1, textures, 0)
        this._cameraTextureId = textures.first()
        val textureTarget = GLES11Ext.GL_TEXTURE_EXTERNAL_OES

        GLES20.glBindTexture(textureTarget, this._cameraTextureId)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR)
        GLES20.glTexParameteri(textureTarget, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)

        val numVertices = 4
        if (numVertices != QUAD_COORDS.size / COORDS_PER_VERTEX) {
            throw RuntimeException("Unexpected number of vertices in BackgroundRenderer.")
        }

        // Initializes vertex byte buffer for shape coordinates
        // [number of coordinate values * 4 bytes per float]
        val bbCoords = ByteBuffer.allocateDirect(QUAD_COORDS.size * FLOAT_SIZE).also {
            // Uses the device hardware's native byte order
            it.order(ByteOrder.nativeOrder())
        }

        // Creates a FloatingBuffer from the ByteBuffer
        this._quadCoords = bbCoords.asFloatBuffer().also {
            // Adds the coordinates to the FloatBuffer
            it.put(QUAD_COORDS)

            // Set the buffer to read the first coordinate
            it.position(0)
        }

        val bbTexCoordsTransformed =
            ByteBuffer.allocateDirect(numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE).also {
                // Uses the device hardware's native byte order
                it.order(ByteOrder.nativeOrder())
            }

        this._quadTexCoords = bbTexCoordsTransformed.asFloatBuffer()

        // CAMERA: Load render camera feed shader.
        val cameraVertexShader = ShaderUtil.loadGLShader(
            this.javaClass.simpleName,
            context,
            GLES20.GL_VERTEX_SHADER,
            CAMERA_VERTEX_SHADER_NAME
        )
        val cameraFragmentShader = ShaderUtil.loadGLShader(
            this.javaClass.simpleName,
            context,
            GLES20.GL_FRAGMENT_SHADER,
            CAMERA_FRAGMENT_SHADER_NAME
        )

        // Creates empty OpenGL ES Program
        this._cameraProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, cameraVertexShader)
            GLES20.glAttachShader(it, cameraFragmentShader)
            GLES20.glLinkProgram(it)
            GLES20.glUseProgram(it)
        }

        this._cameraPositionAttrib = GLES20.glGetAttribLocation(this._cameraProgram, "a_Position")
        this._cameraTexCoordAttrib = GLES20.glGetAttribLocation(this._cameraProgram, "a_TexCoord")
        ShaderUtil.checkGLError(this.javaClass.simpleName, "Program creation")

        this._cameraTextureUniform = GLES20.glGetUniformLocation(this._cameraProgram, "sTexture")
        ShaderUtil.checkGLError(this.javaClass.simpleName, "Program parameters")

        // DEPTH: Load render depth map shader.
        val depthVertexShader = ShaderUtil.loadGLShader(
            this.javaClass.simpleName,
            context,
            GLES20.GL_VERTEX_SHADER,
            DEPTH_VISUALIZER_VERTEX_SHADER_NAME
        )
        val depthFragmentShader = ShaderUtil.loadGLShader(
            this.javaClass.simpleName,
            context,
            GLES20.GL_FRAGMENT_SHADER,
            DEPTH_VISUALIZER_FRAGMENT_SHADER_NAME
        )

        this._depthProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, depthVertexShader)
            GLES20.glAttachShader(it, depthFragmentShader)
            GLES20.glLinkProgram(it)
            GLES20.glUseProgram(it)
        }

        this._depthPositionAttrib = GLES20.glGetAttribLocation(this._depthProgram, "a_Position")
        this._depthTexCoordAttrib = GLES20.glGetAttribLocation(this._depthProgram, "a_TexCoord")
        ShaderUtil.checkGLError(this.javaClass.simpleName, "Program creation")

        this._depthTextureUniform = GLES20.glGetUniformLocation(this._depthProgram, "u_DepthTexture")
        ShaderUtil.checkGLError(this.javaClass.simpleName, "Program parameters")

        this._depthTextureId = depthTextureId
    }

    // -- Draw --

    /**
     * Draws the AR background image.
     * The image will be drawn such that virtual content rendered with the matrices provided
     * by [com.google.ar.core.Camera.getViewMatrix] and
     * [com.google.ar.core.Camera.getProjectionMatrix]
     * will accurately follow static physical objects.
     * This must be called before drawing virtual content.
     * @param frame The current [Frame] as returned by [Session.update].
     * @param debugShowDepthMap Toggles whether to show the live camera feed or latest depth image.
     */
    fun draw(frame: Frame, debugShowDepthMap: Boolean) {
        // If display rotation changed (also includes view size change), we need to re-query the uv
        // coordinates for the screen rect, as they may have changed as well.
        if (frame.hasDisplayGeometryChanged()) {
            frame.transformCoordinates2d(
                Coordinates2d.OPENGL_NORMALIZED_DEVICE_COORDINATES,
                this._quadCoords,
                Coordinates2d.TEXTURE_NORMALIZED,
                this._quadTexCoords)
        }

        if (frame.timestamp == 0L && this._suppressTimestampZeroRendering) {
            // Suppress rendering if the camera did not produce the first frame yet. This is to avoid
            // drawing possible leftover data from previous sessions if the texture is reused.
            return
        }

        this.draw(debugShowDepthMap)
    }

    /**
     * Draws the camera background image using the currently configured
     * [BackgroundRenderer._quadTexCoords] image texture coordinates.
     */
    private fun draw(debugShowDepthMap: Boolean) {
        // Ensure position is rewound before use.
        this._quadTexCoords.position(0)

        // No need to test or write depth, the screen quad has arbitrary depth, and is expected
        // to be drawn first.
        GLES20.glDisable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthMask(false)

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)

        if (debugShowDepthMap) {
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._depthTextureId)
            GLES20.glUseProgram(this._depthProgram)
            GLES20.glUniform1i(this._depthTextureUniform, 0)

            // Set the vertex positions and texture coordinates.
            GLES20.glVertexAttribPointer(
                this._depthPositionAttrib, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, this._quadCoords)
            GLES20.glVertexAttribPointer(
                this._depthTexCoordAttrib, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, this._quadTexCoords)
            GLES20.glEnableVertexAttribArray(this._depthPositionAttrib)
            GLES20.glEnableVertexAttribArray(this._depthTexCoordAttrib)
        } else {
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, this._cameraTextureId)
            GLES20.glUseProgram(this._cameraProgram)
            GLES20.glUniform1i(this._cameraTextureUniform, 0)

            // Set the vertex positions and texture coordinates.
            GLES20.glVertexAttribPointer(
                this._cameraPositionAttrib, COORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, this._quadCoords)
            GLES20.glVertexAttribPointer(
                this._cameraTexCoordAttrib, TEXCOORDS_PER_VERTEX, GLES20.GL_FLOAT, false, 0, this._quadTexCoords)
            GLES20.glEnableVertexAttribArray(this._cameraPositionAttrib)
            GLES20.glEnableVertexAttribArray(this._cameraTexCoordAttrib)
        }

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)

        // Disable vertex arrays
        if (debugShowDepthMap) {
            GLES20.glDisableVertexAttribArray(this._depthPositionAttrib)
            GLES20.glDisableVertexAttribArray(this._depthTexCoordAttrib)
        } else {
            GLES20.glDisableVertexAttribArray(this._cameraPositionAttrib)
            GLES20.glDisableVertexAttribArray(this._cameraTexCoordAttrib)
        }

        // Restore the depth state for further drawing.
        GLES20.glDepthMask(true)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "BackgroundRendererDraw")
    }
}