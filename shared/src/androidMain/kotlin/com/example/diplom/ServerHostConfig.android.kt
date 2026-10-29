package com.example.diplom

import android.os.Build
import com.example.diplom.shared.BuildConfig

/** Эмулятор видит хост-ПК по специальному адресу; без этого приложение ходит в «пустой» LAN-IP. */
private fun isProbablyAndroidEmulator(): Boolean {
    val hw = Build.HARDWARE.orEmpty()
    if (hw.equals("goldfish", ignoreCase = true) || hw.equals("ranchu", ignoreCase = true)) {
        return true
    }
    val fp = Build.FINGERPRINT.orEmpty()
    val model = Build.MODEL.orEmpty()
    val manu = Build.MANUFACTURER.orEmpty()
    val prod = Build.PRODUCT.orEmpty()
    return fp.startsWith("generic")
        || fp.startsWith("unknown")
        || model.contains("google_sdk", ignoreCase = true)
        || model.contains("Emulator", ignoreCase = true)
        || model.contains("Android SDK built for x86", ignoreCase = true)
        || manu.contains("Genymotion", ignoreCase = true)
        || prod.contains("sdk_gphone", ignoreCase = true)
        || prod.contains("emulator", ignoreCase = true)
        || prod.contains("simulator", ignoreCase = true)
        || (Build.BRAND.startsWith("generic") && Build.DEVICE.startsWith("generic"))
        || prod == "google_sdk"
}

actual fun configuredServerHost(): String {
    if (isProbablyAndroidEmulator()) {
        return "10.0.2.2"
    }
    val h = BuildConfig.DIPLOM_SERVER_HOST.trim()
    return if (h.isNotEmpty()) h else DEFAULT_SERVER_HOST
}
