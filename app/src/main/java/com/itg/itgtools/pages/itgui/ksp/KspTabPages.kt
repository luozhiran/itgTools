package com.itg.itgtools.pages.itgui.ksp

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.itg.itg_ui.tab.BaseTabFragment
import com.itg.itg_ksp.annotations.ItgTabItem
import com.itg.itgtools.databinding.FragmentItgUiPageBinding
import com.itg.itgtools.pages.itgui.ItgUiDemoModel

open class KspTabPageFragment : BaseTabFragment<FragmentItgUiPageBinding, ItgUiDemoModel>() {
    protected open val pageTitle: String = "KSP Tab"
    protected open val pageDescription: String = "Generated TabItem demo"
    protected open val pageColor: Int = Color.WHITE

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pageTitle.text = pageTitle
        binding.pageDescription.text = pageDescription
        binding.container.setBackgroundColor(pageColor)
        binding.pageAction.setOnClickListener { messages.toast("$pageTitle clicked") }
    }

    override fun onTabFirstVisible() {
        binding.lifecycleLog.append("\n$pageTitle first visible")
    }

    override fun onTabSelected() {
        binding.lifecycleLog.append("\n$pageTitle selected")
    }

    override fun onTabUnselected() {
        binding.lifecycleLog.append("\n$pageTitle unselected")
    }
}

@ItgTabItem(
    groupName = KSP_TAB_GROUP,
    title = "首页",
    order = 0,
)
class KspTabHomeFragment : KspTabPageFragment() {
    override val pageTitle = "KSP 首页"
    override val pageDescription = "Tab 列表由 KSP 生成"
    override val pageColor = 0xFFE3F2FD.toInt()
}

@ItgTabItem(
    groupName = KSP_TAB_GROUP,
    title = "动态",
    order = 1,
)
class KspTabFeedFragment : KspTabPageFragment() {
    override val pageTitle = "KSP 动态"
    override val pageDescription = "Fragment 声明上直接挂注解"
    override val pageColor = 0xFFE8F5E9.toInt()
}

@ItgTabItem(
    groupName = KSP_TAB_GROUP,
    title = "我的",
    order = 2,
)
class KspTabProfileFragment : KspTabPageFragment() {
    override val pageTitle = "KSP 我的"
    override val pageDescription = "Host 只保留 generated 函数调用"
    override val pageColor = 0xFFFFF3E0.toInt()
}

const val KSP_TAB_GROUP = "KspDemo"
