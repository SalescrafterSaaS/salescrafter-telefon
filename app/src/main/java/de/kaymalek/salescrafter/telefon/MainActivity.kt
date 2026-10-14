package de.kaymalek.salescrafter.telefon

import android.Manifest
import android.app.role.RoleManager
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.telecom.TelecomManager
import android.text.InputType
import android.view.View
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Startseite: Einrichtung, nächster Kontakt aus der Warteschlange, freie Nummer, letzte Ergebnisse.
 */
class MainActivity : AppCompatActivity() {

    private val telecom by lazy { getSystemService(TelecomManager::class.java) }
    private val rollen by lazy { getSystemService(RoleManager::class.java) }

    private lateinit var tvRolle: TextView
    private lateinit var btnRolle: MaterialButton
    private lateinit var tvLeitung: TextView
    private lateinit var tvPosition: TextView
    private lateinit var tvName: TextView
    private lateinit var tvFirma: TextView
    private lateinit var tvNummer: TextView
    private lateinit var tvNotiz: TextView
    private lateinit var etNummer: EditText
    private lateinit var tvAnzahl: TextView
    private lateinit var tvErgebnisse: TextView
    private lateinit var btnAnrufen: MaterialButton
    private lateinit var btnUeberspringen: MaterialButton

    private val rolleAnfrage =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { aktualisiere() }

    private val rechteAnfrage =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { aktualisiere() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Benachrichtigung.kanaeleAnlegen(this)

        tvRolle = findViewById(R.id.tvRolle)
        btnRolle = findViewById(R.id.btnRolle)
        tvLeitung = findViewById(R.id.tvLeitung)
        tvPosition = findViewById(R.id.tvPosition)
        tvName = findViewById(R.id.tvName)
        tvFirma = findViewById(R.id.tvFirma)
        tvNummer = findViewById(R.id.tvNummer)
        tvNotiz = findViewById(R.id.tvNotiz)
        etNummer = findViewById(R.id.etNummer)
        tvAnzahl = findViewById(R.id.tvAnzahl)
        tvErgebnisse = findViewById(R.id.tvErgebnisse)
        btnAnrufen = findViewById(R.id.btnAnrufen)
        btnUeberspringen = findViewById(R.id.btnUeberspringen)

        btnRolle.setOnClickListener { rolleAnfordern() }
        findViewById<MaterialButton>(R.id.btnLeitung).setOnClickListener { leitungWaehlen() }
        btnAnrufen.setOnClickListener { naechstenAnrufen() }
        btnUeberspringen.setOnClickListener {
            Speicher.setzePosition(this, Speicher.position(this) + 1)
            aktualisiere()
        }
        findViewById<MaterialButton>(R.id.btnWaehlen).setOnClickListener {
            val nr = etNummer.text.toString()
            if (Nummer.siehtAusWieNummer(nr)) anrufen(nr, Speicher.findeKontakt(this, nr), geschaeftlich = true, ausWarteschlange = false)
            else zeige("Bitte eine Nummer eingeben.")
        }
        findViewById<MaterialButton>(R.id.btnPrivat).setOnClickListener {
            val nr = etNummer.text.toString()
            if (Nummer.siehtAusWieNummer(nr)) anrufen(nr, null, geschaeftlich = false, ausWarteschlange = false)
            else zeige("Bitte eine Nummer eingeben.")
        }
        findViewById<MaterialButton>(R.id.btnListe).setOnClickListener { listeEinfuegen() }
        findViewById<MaterialButton>(R.id.btnVonVorn).setOnClickListener {
            Speicher.setzePosition(this, 0)
            aktualisiere()
        }
        findViewById<MaterialButton>(R.id.btnTeilen).setOnClickListener { ergebnisseTeilen() }
        findViewById<MaterialButton>(R.id.btnLeeren).setOnClickListener {
            AlertDialog.Builder(this)
                .setMessage("Alle gespeicherten Ergebnisse löschen?")
                .setPositiveButton("Löschen") { _, _ -> Speicher.loescheErgebnisse(this); aktualisiere() }
                .setNegativeButton("Abbrechen", null)
                .show()
        }

        fehlendeRechteAnfragen()
        verarbeite(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        verarbeite(intent)
    }

    override fun onResume() {
        super.onResume()
        aktualisiere()
    }

    /** tel:-Links (z. B. aus Salescrafter im Browser) und "Nächsten anrufen" nach dem Ergebnis. */
    private fun verarbeite(intent: Intent?) {
        intent ?: return
        val nr = intent.data?.takeIf { it.scheme == "tel" }?.schemeSpecificPart
        if (!nr.isNullOrBlank()) {
            etNummer.setText(Uri.decode(nr))
            etNummer.setSelection(etNummer.text.length)
        }
        if (intent.getBooleanExtra(EXTRA_NAECHSTEN_ANRUFEN, false)) {
            intent.removeExtra(EXTRA_NAECHSTEN_ANRUFEN)
            naechstenAnrufen()
        }
    }

    // ---------- Anzeige ----------

    private fun aktualisiere() {
        val istTelefonApp = rollen.isRoleHeld(RoleManager.ROLE_DIALER)
        tvRolle.text = if (istTelefonApp) "✓ Ist Standard-Telefon-App" else "Noch nicht Standard-Telefon-App – dann zeigt Android beim Telefonieren seinen eigenen Bildschirm."
        btnRolle.visibility = if (istTelefonApp) View.GONE else View.VISIBLE

        val leitung = Speicher.leitungName(this)
        tvLeitung.text = if (leitung != null) "Akquise-Leitung: $leitung" else "Akquise-Leitung: noch nicht gewählt"

        val liste = Speicher.warteschlange(this)
        val pos = Speicher.position(this)
        val k = liste.getOrNull(pos)
        if (k != null) {
            tvPosition.text = "Nächster Kontakt · ${pos + 1} von ${liste.size}"
            tvName.text = k.name.ifBlank { k.nummer }
            tvFirma.text = k.firma
            tvFirma.visibility = if (k.firma.isBlank()) View.GONE else View.VISIBLE
            tvNummer.text = k.nummer
            tvNotiz.text = k.notiz
            tvNotiz.visibility = if (k.notiz.isBlank()) View.GONE else View.VISIBLE
            btnAnrufen.isEnabled = true
            btnUeberspringen.isEnabled = true
        } else {
            tvPosition.text = "Nächster Kontakt"
            tvName.text = if (liste.isEmpty()) "Keine Liste geladen" else "Liste abgearbeitet 🎉"
            tvFirma.visibility = View.GONE
            tvNummer.text = if (liste.isEmpty()) "Unten über „Liste einfügen“ Kontakte laden." else ""
            tvNotiz.visibility = View.GONE
            btnAnrufen.isEnabled = false
            btnUeberspringen.isEnabled = false
        }
        tvAnzahl.text = if (liste.isEmpty()) "Leer" else "${liste.size} Kontakte, ${maxOf(0, liste.size - pos)} offen"

        val fmt = SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY)
        val letzte = Speicher.ergebnisse(this).takeLast(8).reversed()
        tvErgebnisse.text = if (letzte.isEmpty()) "Noch keine." else letzte.joinToString("\n") {
            val wer = listOf(it.name, it.firma).filter { s -> s.isNotBlank() }.joinToString(", ").ifBlank { it.nummer }
            "${fmt.format(Date(it.zeit))} · $wer · ${it.ergebnis} · ${dauer(it.dauerSek)}"
        }
    }

    private fun dauer(s: Int) = "%d:%02d".format(s / 60, s % 60)

    // ---------- Einrichtung ----------

    private fun fehlendeRechteAnfragen() {
        val noetig = mutableListOf(Manifest.permission.CALL_PHONE, Manifest.permission.READ_PHONE_STATE)
        if (android.os.Build.VERSION.SDK_INT >= 33) noetig += Manifest.permission.POST_NOTIFICATIONS
        val fehlen = noetig.filter { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }
        if (fehlen.isNotEmpty()) rechteAnfrage.launch(fehlen.toTypedArray())
    }

    private fun rolleAnfordern() {
        if (!rollen.isRoleAvailable(RoleManager.ROLE_DIALER)) {
            zeige("Dieses Gerät erlaubt keine andere Telefon-App.")
            return
        }
        rolleAnfrage.launch(rollen.createRequestRoleIntent(RoleManager.ROLE_DIALER))
    }

    private fun leitungWaehlen() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) {
            fehlendeRechteAnfragen()
            return
        }
        val konten = try {
            telecom.callCapablePhoneAccounts
        } catch (e: SecurityException) {
            emptyList()
        }
        if (konten.isEmpty()) {
            zeige("Keine Leitungen gefunden.")
            return
        }
        val namen = konten.map { h ->
            val acc = telecom.getPhoneAccount(h)
            val label = acc?.label?.toString() ?: "Leitung"
            val nr = acc?.address?.schemeSpecificPart
            if (nr.isNullOrBlank()) label else "$label ($nr)"
        }
        AlertDialog.Builder(this)
            .setTitle("Über welche SIM laufen Akquise-Anrufe?")
            .setItems(namen.toTypedArray()) { _, i ->
                Speicher.setzeLeitung(this, konten[i], namen[i])
                aktualisiere()
            }
            .show()
    }

    // ---------- Anrufen ----------

    private fun naechstenAnrufen() {
        val k = Speicher.aktuellerKontakt(this)
        if (k == null) {
            zeige("Keine offenen Kontakte in der Warteschlange.")
            aktualisiere()
            return
        }
        anrufen(k.nummer, k, geschaeftlich = true, ausWarteschlange = true)
    }

    private fun anrufen(nummer: String, kontakt: Kontakt?, geschaeftlich: Boolean, ausWarteschlange: Boolean) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            fehlendeRechteAnfragen()
            return
        }
        if (AnrufZentrale.anrufe.isNotEmpty()) {
            zeige("Es läuft noch ein Gespräch.")
            startActivity(Intent(this, AnrufActivity::class.java))
            return
        }
        val nr = Nummer.bereinigt(nummer)
        val extras = Bundle()
        if (geschaeftlich) {
            val leitung = Speicher.leitung(this)
            if (leitung != null) extras.putParcelable(TelecomManager.EXTRA_PHONE_ACCOUNT_HANDLE, leitung)
            else zeige("Tipp: oben die Akquise-Leitung (sipgate) festlegen.")
        }
        AnrufZentrale.geplant = Geplant(nr, kontakt, geschaeftlich, ausWarteschlange)
        try {
            telecom.placeCall(Uri.fromParts("tel", nr, null), extras)
        } catch (e: SecurityException) {
            AnrufZentrale.geplant = null
            zeige("Anruf nicht erlaubt: ${e.message}")
        }
    }

    // ---------- Liste & Ergebnisse ----------

    private fun listeEinfuegen() {
        val feld = EditText(this).apply {
            hint = "Eine Zeile pro Kontakt:\nName; Firma; Nummer; Notiz"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 6
            maxLines = 12
            setPadding(48, 32, 48, 32)
        }
        AlertDialog.Builder(this)
            .setTitle("Kontaktliste einfügen")
            .setMessage("Aus Excel oder Salescrafter kopieren und hier einfügen. Ersetzt die aktuelle Warteschlange.")
            .setView(feld)
            .setPositiveButton("Übernehmen") { _, _ ->
                val liste = Speicher.leseListe(feld.text.toString())
                if (liste.isEmpty()) {
                    zeige("Keine Zeile mit Telefonnummer gefunden.")
                } else {
                    Speicher.setzeWarteschlange(this, liste)
                    zeige("${liste.size} Kontakte geladen.")
                    aktualisiere()
                }
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun ergebnisseTeilen() {
        if (Speicher.ergebnisse(this).isEmpty()) {
            zeige("Noch keine Ergebnisse.")
            return
        }
        val senden = Intent(Intent.ACTION_SEND)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_SUBJECT, "Telefonergebnisse")
            .putExtra(Intent.EXTRA_TEXT, Speicher.ergebnisseAlsCsv(this))
        startActivity(Intent.createChooser(senden, "Ergebnisse teilen"))
    }

    private fun zeige(t: String) = Toast.makeText(this, t, Toast.LENGTH_LONG).show()

    companion object {
        const val EXTRA_NAECHSTEN_ANRUFEN = "naechsten_anrufen"
    }
}
