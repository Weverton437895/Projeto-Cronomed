// Formatacao de datas/numeros em pt-BR, sempre no fuso do paciente (o servidor trabalha em UTC).

export const DEFAULT_TZ = 'America/Sao_Paulo';

function safeTz(tz) {
  try {
    new Intl.DateTimeFormat('pt-BR', { timeZone: tz });
    return tz;
  } catch {
    return DEFAULT_TZ;
  }
}

export function capitalize(s) {
  return s ? s.charAt(0).toUpperCase() + s.slice(1) : s;
}

export function formatTime(iso, tz = DEFAULT_TZ) {
  if (!iso) return '--:--';
  return new Intl.DateTimeFormat('pt-BR', { hour: '2-digit', minute: '2-digit', hourCycle: 'h23', timeZone: safeTz(tz) }).format(new Date(iso));
}

export function formatLongDate(date = new Date(), tz = DEFAULT_TZ) {
  const s = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric', timeZone: safeTz(tz) }).format(date);
  return capitalize(s);
}

export function formatShortDate(iso, tz = DEFAULT_TZ) {
  return new Intl.DateTimeFormat('pt-BR', { day: '2-digit', month: '2-digit', timeZone: safeTz(tz) }).format(new Date(iso));
}

export function ymdInTz(date, tz = DEFAULT_TZ) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-CA', { year: 'numeric', month: '2-digit', day: '2-digit', timeZone: safeTz(tz) })
      .formatToParts(date)
      .map((p) => [p.type, p.value]),
  );
  return `${parts.year}-${parts.month}-${parts.day}`;
}

/** "Hoje 07:00", "19/10 20:00" (relativo ao dia atual no fuso do paciente). */
export function formatDoseWhen(iso, tz = DEFAULT_TZ, now = new Date()) {
  if (!iso) return '';
  const d = new Date(iso);
  const same = ymdInTz(d, tz) === ymdInTz(now, tz);
  return `${same ? 'Hoje' : formatShortDate(iso, tz)} ${formatTime(iso, tz)}`;
}

function tzOffsetMs(date, tz) {
  const parts = Object.fromEntries(
    new Intl.DateTimeFormat('en-US', {
      timeZone: safeTz(tz), hourCycle: 'h23', year: 'numeric', month: '2-digit', day: '2-digit',
      hour: '2-digit', minute: '2-digit', second: '2-digit',
    }).formatToParts(date).map((p) => [p.type, p.value]),
  );
  const asUtc = Date.UTC(+parts.year, +parts.month - 1, +parts.day, +parts.hour, +parts.minute, +parts.second);
  return asUtc - Math.floor(date.getTime() / 1000) * 1000;
}

/** Instante UTC em que o dia de `date` comeca no fuso `tz`. */
export function startOfDayInTz(date, tz = DEFAULT_TZ) {
  const [y, m, d] = ymdInTz(date, tz).split('-').map(Number);
  const guess = Date.UTC(y, m - 1, d, 0, 0, 0);
  let t = guess - tzOffsetMs(new Date(guess), tz);
  t = guess - tzOffsetMs(new Date(t), tz);
  return new Date(t);
}

export function endOfDayInTz(date, tz = DEFAULT_TZ) {
  const start = startOfDayInTz(date, tz);
  const nextDay = startOfDayInTz(new Date(start.getTime() + 36 * 3600 * 1000), tz);
  return new Date(nextDay.getTime() - 1);
}

export function daysAgo(n, now = new Date()) {
  return new Date(now.getTime() - n * 24 * 3600 * 1000);
}

export function minutesBetween(a, b) {
  return Math.round((new Date(a).getTime() - new Date(b).getTime()) / 60000);
}

export function relativeFromNow(iso, now = new Date()) {
  if (!iso) return 'sem sinal ainda';
  const min = Math.max(0, minutesBetween(now, iso));
  if (min < 1) return 'agora';
  if (min < 60) return `há ${min} min`;
  const h = Math.floor(min / 60);
  if (h < 24) return `há ${h} h`;
  return `há ${Math.floor(h / 24)} d`;
}

export function initials(name = '') {
  const parts = name.trim().split(/\s+/).filter(Boolean);
  if (parts.length === 0) return '?';
  if (parts.length === 1) return parts[0].slice(0, 2).toUpperCase();
  return (parts[0][0] + parts[parts.length - 1][0]).toUpperCase();
}

export function firstName(name = '') {
  return name.trim().split(/\s+/)[0] || '';
}

const PLURAL = { comprimido: 'comprimidos', 'cápsula': 'cápsulas', capsula: 'capsulas', gota: 'gotas', 'sachê': 'sachês', dose: 'doses', 'ampola': 'ampolas' };
export function measureLabel(measure = '', qty = 1) {
  const m = measure.trim().toLowerCase();
  if (!m) return qty === 1 ? 'unidade' : 'unidades';
  if (qty === 1) return m;
  if (PLURAL[m]) return PLURAL[m];
  if (m === 'ml' || m === 'mg' || m === 'un' || m === 'un.') return m;
  return m.endsWith('s') ? m : `${m}s`;
}

export function formatQty(qty, measure) {
  return `${qty} ${measureLabel(measure, qty)}`;
}

export function pluralDays(n) {
  return `${n} ${n === 1 ? 'dia' : 'dias'}`;
}
