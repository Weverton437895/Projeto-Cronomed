import { useMemo, useState } from 'react';
import { PatientRequired } from '../components/RouteGuards.jsx';
import MedIcon from '../components/MedIcon.jsx';
import Icon from '../components/Icon.jsx';
import { Badge, Button, EmptyState, ErrorNotice, Skeleton } from '../components/ui.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { useCompartmentIndex, useDashboard, useDoseHistory } from '../hooks/queries.js';
import { DashboardApi } from '../lib/api.js';
import { saveBlob } from '../lib/download.js';
import { errorMessage } from '../lib/errors.js';
import { daysAgo, formatShortDate, formatTime } from '../lib/format.js';
import { resultBadge } from '../lib/doseView.js';

const PERIODS = [
  { key: '30', label: '30 dias', days: 30 },
  { key: '7', label: '7 dias', days: 7 },
  { key: 'all', label: 'Todos', days: 365 * 5 },
];

function HistoryInner() {
  const { patient, tz, isOwner } = usePatient();
  const toast = useToast();
  useSubtitle('Histórico de doses');
  const [period, setPeriod] = useState('30');
  const [page, setPage] = useState(0);
  const [exporting, setExporting] = useState(false);
  const { from, to } = useMemo(() => {
    const p = PERIODS.find((x) => x.key === period);
    const now = new Date();
    return { from: daysAgo(p.days, now).toISOString(), to: now.toISOString() };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [period]);

  const dash = useDashboard(patient.id, from, to);
  const hist = useDoseHistory(patient.id, from, to, page, 15);
  const { byMed } = useCompartmentIndex(patient.id, isOwner);
  const s = dash.data && dash.data.summary;
  const total = s ? s.taken + s.missed + s.declined + s.openedUnconfirmed : 0;
  const pct = s && s.adherenceRate !== null && s.adherenceRate !== undefined ? Math.round(s.adherenceRate * 100) : null;
  const data = hist.data;

  async function exportCsv() {
    setExporting(true);
    try {
      const blob = await DashboardApi.report(patient.id, from, to);
      saveBlob(blob, `cronomed-historico-${new Date().toISOString().slice(0, 10)}.csv`);
    } catch (e) {
      toast.error(errorMessage(e, 'Não foi possível exportar o histórico.'));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="page">
      <ErrorNotice error={dash.error || hist.error} onRetry={() => { dash.refetch(); hist.refetch(); }} />
      <div className="stat-trio">
        <div className="card"><p className="muted">Doses registradas</p><p className="num">{s ? total : '—'}</p></div>
        <div className="card"><p className="muted">Doses perdidas</p><p className="num red">{s ? s.missed : '—'}</p></div>
        <div className="card"><p className="muted">Adesão ao tratamento</p><p className="num blue">{pct === null ? '—' : `${pct}%`}</p></div>
      </div>

      <div className="filters">
        {PERIODS.map((p) => (
          <button key={p.key} type="button" className="pill-btn" aria-pressed={period === p.key} onClick={() => { setPeriod(p.key); setPage(0); }}>
            {p.label}
          </button>
        ))}
        <span style={{ marginLeft: 'auto' }} />
        <Button variant="outline" size="sm" loading={exporting} onClick={exportCsv}><Icon name="download" size={16} /> Exportar CSV</Button>
      </div>

      {hist.isLoading ? (
        <Skeleton height={300} />
      ) : !data || data.items.length === 0 ? (
        <EmptyState icon="history" title="Sem doses neste período">Quando houver doses agendadas, o resultado de cada uma aparece aqui.</EmptyState>
      ) : (
        <div className="card table-wrap" style={{ padding: 0 }}>
          <table className="table">
            <thead>
              <tr><th>Medicamento</th><th>Data e hora</th><th>Caixa</th><th>App</th><th>Resultado</th></tr>
            </thead>
            <tbody>
              {data.items.map((d) => {
                const r = resultBadge(d);
                return (
                  <tr key={d.id}>
                    <td><div className="med"><MedIcon index={byMed.get(d.medicationId)} size={32} /><span>{d.medicationName} {d.dosage}</span></div></td>
                    <td>{formatShortDate(d.scheduledAt, tz)} · {formatTime(d.scheduledAt, tz)}</td>
                    <td>{d.openedAt ? <><Icon name="check" size={14} /> Abriu às {formatTime(d.openedAt, tz)}</> : <span className="muted">Não abriu</span>}</td>
                    <td>{d.confirmedAt ? <><Icon name="check" size={14} /> Confirmou às {formatTime(d.confirmedAt, tz)}</> : <span className="muted">Sem confirmação</span>}</td>
                    <td><Badge tone={r.tone}>{r.label}</Badge></td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="pager">
            <span>Página {data.page + 1} de {Math.max(1, data.totalPages)} · {data.totalElements} doses</span>
            <span style={{ display: 'flex', gap: 8 }}>
              <Button variant="outline" size="sm" disabled={page <= 0} onClick={() => setPage((p) => p - 1)}>Anterior</Button>
              <Button variant="outline" size="sm" disabled={page + 1 >= data.totalPages} onClick={() => setPage((p) => p + 1)}>Próxima</Button>
            </span>
          </div>
        </div>
      )}
      <p className="foot-note">A coluna “Caixa” mostra quando o compartimento foi aberto; “App” mostra quando a dose foi confirmada. Doses abertas e não confirmadas não dão baixa no estoque.</p>
    </div>
  );
}

export default function History() {
  return (
    <PatientRequired>
      <HistoryInner />
    </PatientRequired>
  );
}
