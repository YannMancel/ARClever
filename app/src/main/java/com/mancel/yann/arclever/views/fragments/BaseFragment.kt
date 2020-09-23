package com.mancel.yann.arclever.views.fragments

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.LayoutRes
import androidx.fragment.app.Fragment

/**
 * Created by Yann MANCEL on 08/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.views.fragments
 *
 * An abstract [Fragment] subclass.
 */
abstract class BaseFragment : Fragment() {

    // FIELDS --------------------------------------------------------------------------------------

    protected lateinit var _rootView: View

    // METHODS -------------------------------------------------------------------------------------

    /** Gets the integer value of the fragment layout */
    @LayoutRes
    protected abstract fun getFragmentLayout(): Int

    /** Calls this method by [Fragment.onCreateView] call */
    protected abstract fun doOnCreateView()

    /** Calls this method by [Fragment.onResume] call */
    protected abstract fun doOnResume()

    /** Calls this method by [Fragment.onPause] call */
    protected abstract fun doOnPause()

    // -- Fragment --

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // Inflate the layout for this fragment
        this._rootView = inflater.inflate(this.getFragmentLayout(), container, false)

        this.doOnCreateView()

        return this._rootView
    }

    override fun onResume() {
        super.onResume()
        this.doOnResume()
    }

    override fun onPause() {
        super.onPause()
        this.doOnPause()
    }
}