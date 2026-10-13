import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { API_BASE, api, getToken } from "../../api/client";
import { Button, PageHeader, StatusPill } from "../../components/ui";
import { useRecords } from "../../state/records";
import { categoryLabel, statusLabel, type WalletItem, type WalletPage } from "./model";

type Notice = { id: string; title: string; message: string; href: string; readAt: string | null; createdAt: string };
type Activity = { id: string; eventType: string; detail: string; createdAt: string };

export function StudentDashboard() {
  const { session } = useRecords();
  const [wallet, setWallet] = useState<WalletPage | null>(null);
  const [notices, setNotices] = useState<Notice[]>([]);
  const [activity, setActivity] = useState<Activity[]>([]);
  const [error, setError] = useState("");

  useEffect(() => {
    void Promise.all([
      api<WalletPage>("/api/wallet/documents?size=4"),
      api<Notice[]>("/api/notifications"),
      api<Activity[]>("/api/wallet/activity"),
    ])
      .then(([nextWallet, nextNotices, nextActivity]) => {
        setWallet(nextWallet);
        setNotices(nextNotices);
        setActivity(nextActivity);
      })
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : "The wallet could not be loaded."));
  }, []);

  const recent = [...(wallet?.credentials ?? []), ...(wallet?.uploads ?? [])].slice(0, 4);
  const stats = [
    { label: "Documents", value: wallet?.summary.total ?? 0 },
    { label: "Issuer credentials", value: wallet?.summary.issued ?? 0 },
    { label: "Pending requests", value: wallet?.summary.pendingRequests ?? 0 },
    { label: "Active shares", value: wallet?.summary.activeShares ?? 0 },
  ];

  return (
    <div>
      <PageHeader
        title={`Hello, ${session?.name?.split(" ")[0] ?? "there"}`}
        lede="Your wallet holds documents you upload and credentials an institution issues to this email. An upload is not a verified credential."
        action={
          <div className="flex gap-3">
            <Link to="/wallet/upload"><Button>Add document</Button></Link>
            <Link to="/wallet"><Button variant="secondary">View all documents</Button></Link>
          </div>
        }
      />
      {error ? <p className="mb-4 text-sm text-[#8c2f2f]">{error}</p> : null}
      <div className="grid grid-cols-2 gap-3 lg:grid-cols-4">
        {stats.map((stat) => (
          <div key={stat.label} className="panel px-4 py-5">
            <p className="font-serif text-4xl leading-none">{stat.value}</p>
            <p className="mt-2 text-[11px] font-semibold tracking-[0.14em] text-muted uppercase">{stat.label}</p>
          </div>
        ))}
      </div>
      <div className="mt-8 grid gap-8 xl:grid-cols-[1.4fr_0.8fr]">
        <section>
          <h2 className="font-serif text-2xl">Recent documents</h2>
          {recent.length === 0 ? (
            <div className="panel mt-3 px-5 py-8 text-sm leading-6 text-muted">
              Nothing is in the wallet yet. Upload a supporting file, or ask your institution to issue a credential to {session?.email}.
            </div>
          ) : (
            <div className="mt-3 grid gap-3 sm:grid-cols-2">
              {recent.map((item) => (
                <DocumentCard key={item.id} item={item} />
              ))}
            </div>
          )}
          <div className="mt-6 flex flex-wrap gap-3">
            <Link to="/wallet/upload" className="text-sm text-seal">Upload a document</Link>
            <Link to="/wallet" className="text-sm text-seal">Browse the wallet</Link>
            <Link to="/verify" className="text-sm text-seal">Verify a certificate</Link>
            <Link to="/wallet/shared" className="text-sm text-seal">Share a document</Link>
          </div>
        </section>
        <section>
          <h2 className="font-serif text-2xl">Recent activity</h2>
          {activity.length === 0 && notices.length === 0 ? (
            <p className="panel mt-3 px-5 py-8 text-sm text-muted">Uploads, shares, and issued credentials will show up here.</p>
          ) : (
            <ul className="panel mt-3 divide-y divide-line">
              {notices.slice(0, 6).map((notice) => (
                <li key={notice.id} className="px-4 py-3 text-sm">
                  <Link to={notice.href || "/wallet"} className="font-medium">{notice.title}</Link>
                  <p className="mt-1 text-muted">{notice.message}</p>
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </div>
  );
}

export function DocumentCard({ item }: { item: WalletItem }) {
  const [error, setError] = useState("");
  const to = item.kind === "CREDENTIAL" ? `/verify/${item.id}` : `/wallet/documents/${item.id}`;

  async function downloadCredential() {
    setError("");
    const response = await fetch(`${API_BASE}/api/wallet/credentials/${item.id}/document`, {
      headers: { Authorization: `Bearer ${getToken() ?? ""}` },
    });
    if (!response.ok) {
      setError("That credential is not issued to this account.");
      return;
    }
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = `${item.id}.pdf`;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <article className="panel px-4 py-4">
      <div className="flex items-start justify-between gap-3">
        <div>
          <h3 className="font-medium">{item.title}</h3>
          <p className="mt-1 text-xs text-muted">{categoryLabel(item.category)} · {item.issuer || "Uploaded by you"}</p>
          <p className="mt-1 text-xs text-muted">{item.date.slice(0, 10)}</p>
        </div>
        <StatusPill status={item.status} label={statusLabel(item.status)} />
      </div>
      <div className="mt-3 flex gap-4">
        <Link to={to} className="text-sm text-seal">Open</Link>
        {item.kind === "CREDENTIAL" ? (
          <button type="button" className="text-sm text-seal" onClick={() => void downloadCredential()}>Download</button>
        ) : null}
      </div>
      {error ? <p className="mt-2 text-xs text-[#8c2f2f]">{error}</p> : null}
    </article>
  );
}
