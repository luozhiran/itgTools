package com.itg.itg_ksp.annotations

@Target(AnnotationTarget.PROPERTY)
@Retention(AnnotationRetention.SOURCE)
annotation class ItgAutoTextField(
    val viewName: String = "",
    val payloadKey: String = "",
)
