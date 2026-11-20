(function(){
  if (window.__wcYoutube) return;          // una volta sola per pagina
  window.__wcYoutube = true;
  var HD = __HD__;                         // true: riquadro ingrandito, forza 1080p

  // Tiene il player YouTube in riproduzione, muto e sulla diretta (non indietro nel tempo).
  // Nel riquadro ingrandito forza anche la qualita' 1080p (da solo YouTube sceglie 360p).
  function controlla(){
    var p = document.getElementById('movie_player');
    if (!p || !p.getPlayerState) return;
    try { p.mute(); } catch (e) {}
    if (HD) { try { p.setPlaybackQualityRange('hd1080', 'hd1080'); } catch (e) {} }
    try {
      var stato = p.getPlayerState();     // 1 = in riproduzione, 3 = caricamento
      if (stato !== 1 && stato !== 3) p.playVideo();
      // diretta: se e' rimasto indietro di oltre 60 s, torna al momento attuale
      var durata = p.getDuration(), ora = p.getCurrentTime();
      if (durata > 0 && durata - ora > 60) p.seekTo(durata, true);
    } catch (e) {}
  }
  // ogni 2 s per il primo minuto, poi ogni 30 s
  var n = 0;
  var t = setInterval(function(){
    controlla();
    if (++n > 30) { clearInterval(t); setInterval(controlla, 30000); }
  }, 2000);
})();
