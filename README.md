# IPTIMTV

Eenvoudige IPTV-app voor Android TV / Google TV én Android-telefoons. Simpel voor iedereen, met een uitgebreid menu **Geavanceerd** voor wie alles wil tweaken.

- **Start**: je eigen kanalen en groepen, *Verder kijken* en je opnames
- **Live TV**: providergroepen in je eigen volgorde, terugkijken waar de provider dat aanbiedt
- **Sport**: wedstrijden van vandaag (Eredivisie, Premier League, CL, ...) en op welke zenders ze te zien zijn
- **Films** en **Series**, **Zoeken** (ook in de gids)
- **Opnemen** naar `Download/IPTIMTV`, ook gepland vanuit de gids
- **Account** op iptv.timvanbergen.nl: logins, kanalen, groepen, kijkgeschiedenis en kijktijd op al je apparaten
- **Wedstrijdmeldingen**: 5 minuten voor de aftrap een melding, en tijdens het kijken een banner
- **Automatische updates** vanuit de GitHub-releases

## Web en iPhone

Op **https://iptv.timvanbergen.nl** staat de webversie: inloggen met je IPTIMTV-account en je hebt meteen je logins, kanalen en kijkgeschiedenis.
Op de iPhone: open de site in Safari, tik op *Delen* → *Zet op beginscherm*; dan werkt hij als app.

## Installeren

Bij de [nieuwste release](../../releases/latest):
- `IPTIMTV.apk` voor de tv (bijv. met de app *Downloader*)
- `IPTIMTV-mobile.apk` voor je telefoon

Elke push naar `main` bouwt automatisch nieuwe, ondertekende APK's; de app meldt de update zelf.

## Server

`server/` is de Cloudflare Worker (+ D1) achter iptv.timvanbergen.nl. Uitrollen met `npx wrangler deploy` in die map.
