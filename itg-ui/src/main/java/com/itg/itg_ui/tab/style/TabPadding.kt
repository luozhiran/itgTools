package com.itg.itg_ui.tab.style

/**
 * 四向内边距（dp 单位），用于 Tab 项内边距配置。
 *
 * 所有字段默认 0dp —— 零行声明 = 不改变现有 padding。
 * 提供 [companion] 工厂方法简化常见场景。
 */
data class TabPadding(
    val leftDp: Float = 0f,
    val topDp: Float = 0f,
    val rightDp: Float = 0f,
    val bottomDp: Float = 0f,
) {
    companion object {
        /** 均匀内边距（四边相同） */
        fun all(dp: Float) = TabPadding(dp, dp, dp, dp)

        /** 水平内边距（左右相等，上下为 0） */
        fun horizontal(dp: Float) = TabPadding(dp, 0f, dp, 0f)

        /** 垂直内边距（上下相等，左右为 0） */
        fun vertical(dp: Float) = TabPadding(0f, dp, 0f, dp)
    }
}
