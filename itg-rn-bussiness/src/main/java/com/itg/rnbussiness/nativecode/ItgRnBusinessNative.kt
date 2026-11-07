package com.itg.rnbussiness.nativecode

/** Call once after RN 0.84 `loadReactNative(this)` and before creating the first ReactHost. */
object ItgRnBusinessNative {
    @Volatile private var installed = false

    @Synchronized
    fun install() {
        if (installed) return
        System.loadLibrary("itg_rn_business_codegen")
        nativeInstall()
        installed = true
    }

    private external fun nativeInstall()
}
