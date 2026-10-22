import { useState } from 'react';
import { Link, useNavigate, useSearchParams } from 'react-router-dom';
import AuthLayout from '../components/AuthLayout.jsx';
import Icon from '../components/Icon.jsx';
import { Button, FormError, PasswordChecklist, PasswordField, TextField } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { isEmail, isStrongPassword, isValidCpf, maskCpf, maskDate, maskPhone, onlyDigits } from '../lib/validators.js';
import { errorMessage } from '../lib/errors.js';
import { saveProfileExtras } from '../lib/profileExtras.js';

const GUEST_TYPES = [
  { id: 'FAMILIAR', label: 'Familiar', profileType: 'FAMILIAR' },
  { id: 'CUIDADOR', label: 'Cuidador', profileType: 'CUIDADOR' },
  { id: 'ENFERMEIRO', label: 'Enfermeiro(a) cuidador(a)', profileType: 'CUIDADOR' },
  { id: 'MEDICO', label: 'Médico(a)', profileType: 'PROFISSIONAL_SAUDE' },
];

// Cadastro de convidado em 3 etapas (familiar, cuidador, enfermeiro, medico).
// Link de convite aceita: ?titular=Nome&nome=Nome&email=...&whatsapp=... (campos preenchidos ficam travados).
export default function GuestSignup() {
  const [params] = useSearchParams();
  const { register } = useAuth();
  const { patient, patients, isLoading } = usePatient();
  const navigate = useNavigate();
  const inviter = params.get('titular') || '';
  const locked = { name: !!params.get('nome'), email: !!params.get('email') };
  const [step, setStep] = useState(1);
  const [form, setForm] = useState({
    name: params.get('nome') || '', cpf: '', birth: '', type: 'FAMILIAR', council: '', specialty: '',
    phone: '', phone2: '', whatsapp: params.get('whatsapp') ? maskPhone(params.get('whatsapp')) : '', email: params.get('email') || '',
    password: '', confirm: '',
  });
  const [errors, setErrors] = useState({});
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const [done, setDone] = useState(false);
  const set = (k) => (e) => setForm((f) => ({ ...f, [k]: e.target.value }));
  const type = GUEST_TYPES.find((t) => t.id === form.type);

  function validate1() {
    const e = {};
    if (form.name.trim().split(/\s+/).length < 2) e.name = 'Informe o nome completo.';
    if (!isValidCpf(form.cpf)) e.cpf = 'CPF inválido.';
    if (onlyDigits(form.birth).length !== 8) e.birth = 'Informe a data (dd/mm/aaaa).';
    if (form.type === 'ENFERMEIRO' && !form.council.trim()) e.council = 'COREM obrigatório.';
    if (form.type === 'MEDICO') {
      if (!form.council.trim()) e.council = 'CRM obrigatório.';
      if (!form.specialty.trim()) e.specialty = 'Informe a especialidade.';
    }
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  function validate2() {
    const e = {};
    if (onlyDigits(form.phone).length < 10) e.phone = 'Informe um telefone com DDD.';
    if (!isEmail(form.email)) e.email = 'E-mail inválido.';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  async function finish(ev) {
    ev.preventDefault();
    const e = {};
    if (!isStrongPassword(form.password)) e.password = 'A senha ainda não atende a todos os requisitos.';
    if (form.password !== form.confirm) e.confirm = 'As senhas não coincidem.';
    setErrors(e);
    if (Object.keys(e).length) return;
    setLoading(true);
    setError('');
    try {
      const user = await register({
        name: form.name.trim(),
        email: form.email.trim(),
        password: form.password,
        profileType: type.profileType,
        createSelfPatient: false,
        acceptTerms: true,
        cpf: onlyDigits(form.cpf),
        phone: onlyDigits(form.phone),
      });
      saveProfileExtras(user.id, {
        cpf: form.cpf, birth: form.birth, phone: form.phone, phone2: form.phone2, whatsapp: form.whatsapp,
        guestType: form.type, council: form.council, specialty: form.specialty,
      });
      setDone(true);
    } catch (err) {
      setError(errorMessage(err, 'Não foi possível concluir o cadastro.'));
    } finally {
      setLoading(false);
    }
  }

  // ---- tela final: o que foi liberado pelo titular ----
  if (done) {
    const access = patient ? patient.myAccess : 'NONE';
    const rank = ['NONE', 'VIEW', 'CONFIRM', 'MANAGE', 'OWNER'].indexOf(access);
    const rows = [
      { label: 'Próximas Doses', tag: rank >= 1 ? 'Visualização' : null },
      { label: 'Estoque', tag: rank >= 1 ? 'Visualização' : null },
      { label: 'Histórico', tag: rank >= 1 ? 'Visualização' : null },
      { label: 'Gerenciar Medicamentos', tag: rank >= 3 ? 'Edição' : rank >= 1 ? 'Visualização' : null },
      { label: 'Compartilhamento', tag: rank >= 4 ? 'Total' : null },
      { label: 'Dispositivos', tag: rank >= 4 ? 'Total' : null },
      { label: 'Configurações', tag: rank >= 4 ? 'Total' : null },
    ];
    return (
      <AuthLayout>
        <div className="success-mark"><Icon name="check" size={40} strokeWidth={2.6} /></div>
        <h1 style={{ marginBottom: 10 }}>Cadastro concluído!</h1>
        {patient ? (
          <>
            <p className="muted" style={{ marginBottom: 6 }}>
              Você agora acompanha o tratamento de <strong style={{ color: 'var(--ink)' }}>{patient.name}</strong> como {type.label}. O acesso é limitado aos dados que {patients.length > 1 ? 'os titulares autorizaram' : 'o titular autorizou'}.
            </p>
            <div className="access-list">
              <h3>Telas liberadas para você</h3>
              {rows.map((r) => (
                <div key={r.label} className={`access-row${r.tag ? '' : ' locked'}`}>
                  <Icon name={r.tag ? 'check' : 'lock'} size={18} />
                  <span className="grow">{r.label}</span>
                  {r.tag ? <span className="badge badge-success">{r.tag}</span> : <span className="tag">Sem acesso</span>}
                </div>
              ))}
            </div>
            <Button size="lg" block onClick={() => navigate('/app/doses', { replace: true })}>Acessar Próximas Doses</Button>
          </>
        ) : (
          <>
            <p className="muted" style={{ margin: '4px 0 22px', lineHeight: 1.55 }}>
              {isLoading ? 'Verificando seus acessos...' : (
                <>Sua conta foi criada. Para ver o tratamento de alguém, peça ao titular que compartilhe o acesso usando o e-mail <strong style={{ color: 'var(--ink)' }}>{form.email}</strong>.</>
              )}
            </p>
            <Button size="lg" block onClick={() => navigate('/app/doses', { replace: true })}>Entrar no portal</Button>
          </>
        )}
        <p className="muted" style={{ fontSize: '.8rem', textAlign: 'center', marginTop: 14, lineHeight: 1.5 }}>
          Dados compartilhados com seu consentimento. O titular pode revogar o acesso a qualquer momento (LGPD).
        </p>
      </AuthLayout>
    );
  }

  return (
    <AuthLayout>
      {step === 1 ? (
        <Link to="/" className="auth-back"><Icon name="chevronLeft" size={18} /> Voltar</Link>
      ) : (
        <button type="button" className="auth-back" onClick={() => { setErrors({}); setStep(step - 1); }}>
          <Icon name="chevronLeft" size={18} /> {step === 2 ? 'Dados pessoais' : 'Dados de contato'}
        </button>
      )}
      <h1 style={{ marginBottom: 14 }}>Cadastre-se</h1>
      <div className="steps" aria-hidden="true">
        <span className="on" /><span className={step >= 2 ? 'on' : ''} /><span className={step >= 3 ? 'on' : ''} />
      </div>
      <p className="steps-caption">Etapa {step} de 3 · {step === 1 ? 'Dados pessoais' : step === 2 ? 'Dados de contato' : 'Dados de acesso'}</p>
      {inviter && step < 3 && (
        <div className="banner info" style={{ marginBottom: 18 }}>
          <Icon name="lock" size={18} />
          <span><strong>Convite de {inviter}.</strong> Campos com cadeado já vieram preenchidos.</span>
        </div>
      )}

      {step === 1 && (
        <form className="auth-form" noValidate onSubmit={(e) => { e.preventDefault(); if (validate1()) setStep(2); }}>
          <TextField label="Nome completo" autoComplete="name" value={form.name} onChange={set('name')} locked={locked.name} error={errors.name} />
          <div className="form-grid cols-2">
            <TextField label="CPF" inputMode="numeric" placeholder="987.654.321-00" value={form.cpf} onChange={(e) => setForm((f) => ({ ...f, cpf: maskCpf(e.target.value) }))} error={errors.cpf} />
            <TextField label="Data de nascimento" inputMode="numeric" placeholder="dd/mm/aaaa" value={form.birth} onChange={(e) => setForm((f) => ({ ...f, birth: maskDate(e.target.value) }))} error={errors.birth} />
          </div>
          <div className="field">
            <span className="field-label" id="tipo-label">Tipo de convidado</span>
            <div className="chips" role="group" aria-labelledby="tipo-label">
              {GUEST_TYPES.map((t) => (
                <button key={t.id} type="button" className="chip" aria-pressed={form.type === t.id} onClick={() => setForm((f) => ({ ...f, type: t.id }))}>{t.label}</button>
              ))}
            </div>
            <span className="field-hint">Enfermeiro(a): COREM obrigatório · Médico(a): CRM e especialidade obrigatórios</span>
          </div>
          {(form.type === 'ENFERMEIRO' || form.type === 'MEDICO') && (
            <div className="form-grid cols-2">
              {form.type === 'MEDICO' && <TextField label="Especialidade médica" placeholder="Cardiologia" value={form.specialty} onChange={set('specialty')} error={errors.specialty} />}
              <TextField label={form.type === 'MEDICO' ? 'CRM' : 'COREM'} placeholder="123456/SP" value={form.council} onChange={set('council')} error={errors.council} />
            </div>
          )}
          <Button type="submit" size="lg" block>Dados de contato →</Button>
        </form>
      )}

      {step === 2 && (
        <form className="auth-form" noValidate onSubmit={(e) => { e.preventDefault(); if (validate2()) setStep(3); }}>
          <TextField label="Telefone principal" inputMode="tel" placeholder="+55 (13) 99876-5432" value={form.phone} onChange={(e) => setForm((f) => ({ ...f, phone: maskPhone(e.target.value) }))} error={errors.phone} />
          <TextField label="Telefone opcional" inputMode="tel" placeholder="Opcional" value={form.phone2} onChange={(e) => setForm((f) => ({ ...f, phone2: maskPhone(e.target.value) }))} />
          <TextField label="WhatsApp" inputMode="tel" placeholder="Opcional" value={form.whatsapp} onChange={(e) => setForm((f) => ({ ...f, whatsapp: maskPhone(e.target.value) }))} />
          <TextField label="E-mail" type="email" autoComplete="email" value={form.email} onChange={set('email')} locked={locked.email} error={errors.email} />
          <Button type="submit" size="lg" block>Dados de acesso →</Button>
        </form>
      )}

      {step === 3 && (
        <form className="auth-form" noValidate onSubmit={finish}>
          <PasswordChecklist password={form.password} title="Sua senha deve ter no mínimo:" />
          <PasswordField label="Senha" autoComplete="new-password" placeholder="••••••••" value={form.password} onChange={set('password')} error={errors.password} />
          <PasswordField label="Confirmar senha" autoComplete="new-password" placeholder="••••••••" value={form.confirm} onChange={set('confirm')} error={errors.confirm} />
          <p className="muted" style={{ fontSize: '.85rem', lineHeight: 1.5 }}>Ao finalizar, você aceita os termos de uso e a política de privacidade (LGPD).</p>
          <FormError error={error} />
          <Button type="submit" size="lg" block loading={loading}>Finalizar cadastro</Button>
        </form>
      )}
    </AuthLayout>
  );
}
