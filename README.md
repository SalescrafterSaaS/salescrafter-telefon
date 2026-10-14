# Salescrafter Telefon

Eigene Telefon-App fürs Handy: Anrufe laufen über den eigenen Anrufbildschirm statt über die Telefon-App von Android. Kontaktkarte und Notiz bleiben während des Gesprächs sichtbar, nach dem Auflegen kommt sofort die Ergebnis-Erfassung und mit einem Tipp der nächste Kontakt.

Prototyp-Stand: Warteschlange und Ergebnisse liegen lokal auf dem Handy. Die Anbindung an Salescrafter folgt im nächsten Schritt.

## Einrichten (einmalig)

1. `SalescrafterTelefon.apk` unter *Releases → aktuell* aufs Handy laden und installieren.
2. App öffnen, Berechtigungen erlauben (Telefon, Benachrichtigungen).
3. **Als Telefon-App einrichten** tippen und bestätigen. Ab dann zeigt Android bei jedem Anruf den Bildschirm dieser App – auch bei privaten Anrufen. Zurück geht es jederzeit unter *Einstellungen → Apps → Standard-Apps → Telefon-App*.
4. **Leitung wählen** → die sipgate-eSIM. Darüber laufen alle Akquise-Anrufe; „Privat“ wählt über die normale Standard-SIM.

## Benutzen

- **Liste einfügen:** Eine Zeile pro Kontakt, Felder mit `;` oder Tab getrennt, z. B. direkt aus Excel kopiert: `Max Muster; Muster GmbH; 0151 2345678; Notiz`. Das Feld, das wie eine Telefonnummer aussieht, wird die Nummer.
- **Anrufen** → eigener Anrufbildschirm mit Kontakt, Notizfeld, Stumm, Lautsprecher, Tastatur (Durchwahl/Sprachmenü), Halten.
- Nach dem Auflegen: Ergebnis wählen → **Speichern & nächster** wählt sofort den nächsten Kontakt.
- **Als CSV teilen:** Ergebnisse per Mail, Messenger oder in einen Cloud-Ordner schicken.
- Ergebnis-Erfassung erscheint nur bei Anrufen über die Akquise-Leitung (raus und rein), nicht bei privaten Anrufen.
- `tel:`-Links aus dem Browser (z. B. Salescrafter) öffnen die App mit der Nummer im Wählfeld.

## Bauen

Jeder Push auf `main` baut über GitHub Actions eine neue APK und legt sie als Release „aktuell“ ab.
