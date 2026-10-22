import { useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import MedIcon from '../components/MedIcon.jsx';
import { PatientRequired } from '../components/RouteGuards.jsx';
import { Badge, Button, EmptyState, ErrorNotice, FormError, Modal, Skeleton, TextField } from '../components/ui.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { useCompartmentIndex } from '../hooks/queries.js';
import { MedicationApi } from '../lib/api.js';
import { stockTone } from '../lib/doseView.js';
import { formatQty, pluralDays } from '../lib/format.js';

function RefillModal({ med, onClose }) {
  const qc = useQueryClient();
  const toast = useToast();
  const { patient } = usePatient();
  const [qty, setQty] = useState('');
  const [err, setErr] = useState('');
  const [formError, setFormError] = useState(null);
  const mut = useMutation({
    mutationFn: () => MedicationApi.refill(med.id, Number(qty)),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['medications', patient.id] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      toast.success(`Estoque de ${med.name} atualizado.`);
      onClose();
    },
    onError: setFormError,
  });
  function submit(e) {
    e.preventDefault();
    setFormError(null);
    const n = Number(qty);
    if (!Number.isInteger(n) || n < 1 || n > 100000) return setErr('Informe um número inteiro maior que zero.');
    setErr('');
    mut.mutate();
  }
  return (
    <Modal title={`Reabastecer ${med.name}`} subtitle={`Estoque atual: ${formatQty(med.stockCurrent, med.measure)}`} onClose={onClose} size="sm">
      <form onSubmit={submit} noValidate>
        <FormError error={formError} />
        <TextField label={`Quantidade comprada (${med.measure || 'unidades'})`} type="number" min="1" inputMode="numeric" value={qty} onChange={(e) => setQty(e.target.value)} error={err} />
        <div className="modal-actions">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" loading={mut.isPending}>Adicionar ao estoque</Button>
        </div>
      </form>
    </Modal>
  );
}

function StockInner() {
  const { patient, can, isOwner } = usePatient();
  useSubtitle('Estoque de medicamentos');
  const { meds, byMed, isLoading } = useCompartmentIndex(patient.id, isOwner);
  const [refill, setRefill] = useState(null);
  const list = meds.filter((m) => m.active !== false && !m.deleted);

  return (
    <div className="page">
      {isLoading ? (
        <div className="stock-list"><Skeleton height={84} /><Skeleton height={84} /></div>
      ) : list.length === 0 ? (
        <EmptyState icon="box" title="Nenhum medicamento cadastrado" action={can('MANAGE') ? <Link className="btn btn-primary" to="/app/medicamentos">Cadastrar medicamento</Link> : null}>
          O estoque de cada medicamento aparece aqui assim que ele for cadastrado.
        </EmptyState>
      ) : (
        <div className="stock-list">
          {list.map((m) => {
            const t = stockTone(m);
            const total = Math.max(m.stockAcquired || 0, m.stockCurrent || 0, 1);
            const pct = Math.min(100, Math.round(((m.stockCurrent || 0) / total) * 100));
            return (
              <div key={m.id} className="card stock-row">
                <MedIcon index={byMed.get(m.id)} size={44} />
                <div>
                  <h3>{m.name}{m.dosage ? ` ${m.dosage}` : ''}</h3>
                  <p className="muted" style={{ fontSize: '.85rem' }}>{formatQty(m.doseQuantity, m.measure)} por dose</p>
                </div>
                <div className="bar">
                  <div className="meta">
                    <span>{formatQty(m.stockCurrent, m.measure)}</span>
                    <span className="muted">{m.daysRemaining === null || m.daysRemaining === undefined ? '' : `${pluralDays(m.daysRemaining)} restantes`}</span>
                  </div>
                  <div className={`progress ${t.bar}`} role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct} aria-label={`Estoque de ${m.name}`}>
                    <span style={{ width: `${pct}%` }} />
                  </div>
                </div>
                <Badge tone={t.tone}>{t.label}</Badge>
                <Button variant="outline" size="sm" disabled={!can('MANAGE')} onClick={() => setRefill(m)}>Reabastecer</Button>
              </div>
            );
          })}
        </div>
      )}
      <p className="foot-note">O estoque diminui quando a dose é confirmada. O aviso aparece quando restam {pluralDays(5)} ou o prazo definido em cada medicamento.</p>
      {refill && <RefillModal med={refill} onClose={() => setRefill(null)} />}
    </div>
  );
}

export default function Stock() {
  return (
    <PatientRequired>
      <StockInner />
    </PatientRequired>
  );
}
