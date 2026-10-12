import { useEffect, useState, type FormEvent } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import { Button, Field, PageHeader, TextInput } from "../../components/ui";
import { useRecords } from "../../state/records";

type Check = { id: string; certificateId: string; result: string; checkedAt: string };

export function RecruiterPage() {
  const { session } = useRecords();
  const [certificateId, setCertificateId] = useState("");
  const [checks, setChecks] = useState<Check[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    void api<Check[]>("/api/verifier/checks").then(setChecks).catch(() => setChecks([]));
  }, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      await api(`/api/verifier/checks/${certificateId.trim()}`, { method: "POST" });
      window.location.assign(`/verify/${certificateId.trim()}`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "That credential could not be checked.");
    }
  }

  return (
    <div>
      <PageHeader
        title={`Verifier desk, ${session?.name?.split(" ")[0] ?? ""}`}
        lede="Check a credential by its ID, or open a link a student sent you. This desk cannot browse a student’s wallet."
      />
      <form onSubmit={submit} className="panel max-w-xl space-y-4 px-5 py-6">
        <Field label="Credential ID">
          <TextInput value={certificateId} onChange={(event) => setCertificateId(event.target.value)} placeholder="CERT-2026-001245" required />
        </Field>
        {error ? <p className="text-sm text-[#8c2f2f]">{error}</p> : null}
        <Button type="submit">Verify</Button>
      </form>
      <h2 className="mt-8 font-serif text-2xl">Your recent checks</h2>
      {checks.length === 0 ? <p className="mt-3 text-sm text-muted">No checks yet.</p> : (
        <ul className="mt-3 space-y-2">
          {checks.map((check) => (
            <li key={check.id} className="panel px-4 py-3 text-sm">
              <Link to={`/verify/${check.certificateId}`} className="text-seal">{check.certificateId}</Link>
              <span className="ml-3 text-muted">{check.result} · {check.checkedAt.slice(0, 16).replace("T", " ")}</span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
