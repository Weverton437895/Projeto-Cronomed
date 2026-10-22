// Preferencias de acessibilidade e alertas deste navegador (a documentacao pede estes ajustes na tela Configuracoes).
const KEY = 'cronomed.prefs.v1';

export const DEFAULT_PREFS = {
  highContrast: false,
  fontSize: 'normal', // normal | large | xlarge
  voiceNarration: false,
  voiceCommand: false,
  stockDays: 5,
  doseReminders: true,
  batteryAlert: true,
};

export function loadPrefs() {
  try {
    const raw = localStorage.getItem(KEY);
    if (!raw) return { ...DEFAULT_PREFS };
    return { ...DEFAULT_PREFS, ...JSON.parse(raw) };
  } catch {
    return { ...DEFAULT_PREFS };
  }
}

export function savePrefs(prefs) {
  try {
    localStorage.setItem(KEY, JSON.stringify(prefs));
  } catch {
    /* navegador bloqueou o armazenamento: as preferencias valem so nesta sessao */
  }
}

export function applyPrefs(prefs) {
  const root = document.documentElement;
  root.dataset.contrast = prefs.highContrast ? 'high' : 'normal';
  root.dataset.font = prefs.fontSize;
}
