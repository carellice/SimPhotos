package com.simphotos.app.ui.screens

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.simphotos.app.ui.DeleteSummary
import com.simphotos.app.ui.components.GradientButton
import com.simphotos.app.ui.components.formatBytes
import com.simphotos.app.ui.theme.BrandGradient

@Composable
fun PermissionScreen(onGrant: () -> Unit, denied: Boolean, onOpenSettings: () -> Unit) {
    CenteredPage {
        Box(
            Modifier.size(120.dp).clip(CircleShape).background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.PhotoLibrary, null, tint = Color.White, modifier = Modifier.size(56.dp)) }
        Spacer(Modifier.height(32.dp))
        Text("Benvenuto in SimPhotos", style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
        Spacer(Modifier.height(12.dp))
        Text(
            "Per trovare le foto doppie ho bisogno di accedere alla tua galleria. L'analisi avviene interamente sul telefono: le tue foto non vengono mai caricate online.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(40.dp))
        GradientButton("Consenti accesso alle foto", Modifier.fillMaxWidth(), onClick = onGrant)
        if (denied) {
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = onOpenSettings, modifier = Modifier.fillMaxWidth()) {
                Text("Apri impostazioni di sistema")
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Scegli \"Consenti tutto\" per analizzare l'intera galleria.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
fun DoneScreen(summary: DeleteSummary?, remainingGroups: Int, onHome: () -> Unit, onContinue: () -> Unit) {
    val pop = remember { Animatable(0f) }
    LaunchedEffect(Unit) { pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow)) }
    CenteredPage {
        Box(
            Modifier.size(140.dp).scale(pop.value).clip(CircleShape).background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(72.dp)) }
        Spacer(Modifier.height(32.dp))
        Text("Fatto!", style = MaterialTheme.typography.displaySmall)
        Spacer(Modifier.height(8.dp))
        if (summary != null) {
            Text(
                "Hai liberato ${formatBytes(summary.bytes)} eliminando ${summary.count} foto doppie.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Spacer(Modifier.height(40.dp))
        if (remainingGroups > 0) {
            GradientButton("Rivedi altri $remainingGroups gruppi", Modifier.fillMaxWidth(), onClick = onContinue)
            Spacer(Modifier.height(12.dp))
        }
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Torna alla home") }
    }
}

@Composable
private fun CenteredPage(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) { content() }
}
