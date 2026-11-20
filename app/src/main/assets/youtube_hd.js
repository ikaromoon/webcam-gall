(function(){
  if (window.__wcYoutube) return;          // una volta sola per pagina
  window.__wcYoutube = true;
  // Player YouTube aperto come pagina principale del riquadro ingrandito:
  // forza la qualita' 1080p (YouTube da solo sceglie 360p) e tiene il video in riproduzione muto.
  var n = 0;
  var t = setInterval(function(){
    var p = document.getElementById('movie_player');
    if (p && p.setPlaybackQualityRange) {
      try { p.mute(); } catch (e) {}
      try { p.setPlaybackQualityRange('hd1080', 'hd1080'); } catch (e) {}
      try { if (p.getPlayerState && p.getPlayerState() !== 1) p.playVideo(); } catch (e) {}
    }
    if (++n > 15) clearInterval(t);          // ripete per 30 s: YouTube a volte riparte in automatico
  }, 2000);
})();
