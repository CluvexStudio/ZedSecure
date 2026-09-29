package dev.cluvex.zedsecure.ui.components

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink

@Composable
fun NoteText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    maxLines: Int = Int.MAX_VALUE,
) {
    val linkColor = MaterialTheme.colorScheme.primary
    val annotated = remember(text, linkColor) {
        val linkStyles = TextLinkStyles(
            style = SpanStyle(
                color = linkColor,
                fontWeight = FontWeight.SemiBold,
                textDecoration = TextDecoration.Underline,
            ),
        )
        buildAnnotatedString {
            var last = 0
            URL_REGEX.findAll(text).forEach { match ->
                append(text.substring(last, match.range.first))
                val raw = match.value
                val href = if (raw.startsWith("http", ignoreCase = true)) raw else "https://$raw"
                withLink(LinkAnnotation.Url(href, linkStyles)) { append(raw) }
                last = match.range.last + 1
            }
            if (last < text.length) append(text.substring(last))
        }
    }
    Text(text = annotated, modifier = modifier, style = style, color = color, maxLines = maxLines)
}

private val URL_REGEX = Regex("""(https?://[^\s]+|www\.[^\s]+)""", RegexOption.IGNORE_CASE)
