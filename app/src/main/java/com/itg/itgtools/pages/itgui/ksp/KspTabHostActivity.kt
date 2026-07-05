package com.itg.itgtools.pages.itgui.ksp

import android.os.Bundle
import com.itg.itg_ksp.annotations.ItgTabHost
import com.itg.itgtools.databinding.ActivityItgUiTabsBinding
import com.itg.itgtools.pages.itgui.ItgUiDemoModel
import com.itg.itg_ui.tab.TabConfig
import com.itg.itg_ui.tab.TabHostActivity
import com.itg.itg_ui.tab.TabItem

@ItgTabHost(
    groupName = KSP_TAB_GROUP,
    tabMode = 1,
    tabGravity = 0,
    defaultPosition = 0,
    swipeable = true,
    offscreenPageLimit = 1,
    autoTitle = true,
    lazyLoadOnFirstSelect = true,
)
class KspTabHostActivity : TabHostActivity<ActivityItgUiTabsBinding, ItgUiDemoModel>() {
    override fun onCreateTabs(): List<TabItem<*>> = createKspDemoTabItems()

    override fun onCreateTabConfig(): TabConfig = createKspDemoTabConfig()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }
}
