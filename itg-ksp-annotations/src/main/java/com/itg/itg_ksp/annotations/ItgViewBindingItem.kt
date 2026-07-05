package com.itg.itg_ksp.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ItgViewBindingItem(
    val bindingClassName: String,
    val actionsClassName: String,
)
