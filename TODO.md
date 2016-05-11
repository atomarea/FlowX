# TODO

* Hintergrundbild für Chats
* Push Notifications
* Audioplayer mit Sensoren (Telefon like)
* Videos senden & zu konvertieren ggf. seperater Videoplayer einbinden (https://github.com/google/ExoPlayer)
* Chatübersicht vereinachen mit Klickbaren Chats zum löschen
* Links mit Kurztext & Bild in Nachrichten anzeigen
* Alle Chats aufräumen (Option)
* Benutzer optional mit E-Mail verifizieren 
* Meldeoption für störende Benutzer (Option zum melden mit Text / Bild an support Account)
* Lastseen Timestamp bei Änderung in DB speichern (gem. https://github.com/siacs/Conversations/blob/master/src/main/java/eu/siacs/conversations/persistance/DatabaseBackend.java#L63 existiert bereits eine Spalte Contact.LAST_TIME in der DB, jedoch ist diese oft nicht aktuell bzw. wird nur in sehr langen Abständen aktualisiert - überprüft auf dem Handy mittels aSQLiteManager; auch nach einem Neustart des Telefons sind die Lastseen Zeiten falsch, liegen oft Tage oder Wochen zurück)
