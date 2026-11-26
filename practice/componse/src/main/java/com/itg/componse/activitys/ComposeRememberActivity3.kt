package com.itg.componse.activitys

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


public class ComposeRememberActivity3 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            RememberScene2()
        }
    }


    /**
     *  rememberLazyListState
     *  **创建并记住一个 LazyListState，专门给 LazyColumn / LazyRow 使用，
     *  用来读取、控制列表滚动，并且自带 rememberSaveable，旋转 / 配置变更后滚动位置不会丢
     *
     *  LazyListState 能干什么（拿到 state 之后）
     *
     *  1. 读滚动信息（读取这些属性会触发重组）
     *
     * - `firstVisibleItemIndex`：当前第一个可见 item 下标
     * - `firstVisibleItemScrollOffset`：第一个 item 内部偏移 px
     * - `layoutInfo`：完整布局信息（可见 item 集合、总 item 数等）
     * - `canScrollForward` / `canScrollBackward`：能不能继续往下 / 往上滑
     *
     *  2. 代码控制滚动（协程挂起函数，要在协程里调用）
     *
     * - `scrollToItem(index, offset)`：直接跳到指定 item（无动画）
     * - `animateScrollToItem(index, offset)`：平滑滚动到 item（带动画）
     */
    @Composable
    fun RememberScene2(){
        val listState = rememberLazyListState()
        LazyColumn(state = listState) {
            items(100) { Text("item $it") }
        }
        // 回到顶部按钮
        Button(onClick = {
            // 需要协程，用LaunchedEffect或者rememberCoroutineScope
            CoroutineScope(Dispatchers.Main).launch {
                listState.animateScrollToItem(0)
            }
        }) {
            Text("回到顶部")
        }
    }



}
