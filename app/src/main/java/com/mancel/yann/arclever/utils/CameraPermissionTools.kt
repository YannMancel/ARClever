package com.mancel.yann.arclever.utils

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

/**
 * Created by Yann MANCEL on 23/09/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.utils
 */
object CameraPermissionTools {

    // FIELDS --------------------------------------------------------------------------------------

    const val REQUEST_CODE_PERMISSION_CAMERA = 1000
    private const val CAMERA_PERMISSION = Manifest.permission.CAMERA

    // METHODS -------------------------------------------------------------------------------------

    /** Checks the permission: CAMERA */
    fun hasCameraPermission(fragment: Fragment): Boolean {
        val permissionResult = ContextCompat.checkSelfPermission(
            fragment.requireContext(),
            CAMERA_PERMISSION
        )
        return permissionResult == PackageManager.PERMISSION_GRANTED
    }

    /** Requests the permission: CAMERA */
    fun requestCameraPermission(fragment: Fragment) {
        fragment.requestPermissions(
            arrayOf(CAMERA_PERMISSION),
            REQUEST_CODE_PERMISSION_CAMERA
        )
    }
}