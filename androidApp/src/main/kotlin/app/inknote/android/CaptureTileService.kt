package app.inknote.android

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService

/**
 * Il riquadro nelle impostazioni rapide: la porta d'ingresso della cattura a telefono
 * bloccato (D17, D33).
 *
 * Senza di lui D17 non aveva un ingresso: `showWhenLocked` permette al foglio di stare
 * sopra il blocco, ma su Android nessuna app può mettere una scorciatoia sulla
 * schermata di blocco, e i widget lì non esistono più. Le impostazioni rapide invece
 * si aprono col telefono bloccato, da qualunque schermata, con un gesto che l'utente
 * fa già cento volte al giorno.
 *
 * **Non chiama `unlockAndRun`, di proposito:** chiederebbe lo sblocco, che è
 * esattamente l'anello che questo ingresso toglie. Il foglio che si apre è cieco (D17),
 * quindi non c'è niente da proteggere dietro lo sblocco.
 *
 * Un servizio dichiarato nel manifest non costa niente all'avvio della cattura: a
 * differenza di un `ContentProvider`, il sistema lo crea solo quando serve (D32).
 */
class CaptureTileService : TileService() {

    override fun onStartListening() {
        // È un pulsante, non un interruttore: non ha uno stato acceso da mostrare.
        qsTile?.apply {
            state = Tile.STATE_INACTIVE
            updateTile()
        }
    }

    override fun onClick() {
        val intent = Intent(this, CaptureActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            // Da Android 14 la versione con l'Intent solleva un'eccezione.
            startActivityAndCollapse(
                PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE),
            )
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}
