package de.kaymalek.salescrafter.telefon

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telecom.Call
import androidx.core.app.NotificationCompat

/**
 * Benachrichtigungen für eingehende (Vollbild, Annehmen/Ablehnen) und laufende Anrufe.
 * Android verlangt das von einer Telefon-App, damit Anrufe auch bei gesperrtem Bildschirm erscheinen.
 */
object Benachrichtigung {

    private const val KANAL_KLINGELN = "eingehend"
    private const val KANAL_LAUFEND = "laufend"
    private const val ID = 4711

    fun kanaeleAnlegen(ctx: Context) {
        val nm = ctx.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(KANAL_KLINGELN, "Eingehende Anrufe", NotificationManager.IMPORTANCE_HIGH).apply {
                setSound(null, null) // klingeln tut Android selbst
            }
        )
        nm.createNotificationChannel(
            NotificationChannel(KANAL_LAUFEND, "Laufendes Gespräch", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun aktualisiere(ctx: Context) {
        kanaeleAnlegen(ctx)
        val nm = ctx.getSystemService(NotificationManager::class.java)
        val call = AnrufZentrale.hauptanruf()
        if (call == null) {
            nm.cancel(ID)
            return
        }
        val info = AnrufZentrale.info[call]
        val titel = info?.kontakt?.name?.ifBlank { null } ?: info?.nummer?.ifBlank { null } ?: "Unbekannt"
        val unter = info?.kontakt?.firma?.ifBlank { null }

        val oeffnen = PendingIntent.getActivity(
            ctx, 1, Intent(ctx, AnrufActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

        val n: Notification = if (AnrufZentrale.zustand(call) == Call.STATE_RINGING) {
            val annehmen = PendingIntent.getActivity(
                ctx, 2,
                Intent(ctx, AnrufActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(AnrufActivity.EXTRA_ANNEHMEN, true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val ablehnen = PendingIntent.getBroadcast(
                ctx, 3, Intent(ctx, AktionEmpfaenger::class.java).setAction(AktionEmpfaenger.ABLEHNEN),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            NotificationCompat.Builder(ctx, KANAL_KLINGELN)
                .setSmallIcon(R.drawable.ic_telefon)
                .setContentTitle(titel)
                .setContentText(unter ?: "Eingehender Anruf")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setOngoing(true)
                .setContentIntent(oeffnen)
                .setFullScreenIntent(oeffnen, true)
                .addAction(0, "Ablehnen", ablehnen)
                .addAction(0, "Annehmen", annehmen)
                .build()
        } else {
            val auflegen = PendingIntent.getBroadcast(
                ctx, 4, Intent(ctx, AktionEmpfaenger::class.java).setAction(AktionEmpfaenger.AUFLEGEN),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            NotificationCompat.Builder(ctx, KANAL_LAUFEND)
                .setSmallIcon(R.drawable.ic_telefon)
                .setContentTitle(titel)
                .setContentText(unter ?: "Gespräch läuft")
                .setCategory(NotificationCompat.CATEGORY_CALL)
                .setOngoing(true)
                .setUsesChronometer(call.details.connectTimeMillis > 0)
                .setWhen(if (call.details.connectTimeMillis > 0) call.details.connectTimeMillis else System.currentTimeMillis())
                .setContentIntent(oeffnen)
                .addAction(0, "Auflegen", auflegen)
                .build()
        }
        try {
            nm.notify(ID, n)
        } catch (_: SecurityException) {
            // Benachrichtigungen nicht erlaubt – der Anrufbildschirm öffnet sich trotzdem, wenn die App vorn ist.
        }
    }
}

class AktionEmpfaenger : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ABLEHNEN -> AnrufZentrale.klingelnd()?.reject(false, null)
            AUFLEGEN -> AnrufZentrale.hauptanruf()?.disconnect()
        }
    }

    companion object {
        const val ABLEHNEN = "de.kaymalek.salescrafter.telefon.ABLEHNEN"
        const val AUFLEGEN = "de.kaymalek.salescrafter.telefon.AUFLEGEN"
    }
}
