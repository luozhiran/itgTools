package com.itg.itg_ksp.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ItgTabItem(
    val groupName: String,
    val title: String = "",
    val titleResExpression: String = "",
    val iconResExpression: String = "",
    val argumentsExpression: String = "",
    val badgeCount: Int = -1,
    val badgeDot: Boolean = false,
    val order: Int = 0,
)
