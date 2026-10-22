import { useMemo, useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Icon from '../components/Icon.jsx';
import { Button, ConfirmDialog, EmptyState, ErrorNotice, Field, FormError, Modal, Skeleton, TextField } from '../components/ui.jsx';
import { PatientRequired } from '../components/RouteGuards.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { useGrants } from '../hooks/queries.js';
import { PatientApi } from '../lib/api.js';
import { errorMessage } from '../lib/errors.js';
import { formatShortDate, formatTime, initials } from '../lib/format.js';
import { isEmail } from '../lib/validators.js';

export const LEVELS = [
  { value: 'VIEW', label: 'Somente visualizar' },
  { value: 'MANAGE', label: 'Gerenciar medicação' },
];
export const RELATIONSHIPS = [
  { value: 'FAMILIAR', label: 'Familiar' },
  { value: 'CUIDADOR', label: 'Cuidador(a)' },
  { value: 'PROFISSIONAL_SAUDE', label: 'Profissional de saúde' },
  { value: 'INSTITUICAO', label: 'Instituição' },
];
const relLabel = (v) => (RELATIONSHIPS.find((r) => r.value === v) || { label: v }).label;

function InviteModal({ patient, onClose }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [f, setF] = useState({ email: '', relationship: 'FAMILIAR', level: 'VIEW', consent: false });
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const mut = useMutation({
    mutationFn: () => PatientApi.grant(patient.id, { granteeEmail: f.email.trim(), level: f.level, relationship: f.relationship, consentAccepted: true }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['grants', patient.id] });
      toast.success('Acesso concedido.');
      onClose();
    },
    onError: (e) => setFormError(e && e.status === 404 ? 'Não encontramos uma conta com este e-mail. Peça para a pessoa se cadastrar primeiro (tela “Cadastro de convidado”) e tente de novo.' : e),
  });
  function submit(e) {
    e.preventDefault();
    setFormError(null);
    const er = {};
    if (!isEmail(f.email)) er.email = 'Informe um e-mail válido.';
    if (!f.consent) er.consent = 'O seu consentimento é obrigatório para compartilhar dados.';
    setErrors(er);
    if (Object.keys(er).length === 0) mut.mutate();
  }
  return (
    <Modal title="Convidar pessoa" subtitle="A pessoa precisa ter uma conta CronoMed com este e-mail." onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <FormError error={formError} />
        <TextField label="E-mail da pessoa" type="email" value={f.email} onChange={(e) => setF({ ...f, email: e.target.value })} error={errors.email} placeholder="nome@email.com" autoComplete="off" />
        <Field label="Vínculo com o paciente">
          <select className="select" value={f.relationship} onChange={(e) => setF({ ...f, relationship: e.target.value })}>
            {RELATIONSHIPS.map((r) => <option key={r.value} value={r.value}>{r.label}</option>)}
          </select>
        </Field>
        <Field label="Nível de acesso">
          <select className="select" value={f.level} onChange={(e) => setF({ ...f, level: e.target.value })}>
            {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
          </select>
        </Field>
        <Field error={errors.consent}>
          <label style={{ display: 'flex', gap: 10, alignItems: 'flex-start', fontSize: '.9rem', lineHeight: 1.5 }}>
            <input type="checkbox" checked={f.consent} onChange={(e) => setF({ ...f, consent: e.target.checked })} style={{ marginTop: 4, width: 18, height: 18 }} />
            <span>Autorizo o compartilhamento dos meus dados de saúde com esta pessoa, ciente de que posso revogar o acesso a qualquer momento (LGPD).</span>
          </label>
        </Field>
        <div className="modal-actions">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={mut.isPending}>Conceder acesso</Button>
        </div>
      </form>
    </Modal>
  );
}

function SharingInner() {
  const { patient, tz } = usePatient();
  const { user } = useAuth();
  const toast = useToast();
  const qc = useQueryClient();
  useSubtitle('Compartilhamento');
  const grants = useGrants(patient.id, true);
  const [invite, setInvite] = useState(false);
  const [toRemove, setToRemove] = useState(null);

  const change = useMutation({
    mutationFn: ({ id, level }) => PatientApi.changeLevel(patient.id, id, level),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['grants', patient.id] }); toast.success('Nível de acesso atualizado.'); },
    onError: (e) => toast.error(errorMessage(e, 'Não foi possível alterar o acesso.')),
  });
  const remove = useMutation({
    mutationFn: (id) => PatientApi.revoke(patient.id, id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['grants', patient.id] }); toast.success('Acesso removido.'); setToRemove(null); },
    onError: (e) => { setToRemove(null); toast.error(errorMessage(e, 'Não foi possível remover o acesso.')); },
  });

  const list = grants.data || [];
  const log = useMemo(
    () => list.flatMap((g) => [
      { at: g.createdAt, text: `${g.granteeName} recebeu acesso (${(LEVELS.find((l) => l.value === g.level) || {}).label || g.level})` },
      ...(g.consentAt ? [{ at: g.consentAt, text: `Consentimento LGPD registrado para ${g.granteeName}` }] : []),
    ]).sort((a, b) => new Date(b.at) - new Date(a.at)).slice(0, 8),
    [list],
  );

  return (
    <div className="page">
      <div className="share-layout">
        <section>
          <div className="share-head">
            <h2>Pessoas com acesso</h2>
            <Button onClick={() => setInvite(true)}><Icon name="plus" size={18} /> Convidar pessoa</Button>
          </div>
          <ErrorNotice error={grants.error} onRetry={grants.refetch} />
          <div className="card person">
            <span className="avatar">{initials(user.name)}</span>
            <div className="who"><strong>{user.name} (você)</strong><span>Titular · acesso total</span></div>
          </div>
          {grants.isLoading ? <Skeleton height={80} /> : list.length === 0 ? (
            <EmptyState icon="users" title="Ninguém mais tem acesso">Convide um familiar, cuidador ou profissional de saúde para acompanhar o tratamento.</EmptyState>
          ) : list.map((g) => (
            <div key={g.id} className="card person">
              <span className="avatar">{initials(g.granteeName)}</span>
              <div className="who"><strong>{g.granteeName}</strong><span>{relLabel(g.relationship)} · {g.granteeEmail}</span></div>
              <select className="select select-sm person-select" style={{ width: 190 }} aria-label={`Nível de acesso de ${g.granteeName}`} value={g.level} disabled={change.isPending}
                onChange={(e) => change.mutate({ id: g.id, level: e.target.value })}>
                {LEVELS.map((l) => <option key={l.value} value={l.value}>{l.label}</option>)}
              </select>
              <div className="actions">
                <Button variant="danger" size="sm" onClick={() => setToRemove(g)}><Icon name="trash" size={16} /> Excluir</Button>
              </div>
            </div>
          ))}
          <p className="legal-note">Você decide quem vê os seus dados de saúde. O acesso pode ser alterado ou removido a qualquer momento, conforme a LGPD. Quem apenas visualiza não consegue confirmar doses nem editar medicamentos.</p>
        </section>
        <aside className="card log">
          <h3>Registro de acessos</h3>
          {log.length === 0 ? <p className="muted" style={{ fontSize: '.875rem' }}>Os compartilhamentos aparecerão aqui.</p> : (
            <ul>
              {log.map((l, i) => (
                <li key={i}>{l.text}<span>{formatShortDate(l.at, tz)} · {formatTime(l.at, tz)}</span></li>
              ))}
            </ul>
          )}
        </aside>
      </div>
      {invite && <InviteModal patient={patient} onClose={() => setInvite(false)} />}
      {toRemove && (
        <ConfirmDialog title="Remover acesso?" message={`${toRemove.granteeName} deixará de ver os dados de ${patient.name}. Você pode convidar a pessoa de novo depois.`}
          confirmLabel="Remover" loading={remove.isPending} onConfirm={() => remove.mutate(toRemove.id)} onCancel={() => setToRemove(null)} />
      )}
    </div>
  );
}

export default function Sharing() {
  return (
    <PatientRequired>
      <SharingInner />
    </PatientRequired>
  );
}
