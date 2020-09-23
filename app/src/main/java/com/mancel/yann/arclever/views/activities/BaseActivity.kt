package com.mancel.yann.arclever.views.activities

import android.os.Bundle
import androidx.annotation.LayoutRes
import androidx.appcompat.app.AppCompatActivity

/**
 * Created by Yann MANCEL on 08/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.views.activities
 *
 * An abstract [AppCompatActivity] subclass.
 */
abstract class BaseActivity : AppCompatActivity() {

    // METHODS -------------------------------------------------------------------------------------

    /** Gets the integer value of the activity layout */
    @LayoutRes
    protected abstract fun getActivityLayout(): Int

    /** Calls this method by [AppCompatActivity]#onCreate(Bundle?) call */
    protected abstract fun doOnCreate()

    // -- AppCompatActivity --

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.setContentView(this.getActivityLayout())
        this.doOnCreate()
    }
}