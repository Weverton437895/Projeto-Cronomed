import { useEffect, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../components/AuthLayout.jsx';
import Icon from '../components/Icon.jsx';
import { Button, FormError, TextField } from '../components/ui.jsx';
import { AuthApi } from '../lib/api.js';
import { isEmail } from '../lib/validators.js';
import { errorMessage } from '../lib/errors.js';

export default function ForgotPassword() {
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [sent, setSent] = useState(false);
  const [wait, setWait] = useState(0);

  useEffect(() => {
    if (wait <= 0) return undefined;
    const t = setTimeout(() => setWait((w) => w - 1), 1000);
    return () => clearTimeout(t);
  }, [wait]);

  async function send(e) {
    if (e) e.preventDefault();
    if (!isEmail(email)) {
      setError('Informe um e-mail válido.');
      return;
    }
    setLoading(true);
    setError('');
    try {
      await AuthApi.forgot(email.trim());
      setSent(true);
      setWait(60);
    } catch (err) {
      setError(errorMessage(err, 'Não foi possível enviar o código.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <Link to="/entrar" className="auth-back"><Icon name="chevronLeft" size={18} /> Voltar</Link>
      <h1>Esqueci minha senha</h1>
      <p className="lead">Digite seu e-mail para receber o código de redefinição. O código é válido por 10 minutos.</p>
      <form className="auth-form" onSubmit={send} noValidate>
        <TextField label="E-mail" type="email" autoComplete="email" placeholder="user@email.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        <FormError error={error} />
        {sent && (
          <div className="form-ok" role="status">
            Se este e-mail estiver cadastrado, enviamos um código de 6 dígitos. Confira também a caixa de spam.
          </div>
        )}
        {sent ? (
          <Button size="lg" block onClick={() => navigate(`/redefinir-senha?email=${encodeURIComponent(email.trim())}`)}>Já tenho o código</Button>
        ) : (
          <Button type="submit" size="lg" block loading={loading}>Resetar senha</Button>
        )}
      </form>
      {sent && (
        <p className="muted" style={{ textAlign: 'center', marginTop: 18 }}>
          Ainda não recebeu o e-mail?{' '}
          {wait > 0 ? (
            <span>Reenviar em {wait} s</span>
          ) : (
            <button type="button" className="link-btn" style={{ background: 'none', border: 0, color: 'var(--blue)', fontWeight: 700 }} onClick={() => send()}>Reenviar agora</button>
          )}
        </p>
      )}
    </AuthLayout>
  );
}
