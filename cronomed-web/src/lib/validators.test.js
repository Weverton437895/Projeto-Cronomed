import { describe, expect, it } from 'vitest';
import { isEmail, isStrongPassword, isValidCpf, isValidMac, isValidTime, maskCpf, maskDate, maskPhone, normalizeMac, passwordChecks } from './validators.js';

describe('validators', () => {
  it('valida e-mail', () => {
    expect(isEmail('ana@email.com')).toBe(true);
    expect(isEmail('ana@')).toBe(false);
    expect(isEmail('')).toBe(false);
  });
  it('exige senha forte', () => {
    expect(isStrongPassword('Abcdef1!')).toBe(true);
    expect(isStrongPassword('abc')).toBe(false);
    expect(passwordChecks('abc').some((c) => !c.ok)).toBe(true);
  });
  it('valida CPF pelos dígitos verificadores', () => {
    expect(isValidCpf('529.982.247-25')).toBe(true);
    expect(isValidCpf('111.111.111-11')).toBe(false);
    expect(isValidCpf('529.982.247-24')).toBe(false);
  });
  it('aplica máscaras', () => {
    expect(maskCpf('52998224725')).toBe('529.982.247-25');
    expect(maskPhone('13999998888')).toBe('+55 (13) 99999-8888');
    expect(maskDate('01012000')).toBe('01/01/2000');
  });
  it('normaliza MAC', () => {
    expect(isValidMac('aa:bb:cc:dd:ee:ff')).toBe(true);
    expect(isValidMac('aa:bb')).toBe(false);
    expect(normalizeMac('aabbccddeeff')).toBe('AA:BB:CC:DD:EE:FF');
  });
  it('valida horário HH:mm', () => {
    expect(isValidTime('08:30')).toBe(true);
    expect(isValidTime('24:00')).toBe(false);
  });
});
