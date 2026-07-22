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
