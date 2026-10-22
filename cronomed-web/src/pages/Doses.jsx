import { useEffect, useMemo, useState } from 'react';
import { Link } from 'react-router-dom';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import Icon from '../components/Icon.jsx';
import MedIcon from '../components/MedIcon.jsx';
import CompartmentIcon from '../components/CompartmentIcon.jsx';
import { PatientRequired } from '../components/RouteGuards.jsx';
import { Badge, Button, EmptyState, ErrorNotice, Modal, Skeleton } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { usePrefs } from '../context/PrefsContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { useCompartmentIndex, useDashboard, useDevices, useTodayDoses } from '../hooks/queries.js';
import { useNow } from '../hooks/useNow.js';
import { DoseApi } from '../lib/api.js';
import { daysAgo, firstName, formatLongDate, formatQty, formatTime, relativeFromNow } from '../lib/format.js';
import { doseBadge, isDue, reminderInfo, stockAlertMessage } from '../lib/doseView.js';
import { uuid } from '../lib/ids.js';
import { errorMessage } from '../lib/errors.js';
import { canListen, canSpeak, interpretAnswer, listenOnce, speak, stopSpeaking } from '../lib/voice.js';

function ReminderModal({ dose, med, index, tz, now, canConfirm, voiceEnabled, narrate, busy, onAnswer, onClose }) {
  const info = reminderInfo(dose, now);
  const [listening, setListening] = useState(false);
  const toast = useToast();
  const phrase = `${dose.medicationName} ${dose.dosage}. ${formatQty(dose.doseQuantity, med ? med.measure : '')}, às ${formatTime(dose.scheduledAt, tz)}${index ? `, compartimento ${index}` : ''}.`;

  useEffect(() => {
    if (narrate) speak(phrase);
    return () => stopSpeaking();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  async function listen() {
    if (listening) return;
    setListening(true);
    try {
      const text = await listenOnce();
      const answer = interpretAnswer(text);
      if (answer === 'yes') onAnswer('CONFIRMED_YES');
      else if (answer === 'no') onAnswer('POSTPONED');
      else toast.info('Não entendi. Use os botões para responder.');
    } catch (e) {
      toast.info(e.message === 'no-support' ? 'Seu navegador não permite voz. Use os botões.' : 'Não consegui ouvir. Use os botões para responder.');
    } finally {
      setListening(false);
    }
  }

  return (
    <Modal onClose={onClose} size="sm" labelledBy="lembrete-titulo">
      <div className="reminder">
        <span className="badge badge-warning pill">
          <Icon name="bell" size={14} strokeWidth={2.4} /> Lembrete {info.index} de {info.total}{info.last ? '' : ` · próximo em ${info.nextIn} min`}
        </span>
        <div className="cmp-big"><MedIcon index={index} size={56} /></div>
        <p className="small">{index ? `Compartimento ${index} · ` : ''}{formatTime(dose.scheduledAt, tz)}</p>
        <h2 id="lembrete-titulo">{dose.medicationName} {dose.dosage}</h2>
        <p className="small" style={{ fontSize: '.95rem' }}>
          {formatQty(dose.doseQuantity, med ? med.measure : '')}
          {med && med.observations ? ` · ${med.observations.replace(/\.$/, '').toLowerCase()}` : ''}
        </p>
        {dose.openedAt && (
          <div className="note">
            <Icon name="briefcase" size={18} />
            <span>Caixa aberta às {formatTime(dose.openedAt, tz)}. Falta a confirmação para dar baixa no estoque.</span>
          </div>
        )}
        {canConfirm ? (
          <>
            <p className="ask" style={{ marginTop: dose.openedAt ? 0 : 22 }}>A dose foi tomada?</p>
            <div className="answers">
              <Button variant="outline" size="lg" disabled={busy} onClick={() => onAnswer('POSTPONED')}>Ainda não</Button>
              <Button size="lg" loading={busy} onClick={() => onAnswer('CONFIRMED_YES')}>Sim, tomei</Button>
            </div>
            {voiceEnabled && canListen() ? (
              <button type="button" className="voice" aria-pressed={listening} onClick={listen}>
                <Icon name="mic" size={18} /> {listening ? 'Ouvindo… diga “sim, tomei” ou “ainda não”' : 'Confirmação por voz disponível'}
              </button>
            ) : (
              <p className="voice muted" style={{ justifyContent: 'center' }}>
                <Icon name="mic" size={18} /> {voiceEnabled ? 'Voz indisponível neste navegador' : 'Ative o comando de voz em Configurações'}
              </p>
            )}
          </>
        ) : (
          <p className="muted" style={{ marginTop: 22 }}>Você tem acesso somente para visualização. A confirmação é feita pelo paciente ou por quem gerencia.</p>
        )}
      </div>
    </Modal>
  );
}

function DosesInner() {
  const { patient, tz, can, isOwner } = usePatient();
  const { user } = useAuth();
  const { prefs } = usePrefs();
  const toast = useToast();
  const qc = useQueryClient();
  const now = useNow(30000);
  useSubtitle(formatLongDate(now, tz));

  const doses = useTodayDoses(patient.id, tz);
  const { byMed, meds } = useCompartmentIndex(patient.id, isOwner);
  const devices = useDevices(isOwner);
  const dash = useDashboard(patient.id, useMemo(() => daysAgo(30, new Date()).toISOString(), []), useMemo(() => new Date().toISOString(), []));
  const [openId, setOpenId] = useState(null);
  const [dismissed, setDismissed] = useState(() => new Set());

  const medById = useMemo(() => new Map(meds.map((m) => [m.id, m])), [meds]);
  const list = doses.data || [];
  const openDose = list.find((d) => d.id === openId) || null;
  const canConfirm = can('CONFIRM');

  // Abre o lembrete sozinho quando uma dose esta "no ar" (horario previsto ate o fim da tolerancia).
  useEffect(() => {
    if (openId || !canConfirm || !prefs.doseReminders) return;
    const due = list.find((d) => isDue(d, now) && !dismissed.has(d.id));
    if (due) setOpenId(due.id);
  }, [list, now, openId, dismissed, canConfirm, prefs.doseReminders]);

  useEffect(() => {
    if (openDose && !['PENDING', 'OPENED_UNCONFIRMED'].includes(openDose.status)) setOpenId(null);
  }, [openDose]);

  const record = useMutation({
    mutationFn: ({ dose, type }) =>
      DoseApi.record(dose.id, {
        eventId: uuid(),
        type,
        occurredAt: new Date().toISOString(),
        justification: type === 'POSTPONED' ? 'Paciente informou que ainda não tomou.' : undefined,
      }),
    onSuccess: (res, { type }) => {
      qc.invalidateQueries({ queryKey: ['doses'] });
      qc.invalidateQueries({ queryKey: ['medications', patient.id] });
      qc.invalidateQueries({ queryKey: ['dashboard'] });
      if (res && res.result === 'DUPLICATE') toast.info('Esta resposta já tinha sido registrada.');
      else toast.success(type === 'CONFIRMED_YES' ? 'Dose confirmada. Estoque atualizado.' : 'Ok, vamos lembrar você de novo.');
      setOpenId(null);
    },
    onError: (e) => toast.error(errorMessage(e, 'Não foi possível registrar a resposta.')),
  });

  function closeReminder() {
    if (openId) setDismissed((s) => new Set(s).add(openId));
    setOpenId(null);
  }

  const lowStock = meds.find((m) => m.active && (m.stockStatus === 'LOW' || m.stockStatus === 'ZERO'));
  const stockMsg = lowStock ? stockAlertMessage(lowStock) : null;
  const summary = dash.data && dash.data.summary;
  const closed = summary ? summary.taken + summary.missed + summary.declined + summary.openedUnconfirmed : 0;
  const pct = summary && summary.adherenceRate !== null && summary.adherenceRate !== undefined ? Math.round(summary.adherenceRate * 100) : null;
  const device = (devices.data || []).find((d) => d.status === 'ACTIVE') || null;

  return (
    <div className="page">
      <h2 className="greeting">Olá, {firstName(user.name)}! Confira os horários das próximas medicações.</h2>

      {stockMsg && (
        <div className="banner" role="status">
          <Icon name="alert" size={20} />
          <span className="grow"><strong>Confira o estoque de {stockMsg.bold}</strong>{stockMsg.text}</span>
          <Link to="/app/estoque">Ver estoque</Link>
        </div>
      )}

      <h3 className="section-label">Próximas doses</h3>
      <ErrorNotice error={doses.error} onRetry={doses.refetch} />
      {doses.isLoading ? (
        <div className="dose-grid"><Skeleton height={190} /><Skeleton height={190} /><Skeleton height={190} /></div>
      ) : list.length === 0 && !doses.error ? (
        <EmptyState icon="clock" title="Nenhuma dose para hoje" action={can('MANAGE') ? <Link className="btn btn-primary" to="/app/medicamentos">Cadastrar medicamento</Link> : null}>
          Quando houver medicamentos com horário cadastrado, as doses do dia aparecem aqui.
        </EmptyState>
      ) : (
        <div className="dose-grid">
          {list.map((d) => {
            const idx = byMed.get(d.medicationId);
            const med = medById.get(d.medicationId);
            const badge = doseBadge(d, now);
            const actionable = canConfirm && (d.status === 'PENDING' || d.status === 'OPENED_UNCONFIRMED');
            const Tag = actionable ? 'button' : 'div';
            return (
              <Tag
                key={d.id}
                type={actionable ? 'button' : undefined}
                className={`card dose-card${d.status === 'TAKEN' ? ' done' : ''}`}
                onClick={actionable ? () => { setDismissed((s) => { const n = new Set(s); n.delete(d.id); return n; }); setOpenId(d.id); } : undefined}
                aria-label={actionable ? `Responder sobre ${d.medicationName} às ${formatTime(d.scheduledAt, tz)}` : undefined}
              >
                <div className="row">
                  <MedIcon index={idx} size={44} />
                  <Badge tone={badge.tone} icon={badge.icon}>{badge.label}</Badge>
                </div>
                <h3>{d.medicationName} {d.dosage}</h3>
                <p className="sub">{formatQty(d.doseQuantity, med ? med.measure : '')}{idx ? ` · Compartimento ${idx}` : ''}</p>
                <p className="time">{formatTime(d.scheduledAt, tz)}</p>
              </Tag>
            );
          })}
        </div>
      )}

      <div className="summary-row">
        <div className="card stat-card">
          <p className="label">Adesão ao tratamento</p>
          <div className="stat-line">
            <span className="stat-big">{pct === null ? '—' : `${pct}%`}</span>
            <span className="muted" style={{ fontSize: '.9rem' }}>
              {pct === null ? 'sem doses encerradas ainda' : `últimos 30 dias · ${summary.taken} de ${closed} doses`}
            </span>
          </div>
          <div className="progress lg" role="progressbar" aria-valuemin={0} aria-valuemax={100} aria-valuenow={pct || 0} aria-label="Adesão ao tratamento">
            <span style={{ width: `${pct || 0}%` }} />
          </div>
        </div>
        <div className="card box-card">
          <div className="head">
            <h3>Caixa CronoMed</h3>
            {device ? (
              <Badge tone={device.online ? 'success' : 'neutral'} icon="wifi">{device.online ? 'Online' : 'Offline'}</Badge>
            ) : null}
          </div>
          {device ? (
            <>
              <div className="kv">
                <Icon name="battery" size={20} />
                <span>Bateria {device.batteryPercent === null || device.batteryPercent === undefined ? '—' : `${device.batteryPercent}%`}</span>
              </div>
              <p className="kv-sub">Última sincronização {relativeFromNow(device.lastSeenAt, now)}</p>
            </>
          ) : (
            <p className="muted" style={{ fontSize: '.9rem' }}>{isOwner ? 'Nenhuma caixa cadastrada ainda.' : 'Informações da caixa visíveis ao titular.'}</p>
          )}
        </div>
      </div>

      {openDose && (
        <ReminderModal
          dose={openDose}
          med={medById.get(openDose.medicationId)}
          index={byMed.get(openDose.medicationId)}
          tz={tz}
          now={now}
          canConfirm={canConfirm}
          voiceEnabled={prefs.voiceCommand}
          narrate={prefs.voiceNarration && canSpeak()}
          busy={record.isPending}
          onAnswer={(type) => record.mutate({ dose: openDose, type })}
          onClose={closeReminder}
        />
      )}
    </div>
  );
}

export default function Doses() {
  return (
    <PatientRequired>
      <DosesInner />
    </PatientRequired>
  );
}
