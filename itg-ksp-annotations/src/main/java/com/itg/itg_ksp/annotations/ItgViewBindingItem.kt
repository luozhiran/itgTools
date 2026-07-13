package com.itg.itg_ksp.annotations

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class ItgViewBindingItem(
    val bindingClassName: String,
    val actionsClassName: String,
    /**
     * 普通业务模型用于标识 Item 的已有非空属性名，例如 "id" 或 "orderNo"。
     * 实现 ItgListItem 时可留空，继续使用 stableId。
     */
    val itemKeyProperty: String = "",
)
