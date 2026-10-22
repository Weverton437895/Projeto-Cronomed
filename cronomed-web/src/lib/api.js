// Cliente HTTP do CronoMed. Fala com o backend em /api/v1.
// - O portal web envia "X-Client-Type: web": o servidor entrega os tokens em cookies HttpOnly
//   (o JavaScript da pagina nunca ve o token) e por isso usamos credentials: 'include'.
// - Se o token de acesso (15 min) expirar, tenta UMA renovacao em /auth/refresh e repete a chamada.

const BASE = (import.meta.env && import.meta.env.VITE_API_BASE) || '/api/v1';

export class ApiError extends Error {
  constructor(status, code, message, fields = []) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.fields = fields;
  }
}

let onUnauthorized = () => {};
export function setUnauthorizedHandler(fn) {
  onUnauthorized = fn;
}

let refreshing = null;
function refreshSession() {
  if (!refreshing) {
    refreshing = fetch(`${BASE}/auth/refresh`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'X-Client-Type': 'web' },
    })
      .then((res) => res.ok)
      .catch(() => false)
      .finally(() => {
        refreshing = null;
      });
  }
  return refreshing;
}

async function toError(res) {
  let data = null;
  try {
    data = await res.json();
  } catch {
    data = null;
  }
  const message = (data && data.message) || `Erro ${res.status}`;
  return new ApiError(res.status, (data && data.code) || 'HTTP_' + res.status, message, (data && data.fields) || []);
}

async function send(path, { method = 'GET', body, query, signal } = {}) {
  let url = BASE + path;
  if (query) {
    const qs = new URLSearchParams();
    Object.entries(query).forEach(([k, v]) => {
      if (v !== undefined && v !== null && v !== '') qs.set(k, String(v));
    });
    const s = qs.toString();
    if (s) url += `?${s}`;
  }
  const headers = { 'X-Client-Type': 'web', Accept: 'application/json, text/csv' };
  if (body !== undefined) headers['Content-Type'] = 'application/json';
  try {
    return await fetch(url, {
      method,
      credentials: 'include',
      headers,
      body: body !== undefined ? JSON.stringify(body) : undefined,
      signal,
    });
  } catch (e) {
    if (e && e.name === 'AbortError') throw e;
    throw new ApiError(0, 'NETWORK', 'Nao foi possivel falar com o servidor. Verifique sua conexao e tente novamente.');
  }
}

export async function request(path, options = {}) {
  let res = await send(path, options);
  if (res.status === 401 && !path.startsWith('/auth/')) {
    const ok = await refreshSession();
    if (ok) {
      res = await send(path, options);
    }
    if (res.status === 401) {
      onUnauthorized();
    }
  }
  if (!res.ok) throw await toError(res);
  if (res.status === 204) return null;
  const type = res.headers.get('content-type') || '';
  if (options.as === 'blob' || type.includes('text/csv')) return res.blob();
  if (type.includes('application/json')) return res.json();
  return null;
}

export const api = {
  get: (path, query, extra) => request(path, { method: 'GET', query, ...extra }),
  post: (path, body, extra) => request(path, { method: 'POST', body: body === undefined ? {} : body, ...extra }),
  put: (path, body) => request(path, { method: 'PUT', body }),
  del: (path, body) => request(path, { method: 'DELETE', body }),
};

// ---------------------------------------------------------------- endpoints (um por funcao)

export const AuthApi = {
  register: (data) => api.post('/auth/register', data),
  login: (email, password, clientLabel = 'portal-web') => api.post('/auth/login', { email, password, clientLabel }),
  logout: () => request('/auth/logout', { method: 'POST' }).catch(() => null),
  forgot: (email) => api.post('/auth/password/forgot', { email }),
  reset: (email, code, newPassword) => api.post('/auth/password/reset', { email, code, newPassword }),
};

export const UserApi = {
  me: () => api.get('/users/me'),
  update: (data) => api.put('/users/me', data),
  changePassword: (currentPassword, newPassword) => api.put('/users/me/password', { currentPassword, newPassword }),
  remove: (password) => api.del('/users/me', { password }),
};

export const PatientApi = {
  list: () => api.get('/patients'),
  create: (data) => api.post('/patients', data),
  grants: (id) => api.get(`/patients/${id}/grants`),
  grant: (id, data) => api.post(`/patients/${id}/grants`, data),
  changeLevel: (id, grantId, level) => api.put(`/patients/${id}/grants/${grantId}`, { level }),
  revoke: (id, grantId) => api.del(`/patients/${id}/grants/${grantId}`),
};

export const DeviceApi = {
  list: () => api.get('/devices'),
  register: (macAddress, name) => api.post('/devices', { macAddress, name }),
  rename: (id, name) => api.put(`/devices/${id}`, { name }),
  revoke: (id) => api.post(`/devices/${id}/revoke`),
  updateCompartment: (deviceId, compartmentId, data) => api.put(`/devices/${deviceId}/compartments/${compartmentId}`, data),
};

export const MedicationApi = {
  list: (patientId) => api.get(`/patients/${patientId}/medications`),
  create: (patientId, data) => api.post(`/patients/${patientId}/medications`, data),
  update: (id, data) => api.put(`/medications/${id}`, data),
  remove: (id) => api.del(`/medications/${id}`),
  refill: (id, quantity) => api.post(`/medications/${id}/refill`, { quantity }),
};

export const DoseApi = {
  record: (doseId, body) => api.post(`/doses/${doseId}/events`, body),
  upcoming: (patientId, hours = 24) => api.get(`/patients/${patientId}/doses/upcoming`, { hours }),
};

export const DashboardApi = {
  summary: (patientId, from, to) => api.get(`/patients/${patientId}/dashboard`, { from, to }),
  doses: (patientId, { from, to, page = 0, size = 20 } = {}) => api.get(`/patients/${patientId}/doses`, { from, to, page, size }),
  events: (patientId, { from, to, page = 0, size = 20 } = {}) => api.get(`/patients/${patientId}/events`, { from, to, page, size }),
  alerts: (patientId, page = 0, size = 20) => api.get(`/patients/${patientId}/alerts`, { page, size }),
  report: (patientId, from, to) => api.get(`/patients/${patientId}/report.csv`, { from, to }, { as: 'blob' }),
};

export const PublicApi = {
  contact: (name, email, message) => api.post('/public/contact', { name, email, message }),
};
