# 05. 在协程中执行文件操作

`FileCoroutines.withIo` 在 IO 调度器执行阻塞文件操作，结果直接返回给调用方的协程。调用方负责选择作用域。

## 选择作用域

| 场景 | 用法 | 结束时机 |
| --- | --- | --- |
| ViewModel 加载数据 | `viewModelScope.launch` | ViewModel 清除 |
| Activity 操作文件 | `lifecycleScope.launch` | Activity 销毁 |
| Fragment 更新 View | `viewLifecycleOwner.lifecycleScope.launch` | Fragment 的 View 销毁 |

## ViewModel 示例

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itg.itg_file.core.FileCoroutines
import com.itg.itg_file.read.FileReadUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class ConfigViewModel : ViewModel() {
    private val _text = MutableStateFlow<String?>(null)
    val text: StateFlow<String?> = _text

    fun load(path: String) {
        viewModelScope.launch {
            _text.value = FileCoroutines.withIo { FileReadUtils.readText(path) }
        }
    }
}
```

`FileCoroutines.withIo` 结束后会回到调用方原来的协程上下文；上例随后在 ViewModel 的主线程作用域更新状态。要在 `suspend` 函数里使用时，直接写 `val text = FileCoroutines.withIo { FileReadUtils.readText(path) }`，无需创建新的作用域或等待任务句柄。

## 超时与取消

```kotlin
import kotlinx.coroutines.withTimeoutOrNull

val text = withTimeoutOrNull(5_000) {
    FileCoroutines.withIo { FileReadUtils.readText(path) }
}
```

`FileCoroutines.withIo` 对可中断的阻塞调用会响应协程取消。取消不能撤销已写入的文件，也无法保证第三方 I/O 一定立即停止。需要原子替换时使用相应的原子写入方法。

`xxxSuspend(..., callback)` 是已有回调接口的协程版本：必须从协程调用，返回 `Unit`，回调在 IO 线程执行。新代码优先组合 `FileCoroutines.withIo` 与同步文件方法，以返回值传递结果。

[返回 README](../README.md)
