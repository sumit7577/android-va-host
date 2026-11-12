package dev.bello.vahost.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.bello.vahost.probe.LoadedArtifact

@Composable
fun LoaderOverlay(
    state: OverlayState,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
    onDrag: (Float, Float) -> Unit,
) {
    MaterialTheme {
        Surface(
            modifier = Modifier
                .width(300.dp)
                .clip(RoundedCornerShape(12.dp))
                .pointerInput(Unit) {
                    detectDragGestures { change, delta ->
                        change.consume()
                        onDrag(delta.x, delta.y)
                    }
                },
            color = Color(0xE6101418),
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Header(state = state, collapsed = collapsed, onToggle = onToggle, onClose = onClose)
                if (!collapsed) {
                    if (state.processes.isEmpty()) {
                        Text(
                            text = "no guest process running",
                            color = Color(0xFF8A94A0),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp),
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.heightIn(max = 320.dp),
                            verticalArrangement = Arrangement.spacedBy(2.dp),
                        ) {
                            state.processes.forEach { process ->
                                item(key = "p${process.pid}") { ProcessHeader(process) }
                                items(process.artifacts, key = { "${process.pid}${it.path}" }) { artifact ->
                                    ArtifactRow(artifact, isNew = artifact.path in state.newlyLoaded)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Header(
    state: OverlayState,
    collapsed: Boolean,
    onToggle: () -> Unit,
    onClose: () -> Unit,
) {
    val libs = state.processes.sumOf { p -> p.artifacts.count { it.kind == LoadedArtifact.Kind.NATIVE_LIB } }
    val dex = state.processes.sumOf { p -> p.artifacts.count { it.kind == LoadedArtifact.Kind.DEX } }
    Row(
        modifier = Modifier.padding(bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "guest loader",
            color = Color(0xFFE6EDF3),
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f),
        )
        Text("$dex dex", color = Color(0xFF7EE787), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text("$libs so", color = Color(0xFF79C0FF), fontSize = 10.sp, fontFamily = FontFamily.Monospace)
        Text(
            text = if (collapsed) "+" else "–",
            color = Color(0xFF8A94A0),
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 4.dp).clickable(onClick = onToggle),
        )
        Text(
            text = "x",
            color = Color(0xFFF85149),
            fontSize = 12.sp,
            modifier = Modifier.clickable(onClick = onClose),
        )
    }
}

@Composable
private fun ProcessHeader(process: GuestProcess) {
    Text(
        text = "${process.name}  pid ${process.pid}",
        color = Color(0xFFD29922),
        fontSize = 10.sp,
        fontFamily = FontFamily.Monospace,
        modifier = Modifier.padding(top = 6.dp, bottom = 2.dp),
    )
}

@Composable
private fun ArtifactRow(artifact: LoadedArtifact, isNew: Boolean) {
    val kindColor = when (artifact.kind) {
        LoadedArtifact.Kind.NATIVE_LIB -> Color(0xFF79C0FF)
        LoadedArtifact.Kind.DEX -> Color(0xFF7EE787)
        LoadedArtifact.Kind.APK -> Color(0xFFD2A8FF)
        LoadedArtifact.Kind.OTHER -> Color(0xFF8A94A0)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        modifier = if (isNew) {
            Modifier.background(Color(0x3357D06E), RoundedCornerShape(3.dp)).padding(horizontal = 3.dp)
        } else {
            Modifier
        },
    ) {
        Text(
            text = when (artifact.kind) {
                LoadedArtifact.Kind.NATIVE_LIB -> "so "
                LoadedArtifact.Kind.DEX -> "dex"
                LoadedArtifact.Kind.APK -> "apk"
                LoadedArtifact.Kind.OTHER -> "   "
            },
            color = kindColor,
            fontSize = 9.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = artifact.name,
            // Guest artefacts are the interesting ones; system ones are context.
            color = if (artifact.isGuest) Color(0xFFE6EDF3) else Color(0xFF6E7681),
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
        )
    }
}

