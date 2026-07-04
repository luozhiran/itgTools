package com.itg.itgtools.pages.itgui

import android.graphics.Color
import android.os.Bundle
import android.view.View
import com.itg.itg_base.AutoBindingBaseFragment
import com.itg.itg_ui.tab.BaseTabFragment
import com.itg.itgtools.databinding.FragmentItgUiPageBinding

class DemoPageFragment : BaseTabFragment<FragmentItgUiPageBinding, ItgUiDemoModel>() {
    private val title: String get() = arguments?.getString(ARG_TITLE).orEmpty()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pageTitle.text = title
        binding.pageDescription.text = arguments?.getString(ARG_DESCRIPTION)
        binding.container.setBackgroundColor(arguments?.getInt(ARG_COLOR, Color.WHITE) ?: Color.WHITE)
        binding.pageAction.setOnClickListener { messages.toast("$title：页面内事件已触发") }
    }

    override fun onTabFirstVisible() {
        appendLog("首次可见 → 懒加载")
    }

    override fun onTabSelected() {
        appendLog("选中")
    }

    override fun onTabUnselected() {
        appendLog("取消选中")
    }

    private fun appendLog(message: String) {
        if (view == null) return
        binding.lifecycleLog.append("\n$message")
    }

    companion object {
        private const val ARG_TITLE = "title"
        private const val ARG_DESCRIPTION = "description"
        private const val ARG_COLOR = "color"

        fun arguments(title: String, description: String, color: Int = Color.WHITE) = Bundle().apply {
            putString(ARG_TITLE, title)
            putString(ARG_DESCRIPTION, description)
            putInt(ARG_COLOR, color)
        }
    }
}

/** 展示不继承 BaseTabFragment 时，普通 Fragment 仍可由 Adapter 安全管理。 */
class PlainDemoFragment : AutoBindingBaseFragment<FragmentItgUiPageBinding, ItgUiDemoModel>() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.pageTitle.text = "普通 Fragment"
        binding.pageDescription.text = "继承 AutoBindingBaseFragment，不接收 Tab 可见性回调"
        binding.lifecycleLog.text = "由 TabViewPagerAbility 安全跳过 BaseTabFragment 回调"
        binding.pageAction.setOnClickListener { messages.toast("普通 Fragment 事件") }
    }
}
