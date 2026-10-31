package dev.cluvex.zedsecure.platform

private val AES_ARCHES = setOf("amd64", "x86_64", "aarch64", "arm64", "s390x")

internal actual fun hasAesGcmHardware(): Boolean =
    System.getProperty("os.arch").orEmpty().lowercase() in AES_ARCHES
