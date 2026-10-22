import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Icon from '../components/Icon.jsx';
import MedIcon from '../components/MedIcon.jsx';
import { PatientRequired } from '../components/RouteGuards.jsx';
import { Button, ConfirmDialog, EmptyState, ErrorNotice, Field, FormError, Skeleton, TextField } from '../components/ui.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { useDevices, useMedications } from '../hooks/queries.js';
import { DeviceApi, MedicationApi } from '../lib/api.js';
import { COMPARTMENTS } from '../lib/compartments.js';
import { errorMessage } from '../lib/errors.js';
import { formatQty } from '../lib/format.js';
import { isValidTime } from '../lib/validators.js';

const DAY_LABELS = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'];
const DAY_NAMES = ['Domingo', 'Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado'];
// Backend: 1 = segunda ... 7 = domingo (ISO-8601).
const DAY_ISO = [7, 1, 2, 3, 4, 5, 6];
const MEASURES = ['comprimido', 'cápsula', 'gota', 'ml', 'sachê', 'ampola', 'dose'];

function emptyForm() {
  return {
    name: '', dosage: '', measure: 'comprimido', doseQuantity: '1', stockAcquired: '', minDaysAlert: '5',
    observations: '', times: [], days: [1, 2, 3, 4, 5, 6, 7], active: true,
  };
}

function fromMedication(m) {
  const first = (m.schedules || [])[0];
  return {
    name: m.name, dosage: m.dosage || '', measure: m.measure || 'comprimido', doseQuantity: String(m.doseQuantity),
    stockAcquired: String(m.stockCurrent ?? m.stockAcquired ?? ''), minDaysAlert: String(m.minDaysAlert ?? 5),
    observations: m.observations || '',
    times: Array.from(new Set((m.schedules || []).map((s) => String(s.time).slice(0, 5)))).sort(),
    days: first && first.daysOfWeek && first.daysOfWeek.length ? [...first.daysOfWeek] : [1, 2, 3, 4, 5, 6, 7],
    active: m.active,
  };
}

function MedForm({ index, med, compartment, patient, onSaved, onCancel }) {
  const toast = useToast();
  const qc = useQueryClient();
  const [f, setF] = useState(() => (med ? fromMedication(med) : emptyForm()));
  const [newTime, setNewTime] = useState('');
  const [errors, setErrors] = useState({});
  const [formError, setFormError] = useState(null);
  const [confirmDelete, setConfirmDelete] = useState(false);
  const set = (k) => (e) => setF((s) => ({ ...s, [k]: e.target.value }));

  useEffect(() => {
    setF(med ? fromMedication(med) : emptyForm());
    setErrors({});
    setFormError(null);
  }, [med, compartment && compartment.id]);

  function addTime() {
    if (!isValidTime(newTime)) return setErrors((e) => ({ ...e, times: 'Informe um horário válido (ex.: 08:00).' }));
    if (f.times.includes(newTime)) return setNewTime('');
    setF((s) => ({ ...s, times: [...s.times, newTime].sort() }));
    setNewTime('');
    setErrors((e) => ({ ...e, times: undefined }));
  }
  function toggleDay(iso) {
    setF((s) => ({ ...s, days: s.days.includes(iso) ? s.days.filter((d) => d !== iso) : [...s.days, iso].sort() }));
  }

  function validate() {
    const e = {};
    if (!f.name.trim()) e.name = 'Informe o nome do medicamento.';
    const qty = Number(String(f.doseQuantity).replace(',', '.'));
    if (!f.dosage.trim()) e.dosage = 'Informe a dosagem (ex.: 50 mg).';
    if (!Number.isInteger(qty) || qty < 1 || qty > 100) e.doseQuantity = 'Use um número inteiro de 1 a 100.';
    if (!med && (f.stockAcquired === '' || Number(f.stockAcquired) < 0)) e.stockAcquired = 'Informe a quantidade em estoque.';
    if (f.times.length === 0) e.times = 'Adicione ao menos um horário.';
    if (f.days.length === 0) e.days = 'Escolha ao menos um dia da semana.';
    setErrors(e);
    return Object.keys(e).length === 0;
  }

  const save = useMutation({
    mutationFn: async () => {
      // A caixa precisa estar vinculada ao paciente para que as doses cheguem a ela.
      if (compartment && compartment.patientId !== patient.id) {
        await DeviceApi.updateCompartment(compartment.deviceId, compartment.id, { patientId: patient.id });
      }
      const body = {
        name: f.name.trim(),
        measure: f.measure,
        dosage: f.dosage.trim(),
        doseQuantity: Number(f.doseQuantity),
        stockAcquired: med ? med.stockAcquired : Number(f.stockAcquired),
        minDaysAlert: Number(f.minDaysAlert) || 0,
        observations: f.observations.trim() || null,
        compartmentId: compartment ? compartment.id : med ? med.compartmentId : null,
        active: f.active,
        schedules: f.times.map((t) => ({ time: t, daysOfWeek: f.days })),
        clientUpdatedAt: new Date().toISOString(),
      };
      if (med) return MedicationApi.update(med.id, { ...body, version: med.version });
      return MedicationApi.create(patient.id, body);
    },
    onSuccess: (saved) => {
      qc.invalidateQueries({ queryKey: ['medications', patient.id] });
      qc.invalidateQueries({ queryKey: ['devices'] });
      qc.invalidateQueries({ queryKey: ['doses'] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      toast.success(med ? 'Medicamento atualizado.' : 'Medicamento cadastrado.');
      onSaved(saved);
    },
    onError: (e) => {
      if (e && e.code === 'STALE_DATA') {
        qc.invalidateQueries({ queryKey: ['medications', patient.id] });
        setFormError('Este medicamento foi alterado por outra pessoa ou pela caixa. Os dados foram recarregados; revise e salve de novo.');
      } else {
        setFormError(e);
      }
    },
  });

  const remove = useMutation({
    mutationFn: () => MedicationApi.remove(med.id),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['medications', patient.id] });
      qc.invalidateQueries({ queryKey: ['doses'] });
      toast.success('Medicamento excluído.');
      setConfirmDelete(false);
      onSaved(null);
    },
    onError: (e) => {
      setConfirmDelete(false);
      setFormError(e);
    },
  });

  function onSubmit(ev) {
    ev.preventDefault();
    setFormError(null);
    if (validate()) save.mutate();
  }

  const unitLabel = f.measure || 'unidade';
  return (
    <form className="card card-form" onSubmit={onSubmit} noValidate>
      <div className="heading">
        <MedIcon index={index} size={40} />
        <span>{med ? `Editar medicamento${index ? ` · Compartimento ${index}` : ''}` : index ? `Cadastrar medicamento · Compartimento ${index}` : 'Cadastrar medicamento'}</span>
      </div>
      <FormError error={formError} />
      <div className="form-grid">
        <TextField label="Nome do medicamento" value={f.name} onChange={set('name')} error={errors.name} placeholder="Ex.: Losartana" maxLength={120} />
        <TextField label="Dosagem" value={f.dosage} onChange={set('dosage')} error={errors.dosage} placeholder="Ex.: 50 mg" maxLength={60} />
        <Field label="Quantidade por dose" error={errors.doseQuantity}>
          <div className="unit-row">
            <input className="input" type="number" min="1" max="100" inputMode="numeric" value={f.doseQuantity} onChange={set('doseQuantity')} aria-label="Quantidade por dose" />
            <select className="select" value={f.measure} onChange={set('measure')} aria-label="Unidade de medida">
              {MEASURES.map((m) => <option key={m} value={m}>{m}</option>)}
            </select>
          </div>
        </Field>
        {med ? (
          <TextField label={`Estoque atual (${unitLabel}s)`} value={f.stockAcquired} readOnly locked
            hint="O estoque muda ao confirmar doses. Para somar uma compra, use “Reabastecer” em Estoque." />
        ) : (
          <TextField label={`Estoque adquirido (${unitLabel}s)`} type="number" min="0" inputMode="numeric" value={f.stockAcquired}
            onChange={set('stockAcquired')} error={errors.stockAcquired} />
        )}
        <Field label="Horários" error={errors.times} className="span-2">
          <div className="time-chips">
            {f.times.map((t) => (
              <span key={t} className="chip chip-time">
                {t}
                <button type="button" aria-label={`Remover horário ${t}`} onClick={() => setF((s) => ({ ...s, times: s.times.filter((x) => x !== t) }))}>
                  <Icon name="x" size={14} />
                </button>
              </span>
            ))}
            <input type="time" className="time-input" value={newTime} onChange={(e) => setNewTime(e.target.value)} aria-label="Novo horário"
              onKeyDown={(e) => { if (e.key === 'Enter') { e.preventDefault(); addTime(); } }} />
            <button type="button" className="chip chip-add" onClick={addTime}><Icon name="plus" size={14} /> Adicionar horário</button>
          </div>
        </Field>
        <Field label="Dias da semana" error={errors.days} className="span-2">
          <div className="days-row" role="group" aria-label="Dias da semana">
            {DAY_ISO.map((iso, i) => (
              <button key={iso} type="button" className="day" aria-pressed={f.days.includes(iso)} aria-label={DAY_NAMES[i]} onClick={() => toggleDay(iso)}>
                {DAY_LABELS[i]}
              </button>
            ))}
          </div>
        </Field>
        <TextField label="Avisar quando restar (dias)" type="number" min="0" max="365" inputMode="numeric" value={f.minDaysAlert} onChange={set('minDaysAlert')} />
        <Field label="Observações" className="span-2">
          <textarea className="textarea" rows={2} value={f.observations} onChange={set('observations')} placeholder="Ex.: tomar com água, após o café da manhã" maxLength={500} />
        </Field>
      </div>
      <div className="form-actions">
        {med && <Button variant="danger" className="left" onClick={() => setConfirmDelete(true)}><Icon name="trash" size={18} /> Excluir</Button>}
        <Button variant="outline" onClick={onCancel}>Cancelar</Button>
        <Button type="submit" loading={save.isPending}>{med ? 'Salvar alterações' : 'Cadastrar medicamento'}</Button>
      </div>
      {confirmDelete && (
        <ConfirmDialog
          title="Excluir medicamento?"
          message={`${med.name} deixará de ser lembrado e as próximas doses serão canceladas. O histórico é mantido.`}
          confirmLabel="Excluir"
          loading={remove.isPending}
          onConfirm={() => remove.mutate()}
          onCancel={() => setConfirmDelete(false)}
        />
      )}
    </form>
  );
}

function MedicationsInner() {
  const { patient, isOwner } = usePatient();
  useSubtitle('Gerenciar medicamentos');
  const meds = useMedications(patient.id);
  const devices = useDevices(isOwner);
  const [selected, setSelected] = useState(null); // {compartmentId?: string, medId?: string, fresh?: true}

  const device = (devices.data || []).find((d) => d.status === 'ACTIVE') || null;
  const slots = useMemo(() => {
    const active = (meds.data || []).filter((m) => m.active !== false && !m.deleted);
    if (device) {
      return [...device.compartments]
        .sort((a, b) => a.index - b.index)
        .map((c) => ({ key: c.id, index: c.index, compartment: { ...c, deviceId: device.id }, med: active.find((m) => m.compartmentId === c.id) || null }));
    }
    return active.map((m) => ({ key: m.id, index: null, compartment: null, med: m }));
  }, [device, meds.data]);

  const current = selected
    ? slots.find((s) => s.key === selected) || (selected === 'new' ? { key: 'new', index: null, compartment: null, med: null } : null)
    : null;
  const loading = meds.isLoading || (isOwner && devices.isLoading);
  const inactive = (meds.data || []).filter((m) => m.active === false && !m.deleted);

  return (
    <div className="page">
      <ErrorNotice error={meds.error || devices.error} onRetry={() => { meds.refetch(); devices.refetch(); }} />
      {isOwner && !loading && !device && (
        <div className="banner" role="status">
          <Icon name="info" size={20} />
          <span className="grow">Nenhuma caixa cadastrada. Cadastre a sua caixa para vincular cada medicamento a um compartimento.</span>
          <Link to="/app/dispositivos">Cadastrar caixa</Link>
        </div>
      )}
      {loading ? (
        <div className="cmp-grid"><Skeleton height={150} /><Skeleton height={150} /><Skeleton height={150} /></div>
      ) : (
        <>
          <div className="cmp-grid">
            {slots.map((s) => (
              <div key={s.key} className={`card cmp-card${s.med ? '' : ' empty-slot'}`}>
                <div className="row">
                  {s.index ? <MedIcon index={s.index} size={44} /> : <MedIcon size={44} />}
                  <button type="button" className="link-action" onClick={() => setSelected(s.key)}>
                    {s.med ? 'Editar' : 'Cadastrar'}
                  </button>
                </div>
                {s.index && <p className="sub" style={{ marginBottom: 4 }}>Compartimento {s.index} · {COMPARTMENTS[s.index].name}</p>}
                <h3>{s.med ? `${s.med.name}${s.med.dosage ? ` ${s.med.dosage}` : ''}` : 'Vazio'}</h3>
                <p className="sub">
                  {s.med
                    ? `${formatQty(s.med.doseQuantity, s.med.measure)} · ${(s.med.schedules || []).map((x) => String(x.time).slice(0, 5)).filter((v, i, a) => a.indexOf(v) === i).join(', ') || 'sem horário'}`
                    : 'Nenhum medicamento neste compartimento'}
                </p>
              </div>
            ))}
            {!device && (
              <button type="button" className="card cmp-card empty-slot" onClick={() => setSelected('new')} style={{ textAlign: 'left' }}>
                <div className="row"><MedIcon size={44} /><span className="link-action">Cadastrar</span></div>
                <h3>Novo medicamento</h3>
                <p className="sub">Adicionar um medicamento ao tratamento</p>
              </button>
            )}
          </div>
          {slots.length === 0 && device === null && !isOwner && (meds.data || []).length === 0 && (
            <EmptyState icon="pill" title="Nenhum medicamento">Cadastre o primeiro medicamento pelo cartão “Novo medicamento”.</EmptyState>
          )}
          {inactive.length > 0 && (
            <p className="foot-note">{inactive.length} medicamento{inactive.length > 1 ? 's' : ''} desativado{inactive.length > 1 ? 's' : ''} não aparece{inactive.length > 1 ? 'm' : ''} nos compartimentos.</p>
          )}
          {current ? (
            <div style={{ marginTop: 28 }}>
              <MedForm
                index={current.index}
                med={current.med}
                compartment={current.compartment}
                patient={patient}
                onSaved={() => setSelected(null)}
                onCancel={() => setSelected(null)}
              />
            </div>
          ) : (
            <p className="foot-note" style={{ marginTop: 22 }}>Selecione um compartimento para cadastrar ou editar o medicamento.</p>
          )}
        </>
      )}
    </div>
  );
}

export default function Medications() {
  return (
    <PatientRequired>
      <MedicationsInner />
    </PatientRequired>
  );
}
