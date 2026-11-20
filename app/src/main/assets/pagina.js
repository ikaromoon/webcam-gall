(function(){
  if (window.__webcamAuto) return;          // non avviare lo script due volte nella stessa pagina
  window.__webcamAuto = true;

  // 0) nasconde gli elementi della lista NASCONDI
  const css = document.createElement('style');
  css.textContent = __NASCONDI__.map(function(sel){ return sel + '{display:none!important}'; }).join('\n');
  (document.head || document.documentElement).appendChild(css);

  // 1) consenso cookie: cerca il pulsante "Acconsento" (o simili) e lo clicca UNA volta
  const TESTI = /^(acconsento|accetta|accetto|accetta tutto|consent|accept|accept all|i agree)$/i;
  function cliccaConsenso(){
    const sel = document.querySelector('.fc-cta-consent, button[aria-label="Acconsento"], button[aria-label="Consent"]');
    if (sel) { sel.click(); return true; }
    for (const b of document.querySelectorAll('button, a[role=button], [role=button]')) {
      if (TESTI.test((b.innerText || b.textContent || '').trim())) { b.click(); return true; }
    }
    return false;
  }
  let n = 0;
  const tc = setInterval(function(){ if (cliccaConsenso() || ++n > 20) clearInterval(tc); }, 500);

  // 2) tiene la webcam in cima alla finestra e la rimpicciolisce (zoom) se non ci sta
  function player(){
    const p = document.querySelector('div[data-player]');
    if (p) return p;
    const v = document.querySelector('video');
    return v ? v.parentElement : null;
  }
  function adatta(){
    const p = player();
    if (!p) return;
    const root = document.documentElement;
    root.style.zoom = 1;
    const r = p.getBoundingClientRect();
    if (!r.width || !r.height) return;
    const z = Math.min(1, window.innerWidth / r.width, window.innerHeight / r.height);
    root.style.zoom = z;
    p.scrollIntoView({block: 'start', inline: 'nearest'});
  }
  // durante il caricamento la pubblicita' sposta la pagina: riallinea ogni secondo per 40 s,
  // a meno che tu non scorra la pagina a mano
  let manuale = false;
  ['wheel', 'touchstart', 'keydown', 'mousedown'].forEach(function(e){
    window.addEventListener(e, function(ev){ if (!(ev.target.closest && ev.target.closest('div[data-player]'))) manuale = true; }, {passive: true});
  });
  let k = 0;
  const ta = setInterval(function(){ if (!manuale) adatta(); if (++k > 40) clearInterval(ta); }, 1000);
  // quando ridimensioni la finestra
  let tr;
  window.addEventListener('resize', function(){ clearTimeout(tr); tr = setTimeout(function(){ manuale = false; adatta(); }, 300); });

  // 2b) finestre promozionali con il loro pulsante di chiusura (✕): le chiude come faresti tu.
  //     Agisce solo su riquadri in sovrimpressione (position: fixed) e solo sul loro tasto "chiudi".
  function inSovrimpressione(el){
    for (let e = el; e && e !== document.body; e = e.parentElement) {
      const cs = getComputedStyle(e);
      if (cs.position === 'fixed' && parseInt(cs.zIndex || 0, 10) >= 10) return true;
    }
    return false;
  }
  function chiudiPopup(){
    const candidati = document.querySelectorAll(
      'button, [role=button], a, span, div, .close, [class*=close], [aria-label]');
    for (const el of candidati) {
      if (!el.offsetParent && getComputedStyle(el).position !== 'fixed') continue;   // non visibile
      const testo = (el.innerText || el.textContent || '').trim();
      const etichetta = (el.getAttribute('aria-label') || el.getAttribute('title') || '').toLowerCase();
      const classe = (typeof el.className === 'string' ? el.className : '').toLowerCase();
      const eChiudi = /^[×✕✖xX]$/.test(testo) || /^(chiudi|close|dismiss)$/.test(etichetta)
                      || (/(^|[\s_-])close([\s_-]|$)/.test(classe) && testo.length <= 1);
      if (eChiudi && el.children.length <= 1 && inSovrimpressione(el)) { el.click(); return true; }
    }
    return false;
  }
  // controlla ogni 2 secondi (la promozione puo' comparire anche dopo un po')
  setInterval(chiudiPopup, 2000);

  // 3) avvio del video: se il player mostra il grande tasto play, lo preme (ogni 3 s, max 1 minuto).
  //    Il tasto c'e' solo quando il video e' fermo, quindi non mette mai in pausa.
  let tp = 0;
  const tplay = setInterval(function(){
    const v = document.querySelector('div[data-player] video');
    const fermo = !v || v.paused;
    const poster = document.querySelector('div[data-player] .player-poster, div[data-player] [data-poster]');
    const visibile = poster && poster.offsetParent !== null && getComputedStyle(poster).display !== 'none';
    if (fermo && visibile) { poster.click(); const w = poster.querySelector('.play-wrapper'); if (w) w.click(); }
    else if (v && v.paused) { v.play().catch(function(){}); }
    if ((v && !v.paused) || ++tp > 20) clearInterval(tplay);
  }, 3000);
})();
