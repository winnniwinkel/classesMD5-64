# Aufgaben (Docker)

Start auf dem PC/Server:

    cd aufgaben
    docker compose up -d --build

Öffnen: `http://localhost:8080` – vom Handy (gleiches WLAN): `http://<IP-des-PCs>:8080`.

- Daten liegen in `./data/aufgaben.json` (Volume) und überleben Neustarts/Updates. Diese Datei sichern.
- PC und Handy greifen auf dieselben Daten zu (Sync bei Änderung, beim Öffnen und alle 20 s; bei gleichzeitigen Änderungen gewinnt pro Aufgabe die neuere).
- Passwortschutz: in `docker-compose.yml` `APP_USER`/`APP_PASSWORD` einkommentieren.
- Nicht ohne Passwort und HTTPS (z. B. Reverse Proxy/VPN wie Tailscale) ins Internet stellen.
- Ohne Docker: `DATA_FILE=./data/aufgaben.json node server.js`
