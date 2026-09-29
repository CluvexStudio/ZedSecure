package dev.cluvex.zedsecure.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import dev.cluvex.zedsecure.shared.resources.Res

@Composable
fun FlagBadge(
    countryCode: String?,
    modifier: Modifier = Modifier,
    size: Dp = 30.dp,
) {
    val code = countryCode?.uppercase()
    val assetName = code?.lowercase()

    var bytes by remember(assetName) { mutableStateOf<ByteArray?>(null) }
    LaunchedEffect(assetName) {
        bytes = if (assetName == null) null
        else runCatching { Res.readBytes("files/flags/$assetName.svg") }.getOrNull()
    }

    val shape = RoundedCornerShape(6.dp)
    val flagBytes = bytes
    val platformContext = LocalPlatformContext.current
    when {
        flagBytes != null -> AsyncImage(
            model = remember(flagBytes) {
                ImageRequest.Builder(platformContext).data(flagBytes).build()
            },
            contentDescription = code,
            contentScale = ContentScale.Crop,
            modifier = modifier
                .size(width = size * 1.4f, height = size)
                .clip(shape),
        )
        code != null -> CodeBadge(code, modifier, size, shape)
        else -> CodeBadge("··", modifier, size, shape)
    }
}

@Composable
private fun CodeBadge(
    text: String,
    modifier: Modifier,
    size: Dp,
    shape: androidx.compose.ui.graphics.Shape,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = shape,
        modifier = modifier.size(width = size * 1.4f, height = size),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = text,
                fontSize = (size.value * 0.42f).sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
