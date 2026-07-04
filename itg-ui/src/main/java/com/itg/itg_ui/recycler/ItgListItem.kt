package com.itg.itg_ui.recycler

/**
 * RecyclerView 使用的不可变 UI 数据模型。
 *
 * [stableId] 必须在同一列表内全局唯一，并在该条目生命周期内保持不变。
 * 推荐业务层使用 data class 实现，以便默认内容比较可以直接使用 equals。
 */
interface ItgListItem {
    val stableId: Long
}
