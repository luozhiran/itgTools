package com.itg.itg_web_cache

/**
 * Current network category used by preload policy checks.
 */
enum class NetworkType {
    /**
     * Device is connected through Wi-Fi. Preload rules that require Wi-Fi may run.
     */
    WIFI,

    /**
     * Device is connected through cellular data. Wi-Fi-only preload rules are skipped.
     */
    CELLULAR,

    /**
     * Device is connected through another network type, such as Ethernet or VPN.
     */
    OTHER,

    /**
     * Device currently has no available network connection. Network preload should not run.
     */
    NONE
}