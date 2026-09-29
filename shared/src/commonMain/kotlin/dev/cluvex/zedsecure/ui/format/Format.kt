package dev.cluvex.zedsecure.ui.format

fun formatRate(bytesPerSecond: Long): Pair<String, String> = when {
    bytesPerSecond <= 0 -> "0" to "B/s"
    bytesPerSecond < 1_024 -> bytesPerSecond.toString() to "B/s"
    bytesPerSecond < 1_048_576 -> {
        val kb = bytesPerSecond / 1024.0
        (if (kb < 10) "%.1f".format(kb) else kb.toInt().toString()) to "KB/s"
    }
    bytesPerSecond < 1_073_741_824 -> "%.1f".format(bytesPerSecond / 1_048_576.0) to "MB/s"
    else -> "%.2f".format(bytesPerSecond / 1_073_741_824.0) to "GB/s"
}

fun formatBytes(bytes: Long): String = when {
    bytes <= 0 -> "0 B"
    bytes < 1_024 -> "$bytes B"
    bytes < 1_048_576 -> "%.1f KB".format(bytes / 1024.0)
    bytes < 1_073_741_824 -> "%.1f MB".format(bytes / 1_048_576.0)
    else -> "%.2f GB".format(bytes / 1_073_741_824.0)
}

fun formatElapsed(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%02d:%02d".format(m, s)
}

private const val LRI = '⁦'
private const val PDI = '⁩'

fun ltrIsolate(text: String): String = if (text.isEmpty()) text else "$LRI$text$PDI"
