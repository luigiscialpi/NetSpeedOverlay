# Piano di implementazione — 7 miglioramenti NetSpeedOverlay

Questo piano è scritto per essere eseguito **passo-passo, in ordine, da un
modello AI con accesso a strumenti di editing file + terminale**, su questo
stesso repository. Ogni task è autosufficiente: contiene percorsi file
assoluti-relativi alla root del repo, i blocchi di codice esatti da cercare
("PRIMA") e da produrre ("DOPO"), e un comando di verifica.

Root del repo: `/Users/lscialpi/Downloads/Altro/NetSpeedOverlay`

## Regola d'oro: verifica dopo ogni task

Dopo **ogni singolo task** (non solo alla fine):

```bash
cd /Users/lscialpi/Downloads/Altro/NetSpeedOverlay
./gradlew :app:compileDebugKotlin --offline
```

Deve terminare con `BUILD SUCCESSFUL`. Se fallisce, **non passare al task
successivo**: rileggi il file appena modificato per intero e correggi prima
di proseguire. Non fidarti solo di un eventuale linter/language server: solo
questo comando conferma che il codice compila davvero.

Dopo l'edit di un file, esegui anche `git diff <file>` e controlla a occhio
che il blocco sostituito sia esattamente quello atteso — in particolare
controlla che non ci siano righe incollate senza newline (es. due
`import` finiti concatenati sulla stessa riga: `import X.Yimport A.B`).
Se capita, è quasi sempre perché il testo "PRIMA" non terminava con `\n`
esattamente dove finiva il blocco da sostituire: causa errori di
compilazione anche lontani dal punto modificato.

## Errori da NON fare (letti dalla storia di questo progetto)

- **Locale**: qualunque `String.format` con `%f` deve avere `Locale.US` (o
  `Locale.ROOT`) come primo argomento esplicito. Senza, su un device in
  italiano il separatore decimale diventa `,` invece di `.` (es. `1,2M`
  invece di `1.2M`), il motivo esatto del Task 1 di questo piano.
- **`View.GONE` vs `View.INVISIBLE`**: `overlayRoot` (la `LinearLayout`
  radice dell'overlay) usa `View.INVISIBLE` per nascondersi, MAI `GONE`,
  perché una view `GONE` viene esclusa dal passo di layout e smette di
  ricevere `onApplyWindowInsets` per sempre. Questo vincolo riguarda **solo
  `overlayRoot`**. Per la `SparklineView` introdotta nel Task 7 (una view
  figlia, senza alcun listener di WindowInsets) `View.GONE` è corretto e
  voluto (libera lo spazio quando nascosta) — non applicare qui la regola
  di `overlayRoot` per errore.
- **`android:exported` non è "sempre true" o "sempre false"**: in questo
  piano trovi TRE componenti manifest con logica diversa:
  - `NetSpeedOverlayService` (Task 2): va messo `exported="false"` — nessun'altra
    app deve poterlo avviare/fermare, e nessuna intenzione dichiara di
    volerlo esposto.
  - `BootReceiver` (Task 5): **deve restare `exported="true"`** — è il
    sistema stesso a dover consegnare il broadcast `BOOT_COMPLETED`.
  - `NetSpeedTileService` (Task 6): **deve restare `exported="true"`**,
    protetto però dal permesso di sistema `BIND_QUICK_SETTINGS_TILE` (solo
    il sistema lo possiede, quindi nessuna app terza può comunque legarsi).
  Non "correggere" per coerenza i punti 2 e 3 a `false`: romperebbe la
  funzionalità.
- **JUnit4, non JUnit5**: questo progetto Android usa il test runner
  standard di Android Gradle Plugin, basato su JUnit4 (`org.junit.Test`,
  `org.junit.Assert`). Non aggiungere plugin JUnit5
  (`de.mannodermaus.android-junit5`) né annotazioni
  `org.junit.jupiter.api.Test`: non funzionerebbero senza una
  configurazione Gradle aggiuntiva che non fa parte di questo piano.
- **`sourceSets` personalizzati**: `app/build.gradle.kts` ridefinisce già
  `main.kotlin.srcDirs("src/main/kotlin")` invece del default
  `src/main/java`. Il set `test` **non eredita automaticamente** questo
  path: se non aggiungi anche `test.kotlin.srcDirs("src/test/kotlin")`
  (Task 3), Gradle non troverà i test creati in `src/test/kotlin`.

---

## Elenco dei task (esegui in quest'ordine)

| # | Task | File nuovi | File modificati |
|---|------|-----------|------------------|
| 1 | Fix locale numeri | — | `SpeedSampler.kt` |
| 2 | `exported=false` sul service | — | `AndroidManifest.xml` |
| 3 | Setup JUnit + test | `SpeedSamplerTest.kt` | `app/build.gradle.kts` |
| 4 | Totale dati di oggi | `DailyUsageRepository.kt` | `SpeedSampler.kt`, `NetSpeedOverlayService.kt`, `MainActivity.kt`, `SettingsScreen.kt`, `SpeedSamplerTest.kt` |
| 5 | Riavvio automatico al boot | `BootReceiver.kt` | `OverlaySettings.kt`, `SettingsRepository.kt`, `SettingsScreen.kt`, `AndroidManifest.xml` |
| 6 | Quick Settings Tile | `NetSpeedTileService.kt` | `AndroidManifest.xml` |
| 7 | Sparkline opzionale | `SparklineView.kt` | `OverlaySettings.kt`, `SettingsRepository.kt`, `SettingsScreen.kt`, `NetSpeedOverlayService.kt` |
| 8 | Build/verifica finale | — | — |
| 9 | Aggiornare README | — | `README.md` |

Package base: `com.example.netspeedoverlay` (percorso file:
`app/src/main/kotlin/com/example/netspeedoverlay/...`).

---

## Task 1 — Fix locale nella formattazione numeri

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/speed/SpeedSampler.kt`

Aggiungi l'import in cima al file (dopo gli import esistenti):

```kotlin
import java.util.Locale
```

Poi sostituisci ogni `String.format(` aggiungendo `Locale.US` come primo
argomento. Ci sono 5 occorrenze in questo file:

PRIMA:
```kotlin
                bytesPerSec < 1024 -> {
                    val num = String.format("%.1f", bytesPerSec / 1024.0)
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$unit$suffix"
                }
                bytesPerSec < 1024 * 1024 -> {
                    val num = String.format("%.0f", bytesPerSec / 1024.0)
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$unit$suffix"
                }
                else -> {
                    val num = String.format("%.1f", bytesPerSec / (1024.0 * 1024.0))
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$mbUnit$suffix"
                }
```

DOPO:
```kotlin
                bytesPerSec < 1024 -> {
                    val num = String.format(Locale.US, "%.1f", bytesPerSec / 1024.0)
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$unit$suffix"
                }
                bytesPerSec < 1024 * 1024 -> {
                    val num = String.format(Locale.US, "%.0f", bytesPerSec / 1024.0)
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$unit$suffix"
                }
                else -> {
                    val num = String.format(Locale.US, "%.1f", bytesPerSec / (1024.0 * 1024.0))
                    val sep = if (compactUnit && num.length >= 3) "" else " "
                    "$num$sep$mbUnit$suffix"
                }
```

PRIMA:
```kotlin
        fun formatCompact(bytesPerSec: Long): String = when {
            bytesPerSec == 0L -> "0K"
            bytesPerSec < 1024 -> String.format("%.1fK", bytesPerSec / 1024.0)
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024}K"
            else -> String.format("%.1fM", bytesPerSec / (1024.0 * 1024.0))
        }
```

DOPO:
```kotlin
        fun formatCompact(bytesPerSec: Long): String = when {
            bytesPerSec == 0L -> "0K"
            bytesPerSec < 1024 -> String.format(Locale.US, "%.1fK", bytesPerSec / 1024.0)
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024}K"
            else -> String.format(Locale.US, "%.1fM", bytesPerSec / (1024.0 * 1024.0))
        }
```

**Verifica**: `./gradlew :app:compileDebugKotlin --offline`.

---

## Task 2 — `exported=false` su `NetSpeedOverlayService`

**Perché**: nessun'altra app deve poter chiamare `startService`/`stopService`/
`bindService` su questo componente — solo `MainActivity`/`SettingsScreen` lo
avviano, con un intent esplicito dallo stesso processo. Lasciarlo
`exported="true"` è superficie d'attacco gratuita (un'altra app potrebbe
fermarlo o avviarlo senza permesso).

**File**: `app/src/main/AndroidManifest.xml`

PRIMA:
```xml
        <service
            android:name=".overlay.NetSpeedOverlayService"
            android:exported="true"
            android:foregroundServiceType="specialUse">
```

DOPO:
```xml
        <service
            android:name=".overlay.NetSpeedOverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
```

**Verifica**: `./gradlew :app:compileDebugKotlin --offline` (un errore nel
manifest non emerge qui: rimanda anche `./gradlew :app:assembleDebug --offline`
per sicurezza, o rimanda la verifica manifest al Task 8).

---

## Task 3 — Setup JUnit + primi unit test per `SpeedSampler`

Il progetto oggi non ha alcun test (`src/test` non esiste). `SpeedSampler.format`/
`formatCompact` sono funzioni pure (nessuna dipendenza da Android framework),
il candidato ideale per i primi test.

### 3.1 — Dipendenza JUnit4 + sourceSets

**File**: `app/build.gradle.kts`

PRIMA:
```kotlin
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
    }
```

DOPO:
```kotlin
    sourceSets {
        getByName("main") {
            kotlin.srcDirs("src/main/kotlin")
        }
        getByName("test") {
            kotlin.srcDirs("src/test/kotlin")
        }
    }
```

PRIMA:
```kotlin
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")
}
```

DOPO:
```kotlin
    implementation("androidx.datastore:datastore-preferences:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    debugImplementation("androidx.compose.ui:ui-tooling")

    testImplementation("junit:junit:4.13.2")
}
```

### 3.2 — File di test

**Nuovo file**: `app/src/test/kotlin/com/example/netspeedoverlay/speed/SpeedSamplerTest.kt`

```kotlin
package com.example.netspeedoverlay.speed

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import java.util.Locale

class SpeedSamplerTest {

    private lateinit var originalLocale: Locale

    @Before
    fun setUp() {
        originalLocale = Locale.getDefault()
    }

    @After
    fun tearDown() {
        Locale.setDefault(originalLocale)
    }

    @Test
    fun `format zero bytes`() {
        assertEquals("0 KB/s", SpeedSampler.format(0L, showPerSecondSuffix = true))
    }

    @Test
    fun `format sub kilobyte value`() {
        assertEquals("0.5 KB/s", SpeedSampler.format(512L, showPerSecondSuffix = true))
    }

    @Test
    fun `format kilobyte value without suffix`() {
        assertEquals("2 KB", SpeedSampler.format(2048L, showPerSecondSuffix = false))
    }

    @Test
    fun `format megabyte value`() {
        assertEquals("1.0 MB/s", SpeedSampler.format(1024L * 1024L, showPerSecondSuffix = true))
    }

    @Test
    fun `format compactUnit removes space for 3+ digit numbers`() {
        val result = SpeedSampler.format(177L * 1024L, showPerSecondSuffix = false, compactUnit = true)
        assertEquals("177K", result)
    }

    @Test
    fun `format is locale independent (decimal point, not comma)`() {
        Locale.setDefault(Locale.ITALY)
        val bytesPerSec = (1.5 * 1024 * 1024).toLong()
        assertEquals("1.5 MB/s", SpeedSampler.format(bytesPerSec, showPerSecondSuffix = true))
    }

    @Test
    fun `formatCompact zero bytes`() {
        assertEquals("0K", SpeedSampler.formatCompact(0L))
    }

    @Test
    fun `formatCompact megabyte value is locale independent`() {
        Locale.setDefault(Locale.ITALY)
        assertEquals("2.0M", SpeedSampler.formatCompact(2L * 1024L * 1024L))
    }
}
```

Le ultime due asserzioni (`Locale.ITALY`) sono la "prova" che il Task 1 ha
davvero risolto il bug: se eseguite PRIMA del fix del Task 1, questi due
test falliscono (producono `1,5 MB/s` con la virgola).

**Verifica**:
```bash
./gradlew :app:testDebugUnitTest --offline
```
Se fallisce per mancata risoluzione di `junit:junit:4.13.2` in modalità
offline (dipendenza non ancora in cache Gradle locale), riprova senza
`--offline`:
```bash
./gradlew :app:testDebugUnitTest
```
Se anche questo fallisce per mancanza di rete in questo ambiente, non
bloccarti: verifica almeno che compili con
`./gradlew :app:compileDebugKotlin --offline` e
`./gradlew :app:compileDebugUnitTestKotlin --offline`, poi segnala nel
riepilogo finale che l'esecuzione dei test andrà verificata su una macchina
con accesso di rete a Maven Central.

---

## Task 4 — Totale dati di oggi (download/upload accumulati)

**Obiettivo**: oltre alla velocità istantanea, tracciare quanti byte totali
sono stati scaricati/caricati "oggi" (giorno di calendario **locale**,
reset alla mezzanotte del fuso orario del device), sopravvivendo a reboot.

### 4.1 — Estendi `SpeedSampler.Sample` con i delta grezzi

Il consumo giornaliero deve accumulare i **byte realmente trasferiti** in
ogni intervallo di campionamento, non la velocità istantanea (byte/sec):
altrimenti moltiplicare `bytesPerSec * intervallo` sarebbe un'approssimazione
imprecisa. `sample()` calcola già `rxDelta`/`txDelta` internamente: basta
esporli.

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/speed/SpeedSampler.kt`

PRIMA:
```kotlin
    data class Sample(val rxBytesPerSec: Long, val txBytesPerSec: Long)
```

DOPO:
```kotlin
    data class Sample(
        val rxBytesPerSec: Long,
        val txBytesPerSec: Long,
        val rxBytesDelta: Long,
        val txBytesDelta: Long
    )
```

PRIMA:
```kotlin
            val elapsedSec = max(0.001, (now - lastTimestampMs) / 1000.0)
            val rxDelta = (rx - lastRxBytes).coerceAtLeast(0)
            val txDelta = (tx - lastTxBytes).coerceAtLeast(0)
            Sample(
                rxBytesPerSec = (rxDelta / elapsedSec).toLong(),
                txBytesPerSec = (txDelta / elapsedSec).toLong()
            )
```

DOPO:
```kotlin
            val elapsedSec = max(0.001, (now - lastTimestampMs) / 1000.0)
            val rxDelta = (rx - lastRxBytes).coerceAtLeast(0)
            val txDelta = (tx - lastTxBytes).coerceAtLeast(0)
            Sample(
                rxBytesPerSec = (rxDelta / elapsedSec).toLong(),
                txBytesPerSec = (txDelta / elapsedSec).toLong(),
                rxBytesDelta = rxDelta,
                txBytesDelta = txDelta
            )
```

Prima di procedere, verifica che `Sample(` non venga costruito altrove nel
progetto con argomenti posizionali che romperebbero con i nuovi campi:
```bash
grep -rn "Sample(" app/src/main/kotlin
```
Deve comparire solo il punto appena modificato (i chiamanti usano sempre
`sample.rxBytesPerSec`/`sample.txBytesPerSec` per nome, mai destructuring
posizionale `val (rx, tx) = sample`).

### 4.2 — Aggiungi la funzione di formattazione per un totale (non un rate)

Un totale giornaliero può arrivare nell'ordine dei GB, a differenza della
velocità istantanea (`format`/`formatCompact`, che si fermano a MB perché
una velocità in GB/s non è realistica su un device). Serve quindi una
funzione dedicata.

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/speed/SpeedSampler.kt`,
nel `companion object`, subito dopo la fine di `formatCompact`:

PRIMA:
```kotlin
        fun formatCompact(bytesPerSec: Long): String = when {
            bytesPerSec == 0L -> "0K"
            bytesPerSec < 1024 -> String.format(Locale.US, "%.1fK", bytesPerSec / 1024.0)
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024}K"
            else -> String.format(Locale.US, "%.1fM", bytesPerSec / (1024.0 * 1024.0))
        }
    }
```

DOPO:
```kotlin
        fun formatCompact(bytesPerSec: Long): String = when {
            bytesPerSec == 0L -> "0K"
            bytesPerSec < 1024 -> String.format(Locale.US, "%.1fK", bytesPerSec / 1024.0)
            bytesPerSec < 1024 * 1024 -> "${bytesPerSec / 1024}K"
            else -> String.format(Locale.US, "%.1fM", bytesPerSec / (1024.0 * 1024.0))
        }

        /**
         * Formatta un conteggio di byte assoluto (non un rate/sec) come i
         * totali giornalieri di DailyUsageRepository — scala fino ai GB,
         * a differenza di format()/formatCompact() che si fermano a MB
         * perché una VELOCITÀ in GB/s non è realistica su un device.
         */
        fun formatTotalBytes(bytes: Long): String = when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
            bytes < 1024 * 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
            else -> String.format(Locale.US, "%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0))
        }
    }
```

Aggiungi anche questi test in fondo a
`app/src/test/kotlin/com/example/netspeedoverlay/speed/SpeedSamplerTest.kt`
(dentro la classe, prima dell'ultima `}`) — richiede che il Task 3 sia già
stato completato:

```kotlin
    @Test
    fun `formatTotalBytes sub kilobyte`() {
        assertEquals("500 B", SpeedSampler.formatTotalBytes(500L))
    }

    @Test
    fun `formatTotalBytes kilobyte`() {
        assertEquals("1.5 KB", SpeedSampler.formatTotalBytes(1536L))
    }

    @Test
    fun `formatTotalBytes gigabyte`() {
        val twoGb = 2L * 1024 * 1024 * 1024
        assertEquals("2.00 GB", SpeedSampler.formatTotalBytes(twoGb))
    }
```

### 4.3 — Nuovo repository per il totale giornaliero

**Nuovo file**: `app/src/main/kotlin/com/example/netspeedoverlay/data/DailyUsageRepository.kt`

```kotlin
package com.example.netspeedoverlay.data

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.dailyUsageDataStore by preferencesDataStore(name = "daily_usage")

data class DailyUsage(val rxBytes: Long, val txBytes: Long)

/**
 * Traccia i byte totali scaricati/caricati "oggi" (giorno di calendario
 * LOCALE, reset a mezzanotte nel fuso orario del device — java.time.LocalDate
 * è nativo da API 26, minSdk di questo progetto, nessuna dipendenza in più).
 *
 * Accumula i delta grezzi restituiti da ogni SpeedSampler.sample() (byte
 * trasferiti nell'intervallo appena campionato, non byte/sec): resta quindi
 * corretto anche attraverso un riavvio del device, che azzera i contatori
 * assoluti di TrafficStats — SpeedSampler ritorna semplicemente null al
 * primo sample dopo un riavvio (vedi sample()), quindi non c'è alcun delta
 * spurio da sommare qui.
 *
 * Sia la lettura (dailyUsageFlow) che la scrittura (addSample) controllano
 * autonomamente il cambio di giorno, perché possono correre in parallelo:
 * se la UI legge subito dopo mezzanotte ma nessun sample è ancora stato
 * scritto, deve comunque mostrare 0/0 calcolato al volo, non i valori
 * ancora salvati di ieri (che verranno sovrascritti al prossimo addSample).
 */
class DailyUsageRepository(private val context: Context) {

    private object Keys {
        val DAY_EPOCH = longPreferencesKey("day_epoch")
        val RX_BYTES = longPreferencesKey("rx_bytes")
        val TX_BYTES = longPreferencesKey("tx_bytes")
    }

    val dailyUsageFlow: Flow<DailyUsage> = context.dailyUsageDataStore.data.map { prefs ->
        if (prefs[Keys.DAY_EPOCH] != todayEpochDay()) {
            DailyUsage(0L, 0L)
        } else {
            DailyUsage(prefs[Keys.RX_BYTES] ?: 0L, prefs[Keys.TX_BYTES] ?: 0L)
        }
    }

    /** No-op se il delta è (0,0): evita una scrittura su disco ad ogni tick
     * quando il device è del tutto inattivo (nessun traffico). */
    suspend fun addSample(rxDelta: Long, txDelta: Long) {
        if (rxDelta == 0L && txDelta == 0L) return
        context.dailyUsageDataStore.edit { prefs ->
            val today = todayEpochDay()
            if (prefs[Keys.DAY_EPOCH] != today) {
                prefs[Keys.DAY_EPOCH] = today
                prefs[Keys.RX_BYTES] = rxDelta
                prefs[Keys.TX_BYTES] = txDelta
            } else {
                prefs[Keys.RX_BYTES] = (prefs[Keys.RX_BYTES] ?: 0L) + rxDelta
                prefs[Keys.TX_BYTES] = (prefs[Keys.TX_BYTES] ?: 0L) + txDelta
            }
        }
    }

    private fun todayEpochDay(): Long = LocalDate.now().toEpochDay()
}
```

Non serve (e sarebbe complesso) un unit test JUnit puro per questa classe:
dipende da `android.content.Context` e da DataStore, che richiedono
Robolectric o un test strumentato — fuori scope per questo piano. Verifica
manuale in Task 8/9.

### 4.4 — Collega il repository al service (accumula ad ogni sample)

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/overlay/NetSpeedOverlayService.kt`

Aggiungi l'import:
```kotlin
import com.example.netspeedoverlay.data.DailyUsageRepository
```

PRIMA:
```kotlin
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private val sampler = SpeedSampler()
```

DOPO:
```kotlin
    private lateinit var windowManager: WindowManager
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dailyUsageRepository: DailyUsageRepository
    private val sampler = SpeedSampler()
```

PRIMA:
```kotlin
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository(applicationContext)
        startForegroundWithNotification()
```

DOPO:
```kotlin
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        settingsRepository = SettingsRepository(applicationContext)
        dailyUsageRepository = DailyUsageRepository(applicationContext)
        startForegroundWithNotification()
```

PRIMA:
```kotlin
    private fun startSamplingLoop() {
        samplingJob = lifecycleScope.launch {
            while (true) {
                val settings = currentSettings
                sampler.sample()?.let { sample ->
                    when (settings.indicatorMode) {
                        IndicatorMode.OVERLAY -> {
                            updateTexts(sample, settings)
                            updateNotificationText(sample, settings)
                        }
                        IndicatorMode.NOTIFICATION_ICON -> updateNotificationIcon(sample, settings)
                        IndicatorMode.NOTIFICATION_TEXT -> updateNotificationText(sample, settings)
                    }
                }
                delay(settings.updateIntervalMs)
            }
        }
    }
```

DOPO:
```kotlin
    private fun startSamplingLoop() {
        samplingJob = lifecycleScope.launch {
            while (true) {
                val settings = currentSettings
                sampler.sample()?.let { sample ->
                    // Accumula sempre, indipendentemente da indicatorMode:
                    // il consumo di oggi riguarda il traffico reale del
                    // device, non come/se viene mostrato in questo momento.
                    dailyUsageRepository.addSample(sample.rxBytesDelta, sample.txBytesDelta)
                    when (settings.indicatorMode) {
                        IndicatorMode.OVERLAY -> {
                            updateTexts(sample, settings)
                            updateNotificationText(sample, settings)
                        }
                        IndicatorMode.NOTIFICATION_ICON -> updateNotificationIcon(sample, settings)
                        IndicatorMode.NOTIFICATION_TEXT -> updateNotificationText(sample, settings)
                    }
                }
                delay(settings.updateIntervalMs)
            }
        }
    }
```

### 4.5 — Esponi il repository nella UI

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/MainActivity.kt`

Aggiungi l'import:
```kotlin
import com.example.netspeedoverlay.data.DailyUsageRepository
```

PRIMA:
```kotlin
    private lateinit var settingsRepository: SettingsRepository
    private val hasOverlayPermission = mutableStateOf(false)
```

DOPO:
```kotlin
    private lateinit var settingsRepository: SettingsRepository
    private lateinit var dailyUsageRepository: DailyUsageRepository
    private val hasOverlayPermission = mutableStateOf(false)
```

PRIMA:
```kotlin
        settingsRepository = SettingsRepository(applicationContext)
        requestNotificationPermissionIfNeeded()
```

DOPO:
```kotlin
        settingsRepository = SettingsRepository(applicationContext)
        dailyUsageRepository = DailyUsageRepository(applicationContext)
        requestNotificationPermissionIfNeeded()
```

PRIMA:
```kotlin
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        hasOverlayPermission = hasOverlayPermission.value,
```

DOPO:
```kotlin
                    SettingsScreen(
                        settingsRepository = settingsRepository,
                        dailyUsageRepository = dailyUsageRepository,
                        hasOverlayPermission = hasOverlayPermission.value,
```

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/ui/SettingsScreen.kt`

Aggiungi import:
```kotlin
import com.example.netspeedoverlay.data.DailyUsageRepository
import com.example.netspeedoverlay.data.DailyUsage
import com.example.netspeedoverlay.speed.SpeedSampler
```

PRIMA:
```kotlin
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
```

DOPO:
```kotlin
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
```

PRIMA:
```kotlin
        Text("Indicatore velocità di rete", style = MaterialTheme.typography.headlineSmall)

        SectionLabel("Modalità")
```

DOPO:
```kotlin
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
```

**Verifica**: `./gradlew :app:compileDebugKotlin --offline` e, se possibile,
`./gradlew :app:testDebugUnitTest --offline`.

---

## Task 5 — Riavvio automatico dopo il boot del device

**Obiettivo**: oggi, dopo un riavvio del telefono, l'overlay resta spento
finché l'utente non riapre l'app e preme "Avvia indicatore". Aggiungi un
toggle opt-in ("Avvia automaticamente all'accensione", default OFF) che,
se attivo, riavvia il servizio da solo dopo il boot.

Nota tecnica importante: `BOOT_COMPLETED` è uno dei pochi broadcast impliciti
di sistema per cui Android permette ancora un `BroadcastReceiver` dichiarato
nel manifest anche per app che non girano in background (eccezione
esplicita alle restrizioni sui broadcast impliciti introdotte da Android 8+).
Le app che rispondono a `BOOT_COMPLETED` sono anche esplicitamente autorizzate
a chiamare `startForegroundService()` da background, a patto di chiamare
`startForeground()` entro pochi secondi — cosa che
`NetSpeedOverlayService.onCreate()` fa già immediatamente. Per questo questo
task funziona senza bisogno di permessi speciali oltre a quello, "normale"
(nessun prompt), dichiarato sotto.

### 5.1 — Nuovo campo in `OverlaySettings`

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/data/OverlaySettings.kt`

PRIMA:
```kotlin
    // Solo per NOTIFICATION_ICON: se true, ridimensiona automaticamente il testo
    // se supera lo spazio orizzontale dell'icona.
    val notificationAutoFit: Boolean = true
)
```

DOPO:
```kotlin
    // Solo per NOTIFICATION_ICON: se true, ridimensiona automaticamente il testo
    // se supera lo spazio orizzontale dell'icona.
    val notificationAutoFit: Boolean = true,

    // Se true, il servizio si riavvia da solo dopo il boot del device
    // (solo se il permesso overlay è già stato concesso in precedenza).
    // Vedi boot/BootReceiver.kt.
    val autoStartOnBoot: Boolean = false
)
```

### 5.2 — Chiave DataStore + getter/setter in `SettingsRepository`

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/data/SettingsRepository.kt`

PRIMA:
```kotlin
        val NOTIFICATION_AUTO_FIT = booleanPreferencesKey("notification_auto_fit")
    }
```

DOPO:
```kotlin
        val NOTIFICATION_AUTO_FIT = booleanPreferencesKey("notification_auto_fit")
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    }
```

PRIMA:
```kotlin
            notificationAutoFit = prefs[Keys.NOTIFICATION_AUTO_FIT]
                ?: defaults.notificationAutoFit
        )
    }
```

DOPO:
```kotlin
            notificationAutoFit = prefs[Keys.NOTIFICATION_AUTO_FIT]
                ?: defaults.notificationAutoFit,
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT]
                ?: defaults.autoStartOnBoot
        )
    }
```

PRIMA:
```kotlin
    suspend fun setNotificationAutoFit(value: Boolean) = edit { it[Keys.NOTIFICATION_AUTO_FIT] = value }

    suspend fun resetSettings() = edit { it.clear() }
```

DOPO:
```kotlin
    suspend fun setNotificationAutoFit(value: Boolean) = edit { it[Keys.NOTIFICATION_AUTO_FIT] = value }
    suspend fun setAutoStartOnBoot(value: Boolean) = edit { it[Keys.AUTO_START_ON_BOOT] = value }

    suspend fun resetSettings() = edit { it.clear() }
```

### 5.3 — Toggle nella schermata impostazioni

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/ui/SettingsScreen.kt`

PRIMA:
```kotlin
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

        HorizontalDivider()
```

DOPO:
```kotlin
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
```

### 5.4 — `BootReceiver`

**Nuovo file**: `app/src/main/kotlin/com/example/netspeedoverlay/boot/BootReceiver.kt`

```kotlin
package com.example.netspeedoverlay.boot

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.netspeedoverlay.data.SettingsRepository
import com.example.netspeedoverlay.overlay.NetSpeedOverlayService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Riavvia il servizio dopo il boot del device, solo se l'utente ha attivato
 * "Avvia automaticamente all'accensione" nelle impostazioni. Senza questo
 * receiver l'overlay resta spento dopo ogni riavvio finché l'utente non
 * riapre manualmente l'app — per un'app il cui scopo è restare sempre
 * visibile, un buco piuttosto vistoso.
 *
 * BOOT_COMPLETED è tra i pochi broadcast impliciti per cui Android permette
 * ancora un receiver dichiarato nel manifest (eccezione esplicita alle
 * restrizioni sui broadcast impliciti di Android 8+), e le app che vi
 * rispondono sono autorizzate a chiamare startForegroundService() da
 * background — NetSpeedOverlayService chiama già startForeground() entro
 * il limite richiesto in onCreate().
 */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val pendingResult = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val settings = SettingsRepository(appContext).settingsFlow.first()
                if (settings.autoStartOnBoot && NetSpeedOverlayService.canDrawOverlays(appContext)) {
                    NetSpeedOverlayService.start(appContext)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
```

### 5.5 — Manifest: permesso + receiver

**File**: `app/src/main/AndroidManifest.xml`

PRIMA:
```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
```

DOPO:
```xml
    <uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_SPECIAL_USE" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.RECEIVE_BOOT_COMPLETED" />
```

PRIMA:
```xml
        <service
            android:name=".overlay.NetSpeedOverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Mostra un indicatore permanente della velocita di rete corrente, avviato esplicitamente dall'utente dalla schermata dell'app." />
        </service>

    </application>
```

DOPO:
```xml
        <service
            android:name=".overlay.NetSpeedOverlayService"
            android:exported="false"
            android:foregroundServiceType="specialUse">
            <property
                android:name="android.app.PROPERTY_SPECIAL_USE_FGS_SUBTYPE"
                android:value="Mostra un indicatore permanente della velocita di rete corrente, avviato esplicitamente dall'utente dalla schermata dell'app." />
        </service>

        <receiver
            android:name=".boot.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
```

Questo edit presume che il Task 2 sia già stato applicato (`exported="false"`
sul primo service). Se stai eseguendo i task fuori ordine, adatta il blocco
"PRIMA" di conseguenza.

**Verifica**: `./gradlew :app:compileDebugKotlin --offline`. Per testare
davvero il riavvio al boot serve un device/emulatore reale (riavvio
completo) — non è verificabile solo con la build, annotalo come verifica
manuale da fare a parte.

---

## Task 6 — Quick Settings Tile (accendi/spegni dalla tendina)

**Obiettivo**: un tile nelle Impostazioni rapide per accendere/spegnere il
servizio senza aprire l'app. **Non mostra la velocità live** (le tile QS
di sistema non sono pensate per aggiornarsi ogni secondo): è solo un
interruttore on/off con lo stato riflesso da `Tile.STATE_ACTIVE`/`INACTIVE`.
Nessun permesso a prompt: il binding `BIND_QUICK_SETTINGS_TILE` è gestito
dal sistema; l'utente deve solo aggiungere manualmente la tile dal pannello
di modifica delle Impostazioni rapide (comportamento standard per qualunque
tile QS di qualunque app).

### 6.1 — `NetSpeedTileService`

**Nuovo file**: `app/src/main/kotlin/com/example/netspeedoverlay/tile/NetSpeedTileService.kt`

```kotlin
package com.example.netspeedoverlay.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.example.netspeedoverlay.MainActivity
import com.example.netspeedoverlay.R
import com.example.netspeedoverlay.overlay.NetSpeedOverlayService

/**
 * Tile nelle Impostazioni rapide per accendere/spegnere il servizio senza
 * aprire l'app. Se manca il permesso overlay, apre l'app invece di provare
 * ad avviare comunque il servizio (fallirebbe silenziosamente).
 */
class NetSpeedTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        updateTileState()
    }

    override fun onClick() {
        super.onClick()
        if (!NetSpeedOverlayService.canDrawOverlays(this)) {
            openApp()
            return
        }
        if (NetSpeedOverlayService.isRunning.value) {
            NetSpeedOverlayService.stop(this)
        } else {
            NetSpeedOverlayService.start(this)
        }
        updateTileState()
    }

    private fun openApp() {
        val intent = Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pendingIntent = PendingIntent.getActivity(
                this, 0, intent, PendingIntent.FLAG_IMMUTABLE
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTileState() {
        val running = NetSpeedOverlayService.isRunning.value
        qsTile?.apply {
            state = if (running) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = getString(R.string.app_name)
            updateTile()
        }
    }
}
```

Nota sulla doppia implementazione di `openApp()`: `startActivityAndCollapse(Intent)`
è deprecato da API 34 (`UPSIDE_DOWN_CAKE`) a favore della overload con
`PendingIntent`. Questo progetto ha `compileSdk`/`targetSdk = 36`, quindi
serve gestire entrambe le versioni con lo stesso pattern di SDK-gating già
usato altrove nel progetto (es. `ServiceCompat.startForeground` in
`NetSpeedOverlayService`).

### 6.2 — Manifest

**File**: `app/src/main/AndroidManifest.xml`

PRIMA:
```xml
        <receiver
            android:name=".boot.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

    </application>
```

DOPO:
```xml
        <receiver
            android:name=".boot.BootReceiver"
            android:exported="true">
            <intent-filter>
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>

        <service
            android:name=".tile.NetSpeedTileService"
            android:exported="true"
            android:icon="@drawable/ic_speed_notification"
            android:label="@string/app_name"
            android:permission="android.permission.BIND_QUICK_SETTINGS_TILE">
            <intent-filter>
                <action android:name="android.service.quicksettings.action.QS_TILE" />
            </intent-filter>
        </service>

    </application>
```

Se il Task 5 non è ancora stato eseguito, usa come "PRIMA" il blocco
`</service>\n\n    </application>` del `NetSpeedOverlayService` (vedi Task 2)
e inserisci questo nuovo `<service>` prima di `</application>` allo stesso
modo.

`android:exported="true"` qui è corretto e necessario (il sistema deve
potersi legare alla tile): la protezione reale è
`android:permission="android.permission.BIND_QUICK_SETTINGS_TILE"`, un
permesso di sistema che nessuna app terza possiede.

**Verifica**: `./gradlew :app:compileDebugKotlin --offline`.

---

## Task 7 — Mini-grafico (sparkline) opzionale nell'overlay

**Obiettivo**: un piccolo grafico della cronologia recente (download+upload
combinati) disegnato accanto al testo dell'overlay, **disattivabile**
(default OFF) da un nuovo switch nelle impostazioni "Aspetto". Versione
volutamente semplice: una sola linea, buffer fisso di 30 campioni, nessuna
etichetta/asse — non aggiungere altre opzioni (dimensione storico, colori
separati, ecc.) a meno che non venga chiesto esplicitamente.

### 7.1 — `SparklineView`

**Nuovo file**: `app/src/main/kotlin/com/example/netspeedoverlay/overlay/SparklineView.kt`

```kotlin
package com.example.netspeedoverlay.overlay

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.View

/**
 * Mini-grafico opzionale della cronologia recente di download+upload
 * combinati (una sola linea), disegnato come view figlia dentro l'overlay.
 * Buffer circolare a dimensione fissa: nessuna allocazione per-frame oltre
 * al Path (ricostruito ad ogni sample, frequenza già bassa — updateIntervalMs
 * minimo 500ms).
 *
 * A differenza di overlayRoot (che usa INVISIBLE per continuare a ricevere
 * WindowInsets, vedi NetSpeedOverlayService), questa view figlia NON ha
 * alcun listener di insets: usare View.GONE per nasconderla è corretto e
 * voluto, perché libera lo spazio occupato nel layout quando disattivata.
 */
class SparklineView(context: Context) : View(context) {

    private val history = ArrayDeque<Long>()
    private val maxSamples = 30

    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f
    }
    private val path = Path()

    fun pushSample(combinedBytesPerSec: Long) {
        history.addLast(combinedBytesPerSec)
        while (history.size > maxSamples) history.removeFirst()
        invalidate()
    }

    fun setLineColor(argb: Int) {
        linePaint.color = argb
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (history.size < 2) return
        val maxValue = (history.maxOrNull() ?: 0L).coerceAtLeast(1L)
        val stepX = width.toFloat() / (maxSamples - 1)
        path.reset()
        history.forEachIndexed { index, value ->
            val x = index * stepX
            val y = height - (value.toFloat() / maxValue) * height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        canvas.drawPath(path, linePaint)
    }
}
```

### 7.2 — Nuovo campo `showSparkline` in `OverlaySettings`

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/data/OverlaySettings.kt`

Se il Task 5 è già stato applicato, il campo `autoStartOnBoot` è già
l'ultimo prima della `)`: usa questo come "PRIMA".

PRIMA:
```kotlin
    // Se true, il servizio si riavvia da solo dopo il boot del device
    // (solo se il permesso overlay è già stato concesso in precedenza).
    // Vedi boot/BootReceiver.kt.
    val autoStartOnBoot: Boolean = false
)
```

DOPO:
```kotlin
    // Se true, il servizio si riavvia da solo dopo il boot del device
    // (solo se il permesso overlay è già stato concesso in precedenza).
    // Vedi boot/BootReceiver.kt.
    val autoStartOnBoot: Boolean = false,

    // Solo per indicatorMode = OVERLAY: mostra un mini-grafico opzionale
    // della cronologia recente (download+upload combinati) accanto al
    // testo. Default OFF. Vedi overlay/SparklineView.kt.
    val showSparkline: Boolean = false
)
```

(Se il Task 5 NON è stato applicato, usa invece come "PRIMA" il blocco
originale che termina con `val notificationAutoFit: Boolean = true\n)` e
aggiungi `showSparkline` allo stesso modo.)

### 7.3 — Chiave DataStore + getter/setter

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/data/SettingsRepository.kt`

PRIMA (assumendo il Task 5 già applicato):
```kotlin
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
    }
```

DOPO:
```kotlin
        val AUTO_START_ON_BOOT = booleanPreferencesKey("auto_start_on_boot")
        val SHOW_SPARKLINE = booleanPreferencesKey("show_sparkline")
    }
```

PRIMA:
```kotlin
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT]
                ?: defaults.autoStartOnBoot
        )
    }
```

DOPO:
```kotlin
            autoStartOnBoot = prefs[Keys.AUTO_START_ON_BOOT]
                ?: defaults.autoStartOnBoot,
            showSparkline = prefs[Keys.SHOW_SPARKLINE]
                ?: defaults.showSparkline
        )
    }
```

PRIMA:
```kotlin
    suspend fun setAutoStartOnBoot(value: Boolean) = edit { it[Keys.AUTO_START_ON_BOOT] = value }

    suspend fun resetSettings() = edit { it.clear() }
```

DOPO:
```kotlin
    suspend fun setAutoStartOnBoot(value: Boolean) = edit { it[Keys.AUTO_START_ON_BOOT] = value }
    suspend fun setShowSparkline(value: Boolean) = edit { it[Keys.SHOW_SPARKLINE] = value }

    suspend fun resetSettings() = edit { it.clear() }
```

Se il Task 5 non è stato eseguito, adatta i blocchi "PRIMA" usando
`NOTIFICATION_AUTO_FIT`/`notificationAutoFit`/`setNotificationAutoFit` come
ancora, seguendo lo stesso schema.

### 7.4 — Switch nelle impostazioni "Aspetto"

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/ui/SettingsScreen.kt`

PRIMA:
```kotlin
        SwitchSetting("Grassetto", settings.bold) {
            scope.launch { settingsRepository.setBold(it) }
        }

        } // fine sezione "Aspetto", esclusiva di IndicatorMode.OVERLAY
```

DOPO:
```kotlin
        SwitchSetting("Grassetto", settings.bold) {
            scope.launch { settingsRepository.setBold(it) }
        }
        SwitchSetting("Mostra mini-grafico", settings.showSparkline) {
            scope.launch { settingsRepository.setShowSparkline(it) }
        }

        } // fine sezione "Aspetto", esclusiva di IndicatorMode.OVERLAY
```

### 7.5 — Collega la view al service

**File**: `app/src/main/kotlin/com/example/netspeedoverlay/overlay/NetSpeedOverlayService.kt`

Proprietà — PRIMA:
```kotlin
    private var overlayRoot: LinearLayout? = null
    private var downloadText: TextView? = null
    private var uploadText: TextView? = null
    private var overlayBackground: GradientDrawable? = null
    private var layoutParams: WindowManager.LayoutParams? = null
```

DOPO:
```kotlin
    private var overlayRoot: LinearLayout? = null
    private var downloadText: TextView? = null
    private var uploadText: TextView? = null
    private var overlayBackground: GradientDrawable? = null
    private var sparklineView: SparklineView? = null
    private var layoutParams: WindowManager.LayoutParams? = null
```

Creazione della view in `addOverlayView()` — PRIMA:
```kotlin
        root.addView(upload)
        root.addView(download)

        val params = WindowManager.LayoutParams(
```

DOPO:
```kotlin
        root.addView(upload)
        root.addView(download)

        val sparkline = SparklineView(this).apply {
            layoutParams = LinearLayout.LayoutParams(dp(40), dp(16)).apply {
                topMargin = dp(2)
            }
        }
        root.addView(sparkline)
        sparklineView = sparkline

        val params = WindowManager.LayoutParams(
```

Pulizia in `removeOverlayView()` — PRIMA:
```kotlin
    private fun removeOverlayView() {
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        downloadText = null
        uploadText = null
        overlayBackground = null
        layoutParams = null
    }
```

DOPO:
```kotlin
    private fun removeOverlayView() {
        overlayRoot?.let { runCatching { windowManager.removeView(it) } }
        overlayRoot = null
        downloadText = null
        uploadText = null
        overlayBackground = null
        sparklineView = null
        layoutParams = null
    }
```

Applicazione impostazioni in `applySettingsToView()` — PRIMA:
```kotlin
        // Colore testo/sfondo: se "Abbina barra di sistema" è attivo, segui
        // l'aspetto live della navigation bar (chiaro/scuro), altrimenti i
        // valori di default (testo chiaro su sfondo scuro).
        applyOverlayColors(settings)
```

DOPO:
```kotlin
        // Colore testo/sfondo: se "Abbina barra di sistema" è attivo, segui
        // l'aspetto live della navigation bar (chiaro/scuro), altrimenti i
        // valori di default (testo chiaro su sfondo scuro).
        applyOverlayColors(settings)

        // GONE (non INVISIBLE): questa è una view figlia senza listener di
        // WindowInsets, a differenza di overlayRoot — nasconderla con GONE
        // libera correttamente lo spazio nel layout.
        sparklineView?.visibility = if (settings.showSparkline) View.VISIBLE else View.GONE
        sparklineView?.setLineColor(settings.textColorArgb)
```

Aggiornamento dati in `updateTexts()` — PRIMA:
```kotlin
        val idle = sample.rxBytesPerSec < settings.idleThresholdBytesPerSec &&
            sample.txBytesPerSec < settings.idleThresholdBytesPerSec
        overlayRoot?.alpha = if (settings.dimWhenIdle && idle) settings.idleAlpha else 1f
    }
```

DOPO:
```kotlin
        val idle = sample.rxBytesPerSec < settings.idleThresholdBytesPerSec &&
            sample.txBytesPerSec < settings.idleThresholdBytesPerSec
        overlayRoot?.alpha = if (settings.dimWhenIdle && idle) settings.idleAlpha else 1f

        if (settings.showSparkline) {
            sparklineView?.pushSample(sample.rxBytesPerSec + sample.txBytesPerSec)
        }
    }
```

**Verifica**: `./gradlew :app:compileDebugKotlin --offline`.

---

## Task 8 — Build e verifica finale

Dalla root del repo:

```bash
./gradlew :app:compileDebugKotlin --offline
./gradlew :app:testDebugUnitTest --offline   # o senza --offline se junit non è in cache
./gradlew :app:assembleDebug --offline
```

`assembleDebug` è importante perché è l'unico dei tre comandi che valida
anche `AndroidManifest.xml` (merge manifest, risorse dichiarate come
`@drawable/ic_speed_notification` per la tile, ecc.) — gli altri due
verificano solo il codice Kotlin.

Poi, su tutti i file toccati:
```bash
git status
git diff --stat
```
Controlla che l'elenco dei file modificati/creati corrisponda esattamente
alla tabella a inizio piano (nessun file toccato per errore, nessun file
mancante).

Se qualcosa fallisce, individua il task responsabile dalla tabella e
rileggi per intero il file coinvolto prima di correggere — non applicare
patch alla cieca sopra un file che potresti aver già alterato in modo
inatteso.

---

## Task 9 — Aggiornare il README

Il README di questo progetto documenta sistematicamente ogni feature e
ogni scelta tecnica (vedi le sezioni esistenti "Tre modalità", "Rilevamento
delle barre di sistema tramite WindowInsets", "Limitazioni note"). Dopo aver
completato e verificato i Task 1-8, aggiungi coerenza:

1. Aggiorna l'albero in "## Struttura" aggiungendo:
   - `data/DailyUsageRepository.kt`
   - `boot/BootReceiver.kt`
   - `tile/NetSpeedTileService.kt`
   - `overlay/SparklineView.kt`
2. Aggiungi una sezione breve (stile coerente con quelle esistenti, non più
   di 1-2 paragrafi ciascuna) per: totale dati di oggi (reset a mezzanotte
   locale via `LocalDate`, robusto al reboot), riavvio automatico al boot
   (eccezione `BOOT_COMPLETED`, opt-in di default OFF), Quick Settings Tile
   (solo on/off, non mostra numeri live), sparkline (opzionale, default OFF,
   v1 volutamente semplice: una linea, 30 campioni, nessun asse).
3. Non toccare le sezioni esistenti (in particolare non modificare la
   sezione "Colori della navigation bar" già presente).

Non serve un comando di verifica per questo task (è documentazione), ma
rileggi il file dopo l'edit per assicurarti che il Markdown risultante sia
ben formato (heading coerenti, code fence chiusi).
