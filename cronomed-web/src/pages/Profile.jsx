import { useState } from 'react';
import { useMutation } from '@tanstack/react-query';
import { Button, FormError, TextField } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { UserApi } from '../lib/api.js';
import { loadProfileExtras, saveProfileExtras } from '../lib/profileExtras.js';
import { isValidCpf, maskCpf, maskDate, maskPhone } from '../lib/validators.js';

export default function Profile() {
  const { user, setUser } = useAuth();
  const toast = useToast();
  useSubtitle('Alterar cadastro');
  const [name, setName] = useState(user.name);
  const [extras, setExtras] = useState(() => ({ cpf: '', phone: '', birth: '', ...loadProfileExtras(user.id) }));
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);

  const mut = useMutation({
    mutationFn: () => UserApi.update({ name: name.trim(), profileType: user.profileType }),
    onSuccess: (u) => {
      setUser(u);
      saveProfileExtras(user.id, extras);
      toast.success('Cadastro atualizado.');
    },
    onError: setFormError,
  });

  function submit(e) {
    e.preventDefault();
    setFormError(null);
    const er = {};
    if (name.trim().length < 3) er.name = 'Informe o nome completo.';
    if (extras.cpf && !isValidCpf(extras.cpf)) er.cpf = 'CPF inválido.';
    setErrors(er);
    if (Object.keys(er).length === 0) mut.mutate();
  }

  return (
    <div className="page page-narrow">
      <form className="card profile-card" onSubmit={submit} noValidate>
        <div className="block">
          <h2>Dados pessoais</h2>
          <FormError error={formError} />
          <div className="form-grid">
            <TextField label="Nome completo" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} className="span-2" maxLength={120} autoComplete="name" />
            <TextField label="CPF" value={extras.cpf} onChange={(e) => setExtras({ ...extras, cpf: maskCpf(e.target.value) })} error={errors.cpf} inputMode="numeric" placeholder="000.000.000-00" />
            <TextField label="Data de nascimento" value={extras.birth} onChange={(e) => setExtras({ ...extras, birth: maskDate(e.target.value) })} inputMode="numeric" placeholder="DD/MM/AAAA" />
          </div>
        </div>
        <div className="block">
          <h2>Dados de contato</h2>
          <div className="form-grid">
            <TextField label="E-mail" value={user.email} locked hint="O e-mail é o seu acesso e não pode ser alterado aqui." />
            <TextField label="Celular (WhatsApp)" value={extras.phone} onChange={(e) => setExtras({ ...extras, phone: maskPhone(e.target.value) })} inputMode="tel" placeholder="(00) 00000-0000" />
          </div>
        </div>
        <p className="legal-note">CPF, nascimento e celular ficam salvos apenas neste navegador por enquanto; nome e e-mail são os dados guardados no servidor.</p>
        <div className="form-actions"><Button type="submit" loading={mut.isPending}>Salvar alterações</Button></div>
      </form>
    </div>
  );
}
