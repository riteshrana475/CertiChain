import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { api } from "../../api/client";
import { Button, Field, PageHeader, SelectInput, TextInput } from "../../components/ui";
import { DocumentCard } from "./StudentDashboard";
import { CATEGORIES, type WalletPage } from "./model";

export function WalletLibraryPage() {
  const [q, setQ] = useState("");
  const [category, setCategory] = useState("");
  const [source, setSource] = useState("ALL");
  const [sort, setSort] = useState("NEWEST");
  const [archived, setArchived] = useState(false);
  const [page, setPage] = useState(0);
  const [view, setView] = useState<"grid" | "list">("grid");
  const [wallet, setWallet] = useState<WalletPage | null>(null);
  const [error, setError] = useState("");

  useEffect(() => {
    const params = new URLSearchParams({
      q,
      category,
      source,
      sort,
      archived: String(archived),
      page: String(page),
      size: "8",
    });
    void api<WalletPage>(`/api/wallet/documents?${params}`)
      .then(setWallet)
      .catch((caught: unknown) => setError(caught instanceof Error ? caught.message : "Search failed."));
  }, [q, category, source, sort, archived, page]);

  const uploads = wallet?.uploads ?? [];
  const credentials = wallet?.credentials ?? [];
  const pages = Math.max(1, Math.ceil((wallet?.uploadTotal ?? 0) / 8));

  return (
    <div>
      <PageHeader
        title="Wallet"
        lede="Issuer credentials appear when the student record uses the same email as this account. Files you upload stay marked as not verified."
        action={<Link to="/wallet/upload"><Button>Add document</Button></Link>}
      />
      {error ? <p className="mb-4 text-sm text-[#8c2f2f]">{error}</p> : null}
      <div className="grid gap-3 md:grid-cols-4">
        <Field label="Search">
          <TextInput value={q} onChange={(event) => { setPage(0); setQ(event.target.value); }} placeholder="Title, issuer, or ID" />
        </Field>
        <Field label="Category">
          <SelectInput value={category} onChange={(event) => { setPage(0); setCategory(event.target.value); }}>
            <option value="">All categories</option>
            {CATEGORIES.map(([id, label]) => <option key={id} value={id}>{label}</option>)}
          </SelectInput>
        </Field>
        <Field label="Source">
          <SelectInput value={source} onChange={(event) => setSource(event.target.value)}>
            <option value="ALL">Everything</option>
            <option value="CREDENTIAL">Issuer issued</option>
            <option value="UPLOAD">Uploaded by you</option>
          </SelectInput>
        </Field>
        <Field label="Sort">
          <SelectInput value={sort} onChange={(event) => setSort(event.target.value)}>
            <option value="NEWEST">Newest first</option>
            <option value="OLDEST">Oldest first</option>
            <option value="NAME">Name A–Z</option>
            <option value="NAME_DESC">Name Z–A</option>
          </SelectInput>
        </Field>
      </div>
      <div className="mt-4 flex flex-wrap items-center gap-3 text-sm">
        <button className="text-seal" onClick={() => setView("grid")}>Grid</button>
        <button className="text-seal" onClick={() => setView("list")}>List</button>
        <label className="flex items-center gap-2 text-muted">
          <input type="checkbox" checked={archived} onChange={(event) => { setPage(0); setArchived(event.target.checked); }} />
          Archived uploads
        </label>
      </div>
      {source !== "UPLOAD" ? (
        <section className="mt-8">
          <h2 className="font-serif text-2xl">Issued to you</h2>
          {credentials.length === 0 ? <p className="mt-3 text-sm text-muted">No issued credential is linked to this email.</p> : (
            <div className={view === "grid" ? "mt-3 grid gap-3 sm:grid-cols-2" : "mt-3 space-y-3"}>
              {credentials.map((item) => <DocumentCard key={item.id} item={item} />)}
            </div>
          )}
        </section>
      ) : null}
      {source !== "CREDENTIAL" ? (
        <section className="mt-8">
          <h2 className="font-serif text-2xl">Your uploads</h2>
          {uploads.length === 0 ? <p className="mt-3 text-sm text-muted">No uploads match this search.</p> : (
            <div className={view === "grid" ? "mt-3 grid gap-3 sm:grid-cols-2" : "mt-3 space-y-3"}>
              {uploads.map((item) => <DocumentCard key={item.id} item={item} />)}
            </div>
          )}
          <div className="mt-4 flex items-center gap-3 text-sm">
            <Button type="button" variant="secondary" disabled={page === 0} onClick={() => setPage((current) => current - 1)}>Previous</Button>
            <span className="text-muted">Page {page + 1} of {pages}</span>
            <Button type="button" variant="secondary" disabled={page + 1 >= pages} onClick={() => setPage((current) => current + 1)}>Next</Button>
          </div>
        </section>
      ) : null}
    </div>
  );
}
