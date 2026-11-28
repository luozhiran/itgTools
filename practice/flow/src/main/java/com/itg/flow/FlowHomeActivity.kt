package com.itg.flow

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.itg.flow.f.ui.theme.ItgToolsTheme

class FlowHomeActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            ItgToolsTheme {
                HomeScreen(){ demo ->
                    startActivity(Intent(this, demo.activityClass))
                }
            }
        }
    }
}


@Composable
fun HomeScreen(onDemoClick: (FlowDemoEntry) -> Unit) {
    /**
     * Scaffold 是页面骨架容器，**提供固定槽位：topBar、bottomBar、floatingActionButton、snackbarHost**，
     * 自动帮你处理状态栏、导航栏 inset，并且给 content 返回 `innerPadding`，防止内容被 topBar /bottomBar 盖住
     *
     */
    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding(),
        topBar = {
            /**
             * **Surface 是 Material 的基础容器，用来承载内容、控制背景色、形状、阴影、点击水波纹，是 Material 体系的基础块**。
             * 核心能力
             * 1. **背景色**
             *      默认用 `colorScheme.surface`，页面默认底色；你可以改成其他颜色。
             * 2. **阴影 elevation**
             *      `shadowElevation` 控制阴影；`tonalElevation` 是 Material3 的色调抬升（颜色变暗 / 变深，不只是阴影）
             * 3. **形状 shape**
             *      圆角、切角等，比如 `RoundedCornerShape(16.dp)`
             * 4. **点击 + 水波纹（ripple）**
             *      只要传入 `onClick`，自动变成可点击控件，自带 Material 点击涟漪效果，不用自己加 `clickable`。
             * 5. **内容裁剪**
             *      内容会按照 shape 裁剪，比如圆角 Surface 里的图片不会溢出圆角。
             */
            Surface() {
                Text(
                    text = stringResource(R.string.title_activity_flow_home),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
        }
    ) { innerPadding ->

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            item {
                Text(
                    text = stringResource(R.string.flow_home_description),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            items(FlowDemoRegistry.entries, key={it.activityClass.name}){demo ->
                Card(
                    onClick = { onDemoClick(demo) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = stringResource(demo.titleRes),
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Text(
                            text = stringResource(demo.descriptionRes),
                            modifier = Modifier.padding(top = 6.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(R.string.compose_open_demo),
                            modifier = Modifier.padding(top = 14.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

        }

    }
}


@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    ItgToolsTheme {
        HomeScreen(onDemoClick = {})
    }
}