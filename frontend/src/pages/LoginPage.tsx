import { useState, type FormEvent, type KeyboardEvent } from "react";
import { Link, Navigate, useLocation, useNavigate } from "react-router-dom";
import { Button, Field, TextInput } from "../components/ui";
import { useRecords } from "../state/records";
import { AuthFrame } from "./AuthFrame";

const REMEMBERED_EMAIL = "certichain.login.email";

const DEMOS = [
  { label: "Institution", email: "admin@certichain.local", detail: "Issue and revoke" },
  { label: "Student", email: "student@certichain.local", detail: "Open the wallet" },
  { label: "Verifier", email: "recruiter@certichain.local", detail: "Check an ID" },
] as const;

export function LoginPage() {
  const { session, login } = useRecords();
  const navigate = useNavigate();
  const location = useLocation();
  const from = (location.state as { from?: string } | null)?.from ?? "/dashboard";
  const [email, setEmail] = useState(() => localStorage.getItem(REMEMBERED_EMAIL) ?? "");
  const [password, setPassword] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [remember, setRemember] = useState(() => Boolean(localStorage.getItem(REMEMBERED_EMAIL)));
  const [capsLock, setCapsLock] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (session) return <Navigate to="/dashboard" replace />;

  async function signIn(nextEmail: string, nextPassword: string) {
    setBusy(true);
    setError("");
    try {
      await login(nextEmail.trim(), nextPassword);
      if (remember) localStorage.setItem(REMEMBERED_EMAIL, nextEmail.trim());
      else localStorage.removeItem(REMEMBERED_EMAIL);
      navigate(from, { replace: true });
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Sign-in failed.");
      setBusy(false);
    }
  }

  function submit(event: FormEvent) {
    event.preventDefault();
    void signIn(email, password);
  }

  function watchCaps(event: KeyboardEvent<HTMLInputElement>) {
    setCapsLock(event.getModifierState("CapsLock"));
  }

  return (
    <AuthFrame>
      <form onSubmit={submit} className="panel mx-auto w-full max-w-md px-7 py-8">
        <h2 className="font-serif text-4xl">Sign in</h2>
        <p className="mt-2 text-sm text-muted">Institution, student, and verifier accounts use the same page.</p>
        <div className="mt-8 space-y-4">
          <Field label="Email">
            <TextInput
              type="email"
              autoComplete="username"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              required
            />
          </Field>
          <Field label="Password" hint={capsLock ? "Caps Lock is on." : undefined}>
            <span className="flex gap-2">
              <TextInput
                type={showPassword ? "text" : "password"}
                autoComplete="current-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
                onKeyUp={watchCaps}
                required
              />
              <button
                type="button"
                className="shrink-0 rounded-xl border border-line px-3 text-xs font-semibold text-seal"
                onClick={() => setShowPassword((value) => !value)}
              >
                {showPassword ? "Hide" : "Show"}
              </button>
            </span>
          </Field>
          <label className="flex items-center gap-2 text-sm text-muted">
            <input type="checkbox" checked={remember} onChange={(event) => setRemember(event.target.checked)} />
            Remember this email on this browser
          </label>
        </div>
        {error ? <p className="mt-4 text-sm text-[#8c2f2f]">{error}</p> : null}
        <div className="mt-6">
          <Button type="submit" disabled={busy}>{busy ? "Signing in" : "Sign in"}</Button>
        </div>
        <div className="mt-8">
          <p className="text-xs font-semibold tracking-[0.14em] text-muted uppercase">Demo accounts</p>
          <div className="mt-3 grid gap-2">
            {DEMOS.map((account) => (
              <button
                key={account.email}
                type="button"
                disabled={busy}
                className="flex items-center justify-between rounded-xl border border-line px-3 py-2 text-left text-sm hover:border-seal/40 disabled:opacity-50"
                onClick={() => void signIn(account.email, "certichain")}
              >
                <span>
                  <span className="font-medium text-ink">{account.label}</span>
                  <span className="mt-0.5 block text-xs text-muted">{account.email}</span>
                </span>
                <span className="text-xs text-seal">{account.detail}</span>
              </button>
            ))}
          </div>
        </div>
        <p className="mt-6 text-sm text-muted">
          New here?{" "}
          <Link to="/signup" className="font-medium text-seal">Create an account</Link>
        </p>
      </form>
    </AuthFrame>
  );
}
