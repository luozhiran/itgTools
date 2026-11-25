package com.itg.itg_auxiliary

import android.util.Log

/** 在 Logcat 中输出带有调用位置的日志。 */
class L {

    companion object {
        @JvmStatic
        fun v(tag: String, vararg messages: Any?) = printMessages(Log.VERBOSE, tag, messages)

        @JvmStatic
        fun d(tag: String, vararg messages: Any?) = printMessages(Log.DEBUG, tag, messages)

        @JvmStatic
        fun i(tag: String, vararg messages: Any?) = printMessages(Log.INFO, tag, messages)

        @JvmStatic
        fun w(tag: String, vararg messages: Any?) = printMessages(Log.WARN, tag, messages)

        @JvmStatic
        fun e(tag: String, vararg messages: Any?) = printMessages(Log.ERROR, tag, messages)

        /** 默认以 DEBUG 级别输出，例如 `L.printMessages("Demo", "hello")`。 */
        @JvmStatic
        fun printMessages(tag: String, vararg messages: Any?) =
            printMessages(Log.DEBUG, tag, messages)

        private fun printMessages(priority: Int, tag: String, messages: Array<out Any?>) {
            val caller = Throwable().stackTrace.firstOrNull { frame ->
                frame.className != L::class.java.name &&
                    !frame.className.startsWith(L::class.java.name + "$")
            }
            val border = "────────────────────"
            val content = buildString {
                // Logcat 可识别文件名和行号；只显示方法名以缩短调用位置。
                if (caller != null && caller.fileName != null && caller.lineNumber > 0) {
                    appendLine("${caller.methodName}(${caller.fileName}:${caller.lineNumber})")
                }
                appendLine("┌$border $tag")
                messages.forEach { message ->
                    (message?.toString() ?: "null").lineSequence().forEach { line ->
                        appendLine("│ $line")
                    }
                }
                append("└$border")
            }
            Log.println(priority, tag, content)
        }
    }
}
