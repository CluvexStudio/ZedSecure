package dev.cluvex.zedsecure.ui.glass

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.cluvex.zedsecure.shared.resources.Res
import dev.cluvex.zedsecure.shared.resources.action_close
import dev.cluvex.zedsecure.ui.platform.appVerticalScroll
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

@Composable
fun ZedMenuItem(
    text: String,
    icon: DrawableResource?,
    glassIcon: ImageVector?,
    onClick: () -> Unit,
    enabled: Boolean = true,
    destructive: Boolean = false,
    checked: Boolean = false,
) {
    if (Glass.active) {
        val c = Glass.colors
        val color = if (destructive) c.red else c.label
        Row(
            Modifier
                .fillMaxWidth()
                .widthIn(min = 236.dp)
                .clickable(remember { MutableInteractionSource() }, glassRowIndication(), enabled = enabled, role = Role.Button, onClick = onClick)
                .heightIn(min = 46.dp)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .graphicsLayer { alpha = if (enabled) 1f else 0.35f },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (glassIcon != null) {
                Icon(glassIcon, contentDescription = null, tint = color, modifier = Modifier.size(19.dp))
            } else {
                Spacer(Modifier.size(19.dp))
            }
            Text(text, style = GlassType.body(), color = color, modifier = Modifier.weight(1f), maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (checked) {
                Icon(GlassIcons.Check, contentDescription = null, tint = color, modifier = Modifier.size(16.dp))
            }
        }
    } else {
        DropdownMenuItem(
            text = { Text(text) },
            leadingIcon = icon?.let { res -> { Icon(painterResource(res), null) } },
            trailingIcon = if (checked) ({ Icon(GlassIcons.Check, null, modifier = Modifier.size(18.dp)) }) else null,
            enabled = enabled,
            onClick = onClick,
        )
    }
}

@Composable
fun ZedMenuDivider() {
    if (Glass.active) {
        Box(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp)
                .height(6.dp)
                .background(Glass.colors.fill2),
        )
    } else {
        HorizontalDivider()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    title: String,
    sheetState: SheetState,
    onDismiss: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Glass.colors
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 38.dp, topEnd = 38.dp),
        containerColor = c.sheet,
        scrimColor = c.dim,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 8.dp, bottom = 4.dp)
                    .size(width = 36.dp, height = 5.dp)
                    .clip(GlassShapes.capsule)
                    .background(c.label3),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .appVerticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(bottom = 20.dp),
        ) {
            Box(Modifier.fillMaxWidth().height(52.dp).padding(horizontal = 14.dp)) {
                GlassCircleButton(
                    icon = GlassIcons.Close,
                    label = stringResource(Res.string.action_close),
                    onClick = onDismiss,
                    iconSize = 17.dp,
                    modifier = Modifier.align(Alignment.CenterStart),
                )
                Text(
                    title,
                    style = GlassType.headline(),
                    color = c.label,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.align(Alignment.Center).padding(horizontal = 60.dp),
                )
            }
            content()
        }
    }
}

@Composable
fun GlassSheetRow(
    title: String,
    color: Color,
    icon: ImageVector,
    onClick: () -> Unit,
    badge: String? = null,
) {
    val c = Glass.colors
    GlassRow(
        title = title,
        leading = { GlassBadge(color = color, icon = icon) },
        trailing = if (badge != null) {
            {
                Text(
                    badge,
                    style = GlassType.caption().copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White,
                    maxLines = 1,
                    modifier = Modifier
                        .clip(GlassShapes.capsule)
                        .background(c.green)
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                )
            }
        } else {
            null
        },
        chevron = true,
        onClick = onClick,
    )
}

@Composable
fun GlassSheetGroup(title: String?, content: @Composable () -> Unit) {
    if (title != null) GlassSectionHeader(title, Modifier.padding(top = 4.dp)) else Spacer(Modifier.width(0.dp))
    GlassGroup(inset = 58.dp, horizontalPadding = 14.dp, content = content)
}

@Composable
fun zedMenuContainer(): Color = if (Glass.active) Glass.colors.menu else androidx.compose.material3.MenuDefaults.containerColor

@Composable
fun zedMenuShape(fallback: androidx.compose.ui.graphics.Shape): androidx.compose.ui.graphics.Shape =
    if (Glass.active) RoundedCornerShape(24.dp) else fallback
