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
            Column(modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.systemBars)) {
                RememberScene1()
            }
        }
    }


    @Composable
    fun RememberScene1() {
        var abc = 9
        var count by remember { mutableIntStateOf(0) }
        L.e("Hello", "RememberScene1-------------------${count}    ${abc}")
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