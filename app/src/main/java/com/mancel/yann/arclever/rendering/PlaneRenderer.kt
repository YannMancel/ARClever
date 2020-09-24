package com.mancel.yann.arclever.rendering

import android.content.Context
import android.graphics.BitmapFactory
import android.opengl.GLES20
import android.opengl.GLUtils
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.Plane
import com.google.ar.core.Pose
import com.google.ar.core.TrackingState
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Created by Yann MANCEL on 23/09/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.rendering
 *
 * Renders the detected AR planes
 */
class PlaneRenderer {

    // CLASSES -------------------------------------------------------------------------------------

    data class SortablePlane(val _distance: Float, val _plane: Plane)

    // FIELDS --------------------------------------------------------------------------------------

    private var _planeProgram: Int = 0
    private var _textures = IntArray(1)

    private var _planeXZPositionAlphaAttribute: Int = 0

    private var _planeModelUniform: Int = 0
    private var _planeNormalUniform: Int = 0
    private var _planeModelViewProjectionUniform: Int = 0
    private var _textureUniform: Int = 0
    private var _lineColorUniform: Int = 0
    private var _dotColorUniform: Int = 0
    private var _gridControlUniform: Int = 0
    private var _planeUvMatrixUniform: Int = 0

    private var _vertexBuffer =
        ByteBuffer.allocateDirect(INITIAL_VERTEX_BUFFER_SIZE_BYTES)
                  .order(ByteOrder.nativeOrder())
                  .asFloatBuffer()
    private var _indexBuffer =
        ByteBuffer.allocateDirect(INITIAL_INDEX_BUFFER_SIZE_BYTES)
                  .order(ByteOrder.nativeOrder())
                  .asShortBuffer()

    // Temporary lists/matrices allocated here to reduce number of allocations for each frame.
    private val _modelMatrix = FloatArray(16)
    private val _modelViewMatrix = FloatArray(16)
    private val _modelViewProjectionMatrix = FloatArray(16)
    private val _planeColor = floatArrayOf(1F, 1F, 1F, 1F)
    private val _planeAngleUvMatrix = FloatArray(4) // 2x2 rotation matrix applied to uv coords.

    private val _planeIndexMap = HashMap<Plane, Int>()

    private val _equilateralTriangleScale = 1F / sqrt(3.0).toFloat()

    companion object {
        // Name of the PNG file containing the grid texture.
        private const val GRID_TEXTURE_NAME = "models/trigrid.png"

        // Shader names.
        private const val VERTEX_SHADER_NAME = "shaders/plane.vert"
        private const val FRAGMENT_SHADER_NAME = "shaders/plane.frag"

        private const val BYTES_PER_FLOAT = Float.SIZE_BITS / 8
        private const val BYTES_PER_SHORT = Short.SIZE_BITS / 8
        private const val COORDS_PER_VERTEX = 3 // x, z, alpha

        private const val VERTS_PER_BOUNDARY_VERT = 2
        private const val INDICES_PER_BOUNDARY_VERT = 3
        private const val INITIAL_BUFFER_BOUNDARY_VERTS = 64

        private const val INITIAL_VERTEX_BUFFER_SIZE_BYTES =
            BYTES_PER_FLOAT * COORDS_PER_VERTEX * VERTS_PER_BOUNDARY_VERT * INITIAL_BUFFER_BOUNDARY_VERTS

        private const val INITIAL_INDEX_BUFFER_SIZE_BYTES =
            BYTES_PER_SHORT * INDICES_PER_BOUNDARY_VERT * INDICES_PER_BOUNDARY_VERT * INITIAL_BUFFER_BOUNDARY_VERTS

        private const val FADE_RADIUS_M = 0.25F
        private const val DOTS_PER_METER = 10.0F

        // Using the "signed distance field" approach to render sharp lines and circles.
        // {dotThreshold, lineThreshold, lineFadeSpeed, occlusionScale}
        // dotThreshold/lineThreshold: red/green intensity above which dots/lines are present
        // lineFadeShrink:  lines will fade in between alpha = 1-(1/lineFadeShrink) and 1.0
        // occlusionShrink: occluded planes will fade out between alpha = 0 and 1/occlusionShrink
        private val GRID_CONTROL = floatArrayOf(0.2F, 0.4F, 2.0F, 1.5F)
    }

    // METHODS -------------------------------------------------------------------------------------

    // -- GlThread --

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer.
     * Must be called on the OpenGL thread,
     * typically in [android.opengl.GLSurfaceView.Renderer.onSurfaceCreated].
     * @param context Needed to access shader source and texture PNG
     */
    @Throws(IOException::class)
    fun createOnGlThread(context: Context) {
        // Shaders
        val vertexShader = ShaderUtil.loadGLShader(
            this.javaClass.simpleName,
            context,
            GLES20.GL_VERTEX_SHADER,
            VERTEX_SHADER_NAME
        )
        val fragmentShader = ShaderUtil.loadGLShader(
            this.javaClass.simpleName,
            context,
            GLES20.GL_FRAGMENT_SHADER,
            FRAGMENT_SHADER_NAME
        )

        this._planeProgram = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            GLES20.glUseProgram(it)
        }

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Program creation")

        // Read the texture
        val textureBitmap = BitmapFactory.decodeStream(context.assets.open(GRID_TEXTURE_NAME))

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glGenTextures(this._textures.size, this._textures, 0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textures.first())

        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR_MIPMAP_LINEAR)
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR)
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, textureBitmap, 0)
        GLES20.glGenerateMipmap(GLES20.GL_TEXTURE_2D)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Texture loading")

        this._planeXZPositionAlphaAttribute = GLES20.glGetAttribLocation(this._planeProgram, "a_XZPositionAlpha")

        this._planeModelUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_Model")
        this._planeNormalUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_Normal")
        this._planeModelViewProjectionUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_ModelViewProjection")
        this._textureUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_Texture")
        this._lineColorUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_lineColor")
        this._dotColorUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_dotColor")
        this._gridControlUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_gridControl")
        this._planeUvMatrixUniform = GLES20.glGetUniformLocation(this._planeProgram, "u_PlaneUvMatrix")

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Program parameters")
    }

    // -- Draw --

    /**
     * Draws the collection of tracked planes, with closer planes hiding more distant ones.
     * @param allPlanes The collection of planes to draw.
     * @param cameraPose The pose of the camera, as returned by [Camera.getPose]
     * @param cameraPerspective The projection matrix, as returned by [Camera.getProjectionMatrix]
     */
    fun drawPlanes(allPlanes: Collection<Plane>, cameraPose: Pose, cameraPerspective: FloatArray) {
        // Planes must be sorted by distance from camera so that we draw closer planes first, and
        // they occlude the farther planes.
        val sortedPlanes = ArrayList<SortablePlane>()

        for (plane in allPlanes) {
            if (plane.trackingState != TrackingState.TRACKING || plane.subsumedBy != null) {
                continue
            }

            val distance = this.calculateDistanceToPlane(plane.centerPose, cameraPose)
            if (distance < 0) { // Plane is back-facing.
                continue
            }
            sortedPlanes.add(SortablePlane(distance, plane))
        }

        sortedPlanes.sortWith { a, b ->
            a._distance.compareTo(b._distance)
        }

        val cameraView = FloatArray(16)
        cameraPose.inverse().toMatrix(cameraView, 0)

        // Planes are drawn with additive blending, masked by the alpha channel for occlusion.

        // Start by clearing the alpha channel of the color buffer to 1.0.
        GLES20.glClearColor(1F, 1F, 1F, 1F)
        GLES20.glColorMask(false, false, false, true)
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glColorMask(true, true, true, true)

        // Disable depth write.
        GLES20.glDepthMask(false)

        // Additive blending, masked by alpha channel, clearing alpha channel.
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFuncSeparate(
            GLES20.GL_DST_ALPHA, GLES20.GL_ONE, // RGB (src, dest)
            GLES20.GL_ZERO, GLES20.GL_ONE_MINUS_SRC_ALPHA // ALPHA (src, dest)
        )

        // Set up the shader.
        GLES20.glUseProgram(this._planeProgram)

        // Attach the texture.
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, this._textures.first())
        GLES20.glUniform1i(this._textureUniform, 0)

        // Shared fragment uniforms.
        GLES20.glUniform4fv(this._gridControlUniform, 1, GRID_CONTROL, 0)

        // Enable vertex arrays
        GLES20.glEnableVertexAttribArray(this._planeXZPositionAlphaAttribute)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Setting up to draw planes")

        for (sortedPlane in sortedPlanes) {
            val plane = sortedPlane._plane
            val planeMatrix = FloatArray(16)
            plane.centerPose.toMatrix(planeMatrix, 0)

            val normal = FloatArray(3)
            // Get transformed Y axis of plane's coordinate system.
            plane.centerPose.getTransformedAxis(1, 1.0F, normal, 0)

            this.updatePlaneParameters(
                planeMatrix,
                plane.extentX,
                plane.extentZ,
                plane.polygon
            )

            // Get plane index. Keep a map to assign same indices to same planes.
            var planeIndex: Int? = this._planeIndexMap[plane]
            if (planeIndex == null) {
                planeIndex = this._planeIndexMap.size
                this._planeIndexMap[plane] = planeIndex
            }

            // Set plane color.
            GLES20.glUniform4fv(this._lineColorUniform, 1, this._planeColor, 0)
            GLES20.glUniform4fv(this._dotColorUniform, 1, this._planeColor, 0)

            // Each plane will have its own angle offset from others, to make them easier to
            // distinguish. Compute a 2x2 rotation matrix from the angle.
            val angleRadians = planeIndex.toFloat() * 0.144F
            val uScale = DOTS_PER_METER
            val vScale = DOTS_PER_METER * this._equilateralTriangleScale
            this._planeAngleUvMatrix[0] = +cos(angleRadians) * uScale
            this._planeAngleUvMatrix[1] = -sin(angleRadians) * vScale
            this._planeAngleUvMatrix[2] = +sin(angleRadians) * uScale
            this._planeAngleUvMatrix[3] = +cos(angleRadians) * vScale
            GLES20.glUniformMatrix2fv(this._planeUvMatrixUniform, 1, false, this._planeAngleUvMatrix, 0)

            this.draw(cameraView, cameraPerspective, normal)
        }

        // Clean up the state we set
        GLES20.glDisableVertexAttribArray(this._planeXZPositionAlphaAttribute)
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, 0)
        GLES20.glDisable(GLES20.GL_BLEND)
        GLES20.glDepthMask(true)
        GLES20.glClearColor(0.1F, 0.1F, 0.1F, 1.0F)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Cleaning up after drawing planes")
    }

    /**
     * Calculate the normal distance to plane from cameraPose,
     * the given planePose should have y axis parallel to plane's normal,
     * for example plane's center pose or hit test pose.
     */
    private fun calculateDistanceToPlane(planePose: Pose, cameraPose: Pose): Float {
        val normal = FloatArray(3)
        val cameraX = cameraPose.tx()
        val cameraY = cameraPose.ty()
        val cameraZ = cameraPose.tz()
        // Get transformed Y axis of plane's coordinate system.
        planePose.getTransformedAxis(1, 1.0F, normal, 0)
        // Compute dot product of plane's normal with vector from camera to plane center.

        val result =
            (cameraX - planePose.tx()) * normal[0]
            + (cameraY - planePose.ty()) * normal[1]
            + (cameraZ - planePose.tz()) * normal[2]

        return result
    }

    /** Updates the plane model transform matrix and extents. */
    private fun updatePlaneParameters(
        planeMatrix: FloatArray,
        extentX: Float,
        extentZ: Float,
        boundary: FloatBuffer?
    ) {
        System.arraycopy(planeMatrix, 0, this._modelMatrix, 0, 16)
        if (boundary == null) {
            this._vertexBuffer.limit(0)
            this._indexBuffer.limit(0)
            return
        }

        // Generate a new set of vertices and a corresponding triangle strip index set so that
        // the plane boundary polygon has a fading edge. This is done by making a copy of the
        // boundary polygon vertices and scaling it down around center to push it inwards. Then
        // the index buffer is setup accordingly.
        boundary.rewind()
        val boundaryVertices = boundary.limit() / 2
        val numVertices: Int
        val numIndices: Int

        numVertices = boundaryVertices * VERTS_PER_BOUNDARY_VERT
        // drawn as GL_TRIANGLE_STRIP with 3n-2 triangles (n-2 for fill, 2n for perimeter).
        numIndices = boundaryVertices * INDICES_PER_BOUNDARY_VERT

        if (this._vertexBuffer.capacity() < numVertices * COORDS_PER_VERTEX) {
            var size = this._vertexBuffer.capacity()
            while (size < numVertices * COORDS_PER_VERTEX) {
                size *= 2
            }
            this._vertexBuffer =
                ByteBuffer.allocateDirect(BYTES_PER_FLOAT * size)
                    .order(ByteOrder.nativeOrder())
                    .asFloatBuffer()
        }
        this._vertexBuffer.rewind()
        this._vertexBuffer.limit(numVertices * COORDS_PER_VERTEX)

        if (this._indexBuffer.capacity() < numIndices) {
            var size = this._indexBuffer.capacity()
            while (size < numIndices) {
                size *= 2
            }
            this._indexBuffer =
                ByteBuffer.allocateDirect(BYTES_PER_SHORT * size)
                    .order(ByteOrder.nativeOrder())
                    .asShortBuffer()
        }
        this._indexBuffer.rewind()
        this._indexBuffer.limit(numIndices)

        // Note: when either dimension of the bounding box is smaller than 2*FADE_RADIUS_M we
        // generate a bunch of 0-area triangles.  These don't get rendered though so it works
        // out ok.
        val xScale = max((extentX - 2 * FADE_RADIUS_M) / extentX, 0.0F)
        val zScale = max((extentZ - 2 * FADE_RADIUS_M) / extentZ, 0.0f)

        while (boundary.hasRemaining()) {
            val x = boundary.get()
            val z = boundary.get()
            this._vertexBuffer.put(x)
            this._vertexBuffer.put(z)
            this._vertexBuffer.put(0.0F)
            this._vertexBuffer.put(x * xScale)
            this._vertexBuffer.put(z * zScale)
            this._vertexBuffer.put(1.0F)
        }

        // step 1, perimeter
        this._indexBuffer.put(((boundaryVertices - 1) * 2).toShort())
        for (i in 0 until boundaryVertices) {
            this._indexBuffer.put((i * 2).toShort())
            this._indexBuffer.put((i * 2 + 1).toShort())
        }
        this._indexBuffer.put(1.toShort())
        // This leaves us on the interior edge of the perimeter between the inset vertices
        // for boundary verts n-1 and 0.

        // step 2, interior:
        for (i in 1 until (boundaryVertices / 2)) {
            this._indexBuffer.put(((boundaryVertices - 1 - i) * 2 + 1).toShort())
            this._indexBuffer.put((i * 2 + 1).toShort())
        }
        if (boundaryVertices % 2 != 0) {
            this._indexBuffer.put(((boundaryVertices / 2) * 2 + 1).toShort())
        }
    }

    /** Renders the planes */
    private fun draw(
        cameraView: FloatArray,
        cameraPerspective: FloatArray,
        planeNormal: FloatArray
    ) {
        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        Matrix.multiplyMM(this._modelViewMatrix, 0, cameraView, 0, this._modelMatrix, 0)
        Matrix.multiplyMM(this._modelViewProjectionMatrix, 0, cameraPerspective, 0, this._modelViewMatrix, 0)

        // Set the position of the plane
        this._vertexBuffer.rewind()
        GLES20.glVertexAttribPointer(
            this._planeXZPositionAlphaAttribute,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            BYTES_PER_FLOAT * COORDS_PER_VERTEX,
            this._vertexBuffer)

        // Set the Model and ModelViewProjection matrices in the shader.
        GLES20.glUniformMatrix4fv(this._planeModelUniform, 1, false, this._modelMatrix, 0)
        GLES20.glUniform3f(this._planeNormalUniform, planeNormal[0], planeNormal[1], planeNormal[2])
        GLES20.glUniformMatrix4fv(
            this._planeModelViewProjectionUniform, 1, false, this._modelViewProjectionMatrix, 0)

        this._indexBuffer.rewind()
        GLES20.glDrawElements(
            GLES20.GL_TRIANGLE_STRIP, this._indexBuffer.limit(), GLES20.GL_UNSIGNED_SHORT,  this._indexBuffer)
        ShaderUtil.checkGLError(this.javaClass.simpleName, "Drawing plane")
    }
}