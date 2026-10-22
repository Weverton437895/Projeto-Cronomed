// Voz pelo navegador (Web Speech API, RF15/RF18). Depende de internet e de navegadores Blink/WebKit.
// Se nao houver suporte ou falhar, o chamador cai no fluxo por toque sem interromper a tarefa.

export function canSpeak() {
  return typeof window !== 'undefined' && 'speechSynthesis' in window;
}

export function canListen() {
  return typeof window !== 'undefined' && !!(window.SpeechRecognition || window.webkitSpeechRecognition);
}

export function speak(text) {
  if (!canSpeak() || !text) return false;
  try {
    window.speechSynthesis.cancel();
    const u = new SpeechSynthesisUtterance(text);
    u.lang = 'pt-BR';
    u.rate = 0.95;
    window.speechSynthesis.speak(u);
    return true;
  } catch {
    return false;
  }
}

export function stopSpeaking() {
  if (canSpeak()) window.speechSynthesis.cancel();
}

/** Escuta uma frase e devolve o texto. Rejeita com Error(code) em falhas (no-support, no-speech, network...). */
export function listenOnce({ lang = 'pt-BR', timeoutMs = 8000 } = {}) {
  return new Promise((resolve, reject) => {
    const Ctor = window.SpeechRecognition || window.webkitSpeechRecognition;
    if (!Ctor) {
      reject(new Error('no-support'));
      return;
    }
    const rec = new Ctor();
    rec.lang = lang;
    rec.interimResults = false;
    rec.maxAlternatives = 3;
    let done = false;
    const finish = (fn, value) => {
      if (done) return;
      done = true;
      clearTimeout(timer);
      try {
        rec.stop();
      } catch {
        /* ignore */
      }
      fn(value);
    };
    const timer = setTimeout(() => finish(reject, new Error('no-speech')), timeoutMs);
    rec.onresult = (ev) => {
      const alts = Array.from(ev.results[0] || []).map((a) => a.transcript);
      finish(resolve, alts.join(' | '));
    };
    rec.onerror = (ev) => finish(reject, new Error(ev.error || 'error'));
    rec.onend = () => finish(reject, new Error('no-speech'));
    try {
      rec.start();
    } catch (e) {
      finish(reject, e);
    }
  });
}

const YES = /\b(sim|tomei|confirmo|confirmado|ja tomei|já tomei)\b/i;
const NO = /\b(n[aã]o|ainda n[aã]o|depois|adiar)\b/i;

export function interpretAnswer(text = '') {
  const t = text.toLowerCase();
  if (NO.test(t) && !/\bsim\b/.test(t)) return 'no';
  if (YES.test(t)) return 'yes';
  return 'unknown';
}
