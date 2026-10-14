package de.kaymalek.salescrafter.telefon

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.telecom.Call
import android.telecom.CallAudioState
import android.telecom.VideoProfile
import android.text.Editable
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipGroup
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Eigener Anrufbildschirm: Kontaktkarte und Notiz bleiben während des Gesprächs sichtbar,
 * nach dem Auflegen folgt direkt die Ergebnis-Erfassung und der Sprung zum nächsten Kontakt.
 */
class AnrufActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var tvName: TextView
    private lateinit var tvFirma: TextView
    private lateinit var tvNummer: TextView
    private lateinit var tvKontaktNotiz: TextView
    private lateinit var zweitBanner: LinearLayout
    private lateinit var tvZweit: TextView
    private lateinit var btnZweit: MaterialButton
    private lateinit var panelErgebnis: LinearLayout
    private lateinit var chips: ChipGroup
    private lateinit var etNotiz: EditText
    private lateinit var tastatur: GridLayout
    private lateinit var panelGespraech: LinearLayout
    private lateinit var panelKlingeln: LinearLayout
    private lateinit var panelAbschluss: LinearLayout
    private lateinit var btnStumm: MaterialButton
    private lateinit var btnLaut: MaterialButton
    private lateinit var btnHalten: MaterialButton
    private lateinit var btnNaechster: MaterialButton

    private val takt = Handler(Looper.getMainLooper())
    private val sekundentakt = object : Runnable {
        override fun run() {
            zeigeStatus()
            takt.postDelayed(this, 1000)
        }
    }

    private var naeherung: PowerManager.WakeLock? = null
    private var notizUebernommen = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_anruf)

        tvStatus = findViewById(R.id.tvStatus)
        tvName = findViewById(R.id.tvName)
        tvFirma = findViewById(R.id.tvFirma)
        tvNummer = findViewById(R.id.tvNummer)
        tvKontaktNotiz = findViewById(R.id.tvKontaktNotiz)
        zweitBanner = findViewById(R.id.zweitBanner)
        tvZweit = findViewById(R.id.tvZweit)
        btnZweit = findViewById(R.id.btnZweit)
        panelErgebnis = findViewById(R.id.panelErgebnis)
        chips = findViewById(R.id.chipsErgebnis)
        etNotiz = findViewById(R.id.etNotiz)
        tastatur = findViewById(R.id.tastatur)
        panelGespraech = findViewById(R.id.panelGespraech)
        panelKlingeln = findViewById(R.id.panelKlingeln)
        panelAbschluss = findViewById(R.id.panelAbschluss)
        btnStumm = findViewById(R.id.btnStumm)
        btnLaut = findViewById(R.id.btnLaut)
        btnHalten = findViewById(R.id.btnHalten)
        btnNaechster = findViewById(R.id.btnNaechster)

        ERGEBNISSE.forEach { text ->
            chips.addView(Chip(this).apply {
                id = View.generateViewId()
                this.text = text
                isCheckable = true
                textSize = 15f
            })
        }
        baueTastatur()
        tastatur.visibility = View.GONE

        etNotiz.setText(AnrufZentrale.gespraechsnotiz)
        etNotiz.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) {
                if (AnrufZentrale.anrufe.isNotEmpty()) AnrufZentrale.gespraechsnotiz = s?.toString() ?: ""
            }
        })

        findViewById<MaterialButton>(R.id.btnAnnehmen).setOnClickListener { annehmen() }
        findViewById<MaterialButton>(R.id.btnAblehnen).setOnClickListener {
            AnrufZentrale.klingelnd()?.reject(false, null)
        }
        findViewById<MaterialButton>(R.id.btnAuflegen).setOnClickListener {
            AnrufZentrale.hauptanruf()?.disconnect()
        }
        btnStumm.setOnClickListener {
            val stumm = AnrufZentrale.audio.value?.isMuted ?: false
            AnrufZentrale.service?.setMuted(!stumm)
        }
        btnLaut.setOnClickListener { lautsprecherUmschalten() }
        findViewById<MaterialButton>(R.id.btnTastatur).setOnClickListener {
            tastatur.visibility = if (tastatur.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }
        btnHalten.setOnClickListener {
            val c = AnrufZentrale.hauptanruf() ?: return@setOnClickListener
            if (AnrufZentrale.zustand(c) == Call.STATE_HOLDING) c.unhold() else c.hold()
        }
        btnZweit.setOnClickListener {
            // Gehaltenen Anruf zurückholen; Android hält dabei das laufende Gespräch.
            AnrufZentrale.nebenanruf()?.let { if (AnrufZentrale.zustand(it) == Call.STATE_HOLDING) it.unhold() }
        }
        findViewById<MaterialButton>(R.id.btnSpeichern).setOnClickListener { speichern(weiter = false) }
        btnNaechster.setOnClickListener { speichern(weiter = true) }
        findViewById<MaterialButton>(R.id.btnVerwerfen).setOnClickListener {
            AnrufZentrale.abschluss.value = null
            finish()
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (AnrufZentrale.anrufe.isEmpty() && AnrufZentrale.abschluss.value != null) {
                    Toast.makeText(this@AnrufActivity, "Bitte Ergebnis wählen oder „Ohne Ergebnis schließen“.", Toast.LENGTH_SHORT).show()
                } else {
                    // Gespräch läuft weiter; über die Benachrichtigung kommt man zurück.
                    moveTaskToBack(true)
                }
            }
        })

        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                combine(AnrufZentrale.version, AnrufZentrale.audio, AnrufZentrale.abschluss) { _, _, _ -> }
                    .collect { zeige() }
            }
        }

        verarbeite(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        verarbeite(intent)
    }

    override fun onStart() {
        super.onStart()
        takt.post(sekundentakt)
    }

    override fun onStop() {
        takt.removeCallbacks(sekundentakt)
        super.onStop()
    }

    override fun onDestroy() {
        naeherungssensor(false)
        super.onDestroy()
    }

    private fun verarbeite(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_ANNEHMEN, false) == true) {
            intent.removeExtra(EXTRA_ANNEHMEN)
            annehmen()
        }
    }

    private fun annehmen() {
        AnrufZentrale.klingelnd()?.answer(VideoProfile.STATE_AUDIO_ONLY)
    }

    // ---------- Anzeige ----------

    private fun zeige() {
        val call = AnrufZentrale.hauptanruf()
        val abschluss = AnrufZentrale.abschluss.value

        if (call == null) {
            naeherungssensor(false)
            if (abschluss == null) {
                finish()
                return
            }
            zeigeAbschluss(abschluss)
            return
        }

        val info = AnrufZentrale.info[call]
        val zustand = AnrufZentrale.zustand(call)
        zeigeKontakt(info?.kontakt, info?.nummer ?: "")

        val klingelt = zustand == Call.STATE_RINGING
        panelKlingeln.visibility = if (klingelt) View.VISIBLE else View.GONE
        panelGespraech.visibility = if (klingelt) View.GONE else View.VISIBLE
        panelAbschluss.visibility = View.GONE
        panelErgebnis.visibility = View.GONE
        etNotiz.visibility = View.VISIBLE
        if (klingelt) tastatur.visibility = View.GONE

        // Zweiter Anruf
        val neben = AnrufZentrale.nebenanruf()
        if (neben != null) {
            val ni = AnrufZentrale.info[neben]
            val wer = ni?.kontakt?.name?.ifBlank { null } ?: ni?.nummer ?: "Anruf"
            val z = AnrufZentrale.zustand(neben)
            tvZweit.text = if (z == Call.STATE_HOLDING) "Gehalten: $wer" else "Zweiter Anruf: $wer"
            btnZweit.visibility = if (z == Call.STATE_HOLDING && !klingelt) View.VISIBLE else View.GONE
            zweitBanner.visibility = View.VISIBLE
        } else {
            zweitBanner.visibility = View.GONE
        }

        // Knöpfe
        val audio = AnrufZentrale.audio.value
        val stumm = audio?.isMuted ?: false
        val laut = audio?.route == CallAudioState.ROUTE_SPEAKER
        btnStumm.text = if (stumm) "Stumm ✓" else "Stumm"
        btnLaut.text = if (laut) "Lautspr. ✓" else "Lautspr."
        btnHalten.text = if (zustand == Call.STATE_HOLDING) "Fortsetzen" else "Halten"

        zeigeStatus()

        // Display aus, wenn das Handy am Ohr ist (nicht bei Lautsprecher)
        naeherungssensor(!klingelt && !laut && zustand != Call.STATE_HOLDING)
    }

    private fun zeigeKontakt(k: Kontakt?, nummer: String) {
        tvName.text = k?.name?.ifBlank { null } ?: nummer.ifBlank { "Unbekannt" }
        tvFirma.text = k?.firma ?: ""
        tvFirma.visibility = if (k?.firma.isNullOrBlank()) View.GONE else View.VISIBLE
        tvNummer.text = if (k?.name.isNullOrBlank()) "" else nummer
        tvNummer.visibility = if (tvNummer.text.isNullOrBlank()) View.GONE else View.VISIBLE
        tvKontaktNotiz.text = k?.notiz ?: ""
        tvKontaktNotiz.visibility = if (k?.notiz.isNullOrBlank()) View.GONE else View.VISIBLE
    }

    private fun zeigeStatus() {
        val call = AnrufZentrale.hauptanruf()
        if (call == null) {
            val a = AnrufZentrale.abschluss.value ?: return
            tvStatus.text = when {
                a.dauerSek > 0 -> "Beendet · ${dauer(a.dauerSek)}"
                a.eingehend -> "Verpasst / abgelehnt"
                else -> "Nicht verbunden"
            }
            return
        }
        val info = AnrufZentrale.info[call]
        tvStatus.text = when (AnrufZentrale.zustand(call)) {
            Call.STATE_RINGING -> if (info?.geschaeftlich == true) "Eingehend · Akquise-Leitung" else "Eingehender Anruf"
            Call.STATE_DIALING, Call.STATE_CONNECTING, Call.STATE_NEW -> "Wählt …"
            Call.STATE_SELECT_PHONE_ACCOUNT -> "Leitung wählen …"
            Call.STATE_HOLDING -> "Gehalten"
            Call.STATE_DISCONNECTING -> "Wird beendet …"
            Call.STATE_ACTIVE -> {
                val seit = call.details.connectTimeMillis
                if (seit > 0) "Gespräch · ${dauer(((System.currentTimeMillis() - seit) / 1000).toInt())}" else "Gespräch"
            }
            else -> ""
        }
    }

    private fun zeigeAbschluss(a: Abschluss) {
        zeigeKontakt(a.kontakt, a.nummer)
        zweitBanner.visibility = View.GONE
        panelKlingeln.visibility = View.GONE
        panelGespraech.visibility = View.GONE
        tastatur.visibility = View.GONE
        panelErgebnis.visibility = View.VISIBLE
        panelAbschluss.visibility = View.VISIBLE
        etNotiz.visibility = View.VISIBLE
        if (!notizUebernommen) {
            if (etNotiz.text.isNullOrBlank()) etNotiz.setText(a.notiz)
            notizUebernommen = true
            // Naheliegendes Ergebnis vorauswählen
            if (a.dauerSek == 0 && chips.checkedChipId == View.NO_ID) {
                (chips.getChildAt(0) as? Chip)?.isChecked = true   // "Nicht erreicht"
            }
        }
        val gibtNaechsten = a.ausWarteschlange &&
            Speicher.position(this) + 1 < Speicher.warteschlange(this).size
        btnNaechster.visibility = if (gibtNaechsten) View.VISIBLE else View.GONE
        zeigeStatus()
    }

    private fun dauer(s: Int) = "%d:%02d".format(s / 60, s % 60)

    // ---------- Aktionen ----------

    @Suppress("DEPRECATION")
    private fun lautsprecherUmschalten() {
        val s = AnrufZentrale.service ?: return
        val laut = AnrufZentrale.audio.value?.route == CallAudioState.ROUTE_SPEAKER
        s.setAudioRoute(if (laut) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun baueTastatur() {
        val tasten = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#")
        tasten.forEach { t ->
            val b = MaterialButton(this, null, com.google.android.material.R.attr.materialButtonOutlinedStyle).apply {
                text = t
                textSize = 20f
            }
            b.setOnTouchListener { _, e ->
                val c = AnrufZentrale.hauptanruf()
                when (e.action) {
                    MotionEvent.ACTION_DOWN -> c?.playDtmfTone(t[0])
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> c?.stopDtmfTone()
                }
                false
            }
            val lp = GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
                GridLayout.spec(GridLayout.UNDEFINED, 1f),
            ).apply {
                width = 0
                setMargins(6, 4, 6, 4)
            }
            tastatur.addView(b, lp)
        }
    }

    private fun speichern(weiter: Boolean) {
        val a = AnrufZentrale.abschluss.value ?: return finish()
        val gewaehlt = chips.findViewById<Chip>(chips.checkedChipId)?.text?.toString()
        if (gewaehlt == null) {
            Toast.makeText(this, "Bitte ein Ergebnis wählen.", Toast.LENGTH_SHORT).show()
            return
        }
        Speicher.speichereErgebnis(
            this,
            Ergebnis(
                zeit = System.currentTimeMillis(),
                richtung = if (a.eingehend) "rein" else "raus",
                nummer = a.nummer,
                name = a.kontakt?.name ?: "",
                firma = a.kontakt?.firma ?: "",
                dauerSek = a.dauerSek,
                ergebnis = gewaehlt,
                notiz = etNotiz.text.toString().trim(),
            )
        )
        if (a.ausWarteschlange) Speicher.setzePosition(this, Speicher.position(this) + 1)
        AnrufZentrale.abschluss.value = null
        AnrufZentrale.gespraechsnotiz = ""

        val start = Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (weiter) start.putExtra(MainActivity.EXTRA_NAECHSTEN_ANRUFEN, true)
        startActivity(start)
        finish()
    }

    private fun naeherungssensor(an: Boolean) {
        if (an) {
            if (naeherung == null) {
                val pm = getSystemService(PowerManager::class.java)
                if (pm.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) {
                    naeherung = pm.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK, "telefon:ohr")
                }
            }
            naeherung?.let { if (!it.isHeld) it.acquire(4 * 60 * 60 * 1000L) }
        } else {
            naeherung?.let { if (it.isHeld) it.release() }
        }
    }

    companion object {
        const val EXTRA_ANNEHMEN = "annehmen"

        val ERGEBNISSE = listOf(
            "Nicht erreicht",
            "Mailbox",
            "Zentrale / Sekretariat",
            "Rückruf vereinbart",
            "Termin vereinbart",
            "Infos per Mail",
            "Kein Interesse",
            "Falsche Nummer",
        )
    }
}
