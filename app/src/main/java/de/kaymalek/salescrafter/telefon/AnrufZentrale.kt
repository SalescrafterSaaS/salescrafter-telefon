package de.kaymalek.salescrafter.telefon

import android.telecom.Call
import android.telecom.CallAudioState
import kotlinx.coroutines.flow.MutableStateFlow

/** Was wir zu einem laufenden Anruf wissen. */
data class AnrufInfo(
    val nummer: String,
    val kontakt: Kontakt?,
    val eingehend: Boolean,
    val geschaeftlich: Boolean,
)

/** Ein beendeter geschäftlicher Anruf, für den noch das Ergebnis erfasst werden soll. */
data class Abschluss(
    val nummer: String,
    val kontakt: Kontakt?,
    val eingehend: Boolean,
    val dauerSek: Int,
    val notiz: String,
    val ausWarteschlange: Boolean,
)

/** Ein Anruf, den die App gleich startet – damit wir ihn beim Eintreffen dem Kontakt zuordnen können. */
data class Geplant(
    val nummer: String,
    val kontakt: Kontakt?,
    val geschaeftlich: Boolean,
    val ausWarteschlange: Boolean,
)

/**
 * Gemeinsamer Zustand zwischen dem Anruf-Dienst (von Android gesteuert) und den Bildschirmen.
 */
object AnrufZentrale {

    var service: AnrufService? = null

    val anrufe = mutableListOf<Call>()
    val info = HashMap<Call, AnrufInfo>()
    val ausWarteschlange = HashSet<Call>()

    /** Zählt bei jeder Änderung hoch; die Bildschirme beobachten das. */
    val version = MutableStateFlow(0L)
    val audio = MutableStateFlow<CallAudioState?>(null)
    val abschluss = MutableStateFlow<Abschluss?>(null)

    /** Was während des Gesprächs ins Notizfeld getippt wurde. */
    var gespraechsnotiz: String = ""

    var geplant: Geplant? = null

    fun geaendert() {
        version.value = version.value + 1
    }

    @Suppress("DEPRECATION")
    fun zustand(call: Call): Int = call.state

    fun klingelnd(): Call? = anrufe.firstOrNull { zustand(it) == Call.STATE_RINGING }

    /** Der Anruf, um den es auf dem Bildschirm geht: klingelnd vor aktiv vor allem anderen. */
    fun hauptanruf(): Call? =
        klingelnd()
            ?: anrufe.firstOrNull { zustand(it) == Call.STATE_ACTIVE }
            ?: anrufe.firstOrNull { zustand(it) == Call.STATE_DIALING || zustand(it) == Call.STATE_CONNECTING || zustand(it) == Call.STATE_SELECT_PHONE_ACCOUNT }
            ?: anrufe.firstOrNull()

    fun nebenanruf(): Call? {
        val haupt = hauptanruf() ?: return null
        return anrufe.firstOrNull { it != haupt }
    }
}
