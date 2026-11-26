package com.itg.componse.activitys

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.itg.itg_auxiliary.L

class ComposeRememberActivity2 : AppCompatActivity() {


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .windowInsetsPadding(WindowInsets.systemBars)
            ) {
                RememberScene1()
            }
        }
    }


    /**
     * `SideEffect`（Compose UI 层监听，只能在 @Composable）
     * 特点：【每次重组完成之后执行】，读到的 state 发生变化、触发重组后，SideEffect 跑一次；
     * 适合 UI 侧埋点、上报，【不能放到 ViewModel 里】。
     *
     * 1. 不是 “值一变立刻回调”，是**重组完成才跑**；短时间连续多次修改，中间值会被吞掉，只拿到最后一次
     * 2. 只能在 Composable，ViewModel 里不能用
     * 3. 只要重组就会执行，哪怕值没变（如果你在别的地方读 state 触发重组）
     */
    @Composable
    fun RememberScene1() {
        var count by remember { mutableIntStateOf(0) }
        SideEffect {
            // 重组后拿到最新count
            L.e("SideEffect","count=$count")
        }
        Column() {
            Button(onClick = {
                count++

            }) {
                Text("count=${count}")
            }
        }
    }
}