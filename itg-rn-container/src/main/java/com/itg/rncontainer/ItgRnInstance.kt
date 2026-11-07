package com.itg.rncontainer

import androidx.fragment.app.FragmentManager

/** A handle for the Fragment in one slot. Discard it across configuration changes. */
class ItgRnInstance internal constructor(
    private val manager: FragmentManager,
    val instanceId: String,
) {
    val fragment: ItgRnContainerFragment?
        get() = manager.findFragmentByTag(ItgRnContainer.tag(instanceId)) as? ItgRnContainerFragment

    val resultKey: String get() = ItgRnContainer.resultKey(instanceId)

    fun detach() {
        check(!manager.isStateSaved) { "FragmentManager state is already saved" }
        fragment?.let { manager.beginTransaction().remove(it).commit() }
    }
}
