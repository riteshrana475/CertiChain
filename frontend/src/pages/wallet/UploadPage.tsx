import { useState, type FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import { API_BASE, getToken } from "../../api/client";
import { Button, Field, PageHeader, SelectInput, TextArea, TextInput } from "../../components/ui";
import { CATEGORIES } from "./model";

export function UploadPage() {
  const navigate = useNavigate();
  const [file, setFile] = useState<File | null>(null);
  const [title, setTitle] = useState("");
  const [category, setCategory] = useState("OTHER");
  const [issuerName, setIssuerName] = useState("");
  const [issueDate, setIssueDate] = useState("");
  const [expiryDate, setExpiryDate] = useState("");
  const [credentialNumber, setCredentialNumber] = useState("");
  const [description, setDescription] = useState("");
  const [tags, setTags] = useState("");
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function submit(event: FormEvent) {
    event.preventDefault();
    if (!file || busy) return;
    setBusy(true);
    setError("");
    const body = new FormData();
    body.append("file", file);
    body.append("title", title);
    body.append("category", category);
    body.append("issuerName", issuerName);
    body.append("issueDate", issueDate);
    body.append("expiryDate", expiryDate);
    body.append("credentialNumber", credentialNumber);
    body.append("description", description);
    body.append("tags", tags);
    try {
      const response = await fetch(`${API_BASE}/api/wallet/documents`, {
        method: "POST",
        headers: { Authorization: `Bearer ${getToken() ?? ""}` },
        body,
      });
      const payload = (await response.json().catch(() => null)) as { id?: string; detail?: string } | null;
      if (!response.ok || !payload?.id) throw new Error(payload?.detail || "The upload failed.");
      navigate(`/wallet/documents/${payload.id}`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The upload failed.");
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="mx-auto max-w-2xl">
      <PageHeader
        title="Add a document"
        lede="PDF, PNG, or JPEG, up to 10 MB. The file is stored privately. Uploading it does not make it a university-verified credential."
      />
      <div className="panel space-y-4 px-5 py-6">
        <Field label="File" hint="PDF, PNG, or JPEG. Maximum 10 MB.">
          <input
            className="block w-full text-sm"
            type="file"
            accept="application/pdf,image/png,image/jpeg"
            onChange={(event) => setFile(event.target.files?.[0] ?? null)}
            required
          />
        </Field>
        {file ? (
          <p className="text-sm text-muted">
            {file.name} · {file.type || "unknown type"} · {Math.ceil(file.size / 1024)} KB
            <button type="button" className="ml-3 text-seal" onClick={() => setFile(null)}>Remove</button>
          </p>
        ) : null}
        <Field label="Title">
          <TextInput value={title} onChange={(event) => setTitle(event.target.value)} required />
        </Field>
        <Field label="Category">
          <SelectInput value={category} onChange={(event) => setCategory(event.target.value)}>
            {CATEGORIES.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
          </SelectInput>
        </Field>
        <Field label="Issuer name" hint="Optional. This is a label, not a verification.">
          <TextInput value={issuerName} onChange={(event) => setIssuerName(event.target.value)} />
        </Field>
        <div className="grid gap-4 sm:grid-cols-2">
          <Field label="Issue date">
            <TextInput type="date" value={issueDate} onChange={(event) => setIssueDate(event.target.value)} />
          </Field>
          <Field label="Expiry date">
            <TextInput type="date" value={expiryDate} onChange={(event) => setExpiryDate(event.target.value)} />
          </Field>
        </div>
        <Field label="Credential number">
          <TextInput value={credentialNumber} onChange={(event) => setCredentialNumber(event.target.value)} />
        </Field>
        <Field label="Description">
          <TextArea value={description} onChange={(event) => setDescription(event.target.value)} />
        </Field>
        <Field label="Tags" hint="Comma-separated.">
          <TextInput value={tags} onChange={(event) => setTags(event.target.value)} />
        </Field>
        {error ? <p className="text-sm text-[#8c2f2f]">{error}</p> : null}
        <Button type="submit" disabled={busy}>{busy ? "Uploading…" : "Store in wallet"}</Button>
      </div>
    </form>
  );
}
