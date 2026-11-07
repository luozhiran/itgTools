package com.itg.rnbussiness.nativecode

import com.facebook.fbreact.specs.NativeItgDemoSpec
import com.facebook.react.bridge.Promise
import com.facebook.react.bridge.ReactApplicationContext
import com.facebook.react.module.annotations.ReactModule

/** Codegen-backed TurboModule. Add methods to the TS Spec before editing this class. */
@ReactModule(name = NativeItgDemoSpec.NAME)
class ItgDemoTurboModule(context: ReactApplicationContext) : NativeItgDemoSpec(context) {
    override fun multiply(a: Double, b: Double, promise: Promise) {
        promise.resolve(a * b)
    }
}
