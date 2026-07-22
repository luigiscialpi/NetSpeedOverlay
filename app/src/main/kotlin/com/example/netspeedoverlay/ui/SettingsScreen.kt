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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import android.os.Build
import com.example.netspeedoverlay.R
import com.example.netspeedoverlay.data.DailyUsage
import com.example.netspeedoverlay.data.DailyUsageRepository
import com.example.netspeedoverlay.data.DisplayMode
import com.example.netspeedoverlay.data.IconStyle
import com.example.netspeedoverlay.data.IndicatorMode
import com.example.netspeedoverlay.data.NotificationMetric
import com.example.netspeedoverlay.data.OverlaySettings
import com.example.netspeedoverlay.data.SettingsRepository
import com.example.netspeedoverlay.data.VerticalAnchor
import com.example.netspeedoverlay.overlay.NetSpeedOverlayService
import com.example.netspeedoverlay.speed.SpeedSampler
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    settingsRepository: SettingsRepository,
    dailyUsageRepository: DailyUsageRepository,
    hasOverlayPermission: Boolean,
    onRequestOverlayPermission: () -> Unit,
    onStartOverlay: () -> Unit,
    onStopOverlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    val settings by settingsRepository.settingsFlow.collectAsState(initial = OverlaySettings())
    val dailyUsage by dailyUsageRepository.dailyUsageFlow.collectAsState(initial = DailyUsage(0L, 0L))
    val scope = rememberCoroutineScope()
    // Stato reale del servizio (StateFlow esposto da NetSpeedOverlayService),
    // non più un booleano locale: così il pulsante resta corretto anche se il
    // servizio viene avviato/fermato da fuori questa schermata o ucciso dal
    // sistema.
    val indicatorRunning by NetSpeedOverlayService.isRunning.collectAsState()
    var showResetConfirm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Text("Indicatore velocità di rete", style = MaterialTheme.typography.headlineSmall)

        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("Dati di oggi", style = MaterialTheme.typography.titleSmall)
                Text(
                    "↓ ${SpeedSampler.formatTotalBytes(dailyUsage.rxBytes)}   " +
                        "↑ ${SpeedSampler.formatTotalBytes(dailyUsage.txBytes)}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

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
            if (isXiaomiMiui()) {
                Text(
                    stringResource(R.string.miui_icon_warning),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (settings.indicatorMode == IndicatorMode.NOTIFICATION_TEXT) {
            Text(
                "Mostra i valori solo come testo della notifica persistente del servizio " +
                    "(visibile aprendo la tendina), senza overlay flottante né icona " +
                    "ridisegnata: utile se preferisci un indicatore meno invadente, dato che " +
                    "la notifica del servizio in foreground è comunque obbligatoria su " +
                    "qualunque modalità.",
                style = MaterialTheme.typography.bodySmall
            )
        }

        val needsOverlayPermission = settings.indicatorMode == IndicatorMode.OVERLAY && !hasOverlayPermission
        if (needsOverlayPermission) {
            PermissionCard(onRequestOverlayPermission)
        } else {
            Button(onClick = {
                if (indicatorRunning) onStopOverlay() else onStartOverlay()
            }) {
                Text(if (indicatorRunning) "Ferma indicatore" else "Avvia indicatore")
            }
        }
        SwitchSetting("Avvia automaticamente all'accensione", settings.autoStartOnBoot) {
            scope.launch { settingsRepository.setAutoStartOnBoot(it) }
        }

        HorizontalDivider()

        if (settings.indicatorMode == IndicatorMode.NOTIFICATION_ICON) {
            SectionLabel("Cosa mostrare")
            ChoiceRow(NotificationMetric.entries, settings.notificationMetric, { it.label() }) {
                scope.launch { settingsRepository.setNotificationMetric(it) }
            }
            Text(
                "Spazio limitato a poche cifre (es. \"0K\", \"0.2K\", \"12K\"): niente unità \"/s\" " +
                    "né download e upload insieme, solo il valore scelto qui sopra.",
                style = MaterialTheme.typography.bodySmall
            )
            SwitchSetting("Icona a due righe (↑ upload / ↓ download)", settings.notificationTwoLines) {
                scope.launch { settingsRepository.setNotificationTwoLines(it) }
            }
            SectionLabel("Stile icona")
            ChoiceRow(IconStyle.entries, settings.iconStyle, { it.label() }) {
                scope.launch { settingsRepository.setIconStyle(it) }
            }
            if (settings.notificationTwoLines) {
                Text(
                    "Mostra download e upload su due righe invece di un solo valore: " +
                        "la scelta \"Cosa mostrare\" qui sopra viene ignorata e i caratteri " +
                        "sono più piccoli perché il sistema ridimensiona l'icona a pochi dp " +
                        "nella status bar.",
                    style = MaterialTheme.typography.bodySmall
                )
                SliderSetting(
                    "Distanza tra le righe",
                    settings.notificationLineSpacing,
                    -24..24,
                    { "$it px" }
                ) {
                    scope.launch { settingsRepository.setNotificationLineSpacing(it) }
                }
            }
            SliderSetting(
                label = "Dimensione caratteri icona",
                value = settings.notificationFontSizePct,
                range = 50..150,
                valueLabel = { if (settings.notificationAutoFit) "Automatico" else "$it% (${(it * 1.2f).toInt()}% per 1-2 cifre senza punti)" },
                onChange = { scope.launch { settingsRepository.setNotificationFontSizePct(it) } },
                enabled = !settings.notificationAutoFit
            )
            SwitchSetting("Gestione automatica (auto-fit)", settings.notificationAutoFit) {
                scope.launch { settingsRepository.setNotificationAutoFit(it) }
            }
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
            SliderSetting(
                if (settings.verticalAnchor == VerticalAnchor.BOTTOM) "Posizione orizzontale (sotto)" else "Posizione orizzontale (sopra)",
                settings.horizontalOffsetPct,
                0..100,
                { "$it %" }
            ) {
                scope.launch { settingsRepository.setHorizontalOffsetPct(it) }
            }
            ChoiceRow(VerticalAnchor.entries, settings.verticalAnchor, { it.label() }) {
                scope.launch { settingsRepository.setVerticalAnchor(it) }
            }
            val offsetLabel = if (settings.verticalAnchor == VerticalAnchor.BOTTOM) {
                "Distanza dal fondo schermo"
            } else {
                "Distanza dal bordo superiore"
            }
            SliderSetting(offsetLabel, settings.verticalOffsetDp, 0..24, { "$it dp" }) {
                scope.launch { settingsRepository.setVerticalOffsetDp(it) }
            }
            if (settings.verticalAnchor == VerticalAnchor.BOTTOM) {
                Text(
                    "Sotto/interno alla barra di navigazione. Lo slider orizzontale " +
                        "sposta l'overlay su tutta la larghezza: i tasti sono centrati, " +
                        "quindi 50% capita circa tra home e recenti.",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        } // fine sezione "Posizione", esclusiva di IndicatorMode.OVERLAY

        if (settings.indicatorMode == IndicatorMode.OVERLAY || settings.indicatorMode == IndicatorMode.NOTIFICATION_TEXT) {

        SectionLabel("Formato")
        if (settings.indicatorMode == IndicatorMode.OVERLAY) {
            ChoiceRow(DisplayMode.entries, settings.displayMode, { it.label() }) {
                scope.launch { settingsRepository.setDisplayMode(it) }
            }
            if (settings.displayMode == DisplayMode.STACKED) {
                SliderSetting("Distanza tra le righe", settings.lineSpacingDp, -30..24, { "$it dp" }) {
                    scope.launch { settingsRepository.setLineSpacingDp(it) }
                }
            }
        }
        ChoiceRow(IconStyle.entries, settings.iconStyle, { it.label() }) {
            scope.launch { settingsRepository.setIconStyle(it) }
        }
        SwitchSetting("Mostra suffisso \"/s\"", settings.showPerSecondSuffix) {
            scope.launch { settingsRepository.setShowPerSecondSuffix(it) }
        }

        } // fine sezione "Formato", condivisa tra OVERLAY e NOTIFICATION_TEXT

        if (settings.indicatorMode == IndicatorMode.OVERLAY) {

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
        SwitchSetting("Mostra mini-grafico", settings.showSparkline) {
            scope.launch { settingsRepository.setShowSparkline(it) }
        }

        } // fine sezione "Aspetto", esclusiva di IndicatorMode.OVERLAY

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
        
        HorizontalDivider()
        SectionLabel("Impostazioni")
        if (showResetConfirm) {
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Ripristinare tutte le impostazioni ai valori predefiniti?",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            scope.launch {
                                settingsRepository.resetSettings()
                                showResetConfirm = false
                            }
                        }) { Text("Conferma") }
                        Button(onClick = { showResetConfirm = false }) { Text("Annulla") }
                    }
                }
            }
        } else {
            Button(onClick = { showResetConfirm = true }) {
                Text("Reimposta impostazioni")
            }
        }

        HorizontalDivider()
        SectionLabel("Limitazioni note")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    "Nota sul traffico Hotspot (Tethering):",
                    style = MaterialTheme.typography.titleSmall
                )
                Text(
                    "Il traffico Wi-Fi e dati mobile generato direttamente dal telefono viene misurato in tempo reale. " +
                        "Tuttavia, il traffico dei dispositivi collegati al tuo Hotspot non può essere registrato a causa " +
                        "di limitazioni di sicurezza di Android 10+ e dell'accelerazione hardware (Qualcomm IPA offload) " +
                        "che instrada il traffico tethering al di fuori del kernel di sistema.",
                    style = MaterialTheme.typography.bodyMedium
                )
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
    enabled: Boolean = true,
    onChange: (Int) -> Unit
) {
    val textColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    Column {
        Text("$label: ${valueLabel(value)}", color = textColor)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange(it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            enabled = enabled
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
    IndicatorMode.NOTIFICATION_TEXT -> "Solo notifica"
}

private fun NotificationMetric.label() = when (this) {
    NotificationMetric.DOWNLOAD -> "Download"
    NotificationMetric.UPLOAD -> "Upload"
    NotificationMetric.COMBINED -> "Combinato"
}

private fun VerticalAnchor.label() = when (this) {
    VerticalAnchor.TOP -> "Sopra"
    VerticalAnchor.BOTTOM -> "Sotto"
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

/** Detects Xiaomi/Redmi/POCO devices (MIUI/HyperOS), where the status bar
 * replaces the custom notification small icon with the app icon. */
private fun isXiaomiMiui(): Boolean {
    val manufacturer = Build.MANUFACTURER?.lowercase() ?: ""
    if (manufacturer == "xiaomi" || manufacturer == "redmi" || manufacturer == "poco") {
        return true
    }
    return try {
        val prop = Runtime.getRuntime()
            .exec("getprop ro.miui.ui.version.name")
            .inputStream.bufferedReader().readLine()
        !prop.isNullOrBlank()
    } catch (_: Exception) {
        false
    }
}
