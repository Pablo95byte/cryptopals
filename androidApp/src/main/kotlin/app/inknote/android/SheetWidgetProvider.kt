package app.inknote.android

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews

/**
 * Il widget della home: un foglio bianco che apre il foglio vero (D30).
 *
 * `RemoteViews` di piattaforma e non Glance (D33). Glance porta con sé il runtime di
 * Compose e WorkManager, e WorkManager registra un `ContentProvider` per
 * inizializzarsi: il sistema lo eseguirebbe **a ogni avvio del processo**, compreso
 * quello della cattura, che è il percorso che misuriamo al millisecondo. Per un
 * widget che non mostra dati non comprerebbe niente.
 *
 * Non legge l'archivio, non ha niente da aggiornare e non sa che esistono delle note:
 * dalla home si aggiunge, non si rilegge (invariante 11).
 */
class SheetWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val open = PendingIntent.getActivity(
            context,
            0,
            Intent(context, CaptureActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val views = RemoteViews(context.packageName, R.layout.widget_sheet).apply {
            // Tutto il riquadro è il bersaglio: nessun punto da centrare col pollice.
            setOnClickPendingIntent(R.id.widget_sheet, open)
        }
        manager.updateAppWidget(widgetIds, views)
    }
}
