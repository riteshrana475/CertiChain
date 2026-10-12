import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { api, getToken, setToken } from "../api/client";
import type { Certificate, Session, Student, VerificationLog } from "../types";

const SESSION_KEY = "certichain.session.v1";

type CertificateInput = {
  id?: string;
  studentId: string;
  certificateType: string;
  degree: string;
  department: string;
  issueDate: string;
  grade: string;
  status: "DRAFT" | "ISSUED";
};

type LoginResponse = {
  token: string | null;
  name: string;
  email: string;
  role: Session["role"];
  institutionName: string;
};

type RecordsContextValue = {
  students: Student[];
  certificates: Certificate[];
  logs: VerificationLog[];
  session: Session | null;
  login: (email: string, password: string) => Promise<void>;
  logout: () => void;
  addStudent: (input: Omit<Student, "id">) => Promise<Student>;
  updateStudent: (id: string, input: Omit<Student, "id">) => Promise<Student>;
  deleteStudent: (id: string) => Promise<void>;
  saveCertificate: (input: CertificateInput) => Promise<Certificate>;
  revokeCertificate: (id: string, reason: string) => Promise<void>;
  resetDemo: () => Promise<void>;
  ready: boolean;
  studentById: (id: string) => Student | undefined;
  certificateById: (id: string) => Certificate | undefined;
};

const RecordsContext = createContext<RecordsContextValue | null>(null);

function loadSession(): Session | null {
  const raw = localStorage.getItem(SESSION_KEY);
  if (!raw || !getToken()) return null;
  try {
    return JSON.parse(raw) as Session;
  } catch {
    return null;
  }
}

export function RecordsProvider({ children }: { children: ReactNode }) {
  const [students, setStudents] = useState<Student[]>([]);
  const [certificates, setCertificates] = useState<Certificate[]>([]);
  const [logs, setLogs] = useState<VerificationLog[]>([]);
  const [session, setSession] = useState<Session | null>(loadSession);
  const [ready, setReady] = useState(() => loadSession() === null);

  const refresh = useCallback(async () => {
    try {
      const me = await api<LoginResponse>("/api/auth/me");
      const nextSession = {
        name: me.name,
        email: me.email,
        role: me.role,
        institutionName: me.institutionName,
      };
      localStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
      setSession((current) =>
        current &&
        current.email === nextSession.email &&
        current.role === nextSession.role &&
        current.name === nextSession.name &&
        current.institutionName === nextSession.institutionName
          ? current
          : nextSession,
      );
      if (me.role === "INSTITUTION_ADMIN" || me.role === "PLATFORM_ADMIN") {
        const [nextStudents, nextCertificates, nextLogs] = await Promise.all([
          api<Student[]>("/api/students"),
          api<Certificate[]>("/api/certificates"),
          api<VerificationLog[]>("/api/verification-logs"),
        ]);
        setStudents(nextStudents);
        setCertificates(nextCertificates);
        setLogs(nextLogs);
      } else {
        setStudents([]);
        setCertificates([]);
        setLogs([]);
      }
    } finally {
      setReady(true);
    }
  }, []);

  useEffect(() => {
    if (!session) return;
    void refresh().catch(() => {
      setToken(null);
      localStorage.removeItem(SESSION_KEY);
      setSession(null);
    });
  }, [refresh, session]);

  const login = useCallback(async (email: string, password: string) => {
    const response = await api<LoginResponse>(
      "/api/auth/login",
      { method: "POST", body: JSON.stringify({ email, password }) },
      false,
    );
    if (!response.token) throw new Error("The API did not return a sign-in token.");
    const nextSession = {
      name: response.name,
      email: response.email,
      role: response.role,
      institutionName: response.institutionName,
    };
    setToken(response.token);
    localStorage.setItem(SESSION_KEY, JSON.stringify(nextSession));
    localStorage.removeItem("certichain.records.v1");
    setSession(nextSession);
  }, []);

  const logout = useCallback(() => {
    setToken(null);
    localStorage.removeItem(SESSION_KEY);
    setSession(null);
    setStudents([]);
    setCertificates([]);
    setLogs([]);
  }, []);

  const addStudent = useCallback(async (input: Omit<Student, "id">) => {
    const student = await api<Student>("/api/students", { method: "POST", body: JSON.stringify(input) });
    setStudents((current) => [student, ...current]);
    return student;
  }, []);

  const updateStudent = useCallback(async (id: string, input: Omit<Student, "id">) => {
    const student = await api<Student>(`/api/students/${id}`, { method: "PUT", body: JSON.stringify(input) });
    await refresh();
    return student;
  }, [refresh]);

  const deleteStudent = useCallback(async (id: string) => {
    await api<void>(`/api/students/${id}`, { method: "DELETE" });
    setStudents((current) => current.filter((item) => item.id !== id));
  }, []);

  const saveCertificate = useCallback(async (input: CertificateInput) => {
    const certificate = await api<Certificate>("/api/certificates", {
      method: "POST",
      body: JSON.stringify(input),
    });
    setCertificates((current) => [certificate, ...current.filter((item) => item.id !== certificate.id)]);
    return certificate;
  }, []);

  const revokeCertificate = useCallback(async (id: string, reason: string) => {
    const certificate = await api<Certificate>(`/api/certificates/${id}/revoke`, {
      method: "POST",
      body: JSON.stringify({ reason }),
    });
    setCertificates((current) => current.map((item) => (item.id === certificate.id ? certificate : item)));
  }, []);

  const value = useMemo<RecordsContextValue>(
    () => ({
      students,
      certificates,
      logs,
      session,
      login,
      logout,
      addStudent,
      updateStudent,
      deleteStudent,
      saveCertificate,
      revokeCertificate,
      resetDemo: refresh,
      ready,
      studentById: (id) => students.find((item) => item.id === id),
      certificateById: (id) => certificates.find((item) => item.id === id),
    }),
    [students, certificates, logs, session, login, logout, addStudent, updateStudent, deleteStudent, saveCertificate, revokeCertificate, refresh, ready],
  );

  return <RecordsContext.Provider value={value}>{children}</RecordsContext.Provider>;
}

export function useRecords(): RecordsContextValue {
  const context = useContext(RecordsContext);
  if (!context) throw new Error("useRecords must be used within RecordsProvider");
  return context;
}
