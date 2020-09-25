package com.mancel.yann.arclever.utils

import androidx.fragment.app.Fragment
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.whenResumed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Created by Yann MANCEL on 25/09/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.utils
 */

/**
 * Launches a coroutine with [LifecycleOwner.lifecycleScope] of [Fragment]
 * in UIThread with [Dispatchers.Main].
 */
fun Fragment.doOnUiThreadWhenResumed(action: suspend () -> Unit) =
    this.lifecycleScope.launch(context = Dispatchers.Main) {
        whenResumed { action() }
    }