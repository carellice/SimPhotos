package com.simphotos.app.ui.components

import android.text.format.DateUtils
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.simphotos.app.data.Photo
import com.simphotos.app.ui.theme.BrandGradient
import com.simphotos.app.ui.theme.Coral
import com.simphotos.app.ui.theme.Gold
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024) return String.format(Locale.ITALY, "%.0f KB", kb)
    val mb = kb / 1024.0
    if (mb < 1024) return String.format(Locale.ITALY, "%.1f MB", mb)
    return String.format(Locale.ITALY, "%.2f GB", mb / 1024.0)
}

fun formatDate(millis: Long): String =
    SimpleDateFormat("d MMM yyyy · HH:mm", Locale.ITALIAN).format(Date(millis))

fun formatRelative(millis: Long): String =
    DateUtils.getRelativeTimeSpanString(millis, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString()

@Composable
fun PhotoThumb(
    photo: Photo,
    modifier: Modifier = Modifier,
    size: Int = 320,
    contentScale: ContentScale = ContentScale.Crop,
) {
    AsyncImage(
        model = ImageRequest.Builder(LocalContext.current)
            .data(photo.uri)
            .size(size)
            .crossfade(true)
            .build(),
        contentDescription = null,
        contentScale = contentScale,
        modifier = modifier,
    )
}

/**
 * A square thumbnail showing whether the photo will be kept (gold "Migliore" badge)
 * or deleted (red check overlay).
 */
@Composable
fun SelectableThumb(
    photo: Photo,
    isBest: Boolean,
    markedForDelete: Boolean,
    modifier: Modifier = Modifier,
    thumbSize: Dp = 96.dp,
    onClick: () -> Unit,
) {
    val scale by animateFloatAsState(if (markedForDelete) 0.92f else 1f, label = "scale")
    val borderColor by animateColorAsState(
        when {
            isBest -> Gold
            markedForDelete -> Coral
            else -> Color.Transparent
        },
        label = "border",
    )
    Box(
        modifier
            .size(thumbSize)
            .scale(scale)
            .clip(RoundedCornerShape(16.dp))
            .border(3.dp, borderColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
    ) {
        PhotoThumb(photo, Modifier.fillMaxSize())
        if (markedForDelete) {
            Box(Modifier.fillMaxSize().background(Coral.copy(alpha = 0.28f)))
        }
        if (isBest && thumbSize < 80.dp) {
            Box(
                Modifier.align(Alignment.BottomStart).padding(4.dp).size(20.dp).clip(CircleShape).background(Gold),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.WorkspacePremium, null, tint = Color(0xFF2B1D00), modifier = Modifier.size(13.dp)) }
        } else if (isBest) {
            Badge(
                icon = Icons.Rounded.WorkspacePremium, text = "Migliore", color = Gold,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp),
            )
        } else if (photo.isFavorite) {
            Icon(
                Icons.Rounded.Star, null, tint = Gold,
                modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).size(18.dp),
            )
        }
        CheckCircle(
            checked = markedForDelete,
            modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
        )
    }
}

@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier) {
    val bg by animateColorAsState(if (checked) Coral else Color.Black.copy(alpha = 0.25f), label = "check")
    Box(
        modifier
            .size(24.dp)
            .clip(CircleShape)
            .background(bg)
            .border(2.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (checked) Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(16.dp))
    }
}

@Composable
fun Badge(icon: ImageVector?, text: String, color: Color, modifier: Modifier = Modifier, textColor: Color = Color(0xFF2B1D00)) {
    Row(
        modifier
            .clip(RoundedCornerShape(50))
            .background(color)
            .padding(horizontal = 7.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, tint = textColor, modifier = Modifier.size(12.dp))
            Spacer(Modifier.width(3.dp))
        }
        Text(text, color = textColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GradientButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    brush: Brush = BrandGradient,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        modifier
            .height(58.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(if (enabled) brush else Brush.linearGradient(listOf(Color.Gray, Color.Gray)))
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            if (icon != null) {
                Icon(icon, null, tint = Color.White)
                Spacer(Modifier.width(10.dp))
            }
            Text(text, color = Color.White, style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
fun StatTile(value: String, label: String, modifier: Modifier = Modifier) {
    Box(
        modifier
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(16.dp)
    ) {
        androidx.compose.foundation.layout.Column {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary,
        letterSpacing = 1.2.sp,
        modifier = modifier.fillMaxWidth(),
    )
}
