package com.itg.rnhostsample

import android.app.Application
import com.facebook.react.PackageList
import com.facebook.react.ReactApplication
import com.facebook.react.ReactHost
import com.facebook.react.ReactNativeApplicationEntryPoint.loadReactNative
import com.facebook.react.defaults.DefaultReactHost.getDefaultReactHost
import com.itg.rncontainer.bridge.ItgRnBridgePackage
import com.itg.rnbussiness.nativecode.ItgRnBusinessNative
import com.itg.rnbussiness.nativecode.ItgRnBusinessPackage

class SampleApplication : Application(), ReactApplication {
    override val reactHost: ReactHost by lazy {
        getDefaultReactHost(
            context = applicationContext,
            packageList = PackageList(this).packages.apply {
                add(ItgRnBridgePackage())
                add(ItgRnBusinessPackage())
            },
            useDevSupport = BuildConfig.DEBUG,
        )
    }

    override fun onCreate() {
        super.onCreate()
        loadReactNative(this)
        ItgRnBusinessNative.install()
    }
}
