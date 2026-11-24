package com.itg.itgtools

import android.content.Intent
import android.os.Bundle
import androidx.recyclerview.widget.ConcatAdapter
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.itg.itg_base.AutoBindingBaseActivity
import com.itg.itgtools.pages.itgdialog.DialogQueueDemoActivity
import com.itg.itgtools.pages.itgui.ItgUiExamplesActivity
import com.itg.itgtools.pages.matrixtrace.MatrixTraceDemoActivity
import com.itg.itgtools.databinding.ActivityMainBinding
import com.itg.itgtools.route.RoutePath
import com.therouter.router.Route

@Route(path = RoutePath.MAIN_HOME_ACTIVITY)
class MainActivity : AutoBindingBaseActivity<ActivityMainBinding, MainModel>() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val exploreAdapter = HomeExploreAdapter { item ->
            startActivity(Intent(this, item.destination))
        }
        binding.main.layoutManager = LinearLayoutManager(this)
        binding.main.itemAnimator = DefaultItemAnimator().apply {
            addDuration = 220L
            removeDuration = 180L
            moveDuration = 200L
            changeDuration = 180L
        }
        binding.main.adapter = ConcatAdapter(HomeHeaderAdapter(), exploreAdapter)

        exploreAdapter.submitList(
            listOf(
                ExploreItem(R.string.home_ui_title, R.string.home_ui_description, ItgUiExamplesActivity::class.java),
                ExploreItem(R.string.home_dialog_title, R.string.home_dialog_description, DialogQueueDemoActivity::class.java),
                ExploreItem(R.string.home_matrix_title, R.string.home_matrix_description, MatrixTraceDemoActivity::class.java),
            ),
        )
    }
}
