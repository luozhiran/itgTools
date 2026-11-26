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
     * rememberLazyListState() 创建并记住供 LazyColumn / LazyRow 使用的 LazyListState。
     * 它通过 rememberSaveable 保存滚动位置；在可保存状态恢复时（如旋转后）恢复首项索引和偏移。
     *
     * LazyListState 可读取滚动信息，也可控制滚动：
     *
     * 1. 读取信息：这些属性是可观察状态；在组合中读取时，值变化会使读取处重组。
     *    若只需监听并执行副作用，可用 snapshotFlow，避免在组合中频繁读取 layoutInfo。
     * - `firstVisibleItemIndex`：滚动视口内首个可见 item 的下标，不包含 contentPadding 区域中的 item
     * - `firstVisibleItemScrollOffset`：首个可见 item 的滚动偏移，单位为 px
     * - `layoutInfo`：最近一次布局的信息，包括可见 item、总 item 数等
     * - `canScrollForward` / `canScrollBackward`：是否还能向后 / 向前滚动
     *
     * 2. 控制滚动：以下都是挂起函数，需要在协程中调用。
     * - `scrollToItem(index, offset)`：立即跳到指定 item（无动画）
     * - `animateScrollToItem(index, offset)`：平滑滚动到指定 item（带动画）
     */
    val listState = rememberLazyListState()
    /**
     * rememberCoroutineScope() 返回绑定到当前调用位置的组合感知 CoroutineScope。
     * 该调用离开组合时，作用域及其正在运行的子协程会被取消；这与 Activity 的生命周期不同。
     * 未指定调度器时，使用当前 Composition 的 Recomposer 的 applying dispatcher，
     * 不应假定它一定是 Dispatchers.Main.immediate。
     * 重组时返回同一个 CoroutineScope，适合在点击等回调里手动 launch 滚动动画。
     */
    val scope = rememberCoroutineScope()
    val firstVisibleIndex by remember { derivedStateOf { listState.firstVisibleItemIndex } }
    val showBackToTop by remember { derivedStateOf { listState.firstVisibleItemIndex > 2 } }

    /**
     * LaunchedEffect(listState) 进入组合时启动协程，用来收集列表状态变化。
     * key 变化时取消原协程并重新启动；离开组合时取消协程。
     * 仅发生重组而 key 不变时，原协程继续运行，不会重新启动。
     * 与 rememberCoroutineScope 不同，这里由组合自动启动协程；点击回调中的任务则手动 launch。
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
