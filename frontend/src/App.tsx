import { Navigate, Route, Routes, useLocation } from "react-router-dom";
import type { ReactNode } from "react";
import { AdminLayout } from "./components/AdminLayout";
import { BulkIssuancePage } from "./pages/admin/BulkIssuancePage";
import { AnomaliesPage, AnchorsPage, InsightsPage } from "./pages/admin/InstitutionInsights";
import { CertificateDetailPage } from "./pages/admin/CertificateDetailPage";
import { CertificateFormPage } from "./pages/admin/CertificateFormPage";
import { CertificatesPage } from "./pages/admin/CertificatesPage";
import { DashboardPage } from "./pages/admin/DashboardPage";
import { HistoryPage } from "./pages/admin/HistoryPage";
import { SettingsPage } from "./pages/admin/SettingsPage";
import { StudentsPage } from "./pages/admin/StudentsPage";
import { LoginPage } from "./pages/LoginPage";
import { RecruiterPage } from "./pages/recruiter/RecruiterPage";
import { SharedPage } from "./pages/shared/SharedPage";
import { SignupPage } from "./pages/SignupPage";
import { VerifyLookupPage } from "./pages/verify/VerifyLookupPage";
import { VerifyPage } from "./pages/verify/VerifyPage";
import { ActivityPage } from "./pages/wallet/ActivityPage";
import { DocumentPage } from "./pages/wallet/DocumentPage";
import { SharesPage } from "./pages/wallet/SharesPage";
import { StudentDashboard } from "./pages/wallet/StudentDashboard";
import { UploadPage } from "./pages/wallet/UploadPage";
import { WalletLibraryPage } from "./pages/wallet/WalletLibraryPage";
import { useRecords } from "./state/records";
import type { Session } from "./types";

function RequireAuth() {
  const { session } = useRecords();
  const location = useLocation();
  if (!session) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  return <AdminLayout />;
}

function RoleRoute({ roles, children }: { roles: Session["role"][]; children: ReactNode }) {
  const { session } = useRecords();
  if (!session) return <Navigate to="/login" replace />;
  if (!roles.includes(session.role)) return <Navigate to="/dashboard" replace />;
  return children;
}

function DashboardRoute() {
  const { session } = useRecords();
  if (session?.role === "STUDENT") return <StudentDashboard />;
  if (session?.role === "RECRUITER") return <RecruiterPage />;
  return <DashboardPage />;
}

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/signup" element={<SignupPage />} />
      <Route path="/verify" element={<VerifyLookupPage />} />
      <Route path="/verify/:certificateId" element={<VerifyPage />} />
      <Route path="/shared/:token" element={<SharedPage />} />
      <Route element={<RequireAuth />}>
        <Route path="/" element={<Navigate to="/dashboard" replace />} />
        <Route path="/dashboard" element={<DashboardRoute />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/students" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><StudentsPage /></RoleRoute>} />
        <Route path="/certificates" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><CertificatesPage /></RoleRoute>} />
        <Route path="/certificates/new" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><CertificateFormPage /></RoleRoute>} />
        <Route path="/certificates/:certificateId" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><CertificateDetailPage /></RoleRoute>} />
        <Route path="/history" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><HistoryPage /></RoleRoute>} />
        <Route path="/issuer/bulk" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><BulkIssuancePage /></RoleRoute>} />
        <Route path="/issuer/anchors" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><AnchorsPage /></RoleRoute>} />
        <Route path="/issuer/insights" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><InsightsPage /></RoleRoute>} />
        <Route path="/issuer/anomalies" element={<RoleRoute roles={["INSTITUTION_ADMIN", "PLATFORM_ADMIN"]}><AnomaliesPage /></RoleRoute>} />
        <Route path="/wallet" element={<RoleRoute roles={["STUDENT"]}><WalletLibraryPage /></RoleRoute>} />
        <Route path="/wallet/upload" element={<RoleRoute roles={["STUDENT"]}><UploadPage /></RoleRoute>} />
        <Route path="/wallet/documents/:id" element={<RoleRoute roles={["STUDENT"]}><DocumentPage /></RoleRoute>} />
        <Route path="/wallet/shared" element={<RoleRoute roles={["STUDENT"]}><SharesPage /></RoleRoute>} />
        <Route path="/wallet/activity" element={<RoleRoute roles={["STUDENT"]}><ActivityPage /></RoleRoute>} />
        <Route path="/recruiter" element={<RoleRoute roles={["RECRUITER"]}><RecruiterPage /></RoleRoute>} />
      </Route>
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
