export function errorMessage(err, fallback = 'Algo deu errado. Tente novamente.') {
  if (!err) return fallback;
  if (Array.isArray(err.fields) && err.fields.length > 0) {
    return err.fields.map((f) => f.message).filter(Boolean).join(' ') || err.message || fallback;
  }
  return err.message || fallback;
}
