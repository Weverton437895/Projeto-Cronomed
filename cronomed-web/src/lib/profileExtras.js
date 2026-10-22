// Dados de cadastro que o backend ainda nao armazena (CPF, telefones, nascimento...).
// Ficam salvos neste navegador para preencher a tela "Alterar cadastro". Quando o servidor passar a
// guardar esses campos, basta ler/gravar pela API em vez deste arquivo.
import { readJson, writeJson } from './storage.js';

const key = (userId) => `cronomed.profile.${userId}`;

export function loadProfileExtras(userId) {
  return readJson(key(userId), {});
}

export function saveProfileExtras(userId, data) {
  writeJson(key(userId), { ...loadProfileExtras(userId), ...data });
}
