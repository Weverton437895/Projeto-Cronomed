import { useEffect, useRef, useState } from 'react';
import { NavLink, Link, Outlet, useLocation, useNavigate } from 'react-router-dom';
import Logo from './Logo.jsx';
import Icon from './Icon.jsx';
import { useAuth } from '../context/AuthContext.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { HeaderProvider, useHeader } from '../context/HeaderContext.jsx';
import { useDashboard } from '../hooks/queries.js';
import { daysAgo, initials } from '../lib/format.js';

const NAV = [
  { to: '/app/doses', label: 'Próximas Doses', icon: 'clock', need: 'VIEW', title: 'Próximas Doses' },
  { to: '/app/medicamentos', label: 'Gerenciar Medicamentos', icon: 'pill', need: 'VIEW', title: 'Gerenciar Medicamentos' },
  { to: '/app/estoque', label: 'Estoque', icon: 'box', need: 'VIEW', title: 'Gerenciar Estoque' },
  { to: '/app/historico', label: 'Histórico', icon: 'history', need: 'VIEW', title: 'Histórico de Medicação' },
  { to: '/app/compartilhamento', label: 'Compartilhamento', icon: 'users', need: 'OWNER', title: 'Compartilhamento e permissões' },
  { to: '/app/dispositivos', label: 'Dispositivos', icon: 'briefcase', need: 'OWNER', title: 'Dispositivos' },
  { to: '/app/configuracoes', label: 'Configurações', icon: 'sliders', need: 'OWNER', title: 'Configurações' },
];

const EXTRA_TITLES = {
  '/app/cadastro': 'Alterar cadastro',
  '/app/seguranca': 'Segurança',
};

const DEFAULT_SUBTITLES = {
  '/app/medicamentos': 'Compartimentos da caixa',
  '/app/estoque': 'Quantidade restante e previsão de término',
  '/app/historico': 'Aberturas da caixa cruzadas com confirmações no app',
  '/app/compartilhamento': 'Você é o Titular desta conta',
  '/app/dispositivos': 'Caixas vinculadas à sua conta',
  '/app/configuracoes': 'Acessibilidade, alertas e privacidade',
  '/app/cadastro': 'Mantenha seus dados atualizados',
  '/app/seguranca': 'Proteja o acesso à sua conta',
};

function useOutsideClose(ref, onClose, active) {
  useEffect(() => {
    if (!active) return undefined;
    const onDown = (e) => {
      if (ref.current && !ref.current.contains(e.target)) onClose();
    };
    const onKey = (e) => e.key === 'Escape' && onClose();
    document.addEventListener('mousedown', onDown);
    document.addEventListener('keydown', onKey);
    return () => {
      document.removeEventListener('mousedown', onDown);
      document.removeEventListener('keydown', onKey);
    };
  }, [ref, onClose, active]);
}

function AlertsBell({ patientId }) {
  const [open, setOpen] = useState(false);
  const ref = useRef(null);
  useOutsideClose(ref, () => setOpen(false), open);
  const now = new Date();
  const from = daysAgo(30, now).toISOString();
  const q = useDashboard(patientId, from, now.toISOString());
  const alerts = (q.data?.recentAlerts || []).filter((a) => !a.resolvedAt);
  return (
    <div className="menu-wrap" ref={ref}>
      <button type="button" className="icon-btn" aria-label={alerts.length ? `Alertas (${alerts.length})` : 'Alertas'} aria-expanded={open} onClick={() => setOpen((v) => !v)}>
        <Icon name="bell" size={22} />
        {alerts.length > 0 && <span className="pip" />}
      </button>
      {open && (
        <div className="menu" style={{ minWidth: 300 }} role="region" aria-label="Alertas">
          {alerts.length === 0 ? (
            <p className="muted" style={{ padding: '12px 14px' }}>Sem alertas no momento.</p>
          ) : (
            alerts.slice(0, 6).map((a) => (
              <p key={a.id} style={{ padding: '10px 14px', fontSize: '.9rem', borderBottom: '1px solid var(--border)' }}>{a.message}</p>
            ))
          )}
        </div>
      )}
    </div>
  );
}

function Topbar({ onToggleNav }) {
  const { user } = useAuth();
  const { patient, patients, setPatientId, isOwner } = usePatient();
  const { subtitle } = useHeader();
  const { pathname } = useLocation();
  const [menu, setMenu] = useState(false);
  const menuRef = useRef(null);
  useOutsideClose(menuRef, () => setMenu(false), menu);
  useEffect(() => setMenu(false), [pathname]);

  const item = NAV.find((n) => pathname.startsWith(n.to));
  const title = (item && item.title) || EXTRA_TITLES[pathname] || 'CronoMed';
  const sub = subtitle || DEFAULT_SUBTITLES[pathname] || '';
  const role = isOwner || !patient ? 'Titular da conta' : `Acompanha ${patient.name.split(' ')[0]}`;

  return (
    <header className="topbar">
      <div style={{ display: 'flex', alignItems: 'center', gap: 12, minWidth: 0 }}>
        <button type="button" className="icon-btn boxed nav-toggle" aria-label="Abrir menu de navegação" onClick={onToggleNav}>
          <Icon name="menu" size={22} />
        </button>
        <div className="topbar-title">
          <h1>{title}</h1>
          {sub && <p>{sub}</p>}
        </div>
      </div>
      <div className="topbar-right">
        {patients.length > 1 && (
          <select className="select select-sm patient-switch desk-only" aria-label="Paciente" value={patient ? patient.id : ''} onChange={(e) => setPatientId(e.target.value)}>
            {patients.map((p) => (
              <option key={p.id} value={p.id}>{p.name}</option>
            ))}
          </select>
        )}
        {patient && <AlertsBell patientId={patient.id} />}
        <div className="user-chip">
          <span className="avatar avatar-sm" aria-hidden="true">{initials(user.name)}</span>
          <div className="who">
            <strong>{user.name}</strong>
            <span>{role}</span>
          </div>
        </div>
        <div className="menu-wrap desk-only" ref={menuRef}>
          <button type="button" className="icon-btn boxed" aria-label="Menu da conta" aria-haspopup="menu" aria-expanded={menu} onClick={() => setMenu((v) => !v)}>
            <Icon name="menu" size={22} />
          </button>
          {menu && (
            <div className="menu" role="menu">
              <Link to="/app/cadastro" role="menuitem"><Icon name="user" size={20} /> Alterar cadastro</Link>
              <Link to="/app/seguranca" role="menuitem"><Icon name="lock" size={20} /> Segurança</Link>
              {isOwner && <Link to="/app/compartilhamento" role="menuitem"><Icon name="users" size={20} /> Compartilhamento</Link>}
            </div>
          )}
        </div>
      </div>
    </header>
  );
}

function Shell() {
  const { logout } = useAuth();
  const { can, patient } = usePatient();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);
  useEffect(() => setOpen(false), [pathname]);
  useEffect(() => {
    if (!open) return undefined;
    const onKey = (e) => e.key === 'Escape' && setOpen(false);
    document.addEventListener('keydown', onKey);
    return () => document.removeEventListener('keydown', onKey);
  }, [open]);

  const allowed = (need) => !patient || can(need);
  const bottom = NAV.filter((n) => ['/app/doses', '/app/medicamentos', '/app/historico'].includes(n.to)).concat(
    allowed('OWNER') ? NAV.filter((n) => n.to === '/app/compartilhamento') : NAV.filter((n) => n.to === '/app/estoque'),
  );

  async function onLogout() {
    await logout();
    navigate('/entrar', { replace: true });
  }

  return (
    <div className={`shell${open ? ' open' : ''}`}>
      <a className="skip-link" href="#conteudo">Pular para o conteúdo</a>
      <aside className="sidebar" aria-label="Navegação principal">
        <Logo to="/app/doses" />
        <nav className="nav" aria-label="Seções do portal">
          {NAV.map((n) =>
            allowed(n.need) ? (
              <NavLink key={n.to} to={n.to}>
                <Icon name={n.icon} size={22} />
                {n.label}
              </NavLink>
            ) : (
              <span key={n.to} className="nav-locked" aria-disabled="true" title="Sem acesso">
                <Icon name={n.icon} size={22} />
                {n.label}
                <Icon name="lock" size={16} className="lock" />
              </span>
            ),
          )}
        </nav>
        <div className="sidebar-foot">
          <button type="button" className="logout" onClick={onLogout}>
            <Icon name="logout" size={22} />
            Encerrar Acesso
          </button>
        </div>
      </aside>
      <div className="scrim" onClick={() => setOpen(false)} aria-hidden="true" />
      <div className="main">
        <Topbar onToggleNav={() => setOpen((v) => !v)} />
        <main id="conteudo" tabIndex={-1}>
          <Outlet />
        </main>
      </div>
      <nav className="bottom-nav" aria-label="Atalhos">
        {bottom.map((n) => (
          <NavLink key={n.to} to={n.to}>
            <Icon name={n.icon} size={22} />
            {n.label.replace('Gerenciar ', '').replace('Próximas ', '')}
          </NavLink>
        ))}
      </nav>
    </div>
  );
}

export default function AppLayout() {
  return (
    <HeaderProvider>
      <Shell />
    </HeaderProvider>
  );
}
