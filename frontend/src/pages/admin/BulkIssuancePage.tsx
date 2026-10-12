import { useState } from "react";
import { API_BASE, api, getToken } from "../../api/client";
import { Button, PageHeader } from "../../components/ui";

type Row = { rowNumber: number; studentNumber: string; certificateId: string; status: string; detail: string };
type Job = { id: string; status: string; total: number; valid: number; issued: number; failed: number; skipped: number; rows: Row[] };

export function BulkIssuancePage() {
  const [job, setJob] = useState<Job | null>(null);
  const [error, setError] = useState("");
  const [busy, setBusy] = useState(false);

  async function preview(file: File) {
    setBusy(true);
    setError("");
    const body = new FormData();
    body.append("file", file);
    try {
      const response = await fetch(`${API_BASE}/api/issuer/bulk-issuance/preview`, {
        method: "POST",
        headers: { Authorization: `Bearer ${getToken() ?? ""}` },
        body,
      });
      const payload = (await response.json()) as Job & { detail?: string };
      if (!response.ok) throw new Error(payload.detail || "The file could not be read.");
      setJob(payload);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The file could not be read.");
    } finally {
      setBusy(false);
    }
  }

  async function confirm() {
    if (!job) return;
    setBusy(true);
    try {
      setJob(await api<Job>(`/api/issuer/bulk-issuance/${job.id}/confirm`, { method: "POST" }));
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "Issuance did not finish.");
    } finally {
      setBusy(false);
    }
  }

  async function downloadText(path: string, filename: string) {
    const response = await fetch(`${API_BASE}${path}`, { headers: { Authorization: `Bearer ${getToken() ?? ""}` } });
    if (!response.ok) return;
    const url = URL.createObjectURL(await response.blob());
    const anchor = document.createElement("a");
    anchor.href = url;
    anchor.download = filename;
    anchor.click();
    URL.revokeObjectURL(url);
  }

  return (
    <div>
      <PageHeader
        title="Bulk issuance"
        lede="Upload a CSV to preview rows. Nothing is issued until you confirm. Students must already belong to this institution."
        action={<button type="button" className="text-sm text-seal" onClick={() => void downloadText("/api/issuer/bulk-issuance/template", "template.csv")}>Download template</button>}
      />
      {error ? <p className="mb-4 text-sm text-[#8c2f2f]">{error}</p> : null}
      <input type="file" accept=".csv,text/csv" onChange={(event) => { const file = event.target.files?.[0]; if (file) void preview(file); }} />
      {job ? (
        <div className="mt-6">
          <p className="text-sm text-muted">{job.total} rows · {job.valid} ready · {job.issued} issued · {job.failed} failed · {job.skipped} duplicates · {job.status}</p>
          <div className="panel mt-3 overflow-x-auto">
            <table className="w-full text-left text-sm">
              <thead><tr className="border-b border-line text-xs uppercase text-muted"><th className="px-3 py-2">Row</th><th>Student</th><th>Credential</th><th>Status</th><th>Detail</th></tr></thead>
              <tbody>
                {job.rows.map((row) => (
                  <tr key={row.rowNumber} className="border-b border-line">
                    <td className="px-3 py-2">{row.rowNumber}</td><td>{row.studentNumber}</td><td>{row.certificateId}</td><td>{row.status}</td><td>{row.detail}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {job.status === "PREVIEW" ? <Button className="mt-4" type="button" disabled={busy} onClick={() => void confirm()}>Issue valid rows</Button> : null}
          {job.status === "COMPLETED" ? <button type="button" className="mt-4 text-sm text-seal" onClick={() => void downloadText(`/api/issuer/bulk-issuance/${job.id}/report`, "issuance-report.csv")}>Download report</button> : null}
        </div>
      ) : null}
    </div>
  );
}
