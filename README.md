# Net Speed Overlay

Indicatore di velocità di rete (↓/↑) mostrato come overlay flottante in alto
sullo schermo — nessun root, nessun LSPosed. Ispirato nell'aspetto e nelle
impostazioni al mod "Detailed network speed indicator" di **CustoMIUIzer**
(Mikanoshi, open source su `code.highspec.ru/Mikanoshi/CustoMIUIzer`), ma
tecnicamente è un progetto completamente diverso: CustoMIUIzer modifica via
Xposed una view che MIUI include già nel proprio SystemUI; questa app invece
disegna una propria finestra overlay (`TYPE_APPLICATION_OVERLAY`) sopra le
altre app, quindi funziona su qualunque Android/OEM ma non è "dentro" la
vera status bar. Nessun codice è stato copiato da CustoMIUIzer — solo
l'elenco di funzionalità/impostazioni, riscritto da zero in Kotlin/Compose.

## Struttura

```
app/src/main/kotlin/com/example/netspeedoverlay/
├── MainActivity.kt              host Compose + richiesta permessi overlay/notifiche
├── data/
│   ├── OverlaySettings.kt       modello delle opzioni (enum + data class)
│   └── SettingsRepository.kt    persistenza via DataStore, Flow reattivo
├── speed/
│   └── SpeedSampler.kt          delta TrafficStats -> byte/sec, formattazione
├── overlay/
│   └── NetSpeedOverlayService.kt  foreground service: WindowManager overlay
└── ui/
    ├── SettingsScreen.kt        schermata impostazioni Compose
    └── theme/Theme.kt           tema Material3 minimale
```

`SpeedSampler` legge `TrafficStats.getTotalRxBytes()/getTotalTxBytes()` —
API pubblica Android, stessa fonte dati che usa (indirettamente) anche
CustoMIUIzer — e ne fa il delta a ogni tick per ottenere byte/sec.

L'overlay usa `View` classiche (`LinearLayout` + due `TextView`), non
`ComposeView`: una ComposeView fuori da un'Activity richiede di agganciare
a mano un `LifecycleOwner`/`ViewModelStoreOwner`/`SavedStateRegistryOwner`
per renderizzare, complessità inutile per due righe di testo. Compose resta
invece per `SettingsScreen`, che gira in un'Activity normale.

## Mappatura con le impostazioni di CustoMIUIzer

| CustoMIUIzer (`prefs_system_detailednetspeed.xml`) | Qui | Note |
|---|---|---|
| `netspeedinterval` (1–10s) | Intervallo di aggiornamento | stesso concetto |
| `netspeed_fontsize` | Dimensione testo | stesso concetto |
| `netspeed_bold` | Grassetto | stesso concetto |
| `netspeed_leftmargin`/`rightmargin`/`verticaloffset` | Distanza dal bordo superiore | qui semplificato a un solo offset verticale + scelta lato |
| `detailednetspeed_secunit` | Mostra suffisso "/s" | stesso concetto |
| `detailednetspeed_lowlevel` + `detailednetspeed_low` | Soglia inattività + Attenua quando inattivo | lì nasconde/riduce, qui attenua l'alpha — stesso obiettivo (non distrarre quando il telefono è idle) |
| `detailednetspeed_align` | Posizione (sinistra/centro/destra) | stesso concetto |
| `detailednetspeed_fakedualrow` (dualrow finto) vs riga singola | Formato: due righe / una riga | qui è un solo toggle, non due mutuamente esclusivi |
| `detailednetspeed_icon` (stile icona direzione) | Stile icona (nessuna/frecce/lettere) | valori diversi, stesso ruolo |

Non replicato: `fixedcontent_width` (larghezza fissa per evitare che il
testo "salti" cambiando cifre) — facile da aggiungere se ti dà fastidio,
basta un `Modifier`/`minWidth` sul TextView.

## Setup

1. Apri la cartella in Android Studio (o Antigravity) come progetto Gradle esistente.
2. Sync Gradle — la prima volta scaricherà AGP/Kotlin/Compose, serve una
   connessione che raggiunga `dl.google.com` e Maven Central (non verificato
   in questo sandbox, che ha accesso di rete limitato a pochi registri
   pacchetti e non include il Maven di Google — quindi questo progetto non è
   stato compilato/testato qui, trattalo come una prima bozza curata ma da
   verificare al primo build).
3. Rinomina `applicationId`/`namespace` da `com.example.netspeedoverlay` al
   tuo prima di pubblicare, e sostituisci l'icona placeholder in
   `res/mipmap-xxhdpi/` con una generata da Image Asset Studio.
4. Run su device/emulatore Android 8.0+ (API 26+).

## Limitazioni note / prossimi passi

- Il pulsante Avvia/Ferma in `SettingsScreen` tiene uno stato locale
  (`overlayRunning`), non interroga il servizio reale — se il servizio viene
  ucciso dal sistema o l'app viene riaperta dopo un riavvio, il pulsante può
  disallinearsi. Soluzione naturale: esporre uno `StateFlow` dal service
  (es. tramite un `LocalBroadcastManager`-free binder, o più semplice, un
  singleton/`object` con `MutableStateFlow` osservato sia dal service che
  dalla UI).
- Nessuna gestione di notch/cutout dello schermo: `verticalOffsetDp` è un
  valore fisso, non legge `WindowInsets` per evitare l'area della fotocamera.
- L'icona dell'app è un placeholder generato, non un design definitivo.
