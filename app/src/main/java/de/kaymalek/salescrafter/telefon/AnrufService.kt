package de.kaymalek.salescrafter.telefon

import android.content.Intent
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.InCallService

/**
 * Android ruft diesen Dienst für jeden Anruf auf, sobald die App Standard-Telefon-App ist.
 * Er hält den Zustand in der AnrufZentrale aktuell und öffnet den eigenen Anrufbildschirm.
 */
class AnrufService : InCallService() {

    private val rueckruf = object : Call.Callback() {
        override fun onStateChanged(call: Call, state: Int) {
            AnrufZentrale.geaendert()
            Benachrichtigung.aktualisiere(this@AnrufService)
        }

        override fun onDetailsChanged(call: Call, details: Call.Details) {
            AnrufZentrale.geaendert()
        }
    }

    override fun onCreate() {
        super.onCreate()
        AnrufZentrale.service = this
    }

    override fun onDestroy() {
        if (AnrufZentrale.service === this) AnrufZentrale.service = null
        super.onDestroy()
    }

    override fun onCallAdded(call: Call) {
        super.onCallAdded(call)
        val nummer = call.details.handle?.schemeSpecificPart ?: ""
        val eingehend = call.details.callDirection == Call.Details.DIRECTION_INCOMING

        val geplant = AnrufZentrale.geplant?.takeIf {
            !eingehend && Nummer.vergleichsschluessel(it.nummer) == Nummer.vergleichsschluessel(nummer)
        }
        if (geplant != null) AnrufZentrale.geplant = null

        val kontakt = geplant?.kontakt ?: Speicher.findeKontakt(this, nummer)
        val geschaeftlich = geplant?.geschaeftlich ?: istAkquiseLeitung(call)

        AnrufZentrale.info[call] = AnrufInfo(nummer, kontakt, eingehend, geschaeftlich)
        if (geplant?.ausWarteschlange == true) AnrufZentrale.ausWarteschlange.add(call)
        AnrufZentrale.anrufe.add(call)
        if (AnrufZentrale.anrufe.size == 1) AnrufZentrale.gespraechsnotiz = ""
        call.registerCallback(rueckruf)
        AnrufZentrale.geaendert()

        Benachrichtigung.aktualisiere(this)

        // Ausgehend ist die App ohnehin vorn; eingehend sorgt zusätzlich die Vollbild-Benachrichtigung dafür.
        try {
            startActivity(Intent(this, AnrufActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (_: Exception) {
        }
    }

    override fun onCallRemoved(call: Call) {
        super.onCallRemoved(call)
        call.unregisterCallback(rueckruf)
        val i = AnrufZentrale.info.remove(call)
        val ausListe = AnrufZentrale.ausWarteschlange.remove(call)
        AnrufZentrale.anrufe.remove(call)

        if (i != null) {
            val verbunden = call.details.connectTimeMillis
            val dauer = if (verbunden > 0) ((System.currentTimeMillis() - verbunden) / 1000).toInt() else 0
            val geschaeftlich = i.geschaeftlich || istAkquiseLeitung(call)
            if (geschaeftlich) {
                AnrufZentrale.abschluss.value = Abschluss(
                    nummer = i.nummer,
                    kontakt = i.kontakt,
                    eingehend = i.eingehend,
                    dauerSek = dauer,
                    notiz = AnrufZentrale.gespraechsnotiz,
                    ausWarteschlange = ausListe,
                )
            }
        }
        AnrufZentrale.geaendert()
        Benachrichtigung.aktualisiere(this)
    }

    // Ab Android 14 gibt es onCallEndpointChanged; für Lautsprecher/Hörer reicht der ältere Weg.
    @Suppress("OVERRIDE_DEPRECATION", "DEPRECATION")
    override fun onCallAudioStateChanged(audioState: CallAudioState) {
        AnrufZentrale.audio.value = audioState
    }

    private fun istAkquiseLeitung(call: Call): Boolean {
        val leitung = Speicher.leitung(this) ?: return false
        return call.details.accountHandle == leitung
    }
}
