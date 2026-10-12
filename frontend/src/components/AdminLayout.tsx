import { useState } from "react";
import { NavLink, Outlet, useNavigate } from "react-router-dom";
import { LogoMark } from "./ui";
import { useRecords } from "../state/records";
import type { Session } from "../types";

const issuerLinks = [
  { to: "/dashboard", label: "Dashboard", end: true },
  { to: "/students", label: "Students", end: false },
  { to: "/certificates", label: "Certificates", end: false },
  { to: "/history", label: "Verification log", end: true },
  { to: "/issuer/bulk", label: "Bulk issuance", end: true },
  { to: "/issuer/anchors", label: "Anchors", end: true },
  { to: "/issuer/insights", label: "Insights", end: true },
  { to: "/issuer/anomalies", label: "Anomalies", end: true },
  { to: "/settings", label: "Settings", end: true },
];

const studentLinks = [
  { to: "/dashboard", label: "Dashboard", end: true },
  { to: "/wallet", label: "Wallet", end: true },
  { to: "/wallet/upload", label: "Add document", end: true },
  { to: "/wallet/shared", label: "Sharing", end: true },
  { to: "/wallet/activity", label: "Activity", end: true },
  { to: "/verify", label: "Verify", end: true },
  { to: "/settings", label: "Settings", end: true },
];

const recruiterLinks = [
  { to: "/dashboard", label: "Verifier desk", end: true },
  { to: "/verify", label: "Public check", end: true },
  { to: "/settings", label: "Settings", end: true },
];

function linksFor(role: Session["role"] | undefined) {
  if (role === "STUDENT") return studentLinks;
  if (role === "RECRUITER") return recruiterLinks;
  return issuerLinks;
}

export function AdminLayout() {
  const { session, logout, resetDemo } = useRecords();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);

  function signOut() {
    logout();
    navigate("/login");
  }

  return (
    <div className="min-h-screen bg-paper md:grid md:grid-cols-[280px_1fr]">
      {open ? (
        <button
          className="fixed inset-0 z-20 bg-navy/40 md:hidden"
          aria-label="Close menu"
          onClick={() => setOpen(false)}
        />
      ) : null}
      <aside
        className={`${open ? "translate-x-0" : "-translate-x-full"} fixed inset-y-0 left-0 z-30 flex w-64 flex-col bg-navy text-paper shadow-2xl transition-transform md:static md:w-auto md:translate-x-0 md:shadow-none`}
      >
        <div className="border-b border-white/10 px-5 py-7">
          <div className="flex items-center gap-3">
            <LogoMark />
            <div>
              <p className="font-serif text-2xl leading-none">CertiChain</p>
              {session?.institutionName ? (
                <p className="mt-1 text-[11px] tracking-[0.16em] text-paper/55 uppercase">{session.institutionName}</p>
              ) : null}
            </div>
          </div>
        </div>
        <nav className="flex flex-1 flex-col gap-1 px-3 py-5">
          {linksFor(session?.role).map((link) => (
            <NavLink
              key={link.to}
              to={link.to}
              end={link.end}
              onClick={() => setOpen(false)}
              className={({ isActive }) =>
                `rounded-xl px-3 py-2.5 text-sm transition ${
                  isActive
                    ? "bg-white/10 text-white shadow-[inset_3px_0_0_#c4a265]"
                    : "text-paper/70 hover:bg-white/5 hover:text-white"
                }`
              }
            >
              {link.label}
            </NavLink>
          ))}
        </nav>
        <div className="border-t border-white/10 px-5 py-5 text-sm">
          <p className="font-medium">{session?.name}</p>
          <p className="mt-1 text-xs text-paper/60">{session?.email}</p>
          <div className="mt-4 flex gap-4">
            <button className="text-xs text-paper/70 hover:text-white" onClick={signOut}>
              Sign out
            </button>
            <button
              className="text-xs text-paper/70 hover:text-white"
              onClick={() => {
                resetDemo();
                setOpen(false);
              }}
            >
              Reload
            </button>
          </div>
        </div>
      </aside>
      <div className="min-w-0">
        <header className="flex items-center justify-between border-b border-line/80 bg-white/70 px-4 py-3 backdrop-blur md:hidden">
          <div className="flex items-center gap-2">
            <LogoMark className="h-8 w-8" />
            <p className="font-serif text-xl">CertiChain</p>
          </div>
          <button className="rounded-full border border-line bg-white px-3 py-1.5 text-sm font-medium" onClick={() => setOpen(true)}>
            Menu
          </button>
        </header>
        <main className="mx-auto max-w-6xl px-4 py-8 sm:px-8 sm:py-10">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
