package com.simphotos.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.CleaningServices
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Done
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simphotos.app.data.DeleteMode
import com.simphotos.app.data.SimilarGroup
import com.simphotos.app.ui.UiState
import com.simphotos.app.ui.components.Badge
import com.simphotos.app.ui.components.GradientButton
import com.simphotos.app.ui.components.PhotoViewer
import com.simphotos.app.ui.components.SelectableThumb
import com.simphotos.app.ui.components.formatBytes
import com.simphotos.app.ui.components.formatDate
import com.simphotos.app.ui.theme.BrandGradient
import com.simphotos.app.ui.theme.Coral
import com.simphotos.app.ui.theme.Mint

@Composable
fun ResultsScreen(
    state: UiState,
    deleteMode: DeleteMode,
    onBack: () -> Unit,
    onSettings: () -> Unit,
    onToggle: (Long) -> Unit,
    onOpenGroup: (Long) -> Unit,
    onSelectAll: (Boolean) -> Unit,
    onConfirmDelete: () -> Unit,
) {
    var showConfirm by remember { mutableStateOf(false) }
    // (groupId, index) of the photo shown full screen, if any.
    var viewing by remember { mutableStateOf<Pair<Long, Int>?>(null) }
    val count = state.toDelete.size
    val bytes = state.toDeleteBytes

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        LazyColumn(
            contentPadding = PaddingValues(bottom = 120.dp),
            modifier = Modifier.fillMaxSize().statusBarsPadding(),
        ) {
            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
                    Text("Risultati", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Tune, "Sensibilità") }
                }
            }
            if (state.groups.isEmpty()) {
                item { EmptyState() }
            } else {
                item { Summary(state.groups.size, count, bytes) }
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            "Tocca una foto per tenerla o eliminarla, tieni premuto per vederla in grande",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f),
                        )
                        TextButton(onClick = { onSelectAll(count == 0) }) {
                            Text(if (count == 0) "Seleziona tutte" else "Deseleziona")
                        }
                    }
                }
                items(state.groups, key = { it.id }) { group ->
                    GroupCard(
                        group, state.toDelete, onToggle,
                        onOpen = { onOpenGroup(group.id) },
                        onPreview = { viewing = group.id to it },
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = count > 0,
            enter = slideInVertically { it },
            exit = slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                GradientButton(
                    text = "Elimina $count foto · ${formatBytes(bytes)}",
                    icon = Icons.Rounded.Delete,
                    brush = Brush.linearGradient(listOf(Coral, Color(0xFFFF7A59))),
                    modifier = Modifier.fillMaxWidth(),
                    onClick = { showConfirm = true },
                )
            }
        }
    }

    viewing?.let { (groupId, index) ->
        state.groups.firstOrNull { it.id == groupId }?.let { group ->
            PhotoViewer(group, index, state.toDelete, onToggle, onDismiss = { viewing = null })
        }
    }

    if (showConfirm) {
        AlertDialog(
            onDismissRequest = { showConfirm = false },
            icon = { Icon(Icons.Rounded.CleaningServices, null) },
            title = { Text("Eliminare $count foto?") },
            text = {
                Text(
                    if (deleteMode == DeleteMode.TRASH)
                        "Le foto verranno spostate nel cestino e potrai recuperarle per 30 giorni. In ogni gruppo resterà la foto migliore. Libererai ${formatBytes(bytes)}."
                    else
                        "Le foto verranno eliminate definitivamente e non potranno essere recuperate. In ogni gruppo resterà la foto migliore. Libererai ${formatBytes(bytes)}."
                )
            },
            confirmButton = {
                TextButton(onClick = { showConfirm = false; onConfirmDelete() }) {
                    Text("Elimina", color = Coral)
                }
            },
            dismissButton = { TextButton(onClick = { showConfirm = false }) { Text("Annulla") } },
        )
    }
}

@Composable
private fun Summary(groups: Int, count: Int, bytes: Long) {
    Box(
        Modifier
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(26.dp))
            .background(BrandGradient)
            .padding(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Puoi liberare", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
                Text(formatBytes(bytes), style = MaterialTheme.typography.displaySmall, color = Color.White)
                Text(
                    "$count foto in $groups gruppi",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
            }
            Box(
                Modifier.size(64.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Rounded.CleaningServices, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
        }
    }
}

@Composable
private fun GroupCard(
    group: SimilarGroup,
    toDelete: Set<Long>,
    onToggle: (Long) -> Unit,
    onOpen: () -> Unit,
    onPreview: (Int) -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val selected = group.members.count { it.photo.id in toDelete }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .padding(vertical = 14.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${group.members.size} foto", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.width(8.dp))
                    if (group.isExactDuplicate) {
                        Badge(Icons.Rounded.ContentCopy, "Identiche", MaterialTheme.colorScheme.primaryContainer,
                            textColor = MaterialTheme.colorScheme.onPrimaryContainer)
                    } else {
                        val minSim = group.members.minOf { it.similarityToBest }
                        Badge(null, "Simili ${(minSim * 100).toInt()}%", MaterialTheme.colorScheme.secondaryContainer,
                            textColor = MaterialTheme.colorScheme.onSecondaryContainer)
                    }
                }
                Text(
                    formatDate(group.best.photo.dateTaken) + if (selected > 0) " · $selected da eliminare" else "",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, "Dettagli", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.height(12.dp))
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            itemsIndexed(group.members, key = { _, m -> m.photo.id }) { i, m ->
                SelectableThumb(
                    photo = m.photo,
                    isBest = m.photo.id == group.bestId,
                    markedForDelete = m.photo.id in toDelete,
                    thumbSize = if (m.photo.id == group.bestId) 120.dp else 96.dp,
                    onLongClick = {
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        onPreview(i)
                    },
                    onClick = { onToggle(m.photo.id) },
                )
            }
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(CircleShape).background(Mint.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Done, null, tint = Mint, modifier = Modifier.size(48.dp)) }
        Spacer(Modifier.height(20.dp))
        Text("Galleria già in ordine!", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Non ho trovato foto simili con la sensibilità attuale. Prova ad abbassare il grado di somiglianza dalle impostazioni.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
