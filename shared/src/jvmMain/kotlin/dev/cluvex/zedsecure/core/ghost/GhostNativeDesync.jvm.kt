package dev.cluvex.zedsecure.core.ghost

actual object GhostNativeDesync {
    actual fun probe(): NativeDesyncCapability =
        NativeDesyncCapability(
            available = false,
            mode = "unsupported",
            detailFa = "desync کرنل روی این هدف JVM فعال نیست",
        )

    actual fun tryArm(profile: DesyncProfile): Boolean = false

    actual fun disarm() {}
}
