package com.itg.rnbussiness.nativecode

import com.facebook.fbreact.specs.NativeItgDemoSpec
import com.facebook.react.BaseReactPackage
import com.facebook.react.bridge.NativeModule
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.model.ReactModuleInfo
import com.facebook.react.module.model.ReactModuleInfoProvider
import com.facebook.react.uimanager.ViewManager

/** The host Application must add this package to its ReactHost package list. */
class ItgRnBusinessPackage : BaseReactPackage() {
    override fun getModule(name: String, reactContext: ReactApplicationContext): NativeModule? =
        if (name == NativeItgDemoSpec.NAME) ItgDemoTurboModule(reactContext) else null

    override fun getReactModuleInfoProvider() = ReactModuleInfoProvider {
        mapOf(NativeItgDemoSpec.NAME to ReactModuleInfo(
            name = NativeItgDemoSpec.NAME,
            className = ItgDemoTurboModule::class.java.name,
            canOverrideExistingModule = false,
            needsEagerInit = false,
            isCxxModule = false,
            isTurboModule = true,
        ))
    }

    override fun createViewManagers(reactContext: ReactApplicationContext): List<ViewManager<*, *>> =
        listOf(ItgDemoNativeViewManager())
}
