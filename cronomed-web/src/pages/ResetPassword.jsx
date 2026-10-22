import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import AuthLayout from '../components/AuthLayout.jsx';
import Icon from '../components/Icon.jsx';
import { Button, FormError, PasswordChecklist, PasswordField, TextField } from '../components/ui.jsx';
import { AuthApi } from '../lib/api.js';
import { isEmail, isStrongPassword, onlyDigits } from '../lib/validators.js';
import { errorMessage } from '../lib/errors.js';
import { useToast } from '../context/ToastContext.jsx';

export default function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [email, setEmail] = useState(params.get('email') || '');
  const [code, setCode] = useState(params.get('code') || '');
  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    if (!isEmail(email)) errs.email = 'E-mail inválido.';
    if (onlyDigits(code).length !== 6) errs.code = 'O código tem 6 dígitos.';
    if (!isStrongPassword(password)) errs.password = 'A senha ainda não atende a todos os requisitos.';
    if (password !== confirm) errs.confirm = 'As senhas não coincidem.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setLoading(true);
    setError('');
    try {
      await AuthApi.reset(email.trim(), onlyDigits(code), password);
      toast.success('Senha alterada. Entre com a nova senha.');
      navigate('/entrar', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Não foi possível redefinir a senha.'));
    } finally {
      setLoading(false);
    }
  }

  return (
    <AuthLayout>
      <Link to="/esqueci-senha" className="auth-back"><Icon name="chevronLeft" size={18} /> Voltar</Link>
      <h1>Nova senha</h1>
      <form className="auth-form" onSubmit={submit} noValidate>
        <TextField label="E-mail" type="email" autoComplete="email" value={email} onChange={(e) => setEmail(e.target.value)} error={errors.email} />
        <TextField label="Código recebido por e-mail" inputMode="numeric" autoComplete="one-time-code" maxLength={6} placeholder="000000" value={code} onChange={(e) => setCode(onlyDigits(e.target.value))} error={errors.code} />
        <PasswordChecklist password={password} title="Sua nova senha deve ser diferente das anteriores e ter no mínimo:" />
        <PasswordField label="Senha" autoComplete="new-password" placeholder="••••••••" value={password} onChange={(e) => setPassword(e.target.value)} error={errors.password} />
        <PasswordField label="Confirmar senha" autoComplete="new-password" placeholder="••••••••" value={confirm} onChange={(e) => setConfirm(e.target.value)} error={errors.confirm} />
        <FormError error={error} />
        <Button type="submit" size="lg" block loading={loading}>Cadastrar nova senha</Button>
      </form>
    </AuthLayout>
  );
}
