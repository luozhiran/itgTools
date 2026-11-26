package com.itg.componse.activitys

import android.app.Application
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.snapshots.ObserverHandle
import androidx.compose.runtime.snapshots.Snapshot
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.Observer
import androidx.lifecycle.viewModelScope
import com.itg.componse.R
import com.itg.componse.ui.theme.ItgToolsTheme
import com.itg.itg_auxiliary.L
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

class ComposeViewModel(application: Application) : AndroidViewModel(application) {
    var count by mutableIntStateOf(0)

    // 派生状态：只有满足条件才会变更
    private val countOver10State = derivedStateOf { count > 10 }
    val isCountOver10: Boolean get() = countOver10State.value


    init {
        snapshotFlow { countOver10State.value }
            .onEach { newCount ->
                L.e("snapshotFlowCount", "是否大于10：$newCount")
            }.launchIn(viewModelScope)
    }


    private val _snapCount = mutableIntStateOf(0)
    var snapCount by _snapCount

    private var unregister: ObserverHandle? = null

    init {
        unregister = Snapshot.registerApplyObserver { changedObjects, _ ->
            if (_snapCount in changedObjects) {
                L.e("registerApplyObserver", "底层快照监听，count=${_snapCount.intValue}")
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        unregister?.dispose() // 必须手动注销！内存泄漏重灾区
    }


    private val _countState = mutableIntStateOf(0)
    var countBridge by _countState
     val countLd = MutableLiveData<Int>()

    init {
        snapshotFlow { _countState.intValue }
            .onEach { countLd.postValue(it) }
            .launchIn(viewModelScope)
    }
}


class ComposeRememberActivity2 : AppCompatActivity() {
    private val vm: ComposeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItgToolsTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        Surface(shadowElevation = 2.dp, modifier = Modifier.statusBarsPadding()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 8.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                TextButton(onClick = ::finish) {
                                    Text(stringResource(R.string.compose_back))
                                }
                                Text(
                                    text = stringResource(R.string.compose_list_remember_title2),
                                    modifier = Modifier.padding(start = 8.dp),
                                    style = MaterialTheme.typography.titleLarge,
                                )
                            }
                        }
                    },
                ) { innerPadding ->
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .verticalScroll(rememberScrollState())
                            .padding(horizontal = 20.dp, vertical = 20.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(
                            text = "点击各卡片的按钮，观察状态变化；按提示在 Logcat 查看监听日志。",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        RememberScene1()
                        RememberScene2()
                        RememberScene3()
                        RememberScene4()
                        RememberScene5()
                    }
                }
            }
        }
    }


    /**
     * `SideEffect`（Compose UI 层监听，只能在 @Composable）
     *
     * 特点：【每次重组完成之后执行】，读到的 state 发生变化、触发重组后，SideEffect 跑一次；
     * 适合 UI 侧埋点、上报，【不能放到 ViewModel 里】。
     *
     * 重大局限：
     * 1. 不是 “值一变立刻回调”，是**重组完成才跑**；短时间连续多次修改，中间值会被吞掉，只拿到最后一次
     * 2. 只能在 Composable，ViewModel 里不能用
     * 3. 只要重组就会执行，哪怕值没变（如果你在别的地方读 state 触发重组）
     */
    @Composable
    fun RememberScene1() {
        var count by remember { mutableIntStateOf(0) }
        SideEffect {
            // 重组后拿到最新count
            L.e("SideEffect", "count=$count")
        }
        ExperimentCard(
            number = "01",
            title = "SideEffect · 重组完成后",
            description = "点击触发重组；SideEffect 会在界面更新完成后输出计数。",
            count = count,
            detail = "在 Logcat 搜索 SideEffect 查看回调。",
            onIncrement = { count++ },
        )
    }

    /**
     * `derivedStateOf` 派生状态 + 再监听派生 State
     * `derivedStateOf` 本身是生成一个新 State，**只有计算结果真的变化才更新**，
     * 用来过滤高频变更；你再对这个派生 state 做监听（snapshotFlow/setter 都行
     * 适合：count 频繁变，但你只关心某些条件（比如 count>10）
     *
     * ✅ 优势：天然去抖动、过滤无效变更；滚动、拖拽高频状态首选
     * ❌ 缺点：它不是直接监听原值，是派生；单纯监听原值变化没必要上它
     */
    @Composable
    fun RememberScene2() {
        ExperimentCard(
            number = "02",
            title = "derivedStateOf · 阈值判断",
            description = "原始计数每次都变；派生条件只在跨过 10 时改变。",
            count = vm.count,
            detail = "count > 10：${if (vm.isCountOver10) "是" else "否"} · 日志标签 snapshotFlowCount",
            onIncrement = { vm.count++ },
            extraActionLabel = "设置为 11",
            onExtraAction = { vm.count = 11 },
        )
    }


    /**
     * 底层原生快照 API：`Snapshot.registerApplyObserver`（最底层，snapshotFlow 内部就是基于它实现）
     * Compose 快照系统原始回调：每次**快照 apply 提交**的时候回调，拿到这一轮所有被修改的 StateObject 集合
     *
     * 适合框架封装、自定义监听工具；业务代码**一般不推荐直接手写**，坑很多：线程、去重、手动注销
     *
     * 坑：
     * 1. 回调在**apply 所在线程执行**，不在主线程也可能跑，自己要切 Dispatcher
     * 2. 是批量回调，一轮快照里改 N 个 state 一次性通知
     * 3. 不会自动去重，相同值赋值也会进回调
     * 4. 忘记注销直接内存泄漏
     *
     * 一句话：**snapshotFlow 就是官方帮你封装好的 registerApplyObserver + 去重 + 协程**，业务优先 snapshotFlow
     */
    @Composable
    fun RememberScene3() {
        ExperimentCard(
            number = "03",
            title = "Snapshot · 原生快照监听",
            description = "修改状态后，底层快照观察器收到提交事件。",
            count = vm.snapCount,
            detail = "在 Logcat 搜索 registerApplyObserver；ViewModel 清理时会注销观察器。",
            onIncrement = { vm.snapCount++ },
        )
    }

    /**
     * 委托包装器（自定义 `onChange` delegate，语法糖）
     * 把 `mutableIntStateOf` 包一层委托，声明变量的时候直接跟回调，不用写 init 块，非常好看
     *
     * ✅ 优点：写变量一行带回调，和你最开始 `var count by xxx` 风格完全统一
     * ❌ 缺点：回调是**在赋值线程同步执行**，和自定义 setter 一样；不能拿到快照合并后的结果；后台快照修改 state 的时候行为和 snapshotFlow 不一样
     */
    fun <T> mutableStateOfWithOnChange(
        init: T,
        onChange: (old: T, new: T) -> Unit,
    ): ReadWriteProperty<Any?, T> =
        object : ReadWriteProperty<Any?, T> {
            private val state = mutableStateOf(init)
            override fun getValue(thisRef: Any?, property: KProperty<*>): T = state.value
            override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
                val old = state.value
                if (old != value) {
                    onChange(old, value)
                }
                state.value = value
            }
        }

    @Composable
    fun RememberScene4() {
        val countDelegate: ReadWriteProperty<Any?, Int> = remember {
            mutableStateOfWithOnChange(0) { old, new ->
                L.e("delegateOnChange", "委托回调：$old → $new")
            }
        }
        var count by countDelegate
        ExperimentCard(
            number = "04",
            title = "自定义委托 · 赋值时回调",
            description = "每次赋新值时立即执行 onChange，并更新界面。",
            count = count,
            detail = "在 Logcat 搜索 delegateOnChange 查看旧值与新值。",
            onIncrement = { count++ },
        )
    }

    /**
     * LiveData 桥接（mutableStateOf ↔ MutableLiveData）
     * 如果你项目大量老 LiveData 代码，桥接一层：
     *
     * 然后用 LiveData 的 observe 监听。属于兼容方案，新项目不推荐。
     */
    @Composable
    fun RememberScene5() {
        var observedCount by remember { mutableStateOf<Int?>(vm.countLd.value) }
        DisposableEffect(vm.countLd) {
            val observer = Observer<Int> { observedCount = it }
            vm.countLd.observe(this@ComposeRememberActivity2, observer)
            onDispose { vm.countLd.removeObserver(observer) }
        }
        ExperimentCard(
            number = "05",
            title = "LiveData · 状态桥接",
            description = "Compose 状态变化经 snapshotFlow 发布到 LiveData。",
            count = vm.countBridge,
            detail = "LiveData 观察到：${observedCount?.toString() ?: "等待首次值"}",
            onIncrement = { vm.countBridge++ },
        )
    }


}

@Composable
private fun ExperimentCard(
    number: String,
    title: String,
    description: String,
    count: Int,
    detail: String,
    onIncrement: () -> Unit,
    extraActionLabel: String? = null,
    onExtraAction: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = number,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
                Text(
                    text = title,
                    modifier = Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        text = "当前计数",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = count.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Button(onClick = onIncrement) { Text("计数 +1") }
            }
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (extraActionLabel != null && onExtraAction != null) {
                OutlinedButton(onClick = onExtraAction) { Text(extraActionLabel) }
            }
        }
    }
}
