package com.itg.itg_ksp.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ItgDataBindingItem(
    val layoutExpression: String,
    val itemVariableExpression: String,
    val actionsVariableExpression: String = "0",
    val actionsClassName: String,
)
