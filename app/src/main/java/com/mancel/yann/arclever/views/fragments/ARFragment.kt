package com.mancel.yann.arclever.views.fragments

import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import com.mancel.yann.arclever.R
import com.mancel.yann.arclever.rendering.ARCleverRenderer
import com.mancel.yann.arclever.utils.CameraPermissionTools
import com.mancel.yann.arclever.utils.DisplayListenerTools
import com.mancel.yann.arclever.utils.MessageTools
import kotlinx.android.synthetic.main.fragment_ar.view.*

/**
 * Created by Yann MANCEL on 08/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.views.fragments
 *
 * A [BaseFragment] subclass.
 */
class ARFragment : BaseFragment() {

    // FIELDS --------------------------------------------------------------------------------------

    private lateinit var _displayListener: DisplayListenerTools
    private lateinit var _renderer: ARCleverRenderer

    private var _session: Session? = null
    private var _userRequestedInstall = true

    // METHODS -------------------------------------------------------------------------------------

    // -- BaseFragment --

    override fun getFragmentLayout(): Int = R.layout.fragment_ar

    override fun doOnCreateView() = this.configureSurfaceViewFromOpenGL()

    override fun doOnResume() = this.configureCamera()

    override fun doOnPause() = this.managePauseOfAR()

    // -- Fragment --

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        when (requestCode) {
            // Access to the camera of device
            CameraPermissionTools.REQUEST_CODE_PERMISSION_CAMERA -> {
                if (grantResults.isNotEmpty() && grantResults.first() == PackageManager.PERMISSION_GRANTED)
                    this.configureCamera()
                else
                    MessageTools.showMessageWithSnackbar(
                        this._rootView.fragment_ar_root,
                        this.getString(R.string.no_camera_permission)
                    )
            }

            else -> super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        }
    }

    // -- SurfaceView --

    /** Configures a SurfaceView from OpenGL */
    private fun configureSurfaceViewFromOpenGL() {
        // DisplayListener
        this._displayListener = DisplayListenerTools(this.requireContext())

        // Renderer
        this._renderer = ARCleverRenderer(
            this.requireContext(),
            this._displayListener
        )

        // GLSurfaceView
        with(this._rootView.fragment_ar_surface_view) {
            // Manage Context on pause
            preserveEGLContextOnPause = true

            // Create an OpenGL ES 2.0 context
            setEGLContextClientVersion(2)

            // Alpha used for plane blending
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)

            // Set the Renderer for drawing on the GLSurfaceView
            setRenderer(this@ARFragment._renderer)

            // The renderer is called continuously to re-render the scene
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

            // Optimisation
            setWillNotDraw(false)
        }
    }

    // -- Camera --

    /** Configures Camera with Camera permission */
    private fun configureCamera() {
        if (CameraPermissionTools.hasCameraPermission(this@ARFragment))
            this.setupSessionOfAR()
        else
            CameraPermissionTools.requestCameraPermission(this@ARFragment)
    }

    // -- AR Core --

    /** Setups the [Session] of AR */
    private fun setupSessionOfAR() {
        var message: String? = null

        // Make sure Google Play Services for AR is installed and up to date.
        try {
            // Enable ARCore
            if (this._session == null) {
                val installStatus = ArCoreApk.getInstance()
                    .requestInstall(this.requireActivity(), this._userRequestedInstall)

                when (installStatus) {
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // Success, create the AR session
                        this._session = Session(this.requireContext())

                        // Update session into Renderer
                        this._renderer._session = this._session
                    }

                    ArCoreApk.InstallStatus.INSTALL_REQUESTED, null -> {
                        // The current activity pauses and the user is prompted to install
                        // or update Google Play Services for AR.
                        // Ensures next invocation of requestInstall() will either return
                        // INSTALLED or throw an exception.
                        this._userRequestedInstall = false

                        return
                    }
                }
            }
        } catch (e: UnavailableUserDeclinedInstallationException) {
            message = this.getString(R.string.exception_declined_installation)
        } catch (e: UnavailableArcoreNotInstalledException) {
            message = this.getString(R.string.exception_not_installed)
        } catch (e: UnavailableApkTooOldException) {
            message = this.getString(R.string.exception_update_ar_core)
        } catch (e: UnavailableSdkTooOldException) {
            message = this.getString(R.string.exception_update_app)
        } catch (e: UnavailableDeviceNotCompatibleException) {
            message = this.getString(R.string.exception_not_supported)
        } catch (e: Exception) {
            message = this.getString(R.string.exception_failed_session)
        }

        // Manage the exception message
        if (message != null) {
            MessageTools.showMessageWithSnackbar(
                this._rootView.fragment_ar_root,
                message
            )

            // this._Session is still null
            return
        }

        // Manages the Depth API of AR
        this.manageDepthAPIOfAR()

        // Manages the resume of AR
        this.manageResumeOfAR()
    }

    /** Manages the Depth API of AR */
    private fun manageDepthAPIOfAR() {
        this._session?.let { session ->
            // Check if Depth API is supported
            val config = session.config.also {
                // Check whether the user's device supports the Depth API.
                it.depthMode =
                    if (session.isDepthModeSupported(Config.DepthMode.AUTOMATIC))
                        Config.DepthMode.AUTOMATIC
                    else
                        Config.DepthMode.DISABLED
            }
            session.configure(config)
        }
    }

    /** Manages the resume of AR */
    private fun manageResumeOfAR() {
        this._session?.let { session ->
            // Note that order matters - see the note in manageClosureOfAR method in onPause(),
            // the reverse applies here.
            try {
                session.resume()
            } catch (e: CameraNotAvailableException) {
                MessageTools.showMessageWithSnackbar(
                    this._rootView.fragment_ar_root,
                    this.getString(R.string.exception_camera_not_available)
                )
                this._session = null
                return
            }

            this._rootView.fragment_ar_surface_view.onResume()
            this._displayListener.onResume()
        }
    }

    /** Manages the pause of AR */
    private fun managePauseOfAR() {
        this._session?.let { session ->
            // Note that the order matters - GLSurfaceView is paused first so that it does not try
            // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
            // still call session.update() and get a SessionPausedException.
            this._displayListener.onPause()
            this._rootView.fragment_ar_surface_view.onPause()
            session.pause()
        }
    }
}