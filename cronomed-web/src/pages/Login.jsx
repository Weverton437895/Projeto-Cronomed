import { useState } from 'react';
import { Link, useLocation, useNavigate } from 'react-router-dom';
import AuthLayout from '../components/AuthLayout.jsx';
import { Button, FormError, PasswordField, TextField } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { isEmail, isValidCpf, maskCpf } from '../lib/validators.js';
import { errorMessage } from '../lib/errors.js';

export default function Login() {
  const { login } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const [tab, setTab] = useState('email');
  const [email, setEmail] = useState('');
  const [cpf, setCpf] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function onSubmit(e) {
    e.preventDefault();
    setError('');
    if (tab === 'cpf') {
      if (!isValidCpf(cpf)) {
        setError('Informe um CPF válido.');
        return;
      }
      // O servidor autentica por e-mail. Quando passar a aceitar CPF, troque aqui.
      setError('O acesso por CPF ainda não está disponível. Entre com o seu e-mail.');
      setTab('email');
      return;
    }
    if (!isEmail(email)) {
      setError('Informe um e-mail válido.');
      return;
    }
    if (!password) {
      setError('Informe a sua senha.');
      return;
    }
    setLoading(true);
    try {
      await login(email, password);
      navigate((location.state && location.state.from) || '/app/doses', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Não foi possível entrar.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <h1>Acessar</h1>
      <div className="segmented" role="tablist" aria-label="Forma de acesso" style={{ marginBottom: 20 }}>
        <button type="button" role="tab" aria-selected={tab === 'email'} onClick={() => setTab('email')}>E-mail</button>
        <button type="button" role="tab" aria-selected={tab === 'cpf'} onClick={() => setTab('cpf')}>CPF</button>
      </div>
      <form className="auth-form" onSubmit={onSubmit} noValidate>
        {tab === 'email' ? (
          <TextField label="E-mail" type="email" autoComplete="username" placeholder="user@email.com" value={email} onChange={(e) => setEmail(e.target.value)} />
        ) : (
          <TextField label="CPF" inputMode="numeric" autoComplete="off" placeholder="123.456.789-10" value={cpf} onChange={(e) => setCpf(maskCpf(e.target.value))} />
        )}
        <PasswordField label="Senha" autoComplete="current-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} />
        <div className="auth-links">
          <Link to="/esqueci-senha">Esqueci minha senha</Link>
        </div>
        <FormError error={error} />
        <Button type="submit" size="lg" block loading={loading}>Acessar</Button>
      </form>
      <p className="muted" style={{ textAlign: 'center', margin: '22px 0 12px' }}>Seu primeiro acesso aqui?</p>
      <Link to="/cadastro" className="btn btn-outline btn-block btn-lg">Cadastre-se</Link>
    </AuthLayout>
  );
}
