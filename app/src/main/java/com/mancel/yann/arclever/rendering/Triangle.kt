package com.mancel.yann.arclever.rendering

import android.opengl.GLES20
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer

/**
 * Created by Yann MANCEL on 11/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.rendering
 */
class Triangle {

    // FIELDS --------------------------------------------------------------------------------------

    private val _triangleCoords = floatArrayOf( // in counterclockwise order:
        0.0f,   0.622008459f,  0.0f,            // top
       -0.5f,  -0.311004243f,  0.0f,            // bottom left
        0.5f,  -0.311004243f,  0.0f             // bottom right
    )

    private var _vertexBuffer: FloatBuffer

    private var _program = 0
    private var _positionHandle = 0
    private val _vertexStride = COORDS_PER_VERTEX * 4 // 4 bytes per vertex
    private var _colorHandle = 0
    private val _vertexCount = this._triangleCoords.size / COORDS_PER_VERTEX

    // Set color with red, green, blue and alpha (opacity) values
    private val _color = floatArrayOf( 0.63671875f, 0.76953125f, 0.22265625f, 1.0f )

    companion object {

        private const val VERTEX_SHADER =
            "attribute vec4 vPosition;" +
            "void main() {" +
            "  gl_Position = vPosition;" +
            "}"

        private const val FRAGMENT_SHADER =
            "precision mediump float;" +
            "uniform vec4 vColor;" +
            "void main() {" +
            "  gl_FragColor = vColor;" +
            "}"

        private const val COORDS_PER_VERTEX = 3
    }

    // CONSTRUCTORS --------------------------------------------------------------------------------

    init {
        // Initializes vertex byte buffer for shape coordinates
        // [number of coordinate values * 4 bytes per float]
        val byteBuffer = ByteBuffer.allocateDirect(this._triangleCoords.size * 4)

        // Uses the device hardware's native byte order
        byteBuffer.order(ByteOrder.nativeOrder())

        // Creates a FloatingBuffer from the ByteBuffer
        this._vertexBuffer = byteBuffer.asFloatBuffer().also {
            // Adds the coordinates to the FloatBuffer
            it.put(this._triangleCoords)

            // Set the buffer to read the first coordinate
            it.position(0)
        }

        // Shaders
        val vertexShader = ARCleverRenderer.loadShader(
            GLES20.GL_VERTEX_SHADER,
            VERTEX_SHADER
        )
        val fragmentShader = ARCleverRenderer.loadShader(
            GLES20.GL_FRAGMENT_SHADER,
            FRAGMENT_SHADER
        )

        // Creates empty OpenGL ES Program
        this._program = GLES20.glCreateProgram().also {
            // Adds the vertex shader to program
            GLES20.glAttachShader(it, vertexShader)
            // Adds the fragment shader to program
            GLES20.glAttachShader(it, fragmentShader)
            // Creates OpenGL ES program executables
            GLES20.glLinkProgram(it)
        }
    }

    // METHODS -------------------------------------------------------------------------------------

    fun draw() {
        // Adds program to OpenGL ES environment
        GLES20.glUseProgram(this._program)

        // Get handle to vertex shader's vPosition member
        this._positionHandle = GLES20.glGetAttribLocation(this._program, "vPosition")

        // Enables a handle to the triangle vertices
        GLES20.glEnableVertexAttribArray(this._positionHandle)

        // Prepares the triangle coordinate data
        GLES20.glVertexAttribPointer(
            this._positionHandle,
            COORDS_PER_VERTEX,
            GLES20.GL_FLOAT,
            false,
            this._vertexStride,
            this._vertexBuffer)

        // get handle to fragment shader's vColor member
        this._colorHandle = GLES20.glGetUniformLocation(this._program, "vColor").also {
            // Set color for drawing the triangle
            GLES20.glUniform4fv(it, 1, this._color, 0)
        }

        // Draw the triangle
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, this._vertexCount)

        // Disable vertex array
        GLES20.glDisableVertexAttribArray(this._positionHandle)
    }
}