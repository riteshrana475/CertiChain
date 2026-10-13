import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { api, setToken } from "../api/client";
import { Button, Field, TextInput } from "../components/ui";
import { useRecords } from "../state/records";
import { AuthFrame } from "./AuthFrame";

type Role = "INSTITUTION_ADMIN" | "STUDENT" | "RECRUITER";

type LoginResponse = {
  token: string | null;
  name: string;
  email: string;
  role: Role;
  institutionName: string;
};

const ROLES: { id: Role; title: string; text: string }[] = [
  { id: "INSTITUTION_ADMIN", title: "Institution", text: "Issue degrees, transcripts, and bonafide certificates." },
  { id: "STUDENT", title: "Student", text: "See credentials issued to this email, and keep your own uploads." },
  { id: "RECRUITER", title: "Verifier", text: "Check a credential ID and open links a student sends." },
];

export function SignupPage() {
  const { session, login } = useRecords();
  const navigate = useNavigate();
  const [role, setRole] = useState<Role>("INSTITUTION_ADMIN");
  const [institutionName, setInstitutionName] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [confirm, setConfirm] = useState("");
  const [showPassword, setShowPassword] = useState(false);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  if (session) return <Navigate to="/dashboard" replace />;

  const longEnough = password.length >= 8;
  const matches = password.length > 0 && password === confirm;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    if (!longEnough) {
      setError("Use a password of at least 8 characters.");
      return;
    }
    if (!matches) {
      setError("Type the same password in both fields.");
      return;
    }
    setBusy(true);
    try {
      const response = await api<LoginResponse>(
        "/api/auth/register",
        {
          method: "POST",
          body: JSON.stringify({ institutionName, fullName, email, password, role }),
        },
        false,
      );
      if (!response.token) throw new Error("The API did not return a sign-in token.");
      setToken(response.token);
      await login(email, password);
      navigate("/dashboard", { replace: true });
    } catch (caught) {
      setToken(null);
      setError(caught instanceof Error ? caught.message : "The account could not be created.");
      setBusy(false);
    }
  }

  return (
    <AuthFrame>
      <form onSubmit={submit} className="panel mx-auto w-full max-w-md px-7 py-8">
        <h1 className="font-serif text-4xl">Create an account</h1>
        <p className="mt-2 text-sm leading-6 text-muted">Pick what this account is allowed to do. The choice is stored with the account.</p>
        <div className="mt-6 grid gap-2">
          {ROLES.map((item) => (
            <button
              key={item.id}
              type="button"
              className={`rounded-xl border px-3 py-2 text-left ${role === item.id ? "border-seal bg-[#eef4f8]" : "border-line"}`}
              onClick={() => setRole(item.id)}
            >
              <span className="text-sm font-medium text-ink">{item.title}</span>
              <span className="mt-0.5 block text-xs leading-5 text-muted">{item.text}</span>
            </button>
          ))}
        </div>
        <div className="mt-6 space-y-4">
          {role === "INSTITUTION_ADMIN" ? (
            <Field label="Institution name" hint="This name is written into new certificate fingerprints.">
              <TextInput value={institutionName} onChange={(event) => setInstitutionName(event.target.value)} required />
            </Field>
          ) : null}
          <Field label="Your name">
            <TextInput value={fullName} onChange={(event) => setFullName(event.target.value)} required />
          </Field>
          <Field
            label="Email"
            hint={role === "STUDENT" ? "Use the email your university has on the student record, or the wallet stays empty." : undefined}
          >
            <TextInput type="email" autoComplete="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </Field>
          <Field label="Password" hint={longEnough ? "Long enough." : "At least 8 characters."}>
            <span className="flex gap-2">
              <TextInput
                type={showPassword ? "text" : "password"}
                autoComplete="new-password"
                value={password}
                onChange={(event) => setPassword(event.target.value)}
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
          <Field label="Confirm password" hint={confirm.length === 0 ? undefined : matches ? "The passwords match." : "The passwords do not match."}>
            <TextInput
              type={showPassword ? "text" : "password"}
              autoComplete="new-password"
              value={confirm}
              onChange={(event) => setConfirm(event.target.value)}
              required
            />
          </Field>
        </div>
        {error ? <p className="mt-4 text-sm text-[#8c2f2f]">{error}</p> : null}
        <div className="mt-6 flex items-center gap-4">
          <Button type="submit" disabled={busy}>{busy ? "Creating account" : role === "INSTITUTION_ADMIN" ? "Create institution" : "Create account"}</Button>
          <Link to="/login" className="text-sm text-seal">Sign in</Link>
        </div>
      </form>
    </AuthFrame>
  );
}
