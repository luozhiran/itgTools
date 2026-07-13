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
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        findViewById<android.view.View>(R.id.openWebCacheDemoButton).setOnClickListener {
            startActivity(Intent(this, WebCacheDemoActivity::class.java))
        }
    }
}
