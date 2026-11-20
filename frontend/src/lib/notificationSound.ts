"use client";

/**
 * Joue un son synthetique de notification ("ding" deux notes) via Web Audio API.
 *
 * <p>Choix : pas de fichier audio externe a embarquer (pas de 404 possible, pas
 * de download bloquant, pas d'asset a maintenir). Le son est genere a la volee
 * par deux oscillateurs successifs, propre et leger.</p>
 *
 * <p>Auto-ferme l'AudioContext apres la lecture pour ne pas accumuler de contexts.</p>
 *
 * <p>Silencieux si le navigateur bloque l'autoplay (politique navigateur :
 * le user doit avoir interagi au moins une fois avec la page). Le premier
 * appel apres un clic active le contexte audio pour les suivants.</p>
 */
export function jouerSonNotification(): void {
  if (typeof window === "undefined") return;
  try {
    const AudioCtxClass = window.AudioContext
      || (window as unknown as { webkitAudioContext?: typeof AudioContext }).webkitAudioContext;
    if (!AudioCtxClass) return;
    const ctx = new AudioCtxClass();

    const jouerNote = (frequence: number, debutOffset: number, dureeS: number) => {
      const osc = ctx.createOscillator();
      const gain = ctx.createGain();
      osc.type = "sine";
      osc.frequency.value = frequence;
      // Enveloppe ADSR douce : attack rapide, release lent (pas de "pop")
      gain.gain.setValueAtTime(0, ctx.currentTime + debutOffset);
      gain.gain.linearRampToValueAtTime(0.12, ctx.currentTime + debutOffset + 0.01);
      gain.gain.exponentialRampToValueAtTime(0.001, ctx.currentTime + debutOffset + dureeS);
      osc.connect(gain);
      gain.connect(ctx.destination);
      osc.start(ctx.currentTime + debutOffset);
      osc.stop(ctx.currentTime + debutOffset + dureeS);
    };

    // "Ding-ding" : note haute puis note plus haute, agreable et discret
    jouerNote(660, 0, 0.18);       // E5
    jouerNote(880, 0.12, 0.22);    // A5

    // Cleanup : ferme le contexte une fois le son joue (480ms = duree totale + marge)
    setTimeout(() => { ctx.close().catch(() => {}); }, 480);
  } catch {
    // Web Audio indisponible / autoplay bloque -> silencieux, on n'embete pas l'utilisateur
  }
}
