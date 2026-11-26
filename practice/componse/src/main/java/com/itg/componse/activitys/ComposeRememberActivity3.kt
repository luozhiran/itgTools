package com.itg.componse.activitys

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch


public class ComposeRememberActivity3 : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Column(modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)) {
                Box(modifier = Modifier.fillMaxWidth().height(200.dp)) {
                    RememberScene2()
                }

            }

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
     *
     *
     * rememberCoroutineScope()
     * 在 **@Composable 里面拿到一个协程作用域，这个作用域和当前 Composable 的生命周期绑定；
     * Composable 退出（从 UI 树上移除），scope 自动 cancel**。
     * 核心特性
     * 1. **生命周期绑定到这个 Composable**
     *      当这个 Composable 被移除组合（页面退出、if 条件不成立、被折叠），scope 里面所有正在跑的协程自动取消，避免泄漏。
     *
     * 2. **调度器默认是 `Dispatchers.Main.immediate`**
     *      适合 UI 相关任务：滚动、弹窗、动画、点击触发的异步。
     *
     * 3. **它是 remember 缓存的**
     *      重组不会重新生成新的 Scope，同一个 Composable 多次重组拿到的是同一个 scope。
     *
     */
    @Composable
    fun RememberScene2() {
        val listState = rememberLazyListState()
        val coroutineScope = rememberCoroutineScope()
        LazyColumn(state = listState) {
            items(100) { Text("item $it") }
        }
        // 回到顶部按钮
        Button(onClick = {
            coroutineScope.launch {
                // animateScrollToItem需要在协程中使用，因为 animateScrollToItem是suspend方法
                listState.animateScrollToItem(0)
            }
        }) {
            Text("回到顶部")
        }
    }


}
