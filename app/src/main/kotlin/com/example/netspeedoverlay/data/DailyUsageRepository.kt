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
