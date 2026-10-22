import { useState } from 'react';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import boxImg from '../assets/box-glow.jpg';
import Icon from '../components/Icon.jsx';
import CompartmentIcon from '../components/CompartmentIcon.jsx';
import { Badge, Button, ConfirmDialog, EmptyState, ErrorNotice, FormError, Modal, Skeleton, TextField } from '../components/ui.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { useDevices, useMedications } from '../hooks/queries.js';
import { usePatient } from '../context/PatientContext.jsx';
import { DeviceApi } from '../lib/api.js';
import { errorMessage } from '../lib/errors.js';
import { relativeFromNow } from '../lib/format.js';
import { isValidMac, normalizeMac } from '../lib/validators.js';

function RegisterModal({ onClose }) {
  const qc = useQueryClient();
  const [name, setName] = useState('Caixa CronoMed');
  const [mac, setMac] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [secret, setSecret] = useState(null);
  const [copied, setCopied] = useState(false);
  const mut = useMutation({
    mutationFn: () => DeviceApi.register(normalizeMac(mac), name.trim()),
    onSuccess: (res) => { qc.invalidateQueries({ queryKey: ['devices'] }); setSecret(res.deviceSecret); },
    onError: setFormError,
  });
  function submit(e) {
    e.preventDefault();
    setFormError(null);
    const er = {};
    if (!name.trim()) er.name = 'Dê um nome para a caixa.';
    if (!isValidMac(mac)) er.mac = 'Use o formato AA:BB:CC:DD:EE:FF (impresso na placa ou no monitor serial).';
    setErrors(er);
    if (Object.keys(er).length === 0) mut.mutate();
  }
  async function copy() {
    try { await navigator.clipboard.writeText(secret); setCopied(true); } catch { setCopied(false); }
  }
  if (secret) {
    return (
      <Modal title="Caixa cadastrada" onClose={onClose}>
        <p className="muted" style={{ lineHeight: 1.55, marginBottom: 14 }}>
          Esta é a <strong>senha da caixa</strong>. Ela aparece <strong>somente agora</strong>. Copie e coloque em <code>secrets.h</code> (DEVICE_SECRET) no firmware. Não compartilhe nem publique esta senha.
        </p>
        <div className="secret-box"><span>{secret}</span><button type="button" onClick={copy}><Icon name="copy" size={16} /> {copied ? 'Copiado' : 'Copiar'}</button></div>
        <div className="modal-actions"><Button onClick={onClose}>Já guardei a senha</Button></div>
      </Modal>
    );
  }
  return (
    <Modal title="Cadastrar caixa" subtitle="Vincule a sua caixa CronoMed (ESP32) à conta." onClose={onClose}>
      <form onSubmit={submit} noValidate>
        <FormError error={formError} />
        <TextField label="Nome da caixa" value={name} onChange={(e) => setName(e.target.value)} error={errors.name} maxLength={80} />
        <TextField label="Endereço MAC" value={mac} onChange={(e) => setMac(e.target.value.toUpperCase())} error={errors.mac} placeholder="AA:BB:CC:DD:EE:FF" autoComplete="off" />
        <div className="modal-actions">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={mut.isPending}>Cadastrar</Button>
        </div>
      </form>
    </Modal>
  );
}

function RenameModal({ device, onClose }) {
  const qc = useQueryClient();
  const toast = useToast();
  const [name, setName] = useState(device.name);
  const [formError, setFormError] = useState(null);
  const mut = useMutation({
    mutationFn: () => DeviceApi.rename(device.id, name.trim()),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['devices'] }); toast.success('Nome atualizado.'); onClose(); },
    onError: setFormError,
  });
  return (
    <Modal title="Renomear caixa" onClose={onClose} size="sm">
      <form onSubmit={(e) => { e.preventDefault(); if (name.trim()) mut.mutate(); }}>
        <FormError error={formError} />
        <TextField label="Nome da caixa" value={name} onChange={(e) => setName(e.target.value)} maxLength={80} />
        <div className="modal-actions">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={mut.isPending} disabled={!name.trim()}>Salvar</Button>
        </div>
      </form>
    </Modal>
  );
}

export default function Devices() {
  const { patient } = usePatient();
  const qc = useQueryClient();
  const toast = useToast();
  useSubtitle('Dispositivos');
  const devices = useDevices(true);
  const meds = useMedications(patient ? patient.id : null);
  const [register, setRegister] = useState(false);
  const [rename, setRename] = useState(null);
  const [revoke, setRevoke] = useState(null);
  const revokeMut = useMutation({
    mutationFn: (id) => DeviceApi.revoke(id),
    onSuccess: () => { qc.invalidateQueries({ queryKey: ['devices'] }); toast.success('Caixa desvinculada.'); setRevoke(null); },
    onError: (e) => { setRevoke(null); toast.error(errorMessage(e, 'Não foi possível desvincular a caixa.')); },
  });
  const now = new Date();
  const list = devices.data || [];
  const medByCompartment = new Map((meds.data || []).filter((m) => m.active !== false && !m.deleted).map((m) => [m.compartmentId, m]));

  return (
    <div className="page">
      <div className="share-head">
        <h2>Minhas caixas</h2>
        <Button onClick={() => setRegister(true)}><Icon name="plus" size={18} /> Cadastrar caixa</Button>
      </div>
      <ErrorNotice error={devices.error} onRetry={devices.refetch} />
      {devices.isLoading ? <Skeleton height={320} /> : list.length === 0 ? (
        <EmptyState icon="box" title="Nenhuma caixa cadastrada" action={<Button onClick={() => setRegister(true)}>Cadastrar caixa</Button>}>
          Informe o endereço MAC da sua caixa para receber a senha de conexão.
        </EmptyState>
      ) : list.map((d) => (
        <div key={d.id} className="dev-layout" style={{ marginBottom: 24 }}>
          <div className="card dev-card">
            <div className="dev-head">
              <div className="grow"><h2>{d.name}</h2><p>{d.macAddress}</p></div>
              {d.status === 'REVOKED' ? <Badge tone="neutral">Desvinculada</Badge> : <Badge tone={d.online ? 'success' : 'neutral'} icon="wifi">{d.online ? 'Online' : 'Offline'}</Badge>}
            </div>
            <div className="dev-art"><img src={boxImg} alt="Caixa organizadora CronoMed com seis compartimentos" /></div>
            <div className="tiles">
              <div className="tile"><span className="label">Bateria</span><strong>{d.batteryPercent === null || d.batteryPercent === undefined ? '—' : `${d.batteryPercent}%`}</strong></div>
              <div className="tile"><span className="label">Última sincronização</span><strong style={{ fontSize: '1.05rem' }}>{relativeFromNow(d.lastSeenAt, now)}</strong></div>
            </div>
            {d.status === 'ACTIVE' && (
              <div className="dev-actions">
                <Button variant="outline" onClick={() => setRename(d)}><Icon name="edit" size={16} /> Renomear</Button>
                <Button variant="danger" onClick={() => setRevoke(d)}>Desvincular</Button>
              </div>
            )}
          </div>
          <div className="card dev-card">
            <h2 style={{ fontSize: '1.05rem', marginBottom: 14 }}>Compartimentos</h2>
            <div className="sensor-grid">
              {[...d.compartments].sort((a, b) => a.index - b.index).map((c) => {
                const m = medByCompartment.get(c.id);
                return (
                  <div key={c.id} className="sensor">
                    <CompartmentIcon index={c.index} size={36} />
                    <span>{m ? m.name : 'Vazio'}</span>
                  </div>
                );
              })}
            </div>
            <p className="muted" style={{ fontSize: '.85rem', lineHeight: 1.5 }}>Cada compartimento tem cor, forma e número, para identificar o medicamento sem depender só da cor.</p>
          </div>
        </div>
      ))}
      {register && <RegisterModal onClose={() => setRegister(false)} />}
      {rename && <RenameModal device={rename} onClose={() => setRename(null)} />}
      {revoke && (
        <ConfirmDialog title="Desvincular caixa?" message={`${revoke.name} deixará de se comunicar com o servidor. Para usar de novo será preciso cadastrar e gerar uma nova senha.`}
          confirmLabel="Desvincular" loading={revokeMut.isPending} onConfirm={() => revokeMut.mutate(revoke.id)} onCancel={() => setRevoke(null)} />
      )}
    </div>
  );
}
