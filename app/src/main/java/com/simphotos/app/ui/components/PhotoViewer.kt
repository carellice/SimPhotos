package com.simphotos.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.simphotos.app.data.SimilarGroup
import com.simphotos.app.ui.theme.Coral
import com.simphotos.app.ui.theme.Gold

/**
 * Full-screen viewer for the photos of a group: swipe between them, pinch or double-tap to zoom,
 * and mark the current one to keep or delete. [onDismiss] receives the page shown last.
 */
@Composable
fun PhotoViewer(
    group: SimilarGroup,
    startIndex: Int,
    toDelete: Set<Long>,
    onToggle: (Long) -> Unit,
    onDismiss: (Int) -> Unit,
) {
    val pager = rememberPagerState(initialPage = startIndex.coerceIn(0, group.members.lastIndex)) { group.members.size }
    var chromeVisible by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }

    Dialog(
        onDismissRequest = { onDismiss(pager.currentPage) },
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        Box(Modifier.fillMaxSize().background(Color.Black)) {
            HorizontalPager(
                state = pager,
                userScrollEnabled = !zoomed,
                pageSpacing = 16.dp,
                modifier = Modifier.fillMaxSize(),
            ) { page ->
                ZoomablePhoto(
                    group = group,
                    page = page,
                    isCurrent = pager.settledPage == page,
                    onZoomChange = { if (pager.settledPage == page) zoomed = it },
                    onTap = { chromeVisible = !chromeVisible },
                )
            }

            val current = group.members[pager.currentPage.coerceIn(0, group.members.lastIndex)]
            val marked = current.photo.id in toDelete

            AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
                        .statusBarsPadding()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = { onDismiss(pager.currentPage) }) { Icon(Icons.Rounded.Close, "Chiudi", tint = Color.White) }
                    Column(Modifier.weight(1f)) {
                        Text("${pager.currentPage + 1} di ${group.members.size}", style = MaterialTheme.typography.titleMedium, color = Color.White)
                        Text(formatDate(current.photo.dateTaken), style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.75f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.padding(end = 8.dp)) {
                        if (current.photo.id == group.bestId) Badge(Icons.Rounded.WorkspacePremium, "Migliore", Gold)
                        if (marked) Badge(Icons.Rounded.Delete, "Da eliminare", Coral, textColor = Color.White)
                    }
                }
            }

            AnimatedVisibility(chromeVisible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.7f))))
                        .navigationBarsPadding()
                        .padding(20.dp),
                ) {
                    Text(
                        "Qualità ${current.score}/100 · ${current.photo.width}×${current.photo.height} · ${formatBytes(current.photo.sizeBytes)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White.copy(alpha = 0.85f),
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { onToggle(current.photo.id) },
                        modifier = Modifier.fillMaxWidth().height(52.dp),
                        colors = if (marked) ButtonDefaults.buttonColors()
                        else ButtonDefaults.buttonColors(containerColor = Coral, contentColor = Color.White),
                    ) {
                        Icon(if (marked) Icons.Rounded.RestoreFromTrash else Icons.Rounded.Delete, null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (marked) "Tieni" else "Elimina")
                    }
                }
            }
        }
    }
}

@Composable
private fun ZoomablePhoto(
    group: SimilarGroup,
    page: Int,
    isCurrent: Boolean,
    onZoomChange: (Boolean) -> Unit,
    onTap: () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    // Reset the zoom when the user swipes away from this photo.
    LaunchedEffect(isCurrent) {
        if (!isCurrent) { scale = 1f; offset = Offset.Zero }
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                fun clamp(o: Offset, s: Float): Offset {
                    val maxX = size.width * (s - 1f) / 2f
                    val maxY = size.height * (s - 1f) / 2f
                    return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
                }
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        // Only handle the gesture while pinching or already zoomed in, so that
                        // single-finger swipes at 1x still reach the pager.
                        if (event.changes.size > 1 || scale > 1f) {
                            scale = (scale * event.calculateZoom()).coerceIn(1f, 6f)
                            offset = clamp(offset + event.calculatePan(), scale)
                            onZoomChange(scale > 1f)
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        }
                    } while (event.changes.any { it.pressed })
                    if (scale < 1.05f) {
                        scale = 1f; offset = Offset.Zero; onZoomChange(false)
                    }
                }
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (scale > 1f) {
                            scale = 1f; offset = Offset.Zero; onZoomChange(false)
                        } else {
                            scale = 2.5f
                            val center = Offset(size.width / 2f, size.height / 2f)
                            val maxX = size.width * (scale - 1f) / 2f
                            val maxY = size.height * (scale - 1f) / 2f
                            val target = (center - tap) * (scale - 1f)
                            offset = Offset(target.x.coerceIn(-maxX, maxX), target.y.coerceIn(-maxY, maxY))
                            onZoomChange(true)
                        }
                    },
                )
            }
    ) {
        PhotoThumb(
            group.members[page].photo,
            Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale; scaleY = scale
                translationX = offset.x; translationY = offset.y
            },
            size = 3072,
            contentScale = ContentScale.Fit,
        )
    }
}
