import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import AuthLayout from '../components/AuthLayout.jsx';
import Icon from '../components/Icon.jsx';
import { Button, FormError, PasswordChecklist, PasswordField, TextField } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { isEmail, isStrongPassword, isValidCpf, maskCpf, maskPhone, onlyDigits } from '../lib/validators.js';
import { errorMessage } from '../lib/errors.js';
import { saveProfileExtras } from '../lib/profileExtras.js';

export default function Register() {
  const { register } = useAuth();
  const navigate = useNavigate();
  const [step, setStep] = useState(1);
  const [form, setForm] = useState({ name: '', cpf: '', email: '', password: '', confirm: '', phone: '', phone2: '', whatsapp: '', terms: false });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));

  function validateStep1() {
    const e = {};
    if (form.name.trim().split(/\s+/).length < 2) e.name = 'Informe o nome completo.';
    if (!isValidCpf(form.cpf)) e.cpf = 'CPF inválido.';
    if (!isEmail(form.email)) e.email = 'E-mail inválido.';
    if (!isStrongPassword(form.password)) e.password = 'A senha ainda não atende a todos os requisitos.';
    if (form.password !== form.confirm) e.confirm = 'As senhas não coincidem.';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function next(e) {
    e.preventDefault();
    if (validateStep1()) {
      setError('');
      setStep(2);
    }
  }

  async function submit(e) {
    e.preventDefault();
    const errs = {};
    if (onlyDigits(form.phone).length < 10) errs.phone = 'Informe um telefone com DDD.';
    if (!form.terms) errs.terms = 'É necessário aceitar os termos para criar a conta.';
    setErrors(errs);
    if (Object.keys(errs).length) return;
    setLoading(true);
    setError('');
    try {
      const user = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        profileType: 'PACIENTE',
        createSelfPatient: true,
        acceptTerms: true,
        // Campos abaixo ainda nao sao armazenados pelo backend (ele ignora propriedades desconhecidas).
        cpf: onlyDigits(form.cpf),
        phone: onlyDigits(form.phone),
      });
      saveProfileExtras(user.id, { cpf: form.cpf, phone: form.phone, phone2: form.phone2, whatsapp: form.whatsapp });
      navigate('/app/doses', { replace: true });
    } catch (err) {
      setError(errorMessage(err, 'Não foi possível criar a conta.'));
      if (err.status === 409) setStep(1);
    } finally {
      setLoading(false);
    }
  }

  if (step === 1) {
    return (
      <AuthLayout>
        <Link to="/" className="auth-back"><Icon name="chevronLeft" size={18} /> Voltar ao início</Link>
        <h1>Cadastre-se</h1>
        <form className="auth-form" onSubmit={next} noValidate>
          <TextField label="Nome completo" autoComplete="name" placeholder="João Ferreira dos Santos" value={form.name} onChange={set('name')} error={errors.name} />
          <TextField label="CPF" inputMode="numeric" autoComplete="off" placeholder="123.456.789-10" value={form.cpf} onChange={(e) => setForm((f) => ({ ...f, cpf: maskCpf(e.target.value) }))} error={errors.cpf} />
          <TextField label="E-mail principal" type="email" autoComplete="email" placeholder="joao.sf@email.com" value={form.email} onChange={set('email')} error={errors.email} />
          <PasswordChecklist password={form.password} />
          <PasswordField label="Senha" autoComplete="new-password" value={form.password} onChange={set('password')} error={errors.password} />
          <PasswordField label="Confirmar senha" autoComplete="new-password" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />
          <FormError error={error} />
          <Button type="submit" size="lg" block>Dados de contato →</Button>
        </form>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      <button type="button" className="auth-back" onClick={() => setStep(1)}><Icon name="chevronLeft" size={18} /> Dados pessoais</button>
      <h1>Cadastre-se</h1>
      <p className="steps-caption" style={{ marginTop: -8 }}>Etapa 2 de 2 · Dados de contato</p>
      <form className="auth-form" onSubmit={submit} noValidate>
        <TextField label="Telefone principal" inputMode="tel" autoComplete="tel" placeholder="+55 (13) 98765-4321" value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: maskPhone(e.target.value) }))} error={errors.phone} />
        <TextField label="Telefone opcional" inputMode="tel" placeholder="Opcional" value={form.phone2} onChange={(e) => setForm((f) => ({ ...f, phone2: maskPhone(e.target.value) }))} />
        <TextField label="WhatsApp" inputMode="tel" placeholder="Opcional" value={form.whatsapp} onChange={(e) => setForm((f) => ({ ...f, whatsapp: maskPhone(e.target.value) }))} />
        <div className="field">
          <label style={{ display: 'flex', gap: 12, alignItems: 'flex-start', fontWeight: 400, fontSize: '.92rem', lineHeight: 1.5, color: 'var(--ink)' }}>
            <input type="checkbox" checked={form.terms} onChange={(e) => setForm((f) => ({ ...f, terms: e.target.checked }))} style={{ width: 22, height: 22, marginTop: 2, accentColor: 'var(--blue)' }} />
            <span>Li e aceito os termos de uso e a política de privacidade. Autorizo o tratamento dos meus dados conforme a LGPD.</span>
          </label>
          {errors.terms && <span className="field-error" role="alert">{errors.terms}</span>}
        </div>
        <FormError error={error} />
        <Button type="submit" size="lg" block loading={loading}>Criar minha conta</Button>
      </form>
    </AuthLayout>
  );
}
