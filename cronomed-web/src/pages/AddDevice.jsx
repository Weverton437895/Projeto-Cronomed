import { useRef, useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQueryClient } from '@tanstack/react-query';
import { DeviceApi } from '../lib/api.js';
import { errorMessage } from '../lib/errors.js';
import { isValidMac, normalizeMac } from '../lib/validators.js';

// UUIDs do pareamento Bluetooth (os mesmos de cronomed-firmware/Provisioning.cpp)
const SERVICE_UUID = '19b10000-e8f2-537e-4f6c-d104768a1214';
const CHAR_MAC_UUID = '19b10000-e8f2-537e-4f6c-d104768a1215';
const CHAR_SSID_UUID = '19b10001-e8f2-537e-4f6c-d104768a1214';
const CHAR_PASS_UUID = '19b10002-e8f2-537e-4f6c-d104768a1214';
const CHAR_SECRET_UUID = '19b10003-e8f2-537e-4f6c-d104768a1214';
const CHAR_BROKER_UUID = '19b10004-e8f2-537e-4f6c-d104768a1214';

const BROKER_KEY = 'cronomed.brokerHost';

/** IP/host do Mosquitto que a CAIXA vai usar. "localhost" nao serve: a caixa precisa do IP do computador na rede. */
function defaultBroker() {
  try {
    const saved = localStorage.getItem(BROKER_KEY);
    if (saved) return saved;
  } catch { /* armazenamento indisponivel */ }
  const envHost = import.meta.env && import.meta.env.VITE_BROKER_HOST;
  if (envHost) return envHost;
  const h = window.location.hostname;
  return h === 'localhost' || h === '127.0.0.1' ? '' : h;
}

const box = { maxWidth: 600, margin: '40px auto', padding: 32, backgroundColor: 'var(--surface, #fff)', borderRadius: 12, boxShadow: '0 4px 6px rgba(0,0,0,0.05)' };
const input = { width: '100%', padding: 12, borderRadius: 8, border: '1px solid #cbd5e1', fontSize: '1rem', boxSizing: 'border-box' };
const label = { display: 'block', marginBottom: 8, fontWeight: 600 };
const primary = { flex: 1, padding: '12px 24px', backgroundColor: '#0f172a', color: '#fff', borderRadius: 8, border: 'none', cursor: 'pointer', fontWeight: 600 };
const secondary = { padding: '12px 24px', border: '1px solid #cbd5e1', backgroundColor: 'transparent', borderRadius: 8, cursor: 'pointer', fontWeight: 600, color: 'inherit' };

export default function AddDevice() {
  const navigate = useNavigate();
  const qc = useQueryClient();

  const [etapa, setEtapa] = useState('BUSCA'); // BUSCA | WIFI | CONCLUIDO | MANUAL | MANUAL_OK
  const [carregando, setCarregando] = useState(false);
  const [erro, setErro] = useState(null);

  const bleRef = useRef(null); // dispositivo Bluetooth (para reconectar se a conexao cair)
  const [deviceMac, setDeviceMac] = useState('');
  const [deviceSecret, setDeviceSecret] = useState('');

  const [nomeCaixa, setNomeCaixa] = useState('Minha Caixa CronoMed');
  const [ssid, setSsid] = useState('');
  const [wifiPass, setWifiPass] = useState('');
  const [broker, setBroker] = useState(defaultBroker);
  const [macManual, setMacManual] = useState('');

  const brokerOk = broker.trim().length > 0 && !/^(localhost|127\.)/i.test(broker.trim());

  async function iniciarPareamento() {
    setErro(null);
    if (!navigator.bluetooth) {
      setErro('Este navegador não tem Bluetooth Web. Use o Google Chrome ou o Edge (em http://localhost ou https). Se não der, use "Cadastrar informando o MAC".');
      return;
    }
    setCarregando(true);
    try {
      const bleDevice = await navigator.bluetooth.requestDevice({
        filters: [{ namePrefix: 'CronoMed-' }],
        optionalServices: [SERVICE_UUID],
      });
      bleRef.current = bleDevice;
      const server = await bleDevice.gatt.connect();
      const service = await server.getPrimaryService(SERVICE_UUID);
      const macChar = await service.getCharacteristic(CHAR_MAC_UUID);
      const macLido = new TextDecoder('utf-8').decode(await macChar.readValue()).trim();

      let dados;
      try {
        dados = await DeviceApi.register(macLido, nomeCaixa.trim());
      } catch (apiErr) {
        throw new Error(errorMessage(apiErr, 'Não consegui cadastrar a caixa. Ela já está cadastrada?'));
      }
      qc.invalidateQueries({ queryKey: ['devices'] });
      setDeviceMac(macLido);
      setDeviceSecret(dados.deviceSecret);
      setEtapa('WIFI');
    } catch (err) {
      if (err.name !== 'NotFoundError') setErro(err.message || 'Falha na conexão Bluetooth.');
    } finally {
      setCarregando(false);
    }
  }

  async function configurarWifi(e) {
    e.preventDefault();
    setErro(null);
    if (!brokerOk) {
      setErro('Informe o IP do computador que roda o servidor (ex.: 192.168.0.10). "localhost" não funciona para a caixa.');
      return;
    }
    setCarregando(true);
    try {
      const dev = bleRef.current;
      if (!dev) throw new Error('sem dispositivo');
      const server = dev.gatt.connected ? dev.gatt : await dev.gatt.connect();
      const service = await server.getPrimaryService(SERVICE_UUID);
      const enc = new TextEncoder();
      const write = async (uuid, value) => (await service.getCharacteristic(uuid)).writeValue(enc.encode(value));

      await write(CHAR_SSID_UUID, ssid.trim());
      await write(CHAR_PASS_UUID, wifiPass);
      await write(CHAR_BROKER_UUID, broker.trim());
      await write(CHAR_SECRET_UUID, deviceSecret); // por último: é o gatilho para a caixa salvar e reiniciar

      try { localStorage.setItem(BROKER_KEY, broker.trim()); } catch { /* ignora */ }
      setEtapa('CONCLUIDO');
      setTimeout(() => navigate('/app/dispositivos'), 3500);
    } catch (err) {
      // Se a caixa reiniciou logo depois do último envio, a conexão cai: isso é sucesso, não erro.
      setErro('Conexão perdida antes de terminar. Mantenha a caixa perto do computador e tente de novo.');
    } finally {
      setCarregando(false);
    }
  }

  async function cadastrarManual(e) {
    e.preventDefault();
    setErro(null);
    if (!isValidMac(macManual)) {
      setErro('MAC inválido. Use o formato AA:BB:CC:DD:EE:FF (aparece no monitor serial da caixa).');
      return;
    }
    setCarregando(true);
    try {
      const dados = await DeviceApi.register(normalizeMac(macManual), nomeCaixa.trim());
      qc.invalidateQueries({ queryKey: ['devices'] });
      setDeviceMac(normalizeMac(macManual));
      setDeviceSecret(dados.deviceSecret);
      setEtapa('MANUAL_OK');
    } catch (err) {
      setErro(errorMessage(err, 'Não consegui cadastrar a caixa.'));
    } finally {
      setCarregando(false);
    }
  }

  return (
    <div style={box}>
      <h2 style={{ marginTop: 0, fontSize: '1.5rem', marginBottom: 24 }}>Adicionar nova caixa</h2>

      {erro && (
        <div role="alert" style={{ padding: 16, marginBottom: 24, backgroundColor: '#fef2f2', color: '#991b1b', borderRadius: 8, borderLeft: '4px solid #dc2626' }}>
          <strong>Erro: </strong>{erro}
        </div>
      )}

      {etapa === 'BUSCA' && (
        <div>
          <p style={{ marginBottom: 24, lineHeight: 1.5 }}>
            Ligue a caixa CronoMed (sem Wi-Fi salvo ela fica em modo de pareamento). Vamos ler o endereço da caixa e
            configurar a rede dela por Bluetooth.
          </p>
          <div style={{ marginBottom: 24 }}>
            <label style={label} htmlFor="nome">Como quer chamar esta caixa?</label>
            <input id="nome" type="text" value={nomeCaixa} onChange={(e) => setNomeCaixa(e.target.value)} style={input} placeholder="Ex.: Caixa da sala" maxLength={80} />
          </div>
          <div style={{ display: 'flex', gap: 16, marginTop: 32 }}>
            <button type="button" onClick={() => navigate('/app/dispositivos')} style={secondary}>Voltar</button>
            <button type="button" onClick={iniciarPareamento} disabled={carregando || !nomeCaixa.trim()} style={{ ...primary, opacity: carregando ? 0.7 : 1 }}>
              {carregando ? 'Procurando a caixa…' : 'Conectar via Bluetooth'}
            </button>
          </div>
          <p style={{ marginTop: 24, fontSize: 14 }}>
            Sem Bluetooth neste computador?{' '}
            <button type="button" onClick={() => { setErro(null); setEtapa('MANUAL'); }} style={{ background: 'none', border: 'none', padding: 0, cursor: 'pointer', textDecoration: 'underline', color: 'inherit' }}>
              Cadastrar informando o MAC
            </button>
          </p>
        </div>
      )}

      {etapa === 'WIFI' && (
        <form onSubmit={configurarWifi}>
          <div style={{ padding: 16, backgroundColor: '#f0fdf4', borderRadius: 8, marginBottom: 24, borderLeft: '4px solid #22c55e', color: '#166534' }}>
            <strong>Caixa vinculada à sua conta!</strong>
            <div style={{ fontSize: 14, marginTop: 4 }}>MAC: {deviceMac}</div>
          </div>
          <p style={{ marginBottom: 24 }}>Agora informe a rede Wi-Fi que a caixa deve usar e o endereço do servidor.</p>

          <div style={{ marginBottom: 16 }}>
            <label style={label} htmlFor="ssid">Nome da rede Wi-Fi</label>
            <input id="ssid" type="text" required value={ssid} onChange={(e) => setSsid(e.target.value)} style={input} autoComplete="off" />
          </div>
          <div style={{ marginBottom: 16 }}>
            <label style={label} htmlFor="wpass">Senha da rede (deixe em branco se for aberta)</label>
            <input id="wpass" type="password" value={wifiPass} onChange={(e) => setWifiPass(e.target.value)} style={input} autoComplete="off" />
          </div>
          <div style={{ marginBottom: 32 }}>
            <label style={label} htmlFor="broker">IP do computador que roda o servidor</label>
            <input id="broker" type="text" required value={broker} onChange={(e) => setBroker(e.target.value)} style={input} placeholder="192.168.0.10" autoComplete="off" />
            <div style={{ fontSize: 13, marginTop: 6, opacity: 0.75 }}>
              No Windows: <code>ipconfig</code> → "Endereço IPv4" da mesma rede Wi-Fi da caixa. A caixa e o computador precisam estar na mesma rede.
            </div>
          </div>

          <button type="submit" disabled={carregando} style={{ ...primary, width: '100%', backgroundColor: '#22c55e', opacity: carregando ? 0.7 : 1 }}>
            {carregando ? 'Enviando configuração…' : 'Configurar e reiniciar a caixa'}
          </button>
        </form>
      )}

      {etapa === 'CONCLUIDO' && (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <div style={{ fontSize: 48, marginBottom: 16 }} aria-hidden="true">✅</div>
          <h3 style={{ color: '#16a34a', margin: '0 0 12px 0', fontSize: '1.5rem' }}>Pronto!</h3>
          <p style={{ margin: 0, lineHeight: 1.5 }}>
            A caixa salvou a rede e está reiniciando para se conectar.<br />
            Voltando para a lista de caixas…
          </p>
        </div>
      )}

      {etapa === 'MANUAL' && (
        <form onSubmit={cadastrarManual}>
          <p style={{ marginBottom: 24, lineHeight: 1.5 }}>
            Informe o MAC que a caixa mostra no monitor serial ao ligar (linha "MAC desta caixa"). A rede Wi-Fi fica no
            arquivo <code>secrets.h</code> do firmware.
          </p>
          <div style={{ marginBottom: 16 }}>
            <label style={label} htmlFor="nomeM">Nome da caixa</label>
            <input id="nomeM" type="text" value={nomeCaixa} onChange={(e) => setNomeCaixa(e.target.value)} style={input} maxLength={80} />
          </div>
          <div style={{ marginBottom: 24 }}>
            <label style={label} htmlFor="mac">Endereço MAC</label>
            <input id="mac" type="text" value={macManual} onChange={(e) => setMacManual(e.target.value.toUpperCase())} style={input} placeholder="AA:BB:CC:DD:EE:FF" autoComplete="off" />
          </div>
          <div style={{ display: 'flex', gap: 16 }}>
            <button type="button" onClick={() => setEtapa('BUSCA')} style={secondary}>Voltar</button>
            <button type="submit" disabled={carregando || !nomeCaixa.trim()} style={{ ...primary, opacity: carregando ? 0.7 : 1 }}>
              {carregando ? 'Cadastrando…' : 'Cadastrar caixa'}
            </button>
          </div>
        </form>
      )}

      {etapa === 'MANUAL_OK' && (
        <div>
          <div style={{ padding: 16, backgroundColor: '#f0fdf4', borderRadius: 8, marginBottom: 24, borderLeft: '4px solid #22c55e', color: '#166534' }}>
            <strong>Caixa cadastrada!</strong>
            <div style={{ fontSize: 14, marginTop: 4 }}>MAC: {deviceMac}</div>
          </div>
          <p style={{ lineHeight: 1.5, marginBottom: 12 }}>
            Esta é a senha da caixa. Ela aparece <strong>somente agora</strong>; guarde-a (não compartilhe).
          </p>
          <code style={{ display: 'block', padding: 12, borderRadius: 8, background: 'rgba(0,0,0,0.06)', wordBreak: 'break-all', marginBottom: 24 }}>{deviceSecret}</code>
          <button type="button" onClick={() => navigate('/app/dispositivos')} style={{ ...primary, width: '100%' }}>Já guardei a senha</button>
        </div>
      )}
    </div>
  );
}
