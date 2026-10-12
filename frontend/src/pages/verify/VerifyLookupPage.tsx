import { useState, type FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { Button, Field, LogoMark, TextInput } from "../../components/ui";

export function VerifyLookupPage() {
  const navigate = useNavigate();
  const [certificateId, setCertificateId] = useState("");

  function submit(event: FormEvent) {
    event.preventDefault();
    navigate(`/verify/${certificateId.trim()}`);
  }

  return (
    <div className="flex min-h-screen items-center px-6 py-12">
      <form onSubmit={submit} className="panel mx-auto w-full max-w-lg px-7 py-8">
        <div className="flex items-center gap-3">
          <LogoMark className="h-12 w-12" />
          <p className="font-serif text-2xl">CertiChain</p>
        </div>
        <h1 className="mt-6 font-serif text-4xl">Verify a credential</h1>
        <p className="mt-3 text-sm leading-6 text-muted">
          Enter the credential ID printed on the certificate or opened from a QR code. The QR code only identifies the record. The result on the next page is the verification.
        </p>
        <div className="mt-6">
          <Field label="Credential ID">
            <TextInput value={certificateId} onChange={(event) => setCertificateId(event.target.value)} placeholder="CERT-2026-001245" required />
          </Field>
        </div>
        <div className="mt-6 flex items-center gap-4">
          <Button type="submit">Check</Button>
          <Link to="/login" className="text-sm text-seal">Sign in</Link>
        </div>
      </form>
    </div>
  );
}
