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

## Due modalità

Un vero hook sulla status bar reale (come fa CustoMIUIzer) richiede root:
è un confine di sicurezza del sistema operativo, non un permesso aggirabile
(vedi commit history / discussione per i dettagli). Ci sono però due modi
non-root per mostrare il valore, entrambi selezionabili da "Modalità" nelle
impostazioni:

- **Overlay** — la finestra flottante descritta sopra: aspetto e
  impostazioni completi (posizione libera o fissa, colori, formato, ecc.),
  ma è tecnicamente sopra la status bar, non dentro.
- **Icona notifica** — ridisegna l'icona della notifica persistente del
  foreground service a ogni campionamento. Questa vive per davvero nella
  status bar reale (non è un overlay): va nel vassoio icone di sistema,
  il sistema la forza monocromatica quindi zero problemi di contrasto.
  Il costo: ~3-4 caratteri al massimo (`SpeedSampler.formatCompact`), un
  solo valore alla volta (download, upload o combinato — non entrambi come
   nell'overlay) e la posizione nel vassoio la decide il sistema, non l'app.

### Icona notifica a due righe

Opzione della modalità "Icona notifica" che disegna **due righe** dentro
all'icona: **upload in alto, download in basso** (l'ordine è invertito
rispetto all'overlay, dove il download sta sopra). Mostra entrambi i valori
contemporaneamente, a costo di caratteri più piccoli: il sistema ridimensiona
l'icona a pochi dp nella status bar, quindi lo spazio è comunque ridotto e la
leggibilità resta limitata (utile soprattutto per un controllo rapido, non per
leggere cifre precise).

Opzioni esposte quando la modalità a due righe è attiva:

- **Stile icona** — nessuna / frecce (↑↓) / lettere (U/D), lo stesso set
  condiviso con l'overlay.
- **Distanza tra le righe** — spaziatura verticale tra le due righe
  (in px sulla bitmap 96×96 dell'icona). Allarga/stringe solo verticalmente,
  non riduce la dimensione dei caratteri.

In questa modalità la scelta "Cosa mostrare" (singolo valore) viene ignorata,
perché vengono disegnati sia download che upload.

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
│   └── NetSpeedOverlayService.kt  foreground service: overlay WindowManager
│                                   o icona notifica, a seconda di indicatorMode
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
  (`indicatorRunning`), non interroga il servizio reale — se il servizio viene
  ucciso dal sistema o l'app viene riaperta dopo un riavvio, il pulsante può
  disallinearsi. Soluzione naturale: esporre uno `StateFlow` dal service
  (es. tramite un `LocalBroadcastManager`-free binder, o più semplice, un
  singleton/`object` con `MutableStateFlow` osservato sia dal service che
  dalla UI).
- Nessuna gestione di notch/cutout dello schermo tramite `WindowInsets`
  reali: `verticalOffsetDp` in modalità Overlay è un valore fisso scelto
  dall'utente, non calcolato dall'altezza vera della status bar sul device.
- Modalità "Icona notifica": aggiorna la notifica ad ogni campionamento
  (default 1.5s). Nessun limite hard noto lato OS, ma è comunque un
  aggiornamento più frequente del solito per una notifica — se noti consumo
  batteria anomalo su un device specifico, alza l'intervallo. Storicamente
  tecniche simili (icone di notifica come indicatore dati) si sono rotte a
  vari giri di vite Android sulle restrizioni in background: se un giorno
  smette di aggiornarsi su una versione futura, è il sospetto numero uno.
- L'icona dell'app è un placeholder generato, non un design definitivo.
