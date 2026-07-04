package com.itg.itgtools

import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.databinding.ActivityMainBinding
import com.itg.itgtools.route.RoutePath
import com.therouter.router.Route

@Route(path = RoutePath.MAIN_HOME_ACTIVITY)
class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}