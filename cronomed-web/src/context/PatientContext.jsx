import { createContext, useCallback, useContext, useMemo, useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { PatientApi } from '../lib/api.js';
import { useAuth } from './AuthContext.jsx';
import { readString, writeString } from '../lib/storage.js';
import { DEFAULT_TZ } from '../lib/format.js';

const PatientCtx = createContext(null);
const KEY = 'cronomed.patient';
const ORDER = ['NONE', 'VIEW', 'CONFIRM', 'MANAGE', 'OWNER'];

export function atLeast(level, needed) {
  return ORDER.indexOf(level || 'NONE') >= ORDER.indexOf(needed);
}

export function PatientProvider({ children }) {
  const { user } = useAuth();
  const q = useQuery({ queryKey: ['patients'], queryFn: () => PatientApi.list(), enabled: !!user });
  const [selected, setSelected] = useState(() => readString(KEY));
  const patients = q.data || [];

  const patient = useMemo(() => {
    if (patients.length === 0) return null;
    return patients.find((p) => p.id === selected) || patients.find((p) => p.linkedUserId && user && p.linkedUserId === user.id) || patients[0];
  }, [patients, selected, user]);

  const setPatientId = useCallback((id) => {
    setSelected(id);
    writeString(KEY, id);
  }, []);

  const value = useMemo(() => {
    const access = patient ? patient.myAccess : 'NONE';
    return {
      patients,
      patient,
      setPatientId,
      access,
      tz: (patient && patient.timezone) || DEFAULT_TZ,
      isOwner: access === 'OWNER',
      can: (level) => atLeast(access, level),
      isLoading: q.isLoading,
      error: q.error,
      refetch: q.refetch,
    };
  }, [patients, patient, setPatientId, q.isLoading, q.error, q.refetch]);

  return <PatientCtx.Provider value={value}>{children}</PatientCtx.Provider>;
}

export function usePatient() {
  return useContext(PatientCtx);
}
