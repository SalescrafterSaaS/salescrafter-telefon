package de.kaymalek.salescrafter.telefon

import android.content.ComponentName
import android.content.Context
import android.telecom.PhoneAccountHandle
import org.json.JSONArray
import org.json.JSONObject

data class Kontakt(
    val name: String,
    val firma: String,
    val nummer: String,
    val notiz: String,
)

data class Ergebnis(
    val zeit: Long,
    val richtung: String,      // "raus" oder "rein"
    val nummer: String,
    val name: String,
    val firma: String,
    val dauerSek: Int,
    val ergebnis: String,
    val notiz: String,
)

/**
 * Lokale Ablage für den Prototyp: Warteschlange, Position, Ergebnisse, gewählte Leitung.
 * Später kommt das alles aus Salescrafter bzw. geht dorthin zurück.
 */
object Speicher {

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("telefon", Context.MODE_PRIVATE)

    // ---------- Warteschlange ----------

    fun warteschlange(ctx: Context): List<Kontakt> {
        val roh = prefs(ctx).getString("warteschlange", "[]") ?: "[]"
        val arr = JSONArray(roh)
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Kontakt(o.optString("name"), o.optString("firma"), o.optString("nummer"), o.optString("notiz"))
        }
    }

    fun setzeWarteschlange(ctx: Context, liste: List<Kontakt>) {
        val arr = JSONArray()
        liste.forEach {
            arr.put(JSONObject().put("name", it.name).put("firma", it.firma).put("nummer", it.nummer).put("notiz", it.notiz))
        }
        prefs(ctx).edit().putString("warteschlange", arr.toString()).putInt("position", 0).apply()
    }

    fun position(ctx: Context) = prefs(ctx).getInt("position", 0)

    fun setzePosition(ctx: Context, pos: Int) = prefs(ctx).edit().putInt("position", pos).apply()

    fun aktuellerKontakt(ctx: Context): Kontakt? = warteschlange(ctx).getOrNull(position(ctx))

    /** Sucht eine Nummer in der Warteschlange – damit auch Rückrufe von Kontakten mit Namen erscheinen. */
    fun findeKontakt(ctx: Context, nummer: String): Kontakt? {
        val ziel = Nummer.vergleichsschluessel(nummer)
        if (ziel.isEmpty()) return null
        return warteschlange(ctx).firstOrNull { Nummer.vergleichsschluessel(it.nummer) == ziel }
    }

    /**
     * Liest eingefügten Text: eine Zeile pro Kontakt, Felder getrennt durch ; oder Tab (notfalls Komma).
     * Das Feld, das wie eine Telefonnummer aussieht, wird die Nummer; davor Name, dann Firma, Rest ist Notiz.
     */
    fun leseListe(text: String): List<Kontakt> {
        return text.lines().mapNotNull { zeile ->
            val z = zeile.trim()
            if (z.isEmpty()) return@mapNotNull null
            val trenner = when {
                z.contains(';') -> ";"
                z.contains('\t') -> "\t"
                else -> ","
            }
            val felder = z.split(trenner).map { it.trim().trim('"') }
            val nrIndex = felder.indexOfFirst { Nummer.siehtAusWieNummer(it) }
            if (nrIndex < 0) return@mapNotNull null   // Kopfzeile oder Zeile ohne Nummer
            val rest = felder.filterIndexed { i, f -> i != nrIndex && f.isNotEmpty() }
            Kontakt(
                name = rest.getOrElse(0) { "" },
                firma = rest.getOrElse(1) { "" },
                nummer = felder[nrIndex],
                notiz = rest.drop(2).joinToString(" · "),
            )
        }
    }

    // ---------- Ergebnisse ----------

    fun ergebnisse(ctx: Context): List<Ergebnis> {
        val arr = JSONArray(prefs(ctx).getString("ergebnisse", "[]") ?: "[]")
        return (0 until arr.length()).map {
            val o = arr.getJSONObject(it)
            Ergebnis(
                o.optLong("zeit"), o.optString("richtung"), o.optString("nummer"), o.optString("name"),
                o.optString("firma"), o.optInt("dauer"), o.optString("ergebnis"), o.optString("notiz"),
            )
        }
    }

    fun speichereErgebnis(ctx: Context, e: Ergebnis) {
        val arr = JSONArray(prefs(ctx).getString("ergebnisse", "[]") ?: "[]")
        arr.put(
            JSONObject().put("zeit", e.zeit).put("richtung", e.richtung).put("nummer", e.nummer)
                .put("name", e.name).put("firma", e.firma).put("dauer", e.dauerSek)
                .put("ergebnis", e.ergebnis).put("notiz", e.notiz)
        )
        prefs(ctx).edit().putString("ergebnisse", arr.toString()).apply()
    }

    fun loescheErgebnisse(ctx: Context) = prefs(ctx).edit().remove("ergebnisse").apply()

    fun ergebnisseAlsCsv(ctx: Context): String {
        val fmt = java.text.SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.GERMANY)
        val sb = StringBuilder("Zeit;Richtung;Name;Firma;Nummer;Dauer (s);Ergebnis;Notiz\n")
        ergebnisse(ctx).forEach {
            fun q(s: String) = "\"" + s.replace("\"", "\"\"") + "\""
            sb.append(listOf(fmt.format(java.util.Date(it.zeit)), it.richtung, q(it.name), q(it.firma), q(it.nummer),
                it.dauerSek.toString(), q(it.ergebnis), q(it.notiz)).joinToString(";")).append('\n')
        }
        return sb.toString()
    }

    // ---------- Leitung (SIM) für Akquise ----------

    fun leitung(ctx: Context): PhoneAccountHandle? {
        val p = prefs(ctx)
        val komp = p.getString("leitung_komponente", null) ?: return null
        val id = p.getString("leitung_id", null) ?: return null
        val cn = ComponentName.unflattenFromString(komp) ?: return null
        return PhoneAccountHandle(cn, id)
    }

    fun setzeLeitung(ctx: Context, h: PhoneAccountHandle, name: String) {
        prefs(ctx).edit()
            .putString("leitung_komponente", h.componentName.flattenToString())
            .putString("leitung_id", h.id)
            .putString("leitung_name", name)
            .apply()
    }

    fun leitungName(ctx: Context): String? = prefs(ctx).getString("leitung_name", null)
}

object Nummer {
    fun siehtAusWieNummer(s: String): Boolean {
        val ziffern = s.count { it.isDigit() }
        return ziffern >= 5 && s.all { it.isDigit() || it in "+ ()/-." }
    }

    /** Für Telecom: nur + und Ziffern. */
    fun bereinigt(s: String): String {
        val t = s.trim()
        val plus = t.startsWith("+")
        val ziffern = t.filter { it.isDigit() }
        return if (plus) "+$ziffern" else ziffern
    }

    /** Letzte 9 Ziffern – so passen 0151…, +49151… und 0049151… zusammen. */
    fun vergleichsschluessel(s: String): String = s.filter { it.isDigit() }.takeLast(9)
}
