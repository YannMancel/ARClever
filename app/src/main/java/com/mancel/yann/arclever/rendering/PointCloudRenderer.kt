package com.mancel.yann.arclever.rendering

import android.content.Context
import android.opengl.GLES20
import android.opengl.Matrix
import com.google.ar.core.Camera
import com.google.ar.core.PointCloud
import java.io.IOException

/**
 * Created by Yann MANCEL on 24/09/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.rendering
 *
 * Renders a point cloud
 */
class PointCloudRenderer {

    // FIELDS --------------------------------------------------------------------------------------

    private var _vbo: Int = 0
    private var _vboSize: Int = 0

    private var _programName: Int = 0
    private var _positionAttribute: Int = 0
    private var _modelViewProjectionUniform: Int = 0
    private var _colorUniform: Int = 0
    private var _pointSizeUniform: Int = 0

    private var _numPoints = 0

    // Keep track of the last point cloud rendered to avoid updating the VBO if point cloud
    // was not changed.  Do this using the timestamp since we can't compare PointCloud objects.
    private var _lastTimestamp = 0L

    companion object {
        // Shader names.
        private const val VERTEX_SHADER_NAME = "shaders/point_cloud.vert"
        private const val FRAGMENT_SHADER_NAME = "shaders/point_cloud.frag"

        private const val BYTES_PER_FLOAT = Float.SIZE_BITS / 8
        private const val FLOATS_PER_POINT = 4 // X,Y,Z,confidence.
        private const val BYTES_PER_POINT = BYTES_PER_FLOAT * FLOATS_PER_POINT
        private const val INITIAL_BUFFER_POINTS = 1000
    }

    // METHODS -------------------------------------------------------------------------------------

    /**
     * Allocates and initializes OpenGL resources needed by the plane renderer.
     * Must be called on the OpenGL thread,
     * typically in [android.opengl.GLSurfaceView.Renderer.onSurfaceCreated].
     * @param context Needed to access shader source.
     */
    @Throws(IOException::class)
    fun createOnGlThread(context: Context) {
        ShaderUtil.checkGLError(this.javaClass.simpleName, "before create")

        val buffers = IntArray(1)
        GLES20.glGenBuffers(1, buffers, 0)
        this._vbo = buffers.first()
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this._vbo)

        this._vboSize = INITIAL_BUFFER_POINTS * BYTES_PER_POINT
        GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, this._vboSize, null, GLES20.GL_DYNAMIC_DRAW)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "buffer alloc")

        val vertexShader =
            ShaderUtil.loadGLShader(this.javaClass.simpleName, context, GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_NAME)
        val fragmentShader =
            ShaderUtil.loadGLShader(this.javaClass.simpleName, context, GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_NAME)

        this._programName = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vertexShader)
            GLES20.glAttachShader(it, fragmentShader)
            GLES20.glLinkProgram(it)
            GLES20.glUseProgram(it)
        }

        ShaderUtil.checkGLError(this.javaClass.simpleName, "program")

        this._positionAttribute = GLES20.glGetAttribLocation(this._programName, "a_Position")
        this._colorUniform = GLES20.glGetUniformLocation(this._programName, "u_Color")
        this._modelViewProjectionUniform = GLES20.glGetUniformLocation(this._programName, "u_ModelViewProjection")
        this._pointSizeUniform = GLES20.glGetUniformLocation(this._programName, "u_PointSize")

        ShaderUtil.checkGLError(this.javaClass.simpleName, "program  params")
    }

    /**
     * Updates the OpenGL buffer contents to the provided point.
     * Repeated calls with the same point cloud will be ignored.
     */
    fun update(cloud: PointCloud) {
        if (cloud.timestamp == this._lastTimestamp) {
            // Redundant call.
            return
        }
        ShaderUtil.checkGLError(this.javaClass.simpleName, "before update")

        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this._vbo)
        this._lastTimestamp = cloud.timestamp

        // If the VBO is not large enough to fit the new point cloud, resize it.
        this._numPoints = cloud.points.remaining() / FLOATS_PER_POINT
        if (this._numPoints * BYTES_PER_POINT > this._vboSize) {
            while (this._numPoints * BYTES_PER_POINT > this._vboSize) {
                this._vboSize *= 2
            }
            GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, this._vboSize, null, GLES20.GL_DYNAMIC_DRAW)
        }
        GLES20.glBufferSubData(
            GLES20.GL_ARRAY_BUFFER, 0, this._numPoints * BYTES_PER_POINT, cloud.points)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "after update")
    }

    /**
     * Renders the point cloud. ARCore point cloud is given in world space.
     * @param cameraView the camera view matrix for this frame, typically from [Camera.getViewMatrix]
     * @param cameraPerspective the camera projection matrix for this frame, typically from [Camera.getProjectionMatrix]
     */
    fun draw(cameraView: FloatArray, cameraPerspective: FloatArray) {
        val modelViewProjection = FloatArray(16)
        Matrix.multiplyMM(modelViewProjection, 0, cameraPerspective, 0, cameraView, 0)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Before draw")

        GLES20.glUseProgram(this._programName)
        GLES20.glEnableVertexAttribArray(this._positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, this._vbo)
        GLES20.glVertexAttribPointer(this._positionAttribute, 4, GLES20.GL_FLOAT, false, BYTES_PER_POINT, 0)
        GLES20.glUniform4f(this._colorUniform, 31.0f / 255.0f, 188.0f / 255.0f, 210.0f / 255.0f, 1.0f)
        GLES20.glUniformMatrix4fv(this._modelViewProjectionUniform, 1, false, modelViewProjection, 0)
        GLES20.glUniform1f(this._pointSizeUniform, 5.0f)

        GLES20.glDrawArrays(GLES20.GL_POINTS, 0, this._numPoints)
        GLES20.glDisableVertexAttribArray(this._positionAttribute)
        GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0)

        ShaderUtil.checkGLError(this.javaClass.simpleName, "Draw")
    }
}