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

## Tre modalità

Un vero hook sulla status bar reale (come fa CustoMIUIzer) richiede root:
è un confine di sicurezza del sistema operativo, non un permesso aggirabile
(vedi commit history / discussione per i dettagli). Ci sono però modi
non-root per mostrare il valore, tutti selezionabili da "Modalità" nelle
impostazioni:

- **Overlay** — la finestra flottante descritta sopra: aspetto e
  impostazioni completi (posizione libera o fissa, colori, formato, ecc.),
  ma è tecnicamente sopra la status bar, non dentro.
- **Icona notifica** — ridisegna l'icona della notifica persistente del
  foreground service a ogni campionamento. Questa vive per davvero nella
  status bar reale (non è un overlay): va nel vassoio icone di sistema,
  il sistema la forza monocromatica quindi zero problemi di contrasto.
  Il costo: ~3-4 caratteri al massimo (valori formattati con
  `SpeedSampler.formatCompact` nel testo della notifica, e
  `SpeedSampler.format(..., compactUnit=true)` nell'icona), un solo valore
  alla volta (download, upload o combinato — non entrambi come nell'overlay)
  e la posizione nel vassoio la decide il sistema, non l'app.
- **Solo notifica** — nessuna finestra flottante e nessuna icona
  ridisegnata: solo il testo della notifica persistente del foreground
  service (stessi valori/formato che la modalità Overlay scrive già nella
  propria notifica obbligatoria, vedi `NetSpeedOverlayService.updateNotificationText`),
  visibile aprendo la tendina. Utile come indicatore meno invadente su
  device dove quella notifica è comunque impossibile da nascondere del
  tutto (es. MIUI/HyperOS): tanto vale renderla utile invece che lasciarla
  con il testo statico "Indicatore attivo".

### Icona notifica a due righe

Opzione della modalità "Icona notifica" che disegna **due righe** dentro
all'icona: **upload in alto, download in basso** (l'ordine è invertito
rispetto all'overlay, dove il download sta sopra). Mostra entrambi i valori
contemporaneamente, a costo di caratteri più piccoli: il sistema ridimensiona
l'icona a pochi dp nella status bar, quindi lo spazio è comunque ridotto e la
leggibilità resta limitata (utile soprattutto per un controllo rapido, non per
leggere cifre precise).

Opzioni esposte nella modalità "Icona notifica":

- **Cosa mostrare** — download, upload o combinato (solo in modalità a una
  riga; in due righe viene ignorato perché vengono mostrati entrambi).
- **Icona a due righe** — attiva la modalità a due righe.
- **Stile icona** — nessuna / frecce (↑↓) / lettere (U/D), lo stesso set
  condiviso con l'overlay.
- **Dimensione caratteri icona** — percentuale di ridimensionamento del font
  rispetto alla dimensione auto-calcolata (default 100%, range 50–150%).
  Quando è attiva la gestione automatica (vedi sotto), questo slider è
  disabilitato.
- **Gestione automatica (auto-fit)** — quando attiva, il font parte da 150%
  e viene automaticamente ridotto se il testo supera la larghezza
  dell'icona (95% della bitmap 96×96) o l'altezza della riga. Inoltre,
  per valori con 1–2 cifre intere (es. `12K`, `0K`) il font viene aumentato
  del 20% per migliorare la leggibilità.
- **Distanza tra le righe** — spaziatura verticale tra le due righe
  (in px sulla bitmap 96×96 dell'icona). Allarga/stringe solo verticalmente,
  non riduce la dimensione dei caratteri.

#### Formattazione valori in modalità "Icona notifica"

- Le unità sono compatte: `K` per kilobyte, `M` per megabyte.
- Lo spazio tra numero e unità è rimosso quando la parte numerica ha 3 o più
  caratteri (es. `12 K` → `12 K`, `173K` → `173K`, `1.2M` → `1.2M`).
- Quando non c'è traffico (`0 B/s`) viene mostrato `0K`.

#### Nota Xiaomi/MIUI (status bar)

Su Xiaomi/Redmi/POCO (MIUI/HyperOS) la status bar **sostituisce l'icona
personalizzata della notifica con l'icona dell'app**: è un comportamento di
sistema, non un bug dell'app, e non è controllabile dal codice (l'impostazione
"Usa le icone app per le notifiche" in *Impostazioni → Notifiche e barra di
stato* è globale, non per-app, e non ha API pubbliche). Su Android stock /
One UI (es. Galaxy) l'icona con i numeri viene invece mostrata normalmente.

Per ovviare: l'app mostra i valori anche come **testo della notifica** (visibile
aprendo la tendina, su qualunque dispositivo) e, sui dispositivi rilevati come
Xiaomi/MIUI, una nota in-app che spiega come attivare i numeri anche in status
bar (disattivando l'opzione di sistema sopra citata).

## Rilevamento delle barre di sistema tramite WindowInsets

`NetSpeedOverlayService` ascolta i `WindowInsets` reali della propria
finestra overlay (`ViewCompat.setOnApplyWindowInsetsListener`, da AndroidX
Core — già una dipendenza esistente, nessuna libreria aggiunta) per sapere
se status bar e nav bar sono *davvero visibili in questo momento* —
`WindowInsetsCompat.isVisible(Type.statusBars()/navigationBars())` — e per
leggere l'altezza vera della nav bar (`getInsets(Type.navigationBars()).bottom`),
al posto di una stima basata sulla risorsa di sistema `navigation_bar_height`.
Non richiede alcun permesso speciale: gli insets vengono dispatchati a
qualsiasi finestra, incluse quelle `TYPE_APPLICATION_OVERLAY`, perché
riflettono lo stato reale e condiviso a livello di schermo delle barre di
sistema (dalla doc ufficiale di `WindowInsetsCompat.isVisible`: *"regardless
of whether it actually overlaps with this window"*). Per questo l'overlay
si nasconde anche quando un'altra app in primo piano attiva la modalità
immersiva.

La dispatch "passiva" di WindowInsets a volte non arriva da sola quando
un'altra app esce dal fullscreen (osservato sul campo, in particolare
tornando alla home con tasto Home invece che Indietro): `NetSpeedOverlayService`
quindi ricontrolla lo stato ogni secondo (`startInsetsPollingLoop`) e, se
l'overlay risulta nascosto, ricrea la finestra da zero — l'unica cosa che si
è dimostrata sempre affidabile per ottenere lo stato corretto delle barre —
invece di limitarsi a un `requestApplyInsets()` che non basta sempre a
sbloccare la situazione.

Limite noto: le barre mostrate *temporaneamente* con lo swipe in modalità
immersiva ("peek"/transient reveal) non fanno riapparire l'overlay.
Confermato sul campo che `WindowInsets.isVisible()` resta `false` per tutta
la durata del peek: le barre transient si sovrappongono ai contenuti senza
generare un nuovo dispatch di insets, quindi non c'è modo pubblico di
rilevarle da una finestra diversa da quella dell'app in primo piano. Prima
c'era un `AccessibilityService` dedicato a questo (rimosso): il suo segnale
("esiste una finestra di tipo TYPE_SYSTEM?") si è rivelato inaffidabile —
in alcuni casi (es. video fullscreen) restituiva risultati opposti a parità
di scenario, probabilmente perché quella categoria include anche elementi
di sistema non legati alle barre. Non valeva il costo (permesso extra,
schermata di sistema poco rassicurante da concedere) per un guadagno così
incerto.

## Colori della navigation bar: perché non c'è un rilevamento automatico

No: non esiste un modo, nemmeno con permessi extra ragionevoli, per leggere
a runtime il colore reale (sfondo e icone dei pulsanti) della navigation bar
dell'app in primo piano, senza root. Non è mai finito nel README fin qui, ma
è stato davvero tentato e poi tolto — cronologia Git, commit `2290ce2` e
rimozione in `4bf8d08`.

Perché manca un'API pubblica per questo:

- `WindowInsets`/`WindowInsetsCompat` (già usati sopra per la visibilità
  delle barre) espongono solo geometria e visibilità, mai colori: servono a
  fare layout, non a "leggere" la UI di un'altra app.
- `Window.setStatusBarColor()`/`setNavigationBarColor()` e i flag di aspetto
  (`WindowInsetsController.setSystemBarsAppearance` con
  `APPEARANCE_LIGHT_STATUS_BARS`/`APPEARANCE_LIGHT_NAVIGATION_BARS`) sono
  **solo setter sulla propria Window**: non esiste un getter equivalente che
  un processo diverso possa interrogare per sapere cosa ha impostato l'app
  in primo piano.
- Da Android 15 (API 35; questo progetto compila con `compileSdk`/`targetSdk
  = 36`) l'edge-to-edge è forzato per le app che puntano a quell'SDK: sempre
  più spesso la barra non ha un colore "di sistema" fisso da leggere, è
  trasparente e mostra i pixel disegnati dall'app sotto — quindi non
  sarebbe comunque una proprietà interrogabile, ma un dettaglio di
  rendering privato di quella finestra.
- L'unico modo per ottenere il colore vero (pixel reali, incluso quello dei
  pulsanti) è una cattura schermo: `MediaProjection`, che richiede consenso
  esplicito dell'utente a ogni sessione (dialog di sistema "quest'app può
  registrare tutto ciò che appare sullo schermo" + indicatore di
  registrazione persistente) — sproporzionato per leggere due colori, e in
  contrasto con l'obiettivo "root-free" del progetto — oppure
  `adb shell screencap`/root, esplicitamente fuori scope (vedi introduzione).

Il tentativo reale fatto in questo repo (commit `2290ce2`, rimosso in
`4bf8d08` insieme a tutto `SystemBarAccessibilityService`) non leggeva
comunque il colore vero: usava un `AccessibilityService` per **indovinare**
se l'app in primo piano fosse chiara o scura, risalendo al tema dichiarato
nel manifest via `PackageManager`/`Resources.Theme` (prima l'attributo
`android.R.attr.isLightTheme`, poi la luminanza di
`android.R.attr.colorBackground`, infine il fallback al tema di sistema) e
scegliendo tra due palette fisse (bianco su nero / nero su bianco), non il
colore effettivo della barra. Il limite era strutturale, non solo di
permesso: quel lookup legge il tema *statico* dichiarato nel manifest, quindi
resta cieco su tutte le app (moltissime, specie ora che l'edge-to-edge è la
norma) che impostano il colore delle barre via codice a runtime. È stato
rimosso insieme al resto dell'`AccessibilityService` perché l'altro segnale
che produceva (rilevamento fullscreen via finestre `TYPE_SYSTEM`) si è
rivelato inaffidabile sul campo — non per un problema specifico di questa
euristica sui colori, ma il costo di un permesso così invasivo non si
giustificava più per tenerla in vita da sola.

Per questo `textColorArgb`/`backgroundColorArgb` in `OverlaySettings.kt`
restano impostazioni manuali: l'utente sceglie i colori per abbinarli alla
propria barra, invece di un'euristica automatica che potrebbe sbagliare in
silenzio.

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

Non replicato:
- `fixedcontent_width` (larghezza fissa per evitare che il testo "salti" cambiando cifre) — facile da aggiungere se ti dà fastidio, basta un `Modifier`/`minWidth` sul TextView.
- `notification_font_size_pct` — percentuale di ridimensionamento del font dell'icona notifica rispetto alla dimensione auto-calcolata.
- `notification_auto_fit` — gestione automatica del font per adattarlo allo spazio disponibile, con boost del 20% per valori a 1-2 cifre senza punto/virgola.

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

- `verticalOffsetDp` in modalità Overlay resta un valore fisso scelto
  dall'utente quando ancorato in alto (`VerticalAnchor.TOP`): non è calcolato
  dall'altezza reale della status bar. L'ancoraggio in basso invece usa già
  l'altezza vera della nav bar via `WindowInsets` (vedi sezione dedicata più
  sopra). Nessuna gestione dedicata di notch/cutout dello schermo.
- L'overlay non riappare durante lo swipe-reveal temporaneo delle barre in
  modalità immersiva (vedi sezione "Rilevamento delle barre di sistema"):
  limite delle API pubbliche di WindowInsets, non risolvibile senza un
  meccanismo aggiuntivo (valutato e scartato l'uso di un AccessibilityService
  dedicato, il cui segnale si è rivelato inaffidabile).
- Modalità "Icona notifica": aggiorna la notifica ad ogni campionamento
  (default 1.0s). Nessun limite hard noto lato OS, ma è comunque un
  aggiornamento più frequente del solito per una notifica — se noti consumo
  batteria anomalo su un device specifico, alza l'intervallo. Storicamente
  tecniche simili (icone di notifica come indicatore dati) si sono rotte a
  vari giri di vite Android sulle restrizioni in background: se un giorno
  smette di aggiornarsi su una versione futura, è il sospetto numero uno.
- L'icona dell'app è un placeholder generato, non un design definitivo.
