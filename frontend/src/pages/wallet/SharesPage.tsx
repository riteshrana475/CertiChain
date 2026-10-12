import { useEffect, useState, type FormEvent } from "react";
import { api } from "../../api/client";
import { Button, Field, PageHeader, TextInput } from "../../components/ui";
import type { WalletPage } from "./model";

type ShareView = {
  id: string;
  recipient: string;
  expiresAt: string;
  status: string;
  allowDownload: boolean;
  createdAt: string;
  items: { id: string; title: string; kind: string }[];
};

type Created = { id: string; token: string; expiresAt: string; allowDownload: boolean };

export function SharesPage() {
  const [wallet, setWallet] = useState<WalletPage | null>(null);
  const [shares, setShares] = useState<ShareView[]>([]);
  const [selectedDocs, setSelectedDocs] = useState<string[]>([]);
  const [selectedCerts, setSelectedCerts] = useState<string[]>([]);
  const [recipient, setRecipient] = useState("");
  const [expiresAt, setExpiresAt] = useState("");
  const [allowDownload, setAllowDownload] = useState(false);
  const [note, setNote] = useState("");
  const [created, setCreated] = useState<Created | null>(null);
  const [history, setHistory] = useState<Array<{ id: string; eventType: string; createdAt: string }>>([]);
  const [error, setError] = useState("");

  function load() {
    void Promise.all([api<WalletPage>("/api/wallet/documents?size=24"), api<ShareView[]>("/api/shares")])
      .then(([nextWallet, nextShares]) => {
        setWallet(nextWallet);
        setShares(nextShares);
      })
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : "Shares could not be loaded."));
  }

  useEffect(load, []);

  async function submit(event: FormEvent) {
    event.preventDefault();
    setError("");
    try {
      const next = await api<Created>("/api/shares", {
        method: "POST",
        body: JSON.stringify({
          documentIds: selectedDocs,
          certificateIds: selectedCerts,
          recipient,
          expiresAt: expiresAt ? new Date(expiresAt).toISOString() : "",
          allowDownload,
          note,
        }),
      });
      setCreated(next);
      setSelectedDocs([]);
      setSelectedCerts([]);
      load();
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The link could not be created.");
    }
  }

  async function revoke(id: string) {
    await api(`/api/shares/${id}/revoke`, { method: "POST" });
    load();
  }

  const link = created ? `${window.location.origin}/shared/${created.token}` : "";

  return (
    <div>
      <PageHeader
        title="Sharing"
        lede="A link opens only the documents you select, until it expires or you revoke it. Email delivery is not configured, so copy the link. A downloaded copy cannot be deleted from the recipient’s device."
      />
      {error ? <p className="mb-4 text-sm text-[#8c2f2f]">{error}</p> : null}
      <form onSubmit={submit} className="panel space-y-4 px-5 py-6">
        <fieldset>
          <legend className="text-sm font-medium">Credentials</legend>
          <div className="mt-2 space-y-2">
            {(wallet?.credentials ?? []).map((item) => (
              <label key={item.id} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={selectedCerts.includes(item.id)} onChange={() => setSelectedCerts((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id])} />
                {item.title}
              </label>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend className="text-sm font-medium">Uploads</legend>
          <div className="mt-2 space-y-2">
            {(wallet?.uploads ?? []).map((item) => (
              <label key={item.id} className="flex items-center gap-2 text-sm">
                <input type="checkbox" checked={selectedDocs.includes(item.id)} onChange={() => setSelectedDocs((current) => current.includes(item.id) ? current.filter((id) => id !== item.id) : [...current, item.id])} />
                {item.title}
              </label>
            ))}
          </div>
        </fieldset>
        <Field label="Recipient label">
          <TextInput value={recipient} onChange={(event) => setRecipient(event.target.value)} placeholder="Name or email" />
        </Field>
        <Field label="Expires">
          <TextInput type="datetime-local" value={expiresAt} onChange={(event) => setExpiresAt(event.target.value)} />
        </Field>
        <label className="flex items-center gap-2 text-sm">
          <input type="checkbox" checked={allowDownload} onChange={(event) => setAllowDownload(event.target.checked)} />
          Allow download. Leave this off for preview details only.
        </label>
        <Field label="Note">
          <TextInput value={note} onChange={(event) => setNote(event.target.value)} />
        </Field>
        <Button type="submit">Create link</Button>
        {created ? (
          <p className="break-all rounded-xl bg-[#eef4f8] px-3 py-3 text-sm">
            Copy this link. It is shown once.
            <span className="mt-2 block text-ink">{link}</span>
            <button type="button" className="mt-2 text-seal" onClick={() => void navigator.clipboard.writeText(link)}>Copy</button>
          </p>
        ) : null}
      </form>
      <ul className="mt-6 space-y-3">
        {shares.map((share) => (
          <li key={share.id} className="panel px-4 py-4 text-sm">
            <p className="font-medium">{share.recipient || "Untitled share"} · {share.status}</p>
            <p className="mt-1 text-muted">Expires {share.expiresAt.slice(0, 16).replace("T", " ")} · {share.items.map((item) => item.title).join(", ")}</p>
            {share.status === "ACTIVE" ? <button className="mt-2 text-seal" onClick={() => void revoke(share.id)}>Revoke</button> : null}
            <button className="mt-2 ml-3 text-seal" onClick={() => void api<Array<{ id: string; eventType: string; createdAt: string }>>(`/api/shares/${share.id}/access`).then(setHistory)}>Access history</button>
          </li>
        ))}
      </ul>
      {history.length > 0 ? (
        <ul className="mt-4 text-sm text-muted">
          {history.map((event) => <li key={event.id}>{event.eventType} · {event.createdAt.slice(0, 16).replace("T", " ")}</li>)}
        </ul>
      ) : null}
    </div>
  );
}
