package com.mancel.yann.arclever.utils

import android.view.View
import com.google.android.material.snackbar.Snackbar

/**
 * Created by Yann MANCEL on 08/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.utils
 */
object MessageTools {

    /** Shows a Snackbar with a message and an optional Button and its action on click */
    fun showMessageWithSnackbar(
        view: View,
        message: String,
        textButton: String? = null,
        actionOnClick: View.OnClickListener? = null
    ) {
        Snackbar
            .make(view, message, Snackbar.LENGTH_SHORT)
            .setAction(textButton, actionOnClick)
            .show()
    }
}