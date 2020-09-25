package com.mancel.yann.arclever.utils

import android.content.Context
import com.google.ar.core.Camera
import com.google.ar.core.TrackingFailureReason
import com.mancel.yann.arclever.R

/**
 * Created by Yann MANCEL on 25/09/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.utils
 */
object TrackingStateTools {

    /** Gets the [TrackingFailureReason] */
    fun getTrackingFailureReasonString(camera: Camera, context: Context): String {
        return when(val reason = camera.trackingFailureReason) {
            TrackingFailureReason.NONE -> ""
            TrackingFailureReason.BAD_STATE -> context.getString(R.string.error_bad_state)
            TrackingFailureReason.INSUFFICIENT_LIGHT -> context.getString(R.string.error_insufficient_light)
            TrackingFailureReason.EXCESSIVE_MOTION -> context.getString(R.string.error_excessive_motion)
            TrackingFailureReason.INSUFFICIENT_FEATURES -> context.getString(R.string.error_insufficient_features)
            TrackingFailureReason.CAMERA_UNAVAILABLE -> context.getString(R.string.error_camera_unavailable)
            else -> context.getString(R.string.error_other_reason, reason)
        }
    }
}