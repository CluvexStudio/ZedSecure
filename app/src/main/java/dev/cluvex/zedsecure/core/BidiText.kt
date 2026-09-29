package dev.cluvex.zedsecure.core

import androidx.core.text.BidiFormatter

internal object BidiText {
    private const val LRI = '⁦'
    private const val PDI = '⁩'

    private val formatter: BidiFormatter by lazy { BidiFormatter.getInstance() }

    fun ltr(text: String): String =
        if (text.isEmpty()) text else "$LRI$text$PDI"

    fun auto(text: String): String =
        if (text.isEmpty()) text else formatter.unicodeWrap(text)
}
