package dev.cluvex.zedsecure.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ProvidableCompositionLocal
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.ic_arrow_back
import org.jetbrains.compose.resources.painterResource

val LocalPageBack: ProvidableCompositionLocal<(() -> Unit)?> = compositionLocalOf { null }

@Composable
fun ProvidePageBack(onBack: (() -> Unit)?, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalPageBack provides onBack, content = content)
}

@Composable
fun PageHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    titleIcon: org.jetbrains.compose.resources.DrawableResource? = null,
    titleIconTint: androidx.compose.ui.graphics.Color? = null,

    singleLine: Boolean = false,
) {
    val back = LocalPageBack.current
    Column(modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
        if (back != null) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                IconButton(onClick = back, modifier = Modifier.offset(x = (-12).dp)) {
                    Icon(
                        painterResource(Res.drawable.ic_arrow_back),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(0.dp))
            }
        }
        androidx.compose.foundation.layout.BoxWithConstraints {
          val room = maxWidth
          Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
            Text(
                text = title,
                style = when {
                    !singleLine -> MaterialTheme.typography.displaySmall
                    room < 210.dp -> MaterialTheme.typography.headlineSmall
                    room < 280.dp -> MaterialTheme.typography.headlineMedium
                    else -> MaterialTheme.typography.displaySmall
                },
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = if (singleLine) 1 else Int.MAX_VALUE,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
            if (titleIcon != null) {
                androidx.compose.material3.Icon(
                    org.jetbrains.compose.resources.painterResource(titleIcon),
                    contentDescription = null,
                    tint = titleIconTint ?: MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .padding(start = 10.dp, top = 6.dp)
                        .size(28.dp),
                )
            }
          }
        }
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(horizontal = 6.dp, vertical = 4.dp),
    )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun <T> SegmentedToggle(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { (value, label) ->
            ToggleButton(
                checked = selected == value,
                onCheckedChange = { if (it) onSelect(value) },
                modifier = Modifier.weight(1f),
            ) {
                Text(label, maxLines = 1)
            }
        }
    }
}
