# Seafile Manager (Android)

Dateimanager für Android (getestet auf Zielgerät: Galaxy S24) mit Seafile-Anbindung.

- Tab **Dieses Gerät**: lokale Dateien durchsuchen, öffnen, umbenennen, löschen, Ordner anlegen, Datei nach Seafile hochladen (Menü ⋮)
- Tab **Seafile**: Login (URL, Benutzer, Passwort, optional 2FA), Bibliotheken/Ordner durchsuchen, öffnen, herunterladen (→ `Download/Seafile/`), hochladen, umbenennen, löschen, Ordner anlegen
- Selbstsigniertes Zertifikat: Option im Login-Formular
- Nicht unterstützt: verschlüsselte Bibliotheken

## APK bauen
- GitHub: Actions → „Build Seafile Manager APK“ → Artifact `SeafileManager-apk`
- oder Ordner `SeafileManager` in Android Studio öffnen → Build → Build APK(s)

Auf dem S24 „Installation aus unbekannten Quellen“ erlauben, APK installieren, beim ersten Start „Zugriff auf alle Dateien“ gewähren.
