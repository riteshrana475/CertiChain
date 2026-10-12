import { useState, type FormEvent } from "react";
import { Link, Navigate, useNavigate } from "react-router-dom";
import { api, setToken } from "../api/client";
import { Button, Field, SelectInput, TextInput } from "../components/ui";
import { useRecords } from "../state/records";

type LoginResponse = {
  token: string | null;
  name: string;
  email: string;
  role: "INSTITUTION_ADMIN" | "STUDENT" | "RECRUITER";
  institutionName: string;
};

export function SignupPage() {
  const { session, login } = useRecords();
  const navigate = useNavigate();
  const [role, setRole] = useState<"INSTITUTION_ADMIN" | "STUDENT" | "RECRUITER">("INSTITUTION_ADMIN");
  const [institutionName, setInstitutionName] = useState("");
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState("");

  if (session) return <Navigate to="/dashboard" replace />;

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
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
      setError(caught instanceof Error ? caught.message : "The institution could not be created.");
    }
  }

  return (
    <div className="flex min-h-screen items-center px-6 py-12">
      <form onSubmit={submit} className="panel mx-auto w-full max-w-md px-7 py-8">
        <h1 className="font-serif text-4xl">Create an account</h1>
        <p className="mt-2 text-sm leading-6 text-muted">
          An institution account can issue credentials. A student account is a private wallet. A verifier can check IDs and open links students send.
        </p>
        <div className="mt-8 space-y-4">
          <Field label="Account type">
            <SelectInput value={role} onChange={(event) => setRole(event.target.value as typeof role)}>
              <option value="INSTITUTION_ADMIN">Institution</option>
              <option value="STUDENT">Student</option>
              <option value="RECRUITER">Verifier</option>
            </SelectInput>
          </Field>
          {role === "INSTITUTION_ADMIN" ? (
          <Field label="Institution name">
            <TextInput value={institutionName} onChange={(event) => setInstitutionName(event.target.value)} required />
          </Field>
          ) : null}
          <Field label="Your name">
            <TextInput value={fullName} onChange={(event) => setFullName(event.target.value)} required />
          </Field>
          <Field label="Email">
            <TextInput type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
          </Field>
          <Field label="Password" hint="At least 8 characters.">
            <TextInput type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
          </Field>
        </div>
        {error ? <p className="mt-4 text-sm text-[#8c2f2f]">{error}</p> : null}
        <div className="mt-6 flex items-center gap-4">
          <Button type="submit">{role === "INSTITUTION_ADMIN" ? "Create institution" : "Create account"}</Button>
          <Link to="/login" className="text-sm text-seal">
            Sign in
          </Link>
        </div>
      </form>
    </div>
  );
}
