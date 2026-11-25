package com.itg.componse.activitys

import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier

class ComposeRememberActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()//布局会顶到屏幕最边缘，内容会被状态栏、导航栏、软键盘挡住
        setContent {
            Row(modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)) {
                RememberScene1()
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
        Log.e("Hello","-------------------${count}    ${abc}")
        Column(modifier = Modifier.fillMaxSize()) {
            Button(onClick = {
                count++
                abc++
            }) {
                Text("count=${count}")
            }
        }
    }
}
