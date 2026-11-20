# Webcam Gallipoli per Fire TV

Un'app per Fire TV Stick con fino a 4 (o più) webcam live in un'unica schermata.
Ogni riquadro è un vero browser: le webcam YouTube e quelle di SkylineWebcams partono da sole.
Su SkylineWebcams l'app accetta il consenso, chiude la promozione dell'abbonamento,
porta il player in vista e nasconde gli elementi indicati in `webcam.json`.

## Telecomando

| Tasto | Cosa fa |
|---|---|
| Frecce | scegli un riquadro (bordo arancione e nome per 4 secondi) |
| OK | riquadro scelto a tutto schermo / torna alla griglia |
| Play/Pausa (o Menu) | ricarica il riquadro scelto |
| Indietro | dalla vista singola torna alla griglia; dalla griglia esce |

## Cambiare le webcam

Modifica `app/src/main/assets/webcam.json`:

```json
{
  "colonne": 2,
  "nascondi": [".sw-host", ".clappr-watermark"],
  "webcam": [
    { "nome": "Lungomare", "url": "https://www.youtube.com/watch?v=..." }
  ]
}
```

Dopo ogni modifica GitHub ricrea l'APK da solo (vedi sotto): reinstallalo con Downloader.

## 1. Creare l'APK con GitHub (niente da installare)

1. Crea un account su https://github.com (gratis) e poi un nuovo repository
   (pulsante **New**), per esempio `webcam-gallipoli`, **Public**.
2. Nella pagina del repository: **uploading an existing file** (o *Add file → Upload files*)
   e trascina **tutto il contenuto** di questa cartella, poi **Commit changes**.
   - Controlla che ci sia la cartella `.github/workflows/crea-apk.yml`. Se manca (alcuni browser
     non caricano le cartelle che iniziano col punto): *Add file → Create new file*, scrivi come nome
     `.github/workflows/crea-apk.yml` e incolla dentro il contenuto del file.
3. Vai nella scheda **Actions**: parte "Crea APK". Dopo 3-5 minuti diventa verde.
4. L'APK è pubblicato a questo indirizzo fisso:
   `https://github.com/TUO-UTENTE/webcam-gallipoli/releases/download/apk/webcam-gallipoli.apk`

## 1-bis. In alternativa: Android Studio

Apri questa cartella con Android Studio, aspetta la sincronizzazione e usa
*Build → Build APK(s)*. L'APK è in `app/build/outputs/apk/debug/`.

## 2. Installare sulla Fire TV

1. Sulla Fire TV: **Impostazioni → La mia Fire TV → Opzioni sviluppatore**.
   Se non c'è: *La mia Fire TV → Informazioni*, premi 7 volte OK sul nome del dispositivo.
2. Cerca e installa l'app **Downloader** (di AFTVnews) dall'Appstore.
3. In *Opzioni sviluppatore → Installa app sconosciute* abilita **Downloader**.
4. Apri Downloader, scrivi l'indirizzo dell'APK del punto 1, scarica e premi **Installa**.
5. L'app "Webcam Gallipoli" compare tra le app (Impostazioni → Applicazioni o la riga "Le tue app").

## Note

- Quattro video contemporanei sono impegnativi per una chiavetta: se va a scatti,
  metti 2 o 3 webcam in `webcam.json`.
- L'app è per uso personale. I contenuti restano di YouTube e SkylineWebcams.
