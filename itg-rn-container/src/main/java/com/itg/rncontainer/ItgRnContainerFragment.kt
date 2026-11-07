package com.itg.rncontainer

import android.os.Bundle
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.facebook.react.ReactFragment

/** A per-page Android Fragment. The RN runtime is supplied by the host Application. */
class ItgRnContainerFragment : Fragment() {
    val instanceId: String get() = requireArguments().getString(ARG_INSTANCE)!!
    val pageId: String get() = requireArguments().getString(ARG_PAGE)!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_itg_rn_container, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        if (childFragmentManager.findFragmentByTag(RN_TAG) == null) {
            val page = ItgRnPage(
                requireArguments().getString(ARG_COMPONENT)!!,
                pageId,
                requireArguments().getBundle(ARG_PARAMS) ?: Bundle(),
                instanceId,
            )
            childFragmentManager.beginTransaction()
                .replace(R.id.itg_rn_content, ReactFragment.Builder()
                    .setComponentName(page.componentName)
                    .setLaunchOptions(page.launchOptions())
                    .build(), RN_TAG)
                .commitNow()
        }
    }

    fun onBackPressed(): Boolean = (childFragmentManager.findFragmentByTag(RN_TAG) as? ReactFragment)
        ?.onBackPressed() == true

    fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean =
        (childFragmentManager.findFragmentByTag(RN_TAG) as? ReactFragment)?.onKeyUp(keyCode, event) == true

    companion object {
        private const val RN_TAG = "itg-rn-renderer"
        private const val ARG_COMPONENT = "componentName"
        private const val ARG_PAGE = "pageId"
        private const val ARG_PARAMS = "params"
        private const val ARG_INSTANCE = "instanceId"

        fun newInstance(page: ItgRnPage): ItgRnContainerFragment = ItgRnContainerFragment().apply {
            arguments = Bundle().apply {
                putString(ARG_COMPONENT, page.componentName)
                putString(ARG_PAGE, page.pageId)
                putBundle(ARG_PARAMS, Bundle(page.params))
                putString(ARG_INSTANCE, page.instanceId)
            }
        }
    }
}
