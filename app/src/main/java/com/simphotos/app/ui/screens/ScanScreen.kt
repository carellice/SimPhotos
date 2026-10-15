package com.simphotos.app.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.simphotos.app.ui.ScanPhase
import com.simphotos.app.ui.ScanProgress
import com.simphotos.app.ui.theme.NumberStyle
import com.simphotos.app.ui.theme.Orchid
import com.simphotos.app.ui.theme.Violet

@Composable
fun ScanScreen(progress: ScanProgress, onCancel: () -> Unit) {
    val fraction = if (progress.total > 0) progress.done.toFloat() / progress.total else 0f
    val animated by animateFloatAsState(fraction, tween(400), label = "progress")
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "angle")
    val pulse by spin.animateFloat(0.92f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    val track = MaterialTheme.colorScheme.surfaceVariant

    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(260.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 16.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    Brush.sweepGradient(listOf(Violet, Orchid, Violet)),
                    -90f, 360f * animated, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
            // Dashed orbit spinning to show activity even when progress is slow.
            Canvas(Modifier.size(200.dp).rotate(angle)) {
                drawCircle(Orchid.copy(alpha = 0.5f), radius = 5.dp.toPx(), center = Offset(size.width / 2, 0f))
            }
            Box(
                Modifier.size(170.dp * pulse).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center,
            ) {
                AnimatedContent(
                    progress.currentUri,
                    transitionSpec = { fadeIn(tween(250)) togetherWith fadeOut(tween(250)) },
                    label = "thumb",
                ) { uri ->
                    if (uri != null) {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current).data(uri).size(256).build(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize().clip(CircleShape),
                        )
                    }
                }
                Box(
                    Modifier.fillMaxSize().clip(CircleShape)
                        .background(MaterialTheme.colorScheme.background.copy(alpha = 0.55f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("${(animated * 100).toInt()}%", style = NumberStyle, color = MaterialTheme.colorScheme.onBackground)
                }
            }
        }
        Spacer(Modifier.height(36.dp))
        Text(
            when (progress.phase) {
                ScanPhase.READING -> "Leggo la galleria…"
                ScanPhase.ANALYZING -> "Analizzo le tue foto"
                ScanPhase.GROUPING -> "Raggruppo le foto simili…"
            },
            style = MaterialTheme.typography.headlineSmall,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            if (progress.total > 0) "${progress.done} di ${progress.total} foto" else "Un attimo di pazienza",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "La prima scansione richiede più tempo. Le successive analizzeranno solo le foto nuove.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = 24.dp),
        )
        Spacer(Modifier.height(36.dp))
        OutlinedButton(onClick = onCancel) { Text("Annulla") }
    }
}
