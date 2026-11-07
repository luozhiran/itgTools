package com.itg.rnbussiness

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.FragmentManager
import com.itg.rncontainer.ItgRnContainer
import com.itg.rncontainer.ItgRnInstance
import com.itg.rncontainer.ItgRnPage

/** Typed entry point for the business JS component shipped in this AAR. */
object ItgRnBusiness {
    const val COMPONENT = "ItgRnBusiness"
    const val ORDER_DETAIL = "orderDetail"

    fun orderDetailPage(orderId: String): ItgRnPage {
        require(orderId.isNotBlank()) { "orderId is required" }
        return ItgRnPage(COMPONENT, ORDER_DETAIL, Bundle().apply { putString("orderId", orderId) })
    }

    fun orderDetailIntent(activity: Activity, orderId: String): Intent =
        ItgRnContainer.intent(activity, orderDetailPage(orderId))

    fun openOrderDetail(activity: Activity, orderId: String) =
        ItgRnContainer.launch(activity, orderDetailPage(orderId))

    fun attachOrderDetail(manager: FragmentManager, containerViewId: Int, orderId: String): ItgRnInstance =
        ItgRnContainer.attach(manager, containerViewId, orderDetailPage(orderId))
}
