package com.mancel.yann.arclever.utils

import android.content.Context
import android.hardware.camera2.CameraManager
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.WindowManager
import androidx.fragment.app.Fragment
import com.google.ar.core.Session

/**
 * Created by Yann MANCEL on 14/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.utils
 *
 * A class which implements [DisplayManager.DisplayListener].
 *
 * Helper to track the display rotations. In particular, the 180 degree rotations are not notified
 * by the onSurfaceChanged() callback, and thus they require listening to the android display
 * events.
 */
class DisplayListenerTools(
    context: Context
) : DisplayManager.DisplayListener {

    // FIELDS --------------------------------------------------------------------------------------

    private val _displayManager: DisplayManager =
        context.getSystemService(Context.DISPLAY_SERVICE) as DisplayManager

    private val _cameraManager: CameraManager =
        context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    private val _display: Display =
        (context.getSystemService(Context.WINDOW_SERVICE) as WindowManager).defaultDisplay

    private var _viewportChanged = false
    private var _viewportWidth = -1
    private var _viewportHeight = -1

    // METHODS -------------------------------------------------------------------------------------

    // -- DisplayManager.DisplayListener interface --

    override fun onDisplayChanged(displayId: Int) { this._viewportChanged = true }

    override fun onDisplayAdded(displayId: Int) { /* Do nothing here */ }

    override fun onDisplayRemoved(displayId: Int) { /* Do nothing here */ }

    // -- Lifecycle --

    /** Registers the display listener. Should be called from [Fragment.onResume] call */
    fun onResume() {
        this._displayManager.registerDisplayListener(this@DisplayListenerTools, null)
    }

    /** Unregisters the display listener. Should be called from [Fragment.onPause] call */
    fun onPause() {
        this._displayManager.unregisterDisplayListener(this@DisplayListenerTools)
    }

    // -- Display Update --

    /**
     * Records a change in surface dimensions.
     * This will be later used by [DisplayListenerTools.updateSessionIfNeeded] call.
     * Should be called from [android.opengl.GLSurfaceView.Renderer.onSurfaceChanged] call.
     * @param width the updated width of the surface.
     * @param height the updated height of the surface.
     */
    fun onSurfaceChanged(width: Int, height: Int) {
        this._viewportWidth = width
        this._viewportHeight = height
        this._viewportChanged = true
    }

    /**
     * Updates the session display geometry if a change was posted either by
     * [DisplayListenerTools.onSurfaceChanged] call or by
     * [DisplayListenerTools.onDisplayChanged] system callback.
     * This function should be called explicitly before each call to [Session.update].
     * This function will also clear the 'pending update' (viewportChanged) flag.
     * @param session the [Session] object to update if display geometry changed.
     */
    fun updateSessionIfNeeded(session: Session) {
        if (this._viewportChanged) {
            val displayRotation = this._display.rotation
            session.setDisplayGeometry(displayRotation, this._viewportWidth, this._viewportHeight)
            this._viewportChanged = false
        }
    }

//    /**
//     *  Returns the aspect ratio of the GL surface viewport while accounting for the display rotation
//     *  relative to the device camera sensor orientation.
//     */
//    fun getCameraSensorRelativeViewportAspectRatio(cameraId: String) : Float {
//        val cameraSensorToDisplayRotation = this.getCameraSensorToDisplayRotation(cameraId)
//        return when (cameraSensorToDisplayRotation) {
//            90, 270 -> { this._viewportHeight.toFloat() / this._viewportWidth.toFloat() }
//            0, 180 -> { this._viewportWidth.toFloat() / this._viewportHeight.toFloat() }
//            else -> throw RuntimeException("Unhandled rotation: $cameraSensorToDisplayRotation")
//        }
//    }
//
//    /**
//     * Returns the rotation of the back-facing camera with respect to the display.
//     * The value is one of 0, 90, 180, 270.
//     */
//    fun getCameraSensorToDisplayRotation(cameraId: String) : Int {
//        val characteristics: CameraCharacteristics
//        try {
//            characteristics = this._cameraManager.getCameraCharacteristics(cameraId)
//        } catch (e: CameraAccessException) {
//            throw RuntimeException("Unable to determine display orientation $e")
//        }
//
//        // Camera sensor orientation.
//        val sensorOrientation = characteristics.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0
//
//        // Current display orientation.
//        val displayOrientation = this.toDegrees(this._display.rotation)
//
//        // Make sure we return 0, 90, 180, or 270 degrees.
//        return (sensorOrientation - displayOrientation + 360) % 360
//    }
//
//    private fun toDegrees(rotation: Int) : Int {
//        return when (rotation) {
//            Surface.ROTATION_0 -> 0
//            Surface.ROTATION_90 -> 90
//            Surface.ROTATION_180 -> 180
//            Surface.ROTATION_270 -> 270
//            else -> throw RuntimeException("Unknown rotation $rotation")
//        }
//    }
}