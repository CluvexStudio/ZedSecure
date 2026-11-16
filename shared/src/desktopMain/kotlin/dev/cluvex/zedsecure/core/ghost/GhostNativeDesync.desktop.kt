package dev.cluvex.zedsecure.core.ghost

actual object GhostNativeDesync {
    actual fun probe(): NativeDesyncCapability =
        NativeDesyncCapability(
            available = false,
            mode = "unsupported",
            detailFa = "desync کرنل روی دسکتاپ این بیلد فعال نیست (نیاز به nfqws/سیستم‌عامل)",
        )

    actual fun tryArm(profile: DesyncProfile): Boolean = false

    actual fun disarm() {}
}
