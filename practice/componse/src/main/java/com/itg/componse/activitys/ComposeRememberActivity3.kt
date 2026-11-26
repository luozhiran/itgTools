package com.itg.componse.activitys

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.itg.componse.R
import com.itg.componse.ui.theme.ItgToolsTheme
import com.itg.itg_auxiliary.L
import kotlinx.coroutines.launch

class ComposeRememberActivity3 : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItgToolsTheme {
                LazyListScreen(onBack = ::finish)
            }
        }
    }
}

@Composable
private fun LazyListScreen(onBack: () -> Unit) {
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
     */
    val listState = rememberLazyListState()
    /**
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
    val scope = rememberCoroutineScope()
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val showBackToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    /**
     * LaunchedEffect()
     *  在 Composable 里安全启动一个协程副作用；依靠 key 控制协程什么时候重启、什么时候自动取消。
     *  核心规则
     *  1. **首次进入组合**：执行一次，启动协程跑 `block`
     * 2. **任意一个 key 发生变化**：
     *    - 先把上一次正在跑的协程 cancel
     *    - 再重新执行 block，开新协程
     * 3. **Composable 从 UI 树上移除（页面消失、if 隐藏）**：自动 cancel 当前协程，不会泄漏
     * 4. **单纯重组、key 没变**：啥也不干，协程继续跑
     *      和 `rememberCoroutineScope` 对比一句话：
     *          - LaunchedEffect：**自动触发协程**（key 变 / 进入页面就跑）
     *          - rememberCoroutineScope：**手动 launch**（点击、手势回调里自己调用）
     */
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
            L.e("当前首个可见：$index")
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { listState.layoutInfo }.collect { layoutInfo ->
            val total = layoutInfo.totalItemsCount
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            if (total > 0 && lastVisible >= total - 3) {
                L.e("接近列表末尾：$lastVisible / $total")
            }
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            Surface(shadowElevation = 2.dp, modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onBack) {
                        Text(stringResource(R.string.compose_back))
                    }
                    Text(
                        text = stringResource(R.string.compose_lazy_list_title),
                        modifier = Modifier.padding(start = 8.dp),
                        style = MaterialTheme.typography.titleLarge,
                    )
                }
            }
        },
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = stringResource(R.string.compose_lazy_list_intro_title),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Text(
                        text = stringResource(R.string.compose_lazy_list_intro_description),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                    Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surface) {
                        Text(
                            text = stringResource(
                                R.string.compose_lazy_list_position,
                                firstVisibleIndex + 1,
                                100,
                            ),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Text(
                text = stringResource(R.string.compose_lazy_list_scroll_hint),
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 8.dp),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Box(modifier = Modifier.weight(1f)) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 96.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    items(count = 100, key = { it }) { index ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            ),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(16.dp),
                                horizontalArrangement = Arrangement.spacedBy(16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.secondaryContainer,
                                ) {
                                    Text(
                                        text = (index + 1).toString().padStart(2, '0'),
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                        style = MaterialTheme.typography.labelLarge,
                                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                                    )
                                }
                                Column {
                                    Text(
                                        text = stringResource(R.string.compose_lazy_list_item, index + 1),
                                        style = MaterialTheme.typography.titleMedium,
                                    )
                                    Text(
                                        text = stringResource(R.string.compose_lazy_list_item_hint),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                }

                if (showBackToTop) {
                    ExtendedFloatingActionButton(
                        text = { Text(stringResource(R.string.compose_lazy_list_back_to_top)) },
                        icon = { Text("↑") },
                        onClick = { scope.launch { listState.animateScrollToItem(0) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
                    )
                }
            }
        }
    }
}
