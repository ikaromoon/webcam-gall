(function(){
  if (window.__webcamAuto) return;          // non avviare lo script due volte nella stessa pagina
  window.__webcamAuto = true;

  var NASCONDI = __NASCONDI__;
  var RIEMPI = __RIEMPI__;                   // true: il player riempie tutto il riquadro

  // 0) stili: elementi nascosti + modalita' "riempi"
  var regole = NASCONDI.map(function(sel){ return sel + '{display:none!important}'; });
  if (RIEMPI) regole.push(
    'html.wc-riempi,html.wc-riempi body{overflow:hidden!important}',
    '[data-wc-player]{position:fixed!important;left:0!important;top:0!important;right:0!important;bottom:0!important;' +
      'width:100vw!important;height:100vh!important;max-width:none!important;max-height:none!important;' +
      'margin:0!important;padding:0!important;z-index:2147483000!important;background:#000!important;transform:none!important}',
    '[data-wc-player]>div,[data-wc-player] [data-container]{width:100%!important;height:100%!important}',
    '[data-wc-player] video{width:100%!important;height:100%!important;object-fit:contain!important;background:#000!important}'
  );
  var css = document.createElement('style');
  css.textContent = regole.join('\n');
  (document.head || document.documentElement).appendChild(css);

  // 1) consenso cookie: cerca il pulsante "Acconsento" (o simili) e lo clicca UNA volta
  var TESTI = /^(acconsento|accetta|accetto|accetta tutto|consent|accept|accept all|i agree)$/i;
  function cliccaConsenso(){
    var sel = document.querySelector('.fc-cta-consent, button[aria-label="Acconsento"], button[aria-label="Consent"]');
    if (sel) { sel.click(); return true; }
    var bottoni = document.querySelectorAll('button, a[role=button], [role=button]');
    for (var i = 0; i < bottoni.length; i++) {
      if (TESTI.test((bottoni[i].textContent || '').trim())) { bottoni[i].click(); return true; }
    }
    return false;
  }
  var n = 0;
  var tc = setInterval(function(){ if (cliccaConsenso() || ++n > 20) clearInterval(tc); }, 500);

  // 2) porta in vista il player
  function player(){
    var p = document.querySelector('div[data-player]');
    if (p) return p;
    var v = document.querySelector('video');
    return v ? v.parentElement : null;
  }

  // 2a) modalita' "riempi": il player viene fissato a tutto riquadro, una volta sola.
  //     Niente zoom e niente riallineamenti continui (facevano ricalcolare la pagina e il video).
  var fallbackZoom = false;
  function riempi(){
    var p = player();
    if (!p) return false;
    if (!p.hasAttribute('data-wc-player')) {
      var vecchio = document.querySelector('[data-wc-player]');
      if (vecchio && vecchio !== p) vecchio.removeAttribute('data-wc-player');
      p.setAttribute('data-wc-player', '');
      document.documentElement.classList.add('wc-riempi');
      window.scrollTo(0, 0);
      // se un elemento della pagina impedisce il fissaggio, ripiega sullo zoom
      setTimeout(function(){
        var r = p.getBoundingClientRect();
        if (r.width < window.innerWidth * 0.9 || r.top > 5) { fallbackZoom = true; adattaZoom(); }
      }, 1500);
    }
    return true;
  }

  // 2b) alternativa: zoom calcolato senza azzerarlo (niente sfarfallio), applicato solo se cambia
  function adattaZoom(){
    var p = player();
    if (!p) return;
    var root = document.documentElement;
    var zAttuale = parseFloat(root.style.zoom) || 1;
    var r = p.getBoundingClientRect();
    if (!r.width || !r.height) return;
    var zNuovo = Math.min(1, zAttuale * Math.min(window.innerWidth / r.width, window.innerHeight / r.height));
    if (Math.abs(zNuovo - zAttuale) > 0.02) root.style.zoom = zNuovo;
    p.scrollIntoView({block: 'start', inline: 'nearest'});
  }

  function sistema(){ if (RIEMPI && !fallbackZoom) riempi(); else adattaZoom(); }

  // nei primi 40 s (pubblicita' e player si caricano in ritardo) controlla ogni secondo,
  // poi solo ogni 30 s se il sito ha ricreato il player
  var k = 0;
  var ta = setInterval(function(){
    sistema();
    if (++k > 40) {
      clearInterval(ta);
      setInterval(function(){ var p = player(); if (p && RIEMPI && !fallbackZoom && !p.hasAttribute('data-wc-player')) riempi(); }, 30000);
    }
  }, 1000);
  document.addEventListener('visibilitychange', function(){ if (!document.hidden) setTimeout(sistema, 1000); });

  // 2c) finestre promozionali con il loro pulsante di chiusura (✕): le chiude come faresti tu.
  //     Guarda solo pulsanti piccoli dentro riquadri in sovrimpressione; legge textContent
  //     (non innerText, che obbliga il browser a ricalcolare la pagina).
  function inSovrimpressione(el){
    for (var e = el; e && e !== document.body; e = e.parentElement) {
      var cs = getComputedStyle(e);
      if (cs.position === 'fixed' && (parseInt(cs.zIndex, 10) || 0) >= 10) return true;
    }
    return false;
  }
  function chiudiPopup(){
    var candidati = document.querySelectorAll(
      'button, [role=button], [aria-label], [title], [class*=close], [class*=Close], [id*=close]');
    for (var i = 0; i < candidati.length; i++) {
      var el = candidati[i];
      if (el.childElementCount > 1 || el.closest('[data-wc-player]')) continue;
      var testo = (el.textContent || '').trim();
      if (testo.length > 1) continue;
      var etichetta = (el.getAttribute('aria-label') || el.getAttribute('title') || '').toLowerCase();
      var classe = (typeof el.className === 'string' ? el.className : '').toLowerCase() + ' ' + (el.id || '').toLowerCase();
      var eChiudi = /^[×✕✖xX]$/.test(testo) || /^(chiudi|close|dismiss)$/.test(etichetta)
                    || /(^|[\s_-])close([\s_-]|$)/.test(classe);
      if (eChiudi && el.getClientRects().length && inSovrimpressione(el)) { el.click(); return true; }
    }
    return false;
  }
  // ogni 2 s per i primi 2 minuti, poi ogni 15 s
  var cp = 0;
  var tcp = setInterval(function(){
    chiudiPopup();
    if (++cp > 60) { clearInterval(tcp); setInterval(chiudiPopup, 15000); }
  }, 2000);

  // 3) avvio del video: se il player mostra il grande tasto play, lo preme (ogni 3 s, max 1 minuto).
  //    Il tasto c'e' solo quando il video e' fermo, quindi non mette mai in pausa.
  function avvia(){
    var tp = 0;
    var tplay = setInterval(function(){
      var v = document.querySelector('div[data-player] video');
      var fermo = !v || v.paused;
      var poster = document.querySelector('div[data-player] .player-poster, div[data-player] [data-poster]');
      var visibile = poster && poster.getClientRects().length && getComputedStyle(poster).display !== 'none';
      if (fermo && visibile) { poster.click(); var w = poster.querySelector('.play-wrapper'); if (w) w.click(); }
      else if (v && v.paused) { v.play().catch(function(){}); }
      if ((v && !v.paused) || ++tp > 20) clearInterval(tplay);
    }, 3000);
  }
  avvia();
  document.addEventListener('visibilitychange', function(){ if (!document.hidden) avvia(); });
})();
