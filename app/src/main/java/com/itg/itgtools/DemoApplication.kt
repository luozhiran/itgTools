package com.itg.itgtools

import android.app.Application
import android.content.pm.ApplicationInfo
import android.util.Log
import com.itg.itgtools.pages.matrixtrace.MatrixTraceDemoEvents
import com.itg.matrixtracecanary.MatrixTraceCanary
import com.itg.matrixtracecanary.MatrixTraceConfig

class DemoApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        val isDebuggable = applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        MatrixTraceCanary.install(
            this,
            MatrixTraceConfig.Builder()
                .debug(isDebuggable)
                .slowMethodThresholdMs(700)
                .build(),
        ) { event ->
            Log.d("MatrixTraceDemo", "${event.kind}: ${event.contentJson}")
            MatrixTraceDemoEvents.add(event)
        }
    }
}
