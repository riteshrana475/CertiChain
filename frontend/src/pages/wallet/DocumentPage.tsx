import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { API_BASE, api, getToken } from "../../api/client";
import { Button, PageHeader, StatusPill } from "../../components/ui";
import { categoryLabel, statusLabel, type WalletItem } from "./model";
import { ExtractionReview } from "./ExtractionReview";

export function DocumentPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [item, setItem] = useState<WalletItem | null>(null);
  const [preview, setPreview] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    void api<WalletItem>(`/api/wallet/documents/${id}`)
      .then(setItem)
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : "Document was not found."));
  }, [id]);

  useEffect(() => {
    let url = "";
    void fetch(`${API_BASE}/api/wallet/documents/${id}/preview`, {
      headers: { Authorization: `Bearer ${getToken() ?? ""}` },
    })
      .then(async (response) => {
        if (!response.ok) throw new Error("Preview is unavailable.");
        url = URL.createObjectURL(await response.blob());
        setPreview(url);
      })
      .catch(() => setPreview(""));
    return () => {
      if (url) URL.revokeObjectURL(url);
    };
  }, [id]);

  async function download() {
    const response = await fetch(`${API_BASE}/api/wallet/documents/${id}/download`, {
      headers: { Authorization: `Bearer ${getToken() ?? ""}` },
    });
    if (!response.ok) return;
    const blob = await response.blob();
    const url = URL.createObjectURL(blob);
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = "document";
    anchor.click();
    URL.revokeObjectURL(url);
  }

  async function remove() {
    if (!window.confirm("Remove this upload from your wallet? An issued credential is not deleted by this action.")) return;
    await api(`/api/wallet/documents/${id}`, { method: "DELETE" });
    navigate("/wallet");
  }

  async function archive() {
    const next = await api<WalletItem>(`/api/wallet/documents/${id}/archive`, { method: "POST" });
    setItem(next);
  }

  return (
    <div>
      <PageHeader
        title={item?.title ?? "Document"}
        lede="This file is a personal upload. It is not an issuer-signed credential unless a university issued it separately."
        action={<Link to="/wallet" className="text-sm text-seal">Back to wallet</Link>}
      />
      {error ? <p className="text-sm text-[#8c2f2f]">{error}</p> : null}
      {item ? (
        <>
        <div className="grid gap-6 lg:grid-cols-[1fr_280px]">
          <div className="panel min-h-80 overflow-hidden bg-white">
            {preview && item.contentType.startsWith("image/") ? <img src={preview} alt="" className="max-h-[70vh] w-full object-contain" /> : null}
            {preview && item.contentType === "application/pdf" ? <iframe title="Document preview" src={preview} className="h-[70vh] w-full" /> : null}
            {!preview ? <p className="p-6 text-sm text-muted">Preview is unavailable. You can still download the file.</p> : null}
          </div>
          <aside className="space-y-3 text-sm">
            <StatusPill status={item.status} label={statusLabel(item.status)} />
            <p><span className="text-muted">Category. </span>{categoryLabel(item.category)}</p>
            <p><span className="text-muted">Source. </span>{item.issuer || "Uploaded by you"}</p>
            <p><span className="text-muted">Added. </span>{item.date.slice(0, 10)}</p>
            {item.credentialId ? <p><span className="text-muted">Number. </span>{item.credentialId}</p> : null}
            <p><span className="text-muted">File. </span>{item.contentType} · {Math.ceil(item.fileSize / 1024)} KB</p>
            {item.extractedText ? (
              <p className="rounded-xl bg-[#eef4f8] p-3 text-xs leading-5 text-muted">
                Text suggestion from the optional OCR service. This is not a verification result.
                <span className="mt-2 block text-ink">{item.extractedText.slice(0, 400)}</span>
              </p>
            ) : null}
            <div className="flex flex-wrap gap-2">
              <Button type="button" onClick={() => void download()}>Download</Button>
              <Button type="button" variant="secondary" onClick={() => void archive()}>Archive</Button>
              <Button type="button" variant="secondary" onClick={() => void remove()}>Delete</Button>
            </div>
          </aside>
        </div>
        <ExtractionReview documentId={id} />
        </>
      ) : null}
    </div>
  );
}
