package dev.cluvex.zedsecure.core.ghost

import dev.cluvex.zedsecure.platform.Log

/**
 * Android: no stock nfqws. Report app-layer fallback clearly.
 * Future: load optional JNI module if present.
 */
actual object GhostNativeDesync {
    actual fun probe(): NativeDesyncCapability {
        // Placeholder for optional native lib detection (System.loadLibrary).
        val native = false
        return if (native) {
            NativeDesyncCapability(
                available = true,
                mode = "native_nfq",
                detailFa = "ماژول native desync در دسترس است",
            )
        } else {
            NativeDesyncCapability(
                available = false,
                mode = "app_layer",
                detailFa = "nfqws/کرنل در این بیلد نیست – GHOST از الگوی desync در لایه اپ استفاده می‌کند",
            )
        }
    }

    actual fun tryArm(profile: DesyncProfile): Boolean {
        if (profile == DesyncProfile.Off) return false
        Log.i("GhostNativeDesync", "arm profile=$profile → app_layer only")
        return false
    }

    actual fun disarm() {
        Log.i("GhostNativeDesync", "disarm")
    }
}
