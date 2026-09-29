package dev.cluvex.zedsecure.core

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

object LogBus {
    private const val CAPACITY = 2_000

    private const val SLACK = 256

    private val _lines = MutableStateFlow<List<String>>(emptyList())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    var source: ((active: Boolean) -> Unit)? = null

    private var watchers = 0

    private val _ownTags = MutableStateFlow<Set<String>>(emptySet())
    val ownTags: StateFlow<Set<String>> = _ownTags.asStateFlow()

    fun noteOwnTag(tag: String) {
        if (tag !in _ownTags.value) _ownTags.update { it + tag }
    }

    fun append(line: String) {
        appendAll(listOf(line))
    }

    fun appendAll(newLines: List<String>) {
        if (newLines.isEmpty()) return
        _lines.update { prev ->
            val first = stampOf(newLines.first())
            val last = prev.asReversed().firstNotNullOfOrNull { stampOf(it) }
            if (first == null || last == null || first >= last) trim(prev + newLines)
            else trim(mergeByStamp(prev, newLines))
        }
    }

    private fun stampOf(line: String): String? =
        if (line.length >= 18 && line[2] == '-' && line[5] == ' ' && line[8] == ':' && line[14] == '.') {
            line.substring(0, 18)
        } else {
            null
        }

    private fun mergeByStamp(a: List<String>, b: List<String>): List<String> {
        val out = ArrayList<String>(a.size + b.size)
        var i = 0
        var j = 0
        var keyA = ""
        var keyB = ""
        while (i < a.size || j < b.size) {
            if (i < a.size) keyA = stampOf(a[i]) ?: keyA
            if (j < b.size) keyB = stampOf(b[j]) ?: keyB
            when {
                j >= b.size -> out += a[i++]
                i >= a.size -> out += b[j++]
                keyB < keyA -> out += b[j++]
                else -> out += a[i++]
            }
        }
        return out
    }

    private fun trim(next: List<String>): List<String> =
        if (next.size > CAPACITY + SLACK) next.subList(next.size - CAPACITY, next.size) else next

    fun attach() {
        watchers++
        if (watchers == 1) source?.invoke(true)
    }

    fun detach() {
        if (watchers > 0) watchers--
        if (watchers == 0) source?.invoke(false)
    }

    fun clear() {
        _lines.value = emptyList()
    }

    fun snapshot(): String = _lines.value.joinToString("\n")
}
