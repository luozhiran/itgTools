package com.itg.componse.activitys

import android.app.Application
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itg.itg_auxiliary.L
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/** 提供计数状态，演示自定义 setter、snapshotFlow 和 StateFlow。 */
class RememberViewModel(application: Application) : AndroidViewModel(application) {
    var count by mutableIntStateOf(0) //如果设置的值相同不会触发重组

    /*mutableIntStateOf(0)返回的 MutableState 它本身不具有观察者模式，不能监控其值的变化，它的值变化可以驱动重组功能*/

    // 如何既能触发重组，又能监控其值的变化呢？

    /* 方案 1：自定义 setter（最简单，适合少量逻辑）*/
    /**
     * 优点：改动小、直观
     * ⚠️ 缺点：只能在【赋值的地方】拦截；如果是 state 内部其他方式修改（这里 Int 不会）才会失效，对基础类型够用。
     *
     * > 使用：`vm.count++` 就会走进 setter
     */
    private var delegateCount by mutableIntStateOf(0)
    var customSetterCount: Int
        get() = delegateCount
        set(newValue) {
            if (delegateCount == newValue) return // 可选：相同值跳过
            L.e("customSetterCount","delegateCount 准备从 $delegateCount 变成 $newValue")
            delegateCount = newValue //触发重组
        }


    /*方案 2：使用 `snapshotFlow` 监听 State（推荐，Compose 官方方式）*/

    /**
     * - 标准 Compose State 监听方案
     * - 自动去重（值不变不会回调）
     * - 绑定 `viewModelScope`，ViewModel 销毁自动取消，不会泄漏
     * - 你依然可以保持 `var count by mutableIntStateOf(0)` 简洁写法
     *
     * 注意：`snapshotFlow` 是读取 snapshot 状态流，**在主线程运行**，onEach 里不要放耗时 IO；耗时要再切换 `flowOn(Dispatchers.IO)`
     */


    var snapshotFlowCount by mutableIntStateOf(0)

    init {
        snapshotFlow { snapshotFlowCount }
            .onEach {newCount->
                L.e("snapshotFlowCount","snapshotFlow 监听到 snapshotFlowCount = $newCount")
                // 在这里处理：保存、上报、业务逻辑
            }.launchIn(viewModelScope)
    }

    /*方案 3：改用 MutableStateFlow（如果你本来就熟悉 Flow）*/

    /**
     * UI 侧收集：`collectAsState()`
     * ✅ 适合项目大量依赖 Flow、需要组合多个流、复杂异步逻辑
     * ❌ 缺点：写法比 `mutableIntStateOf` 啰嗦一点
     */

    private val _mutableStateFlowCount = MutableStateFlow(0)
    val mutableStateFlowCount = _mutableStateFlowCount.asStateFlow() //对吧使用的

    /** 更新 StateFlow 中的计数，供 RememberScene5 的按钮调用。 */
    fun increase() {
        val new = _mutableStateFlowCount.value + 1
        println("count 变更为 $new")
        _mutableStateFlowCount.value = new
    }

}

class ComposeRememberActivity : ComponentActivity() {

    private val md: RememberViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()//布局会顶到屏幕最边缘，内容会被状态栏、导航栏、软键盘挡住
        setContent {
            Column(modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)) {
                RememberScene1()
                RememberScene2()
                RememberScene3()
                RememberScene4()
                RememberScene5()
            }
        }
    }


    /**
     * 验证 Compose 重组时 `remember` 保存的状态与普通局部变量的区别。
     * 点击按钮会更新 `count` 并触发重组：按钮上的计数持续增加，
     * 日志中的 `abc` 则在每次重组时重新初始化为 9。
     */
    @Composable
    fun RememberScene1() {
        var abc = 9
        var count by remember { mutableIntStateOf(0) }
        L.e("Hello","RememberScene1-------------------${count}    ${abc}")
        Column() {
            Button(onClick = {
                count++
                abc++
            }) {
                Text("count=${count}")
            }
        }
    }

    /**
     * 验证 `rememberSaveable` 保存的计数在重组及屏幕旋转等 Activity 重建后仍可恢复。
     * 点击按钮可增加 `count`；普通局部变量 `abc` 会在每次重组时重新初始化为 9。
     */
    @Composable
    fun RememberScene2() {
        var abc = 9
        var count by rememberSaveable { mutableIntStateOf(0) }
        L.e("Hello","RememberScene2-------------------${count}    ${abc}")
        Column() {
            Button(onClick = {
                count++
                abc++
            }) {
                Text("count=${count}")
            }
        }
    }

    /**
     * 验证同一 Activity 内多次调用 `viewModels()` 会取得同一个 ViewModel。
     * 点击按钮后 `newMd.count` 与 `md.count` 同步增长，普通局部变量 `abc` 在重组时重置。
     */
    @Composable
    fun RememberScene3() {
        var abc = 9
         val newMd: RememberViewModel by viewModels()
        L.e("Hello","--------RememberScene3-----------${newMd.count}  ${md.count}   ${abc}")
        Column() {
            Button(onClick = {
                newMd.count++
                abc++
            }) {
                Text("count=${newMd.count}")
            }
        }
    }


    /**
     * 验证 ViewModel 中的 Compose 状态变化既能更新按钮文本，
     * 又能被 `snapshotFlow` 观察到并输出日志。
     */
    @Composable
    fun RememberScene4() {
        Column() {
            Button(onClick = {
                md.snapshotFlowCount++
            }) {
                Text("count=${md.snapshotFlowCount}")
            }
        }
    }

    /**
     * 验证 `MutableStateFlow` 的计数更新能够驱动 Compose 界面刷新。
     * `collectAsState()` 在组件处于组合期间收集 Flow，不会因 Activity 进入后台而自动暂停。
     * `collectAsStateWithLifecycle()` 结合 Lifecycle 收集，默认在 STARTED 时开始，
     * 低于 STARTED（如进入 STOPPED）时停止，回到 STARTED 后重新收集最新值。
     * 当前按钮文本使用前者的结果；后者的结果保存在 `count`，目前未用于显示。
     */
    @Composable
    fun RememberScene5() {
        val count = md.mutableStateFlowCount.collectAsStateWithLifecycle()
        Column() {
            Button(onClick = {
                md.increase()
            }) {
                Text("count=${md.mutableStateFlowCount.collectAsState().value}")
            }
        }
    }

}
