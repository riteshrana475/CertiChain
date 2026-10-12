export const CATEGORIES = [
  ["DEGREE", "Degrees and diplomas"],
  ["MARKSHEET", "Semester marksheets"],
  ["TRANSCRIPT", "Academic transcripts"],
  ["SCHOOL", "School certificates"],
  ["INTERNSHIP", "Internship and experience"],
  ["SKILL", "Skill and course certificates"],
  ["IDENTITY", "Identity documents"],
  ["ADMISSION", "Admission documents"],
  ["OTHER", "Other supporting documents"],
] as const;

export type WalletItem = {
  id: string;
  kind: "CREDENTIAL" | "UPLOAD";
  title: string;
  category: string;
  source: string;
  issuer: string;
  date: string;
  status: string;
  credentialId: string;
  contentType: string;
  fileSize: number;
  extractedText: string;
  scanStatus?: string;
};

export type WalletPage = {
  credentials: WalletItem[];
  uploads: WalletItem[];
  page: number;
  size: number;
  uploadTotal: number;
  summary: {
    total: number;
    issued: number;
    pendingRequests: number;
    activeShares: number;
  };
};

export function categoryLabel(value: string): string {
  return CATEGORIES.find(([id]) => id === value)?.[1] ?? value;
}

export function statusLabel(status: string): string {
  if (status === "USER_UPLOADED") return "User uploaded — not verified";
  if (status === "ISSUED") return "Issuer issued";
  if (status === "REVOKED") return "Revoked";
  return status.replaceAll("_", " ");
}
