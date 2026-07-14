package com.example.netspeedoverlay.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.graphics.Color
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.netspeedoverlay.data.DisplayMode
import com.example.netspeedoverlay.data.HorizontalPosition
import com.example.netspeedoverlay.data.IconStyle
import com.example.netspeedoverlay.data.IndicatorMode
import com.example.netspeedoverlay.data.NotificationMetric
import com.example.netspeedoverlay.data.OverlaySettings
import com.example.netspeedoverlay.data.SettingsRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by settingsRepository.settingsFlow.collectAsState(initial = OverlaySettings())
    val scope = rememberCoroutineScope()
    // NOTE: this only tracks "did I press start/stop in this screen session",
    // it doesn't query the real service state (e.g. after process death).
    // Fine for a first version; a StateFlow exposed by the service is the
    // natural next step if that gap matters to you.
    var indicatorRunning by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Indicatore velocità di rete", style = MaterialTheme.typography.headlineSmall)

        SectionLabel("Modalità")
        ChoiceRow(IndicatorMode.entries, settings.indicatorMode, { it.label() }) {
            scope.launch { settingsRepository.setIndicatorMode(it) }
        }
        if (settings.indicatorMode == IndicatorMode.NOTIFICATION_ICON) {
            Text(
                "L'icona della notifica vive per davvero nella status bar, non è un overlay. " +
                    "In cambio ha spazio per pochi caratteri e la sua posizione nel vassoio " +
                    "icone la decide il sistema, non tu.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        val needsOverlayPermission = settings.indicatorMode == IndicatorMode.OVERLAY && !hasOverlayPermission
        if (needsOverlayPermission) {
            PermissionCard(onRequestOverlayPermission)
        } else {
            Button(onClick = {
                indicatorRunning = !indicatorRunning
                if (indicatorRunning) onStartOverlay() else onStopOverlay()
            }) {
                Text(if (indicatorRunning) "Ferma indicatore" else "Avvia indicatore")
            }
        }

        HorizontalDivider()

        if (settings.indicatorMode == IndicatorMode.NOTIFICATION_ICON) {
            SectionLabel("Cosa mostrare")
            ChoiceRow(NotificationMetric.entries, settings.notificationMetric, { it.label() }) {
                scope.launch { settingsRepository.setNotificationMetric(it) }
            }
            Text(
                "Spazio limitato a poche cifre (es. \"340K\", \"1.2M\"): niente unità \"/s\" " +
                    "né download e upload insieme, solo il valore scelto qui sopra.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        if (settings.indicatorMode == IndicatorMode.OVERLAY) {

        SectionLabel("Posizione")
        SwitchSetting("Posizione libera (trascina l'overlay)", settings.freePosition) {
            scope.launch { settingsRepository.setFreePosition(it) }
        }
        if (settings.freePosition) {
            Text(
                "Trascina l'indicatore per spostarlo ovunque sullo schermo, " +
                    "anche sopra la status bar o la barra di navigazione. " +
                    "La posizione viene salvata automaticamente.",
                style = MaterialTheme.typography.bodySmall
            )
            Button(onClick = { scope.launch { settingsRepository.setPosition(8, 120) } }) {
                Text("Riporta in posizione raggiungibile")
            }
        } else {
            ChoiceRow(HorizontalPosition.entries, settings.horizontalPosition, { it.label() }) {
                scope.launch { settingsRepository.setHorizontalPosition(it) }
            }
            SliderSetting("Distanza dal bordo superiore", settings.verticalOffsetDp, 0..24, { "$it dp" }) {
                scope.launch { settingsRepository.setVerticalOffsetDp(it) }
            }
        }

        SectionLabel("Formato")
        ChoiceRow(DisplayMode.entries, settings.displayMode, { it.label() }) {
            scope.launch { settingsRepository.setDisplayMode(it) }
        }
        if (settings.displayMode == DisplayMode.STACKED) {
            SliderSetting("Distanza tra le righe", settings.lineSpacingDp, -30..24, { "$it dp" }) {
                scope.launch { settingsRepository.setLineSpacingDp(it) }
            }
        }
        ChoiceRow(IconStyle.entries, settings.iconStyle, { it.label() }) {
            scope.launch { settingsRepository.setIconStyle(it) }
        }
        SwitchSetting("Mostra suffisso \"/s\"", settings.showPerSecondSuffix) {
            scope.launch { settingsRepository.setShowPerSecondSuffix(it) }
        }

        SectionLabel("Aspetto")
        ColorSwatchRow(
            label = "Colore testo",
            swatches = listOf(
                "Bianco" to 0xFFFFFFFF.toInt(),
                "Nero" to 0xFF000000.toInt(),
                "Grigio" to 0xFFAAAAAA.toInt()
            ),
            selected = settings.textColorArgb
        ) { scope.launch { settingsRepository.setTextColorArgb(it) } }

        SwitchSetting("Mostra sfondo", settings.showBackground) {
            scope.launch { settingsRepository.setShowBackground(it) }
        }
        if (settings.showBackground) {
            ColorSwatchRow(
                label = "Colore sfondo",
                swatches = listOf(
                    "Scuro" to 0xAA000000.toInt(),
                    "Chiaro" to 0xCCFFFFFF.toInt(),
                    "Trasparente" to 0x00000000.toInt()
                ),
                selected = settings.backgroundColorArgb
            ) { scope.launch { settingsRepository.setBackgroundColorArgb(it) } }
        }
        SliderSetting("Dimensione testo", settings.fontSizeSp, 9..24, { "$it sp" }) {
            scope.launch { settingsRepository.setFontSizeSp(it) }
        }
        SwitchSetting("Grassetto", settings.bold) {
            scope.launch { settingsRepository.setBold(it) }
        }

        } // fine sezioni esclusive di IndicatorMode.OVERLAY

        SectionLabel("Comportamento")
        SliderSetting(
            "Intervallo di aggiornamento",
            (settings.updateIntervalMs / 500).toInt(),
            1..10,
            { "${it * 0.5} s" }
        ) {
            scope.launch { settingsRepository.setUpdateIntervalMs(it * 500L) }
        }
        if (settings.indicatorMode == IndicatorMode.OVERLAY) {
        SwitchSetting("Attenua quando inattivo", settings.dimWhenIdle) {
            scope.launch { settingsRepository.setDimWhenIdle(it) }
        }
        if (settings.dimWhenIdle) {
            SliderSetting(
                "Soglia inattività",
                (settings.idleThresholdBytesPerSec / 1024).toInt().coerceAtLeast(1),
                1..100,
                { "$it KB/s" }
            ) {
                scope.launch { settingsRepository.setIdleThresholdBytesPerSec(it * 1024L) }
            }
        }
        }
    }
}

@Composable
private fun PermissionCard(onRequestPermission: () -> Unit) {
    Card {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Serve il permesso \"Visualizza sopra altre app\" per mostrare l'overlay.")
            Button(onClick = onRequestPermission) { Text("Concedi permesso") }
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium)
}

@Composable
private fun SwitchSetting(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

@Composable
private fun SliderSetting(
    label: String,
    value: Int,
    range: IntRange,
    valueLabel: (Int) -> String,
    onChange: (Int) -> Unit
) {
    Column {
        Text("$label: ${valueLabel(value)}")
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0)
        )
    }
}

@Composable
private fun ColorSwatchRow(
    label: String,
    swatches: List<Pair<String, Int>>,
    selected: Int,
    onSelect: (Int) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            swatches.forEach { (name, argb) ->
                val isSelected = argb == selected
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            color = Color(argb.toLong() and 0xFFFFFFFFL),
                            shape = RoundedCornerShape(8.dp)
                        )
                        .border(
                            width = if (isSelected) 3.dp else 1.dp,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Gray,
                            shape = RoundedCornerShape(8.dp)
                        )
                        .clickable { onSelect(argb) }
                )
            }
        }
    }
}

@Composable
private fun <T> ChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            FilterChip(
                selected = option == selected,
                onClick = { onSelect(option) },
                label = { Text(label(option)) }
            )
        }
    }
}

private fun IndicatorMode.label() = when (this) {
    IndicatorMode.OVERLAY -> "Overlay"
    IndicatorMode.NOTIFICATION_ICON -> "Icona notifica"
}

private fun NotificationMetric.label() = when (this) {
    NotificationMetric.DOWNLOAD -> "Download"
    NotificationMetric.UPLOAD -> "Upload"
    NotificationMetric.COMBINED -> "Combinato"
}

private fun HorizontalPosition.label() = when (this) {
    HorizontalPosition.LEFT -> "Sinistra"
    HorizontalPosition.CENTER -> "Centro"
    HorizontalPosition.RIGHT -> "Destra"
}

private fun DisplayMode.label() = when (this) {
    DisplayMode.STACKED -> "Due righe"
    DisplayMode.INLINE -> "Una riga"
}

private fun IconStyle.label() = when (this) {
    IconStyle.NONE -> "Nessuna"
    IconStyle.ARROWS -> "Frecce"
    IconStyle.LETTERS -> "Lettere"
}
