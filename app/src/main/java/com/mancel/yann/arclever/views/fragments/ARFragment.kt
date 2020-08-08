package com.mancel.yann.arclever.views.fragments

import com.google.ar.core.ArCoreApk
import com.google.ar.core.CameraConfig
import com.google.ar.core.CameraConfigFilter
import com.google.ar.core.Session
import com.google.ar.core.exceptions.*
import com.mancel.yann.arclever.R
import com.mancel.yann.arclever.utils.MessageTools
import kotlinx.android.synthetic.main.fragment_a_r.view.*
import java.util.*

/**
 * Created by Yann MANCEL on 08/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.views.fragments
 *
 * A [BaseFragment] subclass.
 */
class ARFragment : BaseFragment() {

    // FIELDS --------------------------------------------------------------------------------------

    private var _session: Session? = null

    // Set to true ensures requestInstall() triggers installation if necessary
    private var _userRequestedInstall = true

    // METHODS -------------------------------------------------------------------------------------

    // -- BaseFragment --

    override fun getFragmentLayout(): Int = R.layout.fragment_a_r

    override fun configureDesign() { /* Do nothing here */ }

    override fun doOnResume() = this.configureCamera()

    override fun actionAfterPermission() = this.configureCamera()

    // -- Camera --

    /**
     * Configures Camera with Camera permission
     */
    private fun configureCamera() {
        if (this.hasCameraPermission()) {
            this.manageGooglePlayServicesForAR()
        }
    }

    // -- Google Play Services for AR --

    /**
     * Manages the Google Play Services for AR
     */
    private fun manageGooglePlayServicesForAR() {
        var message: String? = null

        // Make sure Google Play Services for AR is installed and up to date.
        try {
            if (this._session == null) {
                val installStatus = ArCoreApk.getInstance()
                    .requestInstall(this.requireActivity(), this._userRequestedInstall)

                when (installStatus) {
                    ArCoreApk.InstallStatus.INSTALLED -> {
                        // Success, create the AR session
                        this._session = Session(this.requireContext())
                    }

                    ArCoreApk.InstallStatus.INSTALL_REQUESTED, null  -> {
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
                this._rootView.fragment_a_r_root,
                message
            )

            // this._Session is still null
            return
        }

        // Create a camera config filter for the session.
        val filter = CameraConfigFilter(this._session)

        // Return only camera configs that target 30 fps camera capture frame rate.
        filter.targetFps = EnumSet.of(CameraConfig.TargetFps.TARGET_FPS_30)

        // Return only camera configs that will not use the depth sensor.
        filter.depthSensorUsage = EnumSet.of(CameraConfig.DepthSensorUsage.DO_NOT_USE)

        // Get list of configs that match filter settings.
        // In this case, this list is guaranteed to contain at least one element,
        // because both TargetFps.TARGET_FPS_30 and DepthSensorUsage.DO_NOT_USE
        // are supported on all ARCore supported devices.
        val cameraConfigList = this._session!!.getSupportedCameraConfigs(filter)

        // Use element 0 from the list of returned camera configs. This is because
        // it contains the camera config that best matches the specified filter
        // settings.
        this._session?.cameraConfig = cameraConfigList[0]
    }
}