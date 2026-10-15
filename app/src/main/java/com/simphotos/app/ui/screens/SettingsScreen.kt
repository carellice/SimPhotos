package com.simphotos.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.simphotos.app.data.AppSettings
import com.simphotos.app.data.DeleteMode
import com.simphotos.app.data.TimeWindow
import com.simphotos.app.ui.components.SectionLabel
import com.simphotos.app.ui.components.formatBytes
import com.simphotos.app.ui.UpdateState
import com.simphotos.app.ui.theme.BrandGradient
import com.simphotos.app.ui.theme.Mint
import com.simphotos.app.ui.theme.Violet
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudOff
import androidx.compose.material.icons.rounded.NewReleases
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import kotlin.math.roundToInt

private fun thresholdLabel(t: Float): Pair<String, String> = when {
    t >= 0.95f -> "Solo quasi identiche" to "Raggruppa solo foto praticamente uguali, come raffiche o copie."
    t >= 0.89f -> "Molto simili" to "Stessa scena e inquadratura, piccole differenze di posa o movimento."
    t >= 0.83f -> "Simili" to "Stessa scena con leggere variazioni di inquadratura o zoom."
    else -> "Più permissivo" to "Raggruppa anche foto della stessa scena con differenze evidenti. Controlla bene prima di eliminare."
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    settings: AppSettings,
    onBack: () -> Unit,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onClearCache: () -> Unit,
    currentVersion: String,
    update: UpdateState,
    onCheckUpdate: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstallUpdate: () -> Unit,
) {
    LaunchedEffect(Unit) { onCheckUpdate() }
    // Keep the slider value local while dragging and commit on release, so we don't regroup at every frame.
    var sliderValue by remember(settings.threshold) { mutableFloatStateOf(settings.threshold) }
    var cacheCleared by remember { mutableStateOf(false) }

    Column(
        Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Indietro") }
            Text("Impostazioni", style = MaterialTheme.typography.titleLarge)
        }
        Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
            SectionLabel("Aggiornamenti", Modifier.padding(top = 12.dp, bottom = 8.dp))
            UpdateCard(currentVersion, update, onCheckUpdate, onDownloadUpdate, onCancelDownload, onInstallUpdate)

            SectionLabel("Somiglianza", Modifier.padding(top = 24.dp, bottom = 8.dp))
            Card {
                val (title, body) = thresholdLabel(sliderValue)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    Text(
                        "${(sliderValue * 100).roundToInt()}%",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Slider(
                    value = sliderValue,
                    onValueChange = { sliderValue = (it * 100).roundToInt() / 100f },
                    onValueChangeFinished = { onChange { s -> s.copy(threshold = sliderValue) } },
                    valueRange = 0.75f..0.98f,
                    modifier = Modifier.padding(top = 8.dp),
                )
                Row {
                    Text("Più foto", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    Text("Più precisione", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            SectionLabel("Periodo", Modifier.padding(top = 24.dp, bottom = 8.dp))
            Card {
                Text("Confronta foto scattate entro", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TimeWindow.entries.forEach { w ->
                        FilterChip(
                            selected = settings.timeWindow == w,
                            onClick = { onChange { it.copy(timeWindow = w) } },
                            label = { Text(w.label) },
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "Un intervallo breve è più veloce e preciso. \"Sempre\" confronta tutto il rullino.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("Analisi", Modifier.padding(top = 24.dp, bottom = 8.dp))
            Card {
                SwitchRow(
                    "Copie identiche ovunque",
                    "Trova i doppioni esatti anche se salvati in date diverse (es. da WhatsApp).",
                    settings.findExactDuplicatesEverywhere,
                ) { v -> onChange { it.copy(findExactDuplicatesEverywhere = v) } }
                SwitchRow(
                    "Includi screenshot",
                    "Analizza anche le catture dello schermo.",
                    settings.includeScreenshots,
                ) { v -> onChange { it.copy(includeScreenshots = v) } }
                SwitchRow(
                    "Proteggi le preferite",
                    "Le foto contrassegnate come preferite non vengono mai selezionate per l'eliminazione.",
                    settings.protectFavorites,
                ) { v -> onChange { it.copy(protectFavorites = v) } }
            }

            SectionLabel("Eliminazione", Modifier.padding(top = 24.dp, bottom = 8.dp))
            Card {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SegmentedButton(
                        selected = settings.deleteMode == DeleteMode.TRASH,
                        onClick = { onChange { it.copy(deleteMode = DeleteMode.TRASH) } },
                        shape = SegmentedButtonDefaults.itemShape(0, 2),
                    ) { Text("Cestino") }
                    SegmentedButton(
                        selected = settings.deleteMode == DeleteMode.PERMANENT,
                        onClick = { onChange { it.copy(deleteMode = DeleteMode.PERMANENT) } },
                        shape = SegmentedButtonDefaults.itemShape(1, 2),
                    ) { Text("Definitiva") }
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    if (settings.deleteMode == DeleteMode.TRASH)
                        "Consigliato: le foto restano recuperabili nel cestino per 30 giorni."
                    else "Le foto vengono eliminate subito e non possono essere recuperate.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            SectionLabel("Dati", Modifier.padding(top = 24.dp, bottom = 8.dp))
            Card {
                Text("Memoria dell'analisi", style = MaterialTheme.typography.titleMedium)
                Text(
                    "SimPhotos ricorda le foto già analizzate per rendere le scansioni successive più rapide.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TextButton(onClick = { onClearCache(); cacheCleared = true }, enabled = !cacheCleared) {
                    Text(if (cacheCleared) "Memoria svuotata" else "Svuota memoria")
                }
            }
            Spacer(Modifier.height(24.dp))
            Text(
                "Tutte le analisi avvengono sul dispositivo. Nessuna foto lascia il tuo telefono.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 32.dp),
            )
        }
    }
}

@Composable
private fun UpdateCard(
    currentVersion: String,
    update: UpdateState,
    onCheck: () -> Unit,
    onDownload: () -> Unit,
    onCancel: () -> Unit,
    onInstall: () -> Unit,
) {
    val highlighted = update is UpdateState.Available || update is UpdateState.Downloading ||
        update is UpdateState.ReadyToInstall
    val onColor = if (highlighted) Color.White else MaterialTheme.colorScheme.onSurface
    val subColor = if (highlighted) Color.White.copy(alpha = 0.85f) else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(if (highlighted) BrandGradient else SolidColor(MaterialTheme.colorScheme.surfaceContainer))
            .animateContentSize()
            .padding(18.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(44.dp).clip(CircleShape)
                    .background(if (highlighted) Color.White.copy(alpha = 0.2f) else MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center,
            ) {
                when (update) {
                    UpdateState.Checking -> CircularProgressIndicator(
                        strokeWidth = 2.5.dp, modifier = Modifier.size(22.dp), color = MaterialTheme.colorScheme.primary,
                    )
                    UpdateState.UpToDate -> Icon(Icons.Rounded.CheckCircle, null, tint = Mint)
                    is UpdateState.Failed -> Icon(Icons.Rounded.CloudOff, null, tint = MaterialTheme.colorScheme.error)
                    UpdateState.Idle -> Icon(Icons.Rounded.SystemUpdate, null, tint = MaterialTheme.colorScheme.primary)
                    else -> Icon(Icons.Rounded.NewReleases, null, tint = Color.White)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    when (update) {
                        UpdateState.Idle, UpdateState.Checking -> "Cerco aggiornamenti…"
                        UpdateState.UpToDate -> "SimPhotos è aggiornata"
                        is UpdateState.Failed -> "Controllo non riuscito"
                        is UpdateState.Available -> "Nuova versione ${update.info.version}"
                        is UpdateState.Downloading -> "Download in corso…"
                        is UpdateState.ReadyToInstall -> "Pronta da installare"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = onColor,
                )
                Text(
                    when (update) {
                        is UpdateState.Failed -> update.message
                        is UpdateState.Available -> "Versione attuale $currentVersion · ${formatBytes(update.info.sizeBytes)}"
                        is UpdateState.Downloading -> "${(update.progress * 100).toInt()}% di ${formatBytes(update.info.sizeBytes)}"
                        is UpdateState.ReadyToInstall -> "Versione ${update.info.version} scaricata"
                        else -> "Versione $currentVersion"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = subColor,
                )
            }
            if (update == UpdateState.UpToDate || update is UpdateState.Failed) {
                IconButton(onClick = onCheck) {
                    Icon(Icons.Rounded.Refresh, "Controlla di nuovo", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }

        if (update is UpdateState.Available && update.info.notes.isNotBlank()) {
            Spacer(Modifier.height(12.dp))
            Text(update.info.notes, style = MaterialTheme.typography.bodyMedium, color = subColor)
        }
        if (update is UpdateState.Downloading) {
            Spacer(Modifier.height(14.dp))
            LinearProgressIndicator(
                progress = { update.progress },
                modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.25f),
                drawStopIndicator = {},
            )
        }
        if (highlighted) {
            Spacer(Modifier.height(14.dp))
            Box(
                Modifier.fillMaxWidth().height(48.dp).clip(RoundedCornerShape(16.dp)).background(Color.White)
                    .clickable {
                        when (update) {
                            is UpdateState.Available -> onDownload()
                            is UpdateState.Downloading -> onCancel()
                            else -> onInstall()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    when (update) {
                        is UpdateState.Available -> "Scarica e installa"
                        is UpdateState.Downloading -> "Annulla"
                        else -> "Installa ora"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = Violet,
                )
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(22.dp))
            .background(MaterialTheme.colorScheme.surfaceContainer).padding(18.dp)
    ) { content() }
}

@Composable
private fun SwitchRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
