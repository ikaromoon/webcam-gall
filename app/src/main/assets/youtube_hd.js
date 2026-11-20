(function(){
  if (window.__wcYoutube) return;          // una volta sola per pagina
  window.__wcYoutube = true;
  var HD = __HD__;                         // true: riquadro ingrandito, forza 1080p

  // Tiene il player YouTube in riproduzione, muto e sulla diretta (non ore indietro).
  // Interviene solo quando serve davvero, per non provocare riavvii inutili.
  var inizio = Date.now();
  var fermoDa = 0;                         // da quando risulta fermo (ms), 0 = sta andando
  var ultimoSalto = 0;                     // ultimo riposizionamento sulla diretta

  function controlla(){
    var p = document.getElementById('movie_player');
    if (!p || !p.getPlayerState) return;
    var adesso = Date.now();
    try { if (!p.isMuted || !p.isMuted()) p.mute(); } catch (e) {}

    // 1) qualita': la imposta solo se non e' gia' 1080p
    if (HD) {
      try {
        if (p.getPlaybackQuality() !== 'hd1080' &&
            p.getAvailableQualityLevels().indexOf('hd1080') >= 0) {
          p.setPlaybackQualityRange('hd1080', 'hd1080');
        }
      } catch (e) {}
    }

    // 2) riproduzione: 1 = va, 3 = sta caricando. Riparte solo se fermo da oltre 8 secondi.
    var stato = -1;
    try { stato = p.getPlayerState(); } catch (e) {}
    if (stato === 1 || stato === 3) {
      fermoDa = 0;
    } else {
      if (!fermoDa) fermoDa = adesso;
      if (adesso - fermoDa > 8000) { try { p.playVideo(); } catch (e) {} fermoDa = adesso; }
    }

    // 3) diretta: se e' indietro di oltre 3 minuti torna al momento attuale.
    //    Se YouTube dice esplicitamente "non sei sulla diretta" lo fa subito;
    //    altrimenti (stima dalla durata) aspetta 15 s. Al massimo una volta ogni 2 minuti.
    if (stato === 1 && adesso - ultimoSalto > 120000) {
      try {
        var fine, pos, inDiretta = false, sicuro = false;
        var ps = p.getProgressState ? p.getProgressState() : null;
        if (ps && typeof ps.seekableEnd === 'number' && typeof ps.isAtLiveHead === 'boolean') {
          fine = ps.seekableEnd; pos = ps.current; inDiretta = ps.isAtLiveHead; sicuro = true;
        } else {
          fine = p.getDuration(); pos = p.getCurrentTime();
        }
        if (!inDiretta && fine > 0 && fine - pos > 180 && (sicuro || adesso - inizio > 15000)) {
          p.seekTo(fine, true);
          ultimoSalto = adesso;
        }
      } catch (e) {}
    }
  }

  // subito, poi ogni 3 s per il primo minuto, poi ogni 30 s
  var n = 0;
  var t = setInterval(function(){
    controlla();
    if (++n > 20) { clearInterval(t); setInterval(controlla, 30000); }
  }, 3000);
})();
