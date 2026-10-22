// Validacoes e mascaras de formulario.

export const PASSWORD_RULES = [
  { id: 'len', label: '8 caracteres', test: (p) => p.length >= 8 },
  { id: 'upper', label: '1 letra maiúscula', test: (p) => /[A-ZÀ-Ý]/.test(p) },
  { id: 'lower', label: '1 letra minúscula', test: (p) => /[a-zß-ÿ]/.test(p) },
  { id: 'digit', label: '1 número', test: (p) => /\d/.test(p) },
  { id: 'special', label: '1 caractere especial (!@#$%)', test: (p) => /[^A-Za-zÀ-ÿ0-9\s]/.test(p) },
];

export function passwordChecks(password = '') {
  return PASSWORD_RULES.map((r) => ({ id: r.id, label: r.label, ok: r.test(password) }));
}

export function isStrongPassword(password = '') {
  return password.length <= 72 && PASSWORD_RULES.every((r) => r.test(password));
}

export function isEmail(value = '') {
  return /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(value.trim());
}

export function onlyDigits(value = '') {
  return value.replace(/\D/g, '');
}

export function maskCpf(value = '') {
  const d = onlyDigits(value).slice(0, 11);
  let out = d.slice(0, 3);
  if (d.length > 3) out += '.' + d.slice(3, 6);
  if (d.length > 6) out += '.' + d.slice(6, 9);
  if (d.length > 9) out += '-' + d.slice(9, 11);
  return out;
}

export function isValidCpf(value = '') {
  const d = onlyDigits(value);
  if (d.length !== 11 || /^(\d)\1{10}$/.test(d)) return false;
  const calc = (len) => {
    let sum = 0;
    for (let i = 0; i < len; i++) sum += Number(d[i]) * (len + 1 - i);
    const r = (sum * 10) % 11;
    return r === 10 ? 0 : r;
  };
  return calc(9) === Number(d[9]) && calc(10) === Number(d[10]);
}

export function maskPhone(value = '') {
  let d = onlyDigits(value);
  if (d.startsWith('55') && d.length > 11) d = d.slice(2);
  d = d.slice(0, 11);
  if (d.length === 0) return '';
  let out = `+55 (${d.slice(0, 2)}`;
  if (d.length >= 2) out += ')';
  if (d.length > 2) out += ' ' + d.slice(2, d.length > 10 ? 7 : 6);
  if (d.length > (d.length > 10 ? 7 : 6)) out += '-' + d.slice(d.length > 10 ? 7 : 6);
  return out;
}

export function maskDate(value = '') {
  const d = onlyDigits(value).slice(0, 8);
  let out = d.slice(0, 2);
  if (d.length > 2) out += '/' + d.slice(2, 4);
  if (d.length > 4) out += '/' + d.slice(4, 8);
  return out;
}

export function isValidMac(value = '') {
  return /^([0-9A-Fa-f]{2}[:-]?){5}[0-9A-Fa-f]{2}$/.test(value.trim());
}

/** Normaliza para AA:BB:CC:DD:EE:FF. */
export function normalizeMac(value = '') {
  const hex = value.replace(/[^0-9A-Fa-f]/g, '').toUpperCase();
  return hex.match(/.{1,2}/g)?.join(':') ?? '';
}

export function isValidTime(value = '') {
  return /^([01]\d|2[0-3]):[0-5]\d$/.test(value);
}
