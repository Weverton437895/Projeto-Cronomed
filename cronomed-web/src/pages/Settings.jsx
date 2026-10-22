import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useMutation } from '@tanstack/react-query';
import Icon from '../components/Icon.jsx';
import { Button, ConfirmDialog, FormError, Modal, PasswordField, Toggle } from '../components/ui.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { usePrefs } from '../context/PrefsContext.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { useSubtitle } from '../context/HeaderContext.jsx';
import { DashboardApi, UserApi } from '../lib/api.js';
import { saveBlob } from '../lib/download.js';
import { errorMessage } from '../lib/errors.js';
import { daysAgo, pluralDays } from '../lib/format.js';
import { canListen, canSpeak, speak } from '../lib/voice.js';

const SIZES = [
  { key: 'normal', label: 'A' },
  { key: 'large', label: 'A+' },
  { key: 'xlarge', label: 'A++' },
];

function Setting({ title, desc, children }) {
  return (
    <div className="setting">
      <div><strong>{title}</strong>{desc && <span className="d">{desc}</span>}</div>
      {children}
    </div>
  );
}

function DeleteAccountModal({ onClose }) {
  const { logout } = useAuth();
  const navigate = useNavigate();
  const toast = useToast();
  const [password, setPassword] = useState('');
  const [formError, setFormError] = useState(null);
  const mut = useMutation({
    mutationFn: () => UserApi.remove(password),
    onSuccess: async () => { await logout(); toast.success('Conta excluída.'); navigate('/', { replace: true }); },
    onError: setFormError,
  });
  return (
    <Modal title="Excluir minha conta" subtitle="Esta ação não pode ser desfeita." onClose={onClose}>
      <form onSubmit={(e) => { e.preventDefault(); setFormError(null); if (password) mut.mutate(); }}>
        <p className="muted" style={{ lineHeight: 1.55, marginBottom: 14 }}>Os seus dados pessoais, medicamentos, histórico e compartilhamentos serão apagados, conforme a LGPD. Confirme com a sua senha.</p>
        <FormError error={formError} />
        <PasswordField label="Senha atual" value={password} onChange={(e) => setPassword(e.target.value)} autoComplete="current-password" />
        <div className="modal-actions">
          <Button variant="outline" onClick={onClose}>Cancelar</Button>
          <Button type="submit" variant="danger-solid" loading={mut.isPending} disabled={!password}>Excluir definitivamente</Button>
        </div>
      </form>
    </Modal>
  );
}

export default function Settings() {
  const { prefs, setPref } = usePrefs();
  const { patient } = usePatient();
  const toast = useToast();
  useSubtitle('Configurações');
  const [exporting, setExporting] = useState(false);
  const [del, setDel] = useState(false);

  async function exportData() {
    if (!patient) return;
    setExporting(true);
    try {
      const blob = await DashboardApi.report(patient.id, daysAgo(365 * 5).toISOString(), new Date().toISOString());
      saveBlob(blob, `cronomed-meus-dados-${new Date().toISOString().slice(0, 10)}.csv`);
    } catch (e) {
      toast.error(errorMessage(e, 'Não foi possível exportar os dados.'));
    } finally {
      setExporting(false);
    }
  }

  return (
    <div className="page">
      <div className="settings-layout">
        <div className="settings-col">
          <section className="card settings-card" aria-labelledby="h-acess">
            <h2 id="h-acess">Acessibilidade</h2>
            <Setting title="Alto contraste" desc="Cores com contraste reforçado">
              <Toggle checked={prefs.highContrast} onChange={(v) => setPref('highContrast', v)} label="Alto contraste" />
            </Setting>
            <Setting title="Tamanho do texto" desc="Aumenta textos e botões">
              <div className="seg-sm" role="group" aria-label="Tamanho do texto">
                {SIZES.map((s) => (
                  <button key={s.key} type="button" aria-pressed={prefs.fontSize === s.key} onClick={() => setPref('fontSize', s.key)}>{s.label}</button>
                ))}
              </div>
            </Setting>
            <Setting title="Narração por voz" desc={canSpeak() ? 'Lê o lembrete da dose em voz alta' : 'Indisponível neste navegador'}>
              <Toggle checked={prefs.voiceNarration} disabled={!canSpeak()} onChange={(v) => { setPref('voiceNarration', v); if (v) speak('Narração ativada.'); }} label="Narração por voz" />
            </Setting>
            <Setting title="Comando de voz" desc={canListen() ? 'Responda “sim, tomei” ou “ainda não”' : 'Indisponível neste navegador (use Chrome ou Edge)'}>
              <Toggle checked={prefs.voiceCommand} disabled={!canListen()} onChange={(v) => setPref('voiceCommand', v)} label="Comando de voz" />
            </Setting>
          </section>
        </div>
        <div className="settings-col">
          <section className="card settings-card" aria-labelledby="h-alert">
            <h2 id="h-alert">Alertas</h2>
            <Setting title="Aviso de estoque baixo" desc="Avisar quando restar menos que">
              <div className="stepper">
                <button type="button" aria-label="Diminuir dias" onClick={() => setPref('stockDays', Math.max(1, prefs.stockDays - 1))}>−</button>
                <output aria-live="polite">{pluralDays(prefs.stockDays)}</output>
                <button type="button" aria-label="Aumentar dias" onClick={() => setPref('stockDays', Math.min(30, prefs.stockDays + 1))}>+</button>
              </div>
            </Setting>
            <Setting title="Lembretes de dose" desc="Mostrar o lembrete na tela na hora da dose">
              <Toggle checked={prefs.doseReminders} onChange={(v) => setPref('doseReminders', v)} label="Lembretes de dose" />
            </Setting>
            <Setting title="Bateria da caixa" desc="Avisar quando a bateria estiver baixa">
              <Toggle checked={prefs.batteryAlert} onChange={(v) => setPref('batteryAlert', v)} label="Aviso de bateria" />
            </Setting>
          </section>
          <section className="card settings-card" aria-labelledby="h-lgpd">
            <h2 id="h-lgpd">Privacidade e dados (LGPD)</h2>
            <Setting title="Exportar meus dados" desc="Baixe o histórico de doses em CSV">
              <Button variant="outline" size="sm" loading={exporting} onClick={exportData}><Icon name="download" size={16} /> Exportar</Button>
            </Setting>
            <Setting title="Excluir minha conta" desc="Apaga a conta e todos os dados">
              <Button variant="danger" size="sm" onClick={() => setDel(true)}><Icon name="trash" size={16} /> Excluir</Button>
            </Setting>
          </section>
        </div>
      </div>
      <p className="foot-note">As preferências de acessibilidade e alertas ficam salvas neste navegador.</p>
      {del && <DeleteAccountModal onClose={() => setDel(false)} />}
    </div>
  );
}
