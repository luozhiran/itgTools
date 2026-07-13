package com.itg.itg_ksp.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ItgTabHost(
    val groupName: String,
    val tabMode: Int = 1,
    val tabGravity: Int = 0,
    val defaultPosition: Int = 0,
    val swipeable: Boolean = true,
    val offscreenPageLimit: Int = 1,
    val autoTitle: Boolean = true,
    val lazyLoadOnFirstSelect: Boolean = true,
)
