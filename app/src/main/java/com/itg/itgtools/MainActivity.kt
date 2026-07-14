package com.itg.itgtools

import android.content.Intent
import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.pages.itgui.ItgUiExamplesActivity
import com.itg.itgtools.databinding.ActivityMainBinding
import com.itg.itgtools.route.RoutePath
import com.itg.itgtools.route.RoutePath.ITG_UI_ACTIVITY
import com.itg.itgtools.route.RoutePath.ITG_FILE_TEST_ACTIVITY
import com.itg.itgtools.route.RoutePath.ITG_WEB_CACHE_ACTIVITY
import com.itg.itgtools.webcache.WebCacheDemoActivity
import com.therouter.TheRouter
import com.therouter.router.Route

@Route(path = RoutePath.MAIN_HOME_ACTIVITY)
class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding.openItgUiExamples.setOnClickListener {
            TheRouter.build(ITG_UI_ACTIVITY).navigation(this)
        }

        findViewById<android.view.View>(R.id.openWebCacheDemoButton).setOnClickListener {
            TheRouter.build(ITG_WEB_CACHE_ACTIVITY).navigation(this)
        }

        findViewById<android.view.View>(R.id.openFileTestButton).setOnClickListener {
            TheRouter.build(ITG_FILE_TEST_ACTIVITY).navigation(this)
        }
    }
}
