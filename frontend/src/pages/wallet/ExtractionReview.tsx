import { useEffect, useState } from "react";
import { api } from "../../api/client";
import { Button } from "../../components/ui";
import { CATEGORIES } from "./model";

type Field = {
  fieldName: string;
  candidateValue: string;
  correctedValue: string;
  confidence: string;
  reviewStatus: string;
};
type Comparison = { field: string; extracted: string; record: string; outcome: string };
type Extraction = {
  status: string;
  method: string;
  note: string;
  suggestedCategory: string;
  categoryConfidence: string;
  fields: Field[];
  comparisons: Comparison[];
};

export function ExtractionReview({ documentId }: { documentId: string }) {
  const [extraction, setExtraction] = useState<Extraction | null>(null);
  const [drafts, setDrafts] = useState<Record<string, string>>({});
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");

  useEffect(() => {
    void api<Extraction>(`/api/wallet/documents/${documentId}/extraction`)
      .then((next) => {
        setExtraction(next);
        setDrafts(Object.fromEntries(next.fields.map((field) => [field.fieldName, field.correctedValue || field.candidateValue])));
      })
      .catch(() => setExtraction(null));
  }, [documentId]);

  async function read() {
    setError("");
    try {
      const next = await api<Extraction>(`/api/wallet/documents/${documentId}/extract`, { method: "POST" });
      setExtraction(next);
      setDrafts(Object.fromEntries(next.fields.map((field) => [field.fieldName, field.correctedValue || field.candidateValue])));
      setMessage(next.note);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The document could not be read.");
    }
  }

  async function confirm(category?: string) {
    if (!extraction) return;
    const next = await api<Extraction>(`/api/wallet/documents/${documentId}/extraction/confirm`, {
      method: "POST",
      body: JSON.stringify({
        category: category ?? "",
        fields: extraction.fields.map((field) => ({ fieldName: field.fieldName, value: drafts[field.fieldName] ?? "" })),
      }),
    });
    setExtraction(next);
    setMessage("Your corrections are saved separately from the extracted values. This does not verify the document.");
  }

  return (
    <section className="panel mt-6 px-5 py-5">
      <h2 className="font-serif text-2xl">Read fields</h2>
      <p className="mt-2 text-sm leading-6 text-muted">
        Suggestions come from the file text or the local OCR service. They are not a university verification. Confirm anything you want to keep.
      </p>
      <Button type="button" className="mt-4" variant="secondary" onClick={() => void read()}>Read this document</Button>
      {error ? <p className="mt-3 text-sm text-[#8c2f2f]">{error}</p> : null}
      {message ? <p className="mt-3 text-sm text-muted">{message}</p> : null}
      {extraction ? (
        <div className="mt-4 space-y-3">
          <p className="text-xs text-muted">{extraction.status} · {extraction.method} · {extraction.note}</p>
          {extraction.fields.length === 0 ? <p className="text-sm text-muted">No labeled fields were found.</p> : extraction.fields.map((field) => (
            <label key={field.fieldName} className="block text-sm">
              <span className="text-muted">{field.fieldName} · extracted “{field.candidateValue}” · {field.confidence}</span>
              <input
                className="mt-1 w-full rounded-xl border border-line bg-white px-3 py-2"
                value={drafts[field.fieldName] ?? ""}
                onChange={(event) => setDrafts((current) => ({ ...current, [field.fieldName]: event.target.value }))}
              />
            </label>
          ))}
          {extraction.comparisons.length > 0 ? (
            <ul className="text-sm">
              {extraction.comparisons.map((item) => (
                <li key={item.field}>{item.field}: {item.outcome}. Record “{item.record}”, extracted “{item.extracted}”.</li>
              ))}
            </ul>
          ) : null}
          {extraction.categoryConfidence === "LOW" ? (
            <p className="text-sm text-muted">Suggested category {CATEGORIES.find(([id]) => id === extraction.suggestedCategory)?.[1] ?? extraction.suggestedCategory} is low confidence. Confirm it only if it is right.</p>
          ) : null}
          <div className="flex flex-wrap gap-2">
            <Button type="button" onClick={() => void confirm()}>Save corrections</Button>
            <Button type="button" variant="secondary" onClick={() => void confirm(extraction.suggestedCategory)}>Use suggested category</Button>
          </div>
        </div>
      ) : null}
    </section>
  );
}
