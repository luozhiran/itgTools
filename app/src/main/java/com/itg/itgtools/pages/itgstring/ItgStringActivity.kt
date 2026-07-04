package com.itg.itgtools.pages.itgstring

import android.os.Bundle
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.databinding.ActivityItgStringBinding
import com.itg.itgtools.route.RoutePath
import com.therouter.router.Route

@Route(path = RoutePath.ITG_STRING_ACTIVITY)
class ItgStringActivity : AutoBindingBaseActivity<ActivityItgStringBinding, ItgStringModel>() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}