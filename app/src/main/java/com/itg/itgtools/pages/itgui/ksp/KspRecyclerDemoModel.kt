package com.itg.itgtools.pages.itgui.ksp

import android.app.Application
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import com.example.itg_base.arch.ItgModel
import com.itg.itg_ui.recycler.ItgListItem

class KspRecyclerDemoModel(app: Application) : ItgModel(app) {
    private val _rows = MutableLiveData<List<ItgListItem>>(initialRows())
    val rows: LiveData<List<ItgListItem>> = _rows
    private var nextId = 100L
    private var renameCount = 1

    fun renameUser() {
        renameCount++
        _rows.value = _rows.value.orEmpty().map {
            if (it is KspUserRow && it.stableId == 1L) {
                it.copy(name = "KSP 用户 A · 更新 $renameCount")
            } else {
                it
            }
        }
    }

    fun addItem() {
        val id = nextId++
        _rows.value = _rows.value.orEmpty() + KspDataRow(id, "KSP 动态新增条目 #$id")
    }

    fun clearItems() {
        _rows.value = emptyList()
    }

    private fun initialRows(): List<ItgListItem> = listOf(
        KspBannerRow(10, "KSP 生成式 registry：ViewBinding / DataBinding / Payload"),
        KspUserRow(1, "KSP 用户 A"),
        KspAutoRow(15, "自动字段绑定", "不用写 @ItgBind，直接生成 TextView 赋值"),
        KspDataRow(20, "KSP DataBinding 自动注册"),
        KspUserRow(2, "KSP 用户 B"),
    )
}
