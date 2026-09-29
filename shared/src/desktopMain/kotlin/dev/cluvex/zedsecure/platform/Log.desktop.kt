package dev.cluvex.zedsecure.platform

actual fun platformLog(tag: String, message: String) {
    println("I/$tag: $message")
    InAppLog.write('I', tag, message, null, ProcessHandle.current().pid().toInt())
}
