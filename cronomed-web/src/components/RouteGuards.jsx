import { Link, Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from '../context/AuthContext.jsx';
import { usePatient } from '../context/PatientContext.jsx';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { PatientApi } from '../lib/api.js';
import { Button, EmptyState, ErrorNotice, Skeleton } from './ui.jsx';
import { useToast } from '../context/ToastContext.jsx';
import { DEFAULT_TZ } from '../lib/format.js';

export function FullPageSpinner() {
  return (
    <div style={{ minHeight: '100vh', display: 'grid', placeItems: 'center' }} role="status" aria-label="Carregando">
      <span className="spinner" style={{ width: 36, height: 36, color: 'var(--blue)' }} />
    </div>
  );
}

export function RequireAuth() {
  const { status } = useAuth();
  const location = useLocation();
  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'anon') return <Navigate to="/entrar" replace state={{ from: location.pathname }} />;
  return <Outlet />;
}

export function GuestOnly() {
  const { status } = useAuth();
  if (status === 'loading') return <FullPageSpinner />;
  if (status === 'authed') return <Navigate to="/app/doses" replace />;
  return <Outlet />;
}

/** Bloqueia a tela quando o nivel de acesso do usuario sobre o paciente e insuficiente. */
export function Guarded({ need, children }) {
  const { can, isLoading, patient } = usePatient();
  if (isLoading) return <div className="page"><Skeleton height={160} /></div>;
  if (patient && !can(need)) {
    return (
      <div className="page page-narrow">
        <EmptyState icon="lock" title="Você não tem acesso a esta tela" action={<Link className="btn btn-primary" to="/app/doses">Ir para Próximas Doses</Link>}>
          O titular da conta define o que cada pessoa pode ver. Peça a liberação se precisar.
        </EmptyState>
      </div>
    );
  }
  return children;
}

/** Garante que existe um paciente selecionado antes de renderizar a tela. */
export function PatientRequired({ children }) {
  const { patient, isLoading, error, refetch } = usePatient();
  if (isLoading) {
    return (
      <div className="page">
        <Skeleton height={36} />
        <div style={{ height: 18 }} />
        <Skeleton height={200} />
      </div>
    );
  }
  if (error) {
    return (
      <div className="page">
        <ErrorNotice error={error} onRetry={refetch} />
      </div>
    );
  }
  if (!patient) return <NoPatient />;
  return children;
}

function NoPatient() {
  const { user } = useAuth();
  const qc = useQueryClient();
  const toast = useToast();
  const create = useMutation({
    mutationFn: () => PatientApi.create({ name: user.name, timezone: DEFAULT_TZ }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: ['patients'] });
      toast.success('Perfil de paciente criado.');
    },
    onError: (e) => toast.error(e.message),
  });
  return (
    <div className="page page-narrow">
      <EmptyState
        icon="users"
        title="Você ainda não acompanha nenhum paciente"
        action={<Button loading={create.isPending} onClick={() => create.mutate()}>Criar meu perfil de paciente</Button>}
      >
        Se você toma medicamentos, crie seu perfil. Se vai acompanhar alguém, peça ao titular que compartilhe o acesso usando o e-mail da sua conta ({user.email}).
      </EmptyState>
    </div>
  );
}
