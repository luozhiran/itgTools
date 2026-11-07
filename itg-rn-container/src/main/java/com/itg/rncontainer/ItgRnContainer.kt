package com.itg.rncontainer

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentManager

object ItgRnContainer {
    const val EXTRA_RESULT_JSON = "itg_rn_result_json"
    internal const val EXTRA_COMPONENT = "itg_rn_component"
    internal const val EXTRA_PAGE = "itg_rn_page"
    internal const val EXTRA_PARAMS = "itg_rn_params"
    internal const val EXTRA_INSTANCE = "itg_rn_instance"

    fun intent(activity: Activity, page: ItgRnPage): Intent =
        Intent(activity, ItgRnContainerActivity::class.java).apply {
            putExtra(EXTRA_COMPONENT, page.componentName)
            putExtra(EXTRA_PAGE, page.pageId)
            putExtra(EXTRA_PARAMS, Bundle(page.params))
            putExtra(EXTRA_INSTANCE, page.instanceId)
        }

    fun launch(activity: Activity, page: ItgRnPage) { activity.startActivity(intent(activity, page)) }

    fun attach(manager: FragmentManager, containerViewId: Int, page: ItgRnPage): ItgRnInstance {
        require(containerViewId != 0) { "containerViewId must be a real view ID" }
        check(!manager.isStateSaved) { "FragmentManager state is already saved" }
        val tag = tag(page.instanceId)
        require(manager.findFragmentByTag(tag) == null) { "instanceId is already attached: ${page.instanceId}" }
        manager.beginTransaction().add(containerViewId, ItgRnContainerFragment.newInstance(page), tag).commitNow()
        return ItgRnInstance(manager, page.instanceId)
    }

    fun find(manager: FragmentManager, instanceId: String): ItgRnInstance? =
        if (manager.findFragmentByTag(tag(instanceId)) is ItgRnContainerFragment) ItgRnInstance(manager, instanceId) else null

    internal fun tag(id: String) = "itg-rn:$id"
    fun resultKey(id: String) = "itg-rn:result:$id"
}
