package it.gallipoli.webcam;

import android.net.Uri;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Una sessione (un "barattolo" di cookie) riservata a un solo riquadro.
 *
 * Tutti i WebView di un'app Android condividono gli stessi cookie: per i siti indicati
 * le richieste GET vengono quindi scaricate dall'app, con i cookie di questo riquadro,
 * e consegnate al WebView. I cookie impostati dal server restano qui e non finiscono
 * nel barattolo comune, così due riquadri dello stesso sito sono due visitatori distinti.
 */
final class SessioneSeparata {

    private static final String[] DOMINI = {"skylinewebcams.com"};
    private static final int MAX_REDIRECT = 5;

    // file statici: non dipendono dalla sessione, li scarica il WebView da solo (in parallelo, senza code)
    private static final java.util.regex.Pattern STATICI = java.util.regex.Pattern.compile(
            ".*\\.(png|jpe?g|gif|webp|avif|svg|ico|css|js|mjs|woff2?|ttf|otf|eot|json|map)$");

    private final Map<String, String> cookie = new ConcurrentHashMap<>();

    static boolean riguarda(Uri url) {
        String host = url.getHost();
        if (host == null) return false;
        host = host.toLowerCase(Locale.ROOT);
        for (String d : DOMINI) {
            if (host.equals(d) || host.endsWith("." + d)) return true;
        }
        return false;
    }

    /** Toglie dal barattolo comune i cookie dei siti gestiti (rimasti da versioni precedenti). */
    static void pulisciCookieComuni() {
        CookieManager cm = CookieManager.getInstance();
        for (String d : DOMINI) {
            String[] indirizzi = {"https://" + d, "https://www." + d};
            for (String indirizzo : indirizzi) {
                String elenco = cm.getCookie(indirizzo);
                if (elenco == null) continue;
                for (String parte : elenco.split(";")) {
                    String nome = parte.split("=", 2)[0].trim();
                    if (nome.isEmpty()) continue;
                    cm.setCookie(indirizzo, nome + "=; Max-Age=0; Path=/");
                    cm.setCookie(indirizzo, nome + "=; Max-Age=0; Path=/; Domain=." + d);
                }
            }
        }
        cm.flush();
    }

    /** Restituisce la risposta scaricata con i cookie di questo riquadro, oppure null per lasciar fare al WebView. */
    WebResourceResponse scarica(WebResourceRequest richiesta) {
        if (!"GET".equalsIgnoreCase(richiesta.getMethod())) return null;
        if (!riguarda(richiesta.getUrl())) return null;
        if (!richiesta.isForMainFrame()) {
            // passano dall'app solo la pagina e il flusso video (playlist e segmenti)
            String host = richiesta.getUrl().getHost() == null ? "" : richiesta.getUrl().getHost().toLowerCase(Locale.ROOT);
            String percorso = richiesta.getUrl().getPath() == null ? "" : richiesta.getUrl().getPath().toLowerCase(Locale.ROOT);
            if (host.startsWith("cdn.") || STATICI.matcher(percorso).matches()) return null;
        }
        try {
            String url = richiesta.getUrl().toString();
            for (int salto = 0; salto <= MAX_REDIRECT; salto++) {
                HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
                c.setInstanceFollowRedirects(false);
                c.setConnectTimeout(15000);
                c.setReadTimeout(30000);
                Map<String, String> intestazioni = richiesta.getRequestHeaders();
                if (intestazioni != null) {
                    for (Map.Entry<String, String> h : intestazioni.entrySet()) {
                        String k = h.getKey().toLowerCase(Locale.ROOT);
                        // niente richieste condizionali (304) e niente compressione manuale
                        if (k.equals("cookie") || k.equals("if-none-match") || k.equals("if-modified-since")
                                || k.equals("accept-encoding") || k.equals("range") && salto > 0) continue;
                        c.setRequestProperty(h.getKey(), h.getValue());
                    }
                }
                String intestazioneCookie = cookiePer(url);
                if (!intestazioneCookie.isEmpty()) c.setRequestProperty("Cookie", intestazioneCookie);

                int codice = c.getResponseCode();
                salvaCookie(c.getHeaderFields());

                if (codice >= 300 && codice <= 399) {
                    String dove = c.getHeaderField("Location");
                    c.disconnect();
                    if (dove == null) return null;
                    url = new URL(new URL(url), dove).toString();
                    if (!riguarda(Uri.parse(url))) return null;   // redirect verso un altro sito: ci pensa il WebView
                    continue;
                }
                return risposta(c, codice);
            }
        } catch (Exception e) {
            // in caso di problemi lascia che sia il WebView a scaricare normalmente
        }
        return null;
    }

    private String cookiePer(String url) {
        // cookie comuni (es. consenso impostato dalla pagina) + quelli di questo riquadro, che hanno la precedenza
        Map<String, String> tutti = new LinkedHashMap<>();
        String comuni = CookieManager.getInstance().getCookie(url);
        if (comuni != null) {
            for (String parte : comuni.split(";")) {
                String[] nv = parte.trim().split("=", 2);
                if (nv[0].isEmpty()) continue;
                tutti.put(nv[0], nv.length > 1 ? nv[1] : "");
            }
        }
        tutti.putAll(cookie);
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, String> e : tutti.entrySet()) {
            if (sb.length() > 0) sb.append("; ");
            sb.append(e.getKey()).append('=').append(e.getValue());
        }
        return sb.toString();
    }

    private void salvaCookie(Map<String, List<String>> campi) {
        if (campi == null) return;
        for (Map.Entry<String, List<String>> campo : campi.entrySet()) {
            if (campo.getKey() == null || !campo.getKey().equalsIgnoreCase("Set-Cookie")) continue;
            for (String valore : campo.getValue()) {
                String[] parti = valore.split(";");
                String[] nv = parti[0].trim().split("=", 2);
                if (nv[0].isEmpty()) continue;
                String v = nv.length > 1 ? nv[1] : "";
                boolean scaduto = v.isEmpty();
                for (int i = 1; i < parti.length; i++) {
                    String a = parti[i].trim().toLowerCase(Locale.ROOT);
                    if (a.equals("max-age=0") || a.startsWith("max-age=-")) scaduto = true;
                }
                if (scaduto) cookie.remove(nv[0]);
                else cookie.put(nv[0], v);
            }
        }
    }

    private static WebResourceResponse risposta(HttpURLConnection c, int codice) throws Exception {
        String tipo = c.getContentType();
        String mime = "application/octet-stream";
        String charset = null;
        if (tipo != null) {
            String[] parti = tipo.split(";");
            mime = parti[0].trim();
            for (int i = 1; i < parti.length; i++) {
                String p = parti[i].trim();
                if (p.toLowerCase(Locale.ROOT).startsWith("charset=")) charset = p.substring(8).replace("\"", "");
            }
        }
        Map<String, String> intestazioni = new HashMap<>();
        for (Map.Entry<String, List<String>> campo : c.getHeaderFields().entrySet()) {
            String k = campo.getKey();
            if (k == null || campo.getValue() == null || campo.getValue().isEmpty()) continue;
            String kl = k.toLowerCase(Locale.ROOT);
            if (kl.equals("set-cookie") || kl.equals("content-encoding") || kl.equals("content-length")
                    || kl.equals("transfer-encoding") || kl.equals("connection")) continue;
            intestazioni.put(k, campo.getValue().get(0));
        }
        InputStream corpo = codice >= 400 ? c.getErrorStream() : c.getInputStream();
        if (corpo == null) corpo = new ByteArrayInputStream(new byte[0]);
        String motivo = c.getResponseMessage();
        if (motivo == null || motivo.trim().isEmpty()) motivo = "OK";
        if (codice < 100 || codice > 599) codice = 200;
        return new WebResourceResponse(mime, charset, codice, motivo, intestazioni, corpo);
    }
}
