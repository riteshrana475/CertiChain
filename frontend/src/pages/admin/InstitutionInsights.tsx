import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { PageHeader } from "../../components/ui";

type Anchor = {
  certificateId: string;
  documentHash: string;
  credentialStatus: string;
  anchorState: string;
  txHash: string;
  network: string;
  blockNumber: number | null;
  explorerUrl: string;
  checkedAt: string;
};
type Analytics = { lookups: number; lookupsLast7Days: number; outcomes: Record<string, number>; credentials: number; anchored: number; definition: string };
type Flag = { id: string; ruleName: string; severity: string; targetId: string; detail: string; reviewStatus: string; createdAt: string };

export function AnchorsPage() {
  const [rows, setRows] = useState<Anchor[]>([]);
  const [error, setError] = useState("");
  useEffect(() => {
    void api<Anchor[]>("/api/issuer/anchors").then(setRows).catch((caught: unknown) => setError(caught instanceof Error ? caught.message : "Anchors could not be loaded."));
  }, []);
  return (
    <div>
      <PageHeader title="Chain anchors" lede="Anchor state is separate from whether the credential is valid. A confirmed transaction shows the fingerprint was recorded. It does not prove the certificate was genuinely issued." />
      {error ? <p className="text-sm text-[#8c2f2f]">{error}</p> : null}
      {rows.length === 0 ? <p className="text-sm text-muted">No credentials yet.</p> : (
        <div className="space-y-3">
          {rows.map((row) => (
            <article key={row.certificateId} className="panel px-4 py-4 text-sm">
              <p className="font-medium">{row.certificateId}</p>
              <p className="mt-1 text-muted">Credential {row.credentialStatus} · Anchor {row.anchorState}{row.blockNumber ? ` · block ${row.blockNumber}` : ""} · {row.network || "not submitted"}</p>
              <p className="mt-1 break-all font-mono text-xs text-muted">{row.documentHash}</p>
              {row.explorerUrl ? <a className="mt-2 inline-block text-seal" href={row.explorerUrl}>View transaction</a> : <p className="mt-2 text-xs text-muted">{row.txHash ? row.txHash : "No transaction yet."} The local chain has no public explorer.</p>}
            </article>
          ))}
        </div>
      )}
    </div>
  );
}

export function InsightsPage() {
  const [data, setData] = useState<Analytics | null>(null);
  useEffect(() => { void api<Analytics>("/api/issuer/analytics/verification").then(setData).catch(() => setData(null)); }, []);
  const outcomes = Object.entries(data?.outcomes ?? {});
  return (
    <div>
      <PageHeader title="Verification insights" lede={data?.definition ?? "Counts come from saved verification events for this institution."} />
      {!data || data.lookups === 0 ? <p className="text-sm text-muted">No verification events yet.</p> : (
        <>
          <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
            <Card label="Lookups" value={data.lookups} />
            <Card label="Last 7 days" value={data.lookupsLast7Days} />
            <Card label="Credentials" value={data.credentials} />
            <Card label="Anchored" value={data.anchored} />
          </div>
          <ul className="mt-6 space-y-2">
            {outcomes.map(([name, count]) => (
              <li key={name} className="text-sm">
                <div className="mb-1 flex justify-between"><span>{name}</span><span>{count}</span></div>
                <div className="h-2 rounded-full bg-[#e7eef3]"><div className="h-2 rounded-full bg-seal" style={{ width: `${Math.max(4, (count / data.lookups) * 100)}%` }} /></div>
              </li>
            ))}
          </ul>
        </>
      )}
    </div>
  );
}

export function AnomaliesPage() {
  const [flags, setFlags] = useState<Flag[]>([]);
  const [note, setNote] = useState("");
  useEffect(() => { void api<Flag[]>("/api/issuer/anomalies").then(setFlags).catch(() => setFlags([])); }, []);
  async function review(id: string) {
    await api(`/api/issuer/anomalies/${id}/review`, { method: "POST", body: JSON.stringify({ note }) });
    setFlags(await api<Flag[]>("/api/issuer/anomalies"));
  }
  return (
    <div>
      <PageHeader title="Anomaly review" lede="These are rules, not accusations. Reviewing a flag does not revoke a credential." />
      {flags.length === 0 ? <p className="text-sm text-muted">No flags for this institution.</p> : flags.map((flag) => (
        <article key={flag.id} className="panel mb-3 px-4 py-4 text-sm">
          <p className="font-medium">{flag.severity} · {flag.ruleName}</p>
          <p className="mt-1 text-muted">{flag.detail} {flag.targetId}</p>
          <p className="mt-1 text-xs text-muted">{flag.reviewStatus} · {flag.createdAt.slice(0, 16).replace("T", " ")}</p>
          {flag.reviewStatus === "OPEN" ? (
            <div className="mt-3 flex gap-2">
              <input className="flex-1 rounded-xl border border-line px-3 py-2" value={note} onChange={(event) => setNote(event.target.value)} placeholder="Review note" />
              <button className="text-seal" onClick={() => void review(flag.id)}>Mark reviewed</button>
            </div>
          ) : null}
        </article>
      ))}
    </div>
  );
}

function Card({ label, value }: { label: string; value: number }) {
  return <div className="panel px-4 py-5"><p className="font-serif text-4xl">{value}</p><p className="mt-2 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase">{label}</p></div>;
}
