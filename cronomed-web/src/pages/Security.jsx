import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Icon from '../components/Icon.jsx';
import { Button, FormError, Modal, PasswordChecklist, PasswordField } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { UserApi } from '../lib/api.js';
import { isStrongPassword } from '../lib/validators.js';

function ChangePasswordModal({ onClose }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [cur, setCur] = useState('');
  const [next, setNext] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const mut = useMutation({
    mutationFn: () => UserApi.changePassword(cur, next),
    onSuccess: async () => {
      // O servidor encerra todas as sessoes ao trocar a senha: entre de novo com a nova senha.
      await logout();
      toast.success('Senha alterada. Entre novamente com a nova senha.');
      navigate('/entrar', { replace: true });
    },
    onError: setFormError,
  });
  function submit(e) {
    e.preventDefault();
    setFormError(null);
    const er = {};
    if (!cur) er.cur = 'Informe a senha atual.';
    if (!isStrongPassword(next)) er.next = 'A nova senha não atende a todos os requisitos.';
    if (next && next === cur) er.next = 'A nova senha deve ser diferente da atual.';
    setErrors(er);
    if (Object.keys(er).length === 0) mut.mutate();
  }
  return (
    <Modal title="Alterar senha" subtitle="Você será desconectado de todos os dispositivos." onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <FormError error={formError} />
        <PasswordField label="Senha atual" value={cur} onChange={(e) => setCur(e.target.value)} error={errors.cur} autoComplete="current-password" />
        <PasswordField label="Nova senha" value={next} onChange={(e) => setNext(e.target.value)} error={errors.next} autoComplete="new-password" />
        <PasswordChecklist password={next} />
        <div className="modal-actions">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={mut.isPending}>Alterar senha</Button>
        </div>
      </form>
    </Modal>
  );
}

export default function Security() {
  useSubtitle('Segurança');
  const [open, setOpen] = useState(false);
  return (
    <div className="page page-narrow">
      <div className="card">
        <div className="sec-row">
          <span className="ico"><Icon name="lock" size={22} /></span>
          <div className="grow"><strong>Senha</strong><span className="d">Troque a senha de acesso ao portal e ao aplicativo.</span></div>
          <Button variant="outline" onClick={() => setOpen(true)}>Alterar senha</Button>
        </div>
        <div className="sec-row disabled">
          <span className="ico"><Icon name="fingerprint" size={22} /></span>
          <div className="grow"><strong>Biometria</strong><span className="d">Disponível no aplicativo do celular.</span></div>
        </div>
        <div className="sec-row disabled">
          <span className="ico"><Icon name="shield" size={22} /></span>
          <div className="grow"><strong>Verificação em duas etapas</strong><span className="d">Em breve.</span></div>
        </div>
      </div>
      {open && <ChangePasswordModal onClose={() => setOpen(false)} />}
    </div>
  );
}
