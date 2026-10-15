package com.simphotos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.simphotos.app.ui.UiState
import com.simphotos.app.ui.components.GradientButton
import com.simphotos.app.ui.components.StatTile
import com.simphotos.app.ui.components.formatBytes
import com.simphotos.app.ui.components.formatRelative
import com.simphotos.app.ui.theme.BrandGradient
import com.simphotos.app.ui.theme.Violet

@Composable
fun HomeScreen(
    state: UiState,
    hasResults: Boolean,
    onScan: () -> Unit,
    onOpenResults: () -> Unit,
    onSettings: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("SimPhotos", style = MaterialTheme.typography.headlineMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Impostazioni") }
        }
        Spacer(Modifier.height(16.dp))

        // Hero card
        Box(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(30.dp))
                .background(BrandGradient)
                .padding(24.dp)
        ) {
            Column {
                Box(
                    Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White) }
                Spacer(Modifier.height(18.dp))
                Text(
                    "Una galleria più leggera,\nsenza doppioni.",
                    style = MaterialTheme.typography.headlineSmall,
                    color = Color.White,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Trovo le foto identiche o quasi uguali scattate nello stesso momento e ti propongo di tenere solo la migliore.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.85f),
                )
                Spacer(Modifier.height(22.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(54.dp)
                        .clip(RoundedCornerShape(18.dp))
                        .background(Color.White)
                        .clickable(onClick = onScan),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Search, null, tint = Violet)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (state.lastScanAt > 0) "Scansiona di nuovo" else "Avvia scansione",
                            style = MaterialTheme.typography.titleMedium,
                            color = Violet,
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile(state.galleryCount?.toString() ?: "–", "Foto in galleria", Modifier.weight(1f))
            StatTile(formatBytes(state.totalFreedBytes), "Spazio liberato", Modifier.weight(1f))
        }
        if (state.lastScanAt > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                "Ultima scansione ${formatRelative(state.lastScanAt)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 4.dp),
            )
        }

        if (hasResults) {
            Spacer(Modifier.height(16.dp))
            GradientButton(
                "Rivedi ${state.groups.size} gruppi trovati",
                icon = Icons.Rounded.PhotoLibrary,
                modifier = Modifier.fillMaxWidth(),
                onClick = onOpenResults,
            )
        }

        Spacer(Modifier.height(28.dp))
        Text("Come funziona", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(12.dp))
        Step(Icons.Rounded.Search, "Analisi intelligente", "Ogni foto viene confrontata con quelle scattate nello stesso periodo. Tutto avviene sul telefono, nulla viene caricato online.")
        Step(Icons.Rounded.AutoAwesome, "Sceglie la migliore", "Per ogni gruppo valuta nitidezza, esposizione e risoluzione e mette da parte la foto venuta meglio.")
        Step(Icons.Rounded.CheckCircle, "Decidi tu", "Niente viene cancellato senza la tua conferma. Le foto finiscono nel cestino e puoi recuperarle per 30 giorni.")
        Step(Icons.Rounded.Tune, "Regola la sensibilità", "Dalle impostazioni scegli quanto devono essere simili le foto per essere raggruppate.")
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Step(icon: ImageVector, title: String, body: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp))
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, tint = MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
