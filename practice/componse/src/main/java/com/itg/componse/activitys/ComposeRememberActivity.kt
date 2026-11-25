package com.itg.componse.activitys

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
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import com.itg.itg_auxiliary.L

class RememberViewModel : ViewModel()

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
        L.e("Hello","-------------------${count}    ${abc}")
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
        val vm = md
        var count by rememberSaveable { mutableIntStateOf(0) }
        L.e("Hello","-------------------${count}    ${abc}")
        Column() {
            Button(onClick = {
                count++
                abc++
            }) {
                Text("count=${count}")
            }
        }
    }

}
