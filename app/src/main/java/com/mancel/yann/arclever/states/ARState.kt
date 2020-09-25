package com.mancel.yann.arclever.states

import com.mancel.yann.arclever.rendering.ARCleverRenderer

/**
 * Created by Yann MANCEL on 25/09/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.states
 */
sealed class ARState {

    /**
     * State:  SearchingPlane
     * Where:  [ARCleverRenderer.onDrawFrame]
     * Why:    To search the planes
     */
    object SearchingPlane : ARState()

    /**
     * State:  TrackingPlaneSuccess
     * Where:  [ARCleverRenderer.onDrawFrame]
     * Why:    at least one plane is tracked
     */
    object TrackingPlaneSuccess : ARState()

    /**
     * State:  TrackingFailure
     * Where:  [ARCleverRenderer.onDrawFrame]
     * Why:    TrackingState.PAUSED
     */
    class TrackingFailure(val _reason: String) : ARState()
}