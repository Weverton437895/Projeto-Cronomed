import { Link, Navigate, Route, Routes } from 'react-router-dom';
import AppLayout from './components/AppLayout.jsx';
import { Guarded, GuestOnly, RequireAuth } from './components/RouteGuards.jsx';
import Landing from './pages/Landing.jsx';
import Login from './pages/Login.jsx';
import Register from './pages/Register.jsx';
import GuestSignup from './pages/GuestSignup.jsx';
import ForgotPassword from './pages/ForgotPassword.jsx';
import ResetPassword from './pages/ResetPassword.jsx';
import Doses from './pages/Doses.jsx';
import Medications from './pages/Medications.jsx';
import Stock from './pages/Stock.jsx';
import History from './pages/History.jsx';
import Sharing from './pages/Sharing.jsx';
import Devices from './pages/Devices.jsx';
import Settings from './pages/Settings.jsx';
import Profile from './pages/Profile.jsx';
import Security from './pages/Security.jsx';

function NotFound() {
  return (
    <div className="notfound">
      <div>
        <h1>404</h1>
        <p className="muted" style={{ margin: '8px 0 20px' }}>Não encontramos esta página.</p>
        <Link className="btn btn-primary" to="/">Voltar ao início</Link>
      </div>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route path="/" element={<Landing />} />
      <Route path="/convite" element={<GuestSignup />} />
      <Route element={<GuestOnly />}>
        <Route path="/entrar" element={<Login />} />
        <Route path="/cadastro" element={<Register />} />
        <Route path="/esqueci-senha" element={<ForgotPassword />} />
        <Route path="/redefinir-senha" element={<ResetPassword />} />
      </Route>
      <Route element={<RequireAuth />}>
        <Route path="/app" element={<AppLayout />}>
          <Route index element={<Navigate to="doses" replace />} />
          <Route path="doses" element={<Doses />} />
          <Route path="medicamentos" element={<Medications />} />
          <Route path="estoque" element={<Stock />} />
          <Route path="historico" element={<History />} />
          <Route path="compartilhamento" element={<Guarded need="OWNER"><Sharing /></Guarded>} />
          <Route path="dispositivos" element={<Guarded need="OWNER"><Devices /></Guarded>} />
          <Route path="configuracoes" element={<Guarded need="OWNER"><Settings /></Guarded>} />
          <Route path="cadastro" element={<Profile />} />
          <Route path="seguranca" element={<Security />} />
        </Route>
      </Route>
      <Route path="*" element={<NotFound />} />
    </Routes>
  );
}
