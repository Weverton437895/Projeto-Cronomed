// Regras de apresentacao das doses (status do backend -> rotulo/tom na interface).
import { minutesBetween } from './format.js';

export const REMINDER_INTERVAL_MIN = 5;
export const REMINDER_MAX = 12;
export const TOLERANCE_MIN = 60;

/** Rotulo e tom do selo de uma dose na tela "Proximas Doses". */
export function doseBadge(dose, now = new Date()) {
  switch (dose.status) {
    case 'TAKEN':
      return { tone: 'success', icon: 'check', label: 'Confirmada' };
    case 'OPENED_UNCONFIRMED':
      return { tone: 'warning', icon: 'clock', label: 'Sem confirmação' };
    case 'MISSED':
      return { tone: 'danger', icon: 'alert', label: 'Perdida' };
    case 'DECLINED':
      return { tone: 'danger', icon: 'x', label: 'Não tomada' };
    case 'CANCELLED':
      return { tone: 'neutral', icon: 'x', label: 'Cancelada' };
    default: {
      const diff = minutesBetween(dose.scheduledAt, now);
      if (diff > 60) return { tone: 'soft', icon: 'clock', label: 'Agendada' };
      if (diff > 0) return { tone: 'warning', icon: 'clock', label: `Em ${diff} min` };
      return { tone: 'warning', icon: 'bell', label: 'Hora da dose' };
    }
  }
}

/** Rotulo e tom do resultado na tela de historico. */
export function resultBadge(dose) {
  switch (dose.status) {
    case 'TAKEN':
      return { tone: 'success', label: 'Tomada' };
    case 'OPENED_UNCONFIRMED':
      return { tone: 'warning', label: 'Sem confirmação' };
    case 'MISSED':
      return { tone: 'danger', label: 'Perdida' };
    case 'DECLINED':
      return { tone: 'danger', label: 'Recusada' };
    case 'CANCELLED':
      return { tone: 'neutral', label: 'Cancelada' };
    default:
      return { tone: 'soft', label: 'Pendente' };
  }
}

/** Uma dose esta "no ar" (alarme/lembrete) entre o horario e o fim da tolerancia. */
export function isDue(dose, now = new Date()) {
  if (dose.status !== 'PENDING' && dose.status !== 'OPENED_UNCONFIRMED') return false;
  const elapsed = minutesBetween(now, dose.scheduledAt);
  return elapsed >= 0 && elapsed <= TOLERANCE_MIN;
}

/** "Lembrete 2 de 12 · proximo em 5 min", calculado a partir do horario previsto. */
export function reminderInfo(dose, now = new Date()) {
  const elapsed = Math.max(0, minutesBetween(now, dose.scheduledAt));
  const index = Math.min(REMINDER_MAX, Math.floor(elapsed / REMINDER_INTERVAL_MIN) + 1);
  const nextIn = REMINDER_INTERVAL_MIN - (elapsed % REMINDER_INTERVAL_MIN);
  return { index, total: REMINDER_MAX, nextIn, last: index >= REMINDER_MAX };
}

/** Aviso de estoque (faixa amarela da tela inicial). */
export function stockAlertMessage(med) {
  if (med.stockStatus === 'ZERO') return { bold: med.name, text: ': o estoque acabou!' };
  const d = med.daysRemaining;
  return {
    bold: med.name,
    text: d === null || d === undefined ? ': estoque baixo!' : `, que acabará em ${d} ${d === 1 ? 'dia' : 'dias'}!`,
  };
}

/** Selo e cor da barra de estoque. */
export function stockTone(med) {
  if (med.stockStatus === 'ZERO') return { tone: 'danger', bar: 'danger', label: 'Acabou' };
  if (med.stockStatus === 'LOW') {
    const d = med.daysRemaining;
    if (d !== null && d !== undefined && d <= 3) return { tone: 'danger', bar: 'danger', label: 'Acabando' };
    return { tone: 'warning', bar: 'warning', label: 'Atenção' };
  }
  return { tone: 'success', bar: 'primary', label: 'Em dia' };
}
