package it.gallipoli.webcam;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.KeyEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Webcam Gallipoli per Fire TV: più webcam live in una griglia, ognuna in un proprio WebView.
 * Le webcam si configurano in assets/webcam.json.
 *
 * Telecomando:
 *   frecce          -> scegli il riquadro (bordo arancione)
 *   OK              -> riquadro a tutto schermo / torna alla griglia
 *   Play/Pausa      -> ricarica il riquadro scelto
 *   Avanti veloce   -> ricarica tutti i riquadri
 *   Indietro        -> dalla vista singola torna alla griglia, dalla griglia esce
 */
public class MainActivity extends Activity {

    private static final String BASE_YT = "https://webcam-gallipoli.app/";
    private static final Pattern YT_ID =
            Pattern.compile("(?:v=|youtu\\.be/|/live/|/embed/|/shorts/)([\\w-]{11})");

    private final List<FrameLayout> riquadri = new ArrayList<>();
    private final List<LinearLayout> righe = new ArrayList<>();
    private final List<WebView> viste = new ArrayList<>();
    private final List<TextView> etichette = new ArrayList<>();
    private final List<String> urls = new ArrayList<>();
    // riquadri YouTube per cui l'apertura diretta del player ha dato errore: usano sempre l'iframe
    private final Set<Integer> youtubeSoloIframe = new HashSet<>();
    private String scriptYoutubeHd = "";
    private String nascondiYoutube = "[]";

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable nascondiEvidenza = new Runnable() {
        @Override
        public void run() {
            mostraEvidenza(false);
        }
    };

    private String scriptPagina = "";
    private String playerHtml = "";
    private int colonne = 2;
    private int scelto = 0;
    private int singolo = -1;   // indice del riquadro a tutto schermo, -1 = griglia
    private long ricaricaMs = 30 * 60 * 1000L;
    private boolean ricaricaZoom = false;
    private boolean eraFermo = false;

    // ricarica tutto a intervalli regolari (i flussi live ogni tanto si bloccano)
    private final Runnable ricaricaPeriodica = new Runnable() {
        @Override
        public void run() {
            ricaricaTutto();
        }
    };

    // TV riaccesa / ritorno dalla modalita' quadro: lo schermo si riaccende
    private final BroadcastReceiver schermoAcceso = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handler.postDelayed(ricaricaPeriodica, 1500);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // nelle versioni "debug" le pagine si possono ispezionare dal PC con chrome://inspect
        if ((getApplicationInfo().flags & android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE) != 0) {
            WebView.setWebContentsDebuggingEnabled(true);
        }
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_FULLSCREEN);

        LinearLayout radice = new LinearLayout(this);
        radice.setOrientation(LinearLayout.VERTICAL);
        radice.setBackgroundColor(Color.BLACK);

        try {
            JSONObject cfg = new JSONObject(leggiAsset("webcam.json"));
            colonne = Math.max(1, cfg.optInt("colonne", 2));
            ricaricaMs = Math.max(0, cfg.optInt("ricarica_minuti", 30)) * 60 * 1000L;
            ricaricaZoom = cfg.optBoolean("ricarica_zoom", false);
            JSONArray nascondi = cfg.optJSONArray("nascondi");
            scriptPagina = leggiAsset("pagina.js")
                    .replace("__NASCONDI__", nascondi != null ? nascondi.toString() : "[]")
                    .replace("__RIEMPI__", cfg.optBoolean("riempi", true) ? "true" : "false")
                    .replace("__RITAGLIA__", cfg.optBoolean("ritaglia", true) ? "true" : "false")
                    .replace("__POSY__", String.valueOf(Math.max(0, Math.min(100, cfg.optInt("ritaglio_verticale", 80)))));
            playerHtml = leggiAsset("player.html");
            scriptYoutubeHd = leggiAsset("youtube_hd.js");
            JSONArray nascondiYt = cfg.optJSONArray("nascondi_youtube");
            nascondiYoutube = nascondiYt != null ? nascondiYt.toString() : "[]";

            JSONArray cams = cfg.getJSONArray("webcam");
            int n = cams.length();
            colonne = Math.min(colonne, Math.max(1, n));
            LinearLayout riga = null;
            for (int i = 0; i < n; i++) {
                if (i % colonne == 0) {
                    riga = new LinearLayout(this);
                    riga.setOrientation(LinearLayout.HORIZONTAL);
                    radice.addView(riga, new LinearLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));
                    righe.add(riga);
                }
                JSONObject c = cams.getJSONObject(i);
                riga.addView(creaRiquadro(c.optString("nome", "Webcam " + (i + 1)), c.getString("url")),
                        new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            // riempie l'ultima riga con spazi vuoti, così i riquadri restano della stessa larghezza
            if (riga != null) {
                for (int k = n % colonne == 0 ? colonne : n % colonne; k < colonne; k++) {
                    riga.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1f));
                }
            }
        } catch (Exception e) {
            TextView errore = new TextView(this);
            errore.setTextColor(Color.WHITE);
            errore.setTextSize(22);
            errore.setPadding(48, 48, 48, 48);
            errore.setText("Errore nella configurazione (webcam.json):\n" + e.getMessage());
            radice.addView(errore);
        }

        setContentView(radice);
        SessioneSeparata.pulisciCookieComuni();
        nascondiBarre();
        caricaScaglionato();
        mostraEvidenza(true);
        programmaRicarica();
        registerReceiver(schermoAcceso, new IntentFilter(Intent.ACTION_SCREEN_ON));
    }

    private View creaRiquadro(String nome, String url) {
        FrameLayout box = new FrameLayout(this);
        box.setBackgroundColor(Color.BLACK);
        box.setPadding(2, 2, 2, 2);

        WebView wv = new WebView(this);
        wv.setBackgroundColor(Color.BLACK);
        wv.setFocusable(false);               // i tasti del telecomando li gestisce l'app
        wv.setFocusableInTouchMode(false);
        WebSettings s = wv.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setMediaPlaybackRequiresUserGesture(false);   // i video partono da soli
        s.setSupportMultipleWindows(false);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        CookieManager.getInstance().setAcceptCookie(true);
        CookieManager.getInstance().setAcceptThirdPartyCookies(wv, true);

        final boolean inietta = youtubeId(url) == null;
        if (!inietta) {
            // YouTube: si presenta come Chrome per computer (stessa versione), non come telefono,
            // cosi' propone le qualita' alte
            s.setUserAgentString(agenteDesktop(s.getUserAgentString()));
        }
        // ogni riquadro che non e' YouTube ha i propri cookie (vedi SessioneSeparata)
        final SessioneSeparata sessione = inietta ? new SessioneSeparata() : null;
        wv.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String u) {
                if (inietta && u != null && u.startsWith("http")) view.evaluateJavascript(scriptPagina, null);
                if (!inietta && u != null && u.startsWith("https://www.youtube.com/embed/")) {
                    boolean grande = viste.indexOf(view) == singolo;
                    view.evaluateJavascript(scriptYoutubeHd.replace("__HD__", grande ? "true" : "false")
                            .replace("__NASCONDI_YT__", nascondiYoutube), null);
                    controllaErroreYoutube(view);
                }
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest richiesta) {
                return sessione != null ? sessione.scarica(richiesta) : null;
            }
        });
        wv.setWebChromeClient(new WebChromeClient() {
            @Override
            public Bitmap getDefaultVideoPoster() {   // niente icona grigia prima che parta il video
                return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
            }
        });

        TextView etichetta = new TextView(this);
        etichetta.setText(nome);
        etichetta.setTextColor(Color.WHITE);
        etichetta.setTextSize(16);
        etichetta.setBackgroundColor(0xAA000000);
        etichetta.setPadding(16, 6, 16, 6);
        etichetta.setVisibility(View.GONE);

        box.addView(wv, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        box.addView(etichetta, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.TOP | Gravity.START));

        riquadri.add(box);
        viste.add(wv);
        etichette.add(etichetta);
        urls.add(url);
        return box;
    }

    private void carica(int i) {
        WebView wv = viste.get(i);
        String url = urls.get(i);
        String id = youtubeId(url);
        if (id != null && !youtubeSoloIframe.contains(i)) {
            // il player YouTube e' la pagina principale del riquadro: l'app puo' tenerlo in riproduzione
            // sulla diretta e, quando e' ingrandito, forzare l'HD.
            // Il Referer dice a YouTube da dove arriva la richiesta (serve contro l'errore 153).
            Map<String, String> intestazioni = new HashMap<>();
            intestazioni.put("Referer", BASE_YT);
            wv.loadUrl("https://www.youtube.com/embed/" + id
                    + "?autoplay=1&mute=1&playsinline=1&rel=0&controls=0&enablejsapi=1", intestazioni);
        } else if (id != null) {
            // pagina locale con l'iframe YouTube: l'indirizzo di base evita l'errore 153
            // a tutto schermo la pagina si dichiara larga 1920 px: YouTube vede un player Full HD
            String viewport = (i == singolo) ? "width=1920" : "width=device-width, initial-scale=1";
            wv.loadDataWithBaseURL(BASE_YT,
                    playerHtml.replace("__ID__", id).replace("__VIEWPORT__", viewport)
                            // a tutto schermo chiede anche esplicitamente l'HD (YouTube puo' ignorarlo)
                            .replace("__QUALITA__", i == singolo ? "&vq=hd1080" : ""),
                    "text/html", "utf-8", null);
        } else {
            wv.loadUrl(url);
        }
    }

    private void ricaricaTutto() {
        handler.removeCallbacks(ricaricaPeriodica);
        caricaScaglionato();
        programmaRicarica();
    }

    /**
     * Carica i riquadri uno alla volta, a 2 secondi di distanza: avviare quattro video nello stesso
     * istante sovraccarica i decoder video della chiavetta (immagini bloccate o duplicate).
     */
    private void caricaScaglionato() {
        for (int i = 0; i < viste.size(); i++) {
            final int indice = i;
            viste.get(i).stopLoading();
            viste.get(i).loadUrl("about:blank");      // libera subito il decoder del video precedente
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    carica(indice);
                }
            }, 300L + 2000L * i);
        }
    }

    private void programmaRicarica() {
        handler.removeCallbacks(ricaricaPeriodica);
        if (ricaricaMs > 0) handler.postDelayed(ricaricaPeriodica, ricaricaMs);
    }

    /**
     * Dopo 6 secondi controlla se il player YouTube aperto direttamente mostra un errore
     * (es. 153): in quel caso quel riquadro torna per sempre al metodo con l'iframe.
     */
    private void controllaErroreYoutube(final WebView view) {
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                final int i = viste.indexOf(view);
                if (i < 0) return;
                view.evaluateJavascript("(function(){var e=document.querySelector('.ytp-error');"
                        + "return !!(e && e.offsetParent !== null);})()", new android.webkit.ValueCallback<String>() {
                    @Override
                    public void onReceiveValue(String risultato) {
                        if ("true".equals(risultato) && !youtubeSoloIframe.contains(i)) {
                            youtubeSoloIframe.add(i);
                            carica(i);
                        }
                    }
                });
            }
        }, 6000);
    }

    /** Identificativo di Chrome per computer con la stessa versione di Chrome del WebView. */
    private static String agenteDesktop(String agenteAndroid) {
        Matcher m = Pattern.compile("Chrome/([\\d.]+)").matcher(agenteAndroid == null ? "" : agenteAndroid);
        String versione = m.find() ? m.group(1) : "90.0.4430.91";
        return "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + versione + " Safari/537.36";
    }

    private static String youtubeId(String url) {
        Matcher m = YT_ID.matcher(url);
        if (m.find() && (url.contains("youtube.com") || url.contains("youtu.be"))) return m.group(1);
        return null;
    }

    // ---------------------------------------------------------------- telecomando

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int k = e.getKeyCode();
        boolean gestito = k == KeyEvent.KEYCODE_DPAD_LEFT || k == KeyEvent.KEYCODE_DPAD_RIGHT
                || k == KeyEvent.KEYCODE_DPAD_UP || k == KeyEvent.KEYCODE_DPAD_DOWN
                || k == KeyEvent.KEYCODE_DPAD_CENTER || k == KeyEvent.KEYCODE_ENTER
                || k == KeyEvent.KEYCODE_NUMPAD_ENTER || k == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
                || k == KeyEvent.KEYCODE_MEDIA_PLAY || k == KeyEvent.KEYCODE_MENU
                || k == KeyEvent.KEYCODE_MEDIA_FAST_FORWARD
                || (k == KeyEvent.KEYCODE_BACK && singolo >= 0);
        if (!gestito || viste.isEmpty()) return super.dispatchKeyEvent(e);
        if (e.getAction() != KeyEvent.ACTION_DOWN || e.getRepeatCount() > 0) return true;

        int n = viste.size();
        switch (k) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                if (singolo < 0 && scelto % colonne > 0) scelto--;
                break;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                if (singolo < 0 && scelto % colonne < colonne - 1 && scelto + 1 < n) scelto++;
                break;
            case KeyEvent.KEYCODE_DPAD_UP:
                if (singolo < 0 && scelto - colonne >= 0) scelto -= colonne;
                break;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                if (singolo < 0 && scelto + colonne < n) scelto += colonne;
                break;
            case KeyEvent.KEYCODE_BACK:
                vistaSingola(-1);
                break;
            case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
            case KeyEvent.KEYCODE_MEDIA_PLAY:
            case KeyEvent.KEYCODE_MENU:
                carica(scelto);
                break;
            case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                ricaricaTutto();
                break;
            default: // OK
                vistaSingola(singolo >= 0 ? -1 : scelto);
                break;
        }
        mostraEvidenza(true);
        return true;
    }

    private void vistaSingola(int indice) {
        final int precedente = singolo;
        singolo = indice;
        // Con "ricarica_zoom": true il video YouTube riparte quando si ingrandisce (qualita' alta)
        // e quando si torna alla griglia. Con false (predefinito) continua senza interruzioni.
        final int daRicaricare = indice >= 0 ? indice : precedente;
        if (ricaricaZoom && daRicaricare >= 0 && daRicaricare < urls.size() && youtubeId(urls.get(daRicaricare)) != null) {
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    carica(daRicaricare);
                }
            }, 300);
        }
        for (int i = 0; i < riquadri.size(); i++) {
            riquadri.get(i).setVisibility(indice < 0 || i == indice ? View.VISIBLE : View.GONE);
        }
        for (int r = 0; r < righe.size(); r++) {
            boolean contiene = indice < 0 || indice / colonne == r;
            righe.get(r).setVisibility(contiene ? View.VISIBLE : View.GONE);
        }
        if (indice >= 0) {
            // nella vista singola i riempitivi dell'ultima riga non devono rubare spazio
            LinearLayout riga = righe.get(indice / colonne);
            for (int c = 0; c < riga.getChildCount(); c++) {
                View v = riga.getChildAt(c);
                if (!riquadri.contains(v)) v.setVisibility(View.GONE);
            }
        } else {
            for (LinearLayout riga : righe) {
                for (int c = 0; c < riga.getChildCount(); c++) riga.getChildAt(c).setVisibility(View.VISIBLE);
            }
        }
    }

    /** Bordo arancione e nome sul riquadro scelto; spariscono dopo 4 secondi. */
    private void mostraEvidenza(boolean attiva) {
        for (int i = 0; i < riquadri.size(); i++) {
            boolean qui = attiva && i == scelto && singolo < 0;
            FrameLayout box = riquadri.get(i);
            if (qui) {
                GradientDrawable bordo = new GradientDrawable();
                bordo.setColor(Color.TRANSPARENT);
                bordo.setStroke(6, 0xFFFF8A00);
                box.setForeground(bordo);
            } else {
                box.setForeground(null);
            }
            etichette.get(i).setVisibility(attiva && i == scelto ? View.VISIBLE : View.GONE);
        }
        handler.removeCallbacks(nascondiEvidenza);
        if (attiva) handler.postDelayed(nascondiEvidenza, 4000);
    }

    // ---------------------------------------------------------------- ciclo di vita

    private void nascondiBarre() {
        getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) nascondiBarre();
    }

    @Override
    protected void onResume() {
        super.onResume();
        for (WebView wv : viste) wv.onResume();
        if (eraFermo) {           // tornati dalla modalita' quadro, dallo standby o da un'altra app
            eraFermo = false;
            handler.postDelayed(ricaricaPeriodica, 500);
        }
    }

    @Override
    protected void onPause() {
        for (WebView wv : viste) wv.onPause();
        CookieManager.getInstance().flush();   // salva il consenso dato
        super.onPause();
    }

    @Override
    protected void onStop() {
        eraFermo = true;
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        try {
            unregisterReceiver(schermoAcceso);
        } catch (Exception ignorato) {
            // non registrato
        }
        for (WebView wv : viste) wv.destroy();
        super.onDestroy();
    }

    private String leggiAsset(String nome) throws Exception {
        try (InputStream in = getAssets().open(nome)) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) != -1) out.write(buf, 0, r);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
