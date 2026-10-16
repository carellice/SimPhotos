package com.simphotos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.WorkspacePremium
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.simphotos.app.data.SimilarGroup
import com.simphotos.app.ui.components.Badge
import com.simphotos.app.ui.components.PhotoThumb
import com.simphotos.app.ui.components.PhotoViewer
import com.simphotos.app.ui.components.SelectableThumb
import com.simphotos.app.ui.components.formatBytes
import com.simphotos.app.ui.components.formatDate
import com.simphotos.app.ui.theme.Coral
import com.simphotos.app.ui.theme.Gold
import kotlinx.coroutines.launch
import java.util.Locale

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GroupDetailScreen(
    group: SimilarGroup,
    toDelete: Set<Long>,
    onBack: () -> Unit,
    onToggle: (Long) -> Unit,
    onKeepOnly: (Long) -> Unit,
) {
    val pager = rememberPagerState { group.members.size }
    val strip = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var viewerOpen by remember { mutableStateOf(false) }
    LaunchedEffect(pager.currentPage) { strip.animateScrollToItem(pager.currentPage) }

    val current = group.members[pager.currentPage.coerceIn(0, group.members.lastIndex)]
    val isBest = current.photo.id == group.bestId
    val marked = current.photo.id in toDelete

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
            Column(Modifier.weight(1f)) {
                Text("${pager.currentPage + 1} di ${group.members.size}", style = MaterialTheme.typography.titleMedium)
                Text(formatDate(current.photo.dateTaken), style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }

        HorizontalPager(
            state = pager,
            contentPadding = PaddingValues(horizontal = 20.dp),
            pageSpacing = 12.dp,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            val m = group.members[page]
            Box(
                Modifier.fillMaxSize().clip(RoundedCornerShape(26.dp)).background(MaterialTheme.colorScheme.surfaceContainer)
                    .clickable { viewerOpen = true }
            ) {
                PhotoThumb(m.photo, Modifier.fillMaxSize(), size = 1600, contentScale = ContentScale.Fit)
                Row(Modifier.align(Alignment.TopStart).padding(12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (m.photo.id == group.bestId) Badge(Icons.Rounded.WorkspacePremium, "Da tenere", Gold)
                    if (m.photo.id in toDelete) Badge(Icons.Rounded.Delete, "Da eliminare", Coral, textColor = androidx.compose.ui.graphics.Color.White)
                }
            }
        }

        // Metrics
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Qualità ${current.score}/100", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (!isBest) {
                    Text(
                        "Somiglianza ${(current.similarityToBest * 100).toInt()}%",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                current.reasons.forEach {
                    Badge(null, it, MaterialTheme.colorScheme.primaryContainer, textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                Badge(null, "${current.photo.width}×${current.photo.height}", MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Badge(null, String.format(Locale.ITALY, "%.1f MP", current.photo.megapixels), MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                Badge(null, formatBytes(current.photo.sizeBytes), MaterialTheme.colorScheme.surfaceVariant,
                    textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                if (current.photo.bucket.isNotBlank()) {
                    Badge(null, current.photo.bucket, MaterialTheme.colorScheme.surfaceVariant,
                        textColor = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        LazyRow(
            state = strip,
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(group.members, key = { _, m -> m.photo.id }) { i, m ->
                SelectableThumb(
                    photo = m.photo,
                    isBest = m.photo.id == group.bestId,
                    markedForDelete = m.photo.id in toDelete,
                    thumbSize = 64.dp,
                    onClick = { scope.launch { pager.animateScrollToPage(i) } },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(
                onClick = { onToggle(current.photo.id) },
                modifier = Modifier.weight(1f).height(52.dp),
            ) {
                Icon(if (marked) Icons.Rounded.RestoreFromTrash else Icons.Rounded.Delete, null)
                Spacer(Modifier.width(6.dp))
                Text(if (marked) "Tieni" else "Elimina")
            }
            Button(
                onClick = { onKeepOnly(current.photo.id) },
                enabled = !(isBest && group.members.all { it.photo.id == group.bestId || it.photo.id in toDelete }),
                modifier = Modifier.weight(1.4f).height(52.dp),
                colors = ButtonDefaults.buttonColors(),
            ) {
                Icon(Icons.Rounded.WorkspacePremium, null)
                Spacer(Modifier.width(6.dp))
                Text("Tieni solo questa")
            }
        }
    }

    if (viewerOpen) {
        PhotoViewer(group, pager.currentPage, toDelete, onToggle, onDismiss = { page ->
            viewerOpen = false
            scope.launch { pager.scrollToPage(page) }
        })
    }
}
