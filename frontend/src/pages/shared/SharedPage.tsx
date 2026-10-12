import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { API_BASE } from "../../api/client";
import { StatusPill } from "../../components/ui";

type Item = { id: string; kind: string; title: string; status: string; category: string };
type Bundle = { expiresAt: string; allowDownload: boolean; recipient: string; items: Item[] };

export function SharedPage() {
  const { token = "" } = useParams();
  const [bundle, setBundle] = useState<Bundle | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    void fetch(`${API_BASE}/api/shared/${encodeURIComponent(token)}`)
      .then(async (response) => {
        const payload = (await response.json().catch(() => null)) as Bundle & { detail?: string };
        if (!response.ok) throw new Error(payload?.detail || "This link is not available.");
        setBundle(payload);
      })
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : "This link is not available."));
  }, [token]);

  return (
    <div className="mx-auto min-h-screen max-w-3xl px-6 py-12">
      <h1 className="font-serif text-4xl">Shared documents</h1>
      <p className="mt-2 text-sm text-muted">This page shows only what the student included. It is not the rest of their wallet.</p>
      {error ? <p className="mt-6 text-sm text-[#8c2f2f]">{error}</p> : null}
      {bundle ? (
        <div className="mt-6 space-y-3">
          <p className="text-sm text-muted">Expires {bundle.expiresAt.slice(0, 16).replace("T", " ")} · {bundle.allowDownload ? "Download allowed" : "Details only"}</p>
          {bundle.items.map((item) => (
            <article key={item.id} className="panel px-4 py-4">
              <div className="flex items-start justify-between gap-3">
                <div>
                  <h2 className="font-medium">{item.title}</h2>
                  <p className="mt-1 text-xs text-muted">{item.kind === "CREDENTIAL" ? "Issuer credential" : "Student upload"}</p>
                </div>
                <StatusPill status={item.status} label={item.status === "USER_UPLOADED" ? "User uploaded — not verified" : item.status} />
              </div>
              {item.kind === "CREDENTIAL" ? <Link to={`/verify/${item.id}`} className="mt-3 inline-block text-sm text-seal">Open verification</Link> : null}
              {item.kind === "UPLOAD" && bundle.allowDownload ? (
                <a className="mt-3 inline-block text-sm text-seal" href={`${API_BASE}/api/shared/${encodeURIComponent(token)}/documents/${item.id}`}>Download</a>
              ) : null}
            </article>
          ))}
        </div>
      ) : null}
    </div>
  );
}
