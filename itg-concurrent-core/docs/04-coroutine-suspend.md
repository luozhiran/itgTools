# 04. 协程内切线程

本节解决已经在协程中时，如何通过 `itg-concurrent-core` 切换执行上下文。

## 适用条件

- 当前已经在 `lifecycleScope`、`viewModelScope` 或其他 `CoroutineScope` 中。
- 需要在统一后端配置下执行 I/O、计算、后台或主线程任务。
- 希望线程池后端和协程后端使用同一套业务 API。

## 推荐做法

```kotlin
lifecycleScope.launch {
    val user = Concurrent.ioSuspend {
        api.getUser(userId)
    }

    val state = Concurrent.computeSuspend {
        buildViewState(user)
    }

    Concurrent.mainSuspend {
        render(state)
    }
}
```

## 可复制 Demo

下面示例可以直接放进 ViewModel。需要替换 `repository.loadUser(userId)` 和 `buildState(user)`。

```kotlin
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.itg.concurrent.Concurrent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class UserViewModel(
    private val repository: UserRepository
) : ViewModel() {
    private val _state = MutableStateFlow<UserState>(UserState.Idle)
    val state: StateFlow<UserState> = _state

    fun load(userId: String) {
        viewModelScope.launch {
            _state.value = UserState.Loading

            try {
                val user = Concurrent.ioSuspend {
                    repository.loadUser(userId) // TODO: 替换成你的 I/O 操作
                }

                val viewState = Concurrent.computeSuspend {
                    buildState(user) // TODO: 替换成你的计算逻辑
                }

                Concurrent.mainSuspend {
                    _state.value = UserState.Success(viewState)
                }
            } catch (e: Exception) {
                Concurrent.mainSuspend {
                    _state.value = UserState.Error(e.message ?: "unknown error")
                }
            }
        }
    }
}
```

## API 选择

| 场景 | API |
| --- | --- |
| 协程内执行 I/O | `Concurrent.ioSuspend { }` |
| 协程内执行计算 | `Concurrent.computeSuspend { }` |
| 协程内执行普通后台逻辑 | `Concurrent.backgroundSuspend { }` |
| 协程内切回主线程 | `Concurrent.mainSuspend { }` |

## 关键说明

- `ioSuspend` 等 API 不创建新的顶层生命周期，它们跟随调用方协程取消。
- 线程池后端会通过 `toCoroutineDispatcher()` 桥接成协程 dispatcher。
- 不要在 `mainSuspend` 中执行阻塞任务。

## 验证方式

- 取消 `viewModelScope` 后，未完成任务随之取消。
- 在 `ioSuspend` 和 `computeSuspend` 中打印线程名，应不同于主线程。
- UI 状态只在主线程更新。

[返回 README](../README.md)