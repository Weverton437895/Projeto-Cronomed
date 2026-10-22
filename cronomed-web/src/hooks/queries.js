import { useMemo } from 'react';
import { useQuery } from '@tanstack/react-query';
import { DashboardApi, DeviceApi, MedicationApi, PatientApi } from '../lib/api.js';
import { endOfDayInTz, startOfDayInTz } from '../lib/format.js';

export function useDevices(enabled = true) {
  return useQuery({ queryKey: ['devices'], queryFn: () => DeviceApi.list(), enabled });
}

export function useMedications(patientId) {
  return useQuery({ queryKey: ['medications', patientId], queryFn: () => MedicationApi.list(patientId), enabled: !!patientId });
}

/** Doses do dia atual (todas as situacoes), atualizadas a cada 30 s para refletir a caixa/app. */
export function useTodayDoses(patientId, tz) {
  const now = new Date();
  const start = startOfDayInTz(now, tz);
  const end = endOfDayInTz(now, tz);
  return useQuery({
    queryKey: ['doses', 'today', patientId, start.toISOString()],
    queryFn: async () => {
      const page = await DashboardApi.doses(patientId, { from: start.toISOString(), to: end.toISOString(), page: 0, size: 100 });
      return [...page.items].sort((a, b) => new Date(a.scheduledAt) - new Date(b.scheduledAt));
    },
    enabled: !!patientId,
    refetchInterval: 30000,
  });
}

export function useDashboard(patientId, fromIso, toIso) {
  return useQuery({
    queryKey: ['dashboard', patientId, fromIso, toIso],
    queryFn: () => DashboardApi.summary(patientId, fromIso, toIso),
    enabled: !!patientId,
  });
}

export function useDoseHistory(patientId, fromIso, toIso, page, size = 20) {
  return useQuery({
    queryKey: ['doses', 'history', patientId, fromIso, toIso, page, size],
    queryFn: () => DashboardApi.doses(patientId, { from: fromIso, to: toIso, page, size }),
    enabled: !!patientId,
    placeholderData: (prev) => prev,
  });
}

export function useGrants(patientId, enabled) {
  return useQuery({ queryKey: ['grants', patientId], queryFn: () => PatientApi.grants(patientId), enabled: !!patientId && enabled });
}

/** Mapa medicamento -> numero do compartimento (1-6), cruzando caixas e medicamentos do paciente. */
export function useCompartmentIndex(patientId, devicesEnabled = true) {
  const devices = useDevices(devicesEnabled);
  const meds = useMedications(patientId);
  const byMed = useMemo(() => {
    const byCompartment = new Map();
    (devices.data || []).forEach((d) => (d.compartments || []).forEach((c) => byCompartment.set(c.id, c)));
    const map = new Map();
    (meds.data || []).forEach((m) => {
      const c = m.compartmentId ? byCompartment.get(m.compartmentId) : null;
      if (c) map.set(m.id, c.index);
    });
    return map;
  }, [devices.data, meds.data]);
  return { byMed, meds: meds.data || [], devices: devices.data || [], isLoading: devices.isLoading || meds.isLoading };
}
