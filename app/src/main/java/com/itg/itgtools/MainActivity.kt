package com.itg.itgtools

import android.content.Intent
import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.pages.itgui.ItgUiExamplesActivity
import com.itg.itgtools.databinding.ActivityMainBinding
import com.itg.itgtools.route.RoutePath
import com.therouter.router.Route

@Route(path = RoutePath.MAIN_HOME_ACTIVITY)
class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.openItgUiExamples.setOnClickListener {
            startActivity(Intent(this, ItgUiExamplesActivity::class.java))
        }
    }
}
