package com.mancel.yann.arclever.views.activities

import androidx.navigation.NavController
import androidx.navigation.findNavController
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.navigateUp
import androidx.navigation.ui.setupActionBarWithNavController
import com.mancel.yann.arclever.R
import com.mancel.yann.arclever.utils.FullScreenTools

/**
 * Created by Yann MANCEL on 08/08/2020.
 * Name of the project: ARClever
 * Name of the package: com.mancel.yann.arclever.views.activities
 *
 * A [BaseActivity] subclass.
 */
class MainActivity : BaseActivity() {

    // FIELDS --------------------------------------------------------------------------------------

    private lateinit var _navController: NavController
    private lateinit var _appBarConfiguration: AppBarConfiguration

    // METHODS -------------------------------------------------------------------------------------

    // -- BaseActivity --

    override fun getActivityLayout(): Int = R.layout.activity_main

    override fun doOnCreate() = this.configureActionBarForNavigation()

    // -- Activity --

    override fun onSupportNavigateUp(): Boolean {
        return this._navController.navigateUp(this._appBarConfiguration)
                || super.onSupportNavigateUp()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) FullScreenTools.hideSystemUI(this@MainActivity)
    }

    // -- Action bar --

    /** Configures the Action bar for the navigation */
    private fun configureActionBarForNavigation() {
        // NavController
        this._navController = this.findNavController(R.id.activity_main_NavHostFragment)

        // AppBarConfiguration
        this._appBarConfiguration = AppBarConfiguration(this._navController.graph)

        // Action bar
        this.setupActionBarWithNavController(this._navController, this._appBarConfiguration)
    }
}