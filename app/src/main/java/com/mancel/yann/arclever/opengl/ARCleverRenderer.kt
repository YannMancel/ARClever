package com.mancel.yann.arclever.opengl

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * Created by Yann MANCEL on 11/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.opengl
 *
 * A class which implements [GLSurfaceView.Renderer]
 */
class ARCleverRenderer : GLSurfaceView.Renderer {

    // FIELDS --------------------------------------------------------------------------------------

    private lateinit var _triangle: Triangle

    // METHODS -------------------------------------------------------------------------------------

    // -- GLSurfaceView.Renderer interface --

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Set the background frame color
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f)

        // initialize a triangle
        this._triangle = Triangle()
    }

    override fun onDrawFrame(gl: GL10?) {
        // Redraws background color
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

        // Draws the shape
        this._triangle.draw()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
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