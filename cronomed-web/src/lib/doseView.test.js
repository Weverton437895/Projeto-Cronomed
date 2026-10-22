import { describe, expect, it } from 'vitest';
import { doseBadge, isDue, reminderInfo, stockAlertMessage, stockTone } from './doseView.js';
import { formatQty, initials, pluralDays, relativeFromNow } from './format.js';
import { interpretAnswer } from './voice.js';
import { uuid } from './ids.js';

const at = (iso) => new Date(iso);
const dose = (status, scheduledAt = '2026-10-21T12:00:00Z') => ({ status, scheduledAt });

describe('doses', () => {
  it('detecta dose no ar dentro da tolerância de 60 min', () => {
    expect(isDue(dose('PENDING'), at('2026-10-21T12:10:00Z'))).toBe(true);
    expect(isDue(dose('PENDING'), at('2026-10-21T11:59:00Z'))).toBe(false);
    expect(isDue(dose('PENDING'), at('2026-10-21T13:10:00Z'))).toBe(false);
    expect(isDue(dose('TAKEN'), at('2026-10-21T12:10:00Z'))).toBe(false);
  });
  it('calcula o lembrete a cada 5 min (máximo 12)', () => {
    expect(reminderInfo(dose('PENDING'), at('2026-10-21T12:00:00Z'))).toMatchObject({ index: 1, nextIn: 5 });
    expect(reminderInfo(dose('PENDING'), at('2026-10-21T12:06:00Z'))).toMatchObject({ index: 2, nextIn: 4 });
    expect(reminderInfo(dose('PENDING'), at('2026-10-21T13:30:00Z')).last).toBe(true);
  });
  it('rotula o selo da dose', () => {
    expect(doseBadge(dose('TAKEN')).label).toBe('Confirmada');
    expect(doseBadge(dose('PENDING'), at('2026-10-21T11:35:00Z')).label).toBe('Em 25 min');
    expect(doseBadge(dose('PENDING'), at('2026-10-21T08:00:00Z')).label).toBe('Agendada');
  });
  it('classifica o estoque', () => {
    expect(stockTone({ stockStatus: 'OK' }).label).toBe('Em dia');
    expect(stockTone({ stockStatus: 'LOW', daysRemaining: 5 }).label).toBe('Atenção');
    expect(stockTone({ stockStatus: 'LOW', daysRemaining: 2 }).label).toBe('Acabando');
    expect(stockTone({ stockStatus: 'ZERO' }).label).toBe('Acabou');
    expect(stockAlertMessage({ name: 'Losartana', stockStatus: 'LOW', daysRemaining: 1 }).text).toContain('1 dia!');
  });
});

describe('formatação e voz', () => {
  it('formata quantidades e dias', () => {
    expect(formatQty(1, 'comprimido')).toBe('1 comprimido');
    expect(formatQty(2, 'comprimido')).toBe('2 comprimidos');
    expect(pluralDays(1)).toBe('1 dia');
    expect(pluralDays(5)).toBe('5 dias');
    expect(initials('Ana Maria Souza')).toBe('AS');
  });
  it('descreve a última sincronização', () => {
    expect(relativeFromNow(null)).toBe('sem sinal ainda');
    expect(relativeFromNow('2026-10-21T11:55:00Z', at('2026-10-21T12:00:00Z'))).toBe('há 5 min');
  });
  it('interpreta respostas por voz', () => {
    expect(interpretAnswer('sim, tomei')).toBe('yes');
    expect(interpretAnswer('ainda não')).toBe('no');
    expect(interpretAnswer('talvez')).toBe('unknown');
  });
  it('gera UUID v4 único', () => {
    const a = uuid();
    expect(a).toMatch(/^[0-9a-f]{8}-[0-9a-f]{4}-4[0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}$/);
    expect(uuid()).not.toBe(a);
  });
});
