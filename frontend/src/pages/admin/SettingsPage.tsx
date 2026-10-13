import { useEffect, useState, type FormEvent } from "react";
import { api } from "../../api/client";
import { Button, Field, PageHeader, TextInput } from "../../components/ui";
import { useRecords } from "../../state/records";

type ChainView = {
  rpcUrl: string;
  chainId: number;
  network: string;
  keyConfigured: boolean;
};

type Account = {
  id: string;
  name: string;
  email: string;
};

export function SettingsPage() {
  const { session } = useRecords();
  const issuer = session?.role === "INSTITUTION_ADMIN" || session?.role === "PLATFORM_ADMIN";
  const [chain, setChain] = useState<ChainView | null>(null);
  const [network, setNetwork] = useState<"local" | "sepolia">("local");
  const [rpcUrl, setRpcUrl] = useState("http://localhost:8545");
  const [privateKey, setPrivateKey] = useState("");
  const [accounts, setAccounts] = useState<Account[]>([]);
  const [fullName, setFullName] = useState("");
  const [email, setEmail] = useState("");
  const [password, setPassword] = useState("");
  const [currentPassword, setCurrentPassword] = useState("");
  const [newPassword, setNewPassword] = useState("");
  const [message, setMessage] = useState("");
  const [error, setError] = useState("");
  const [signatoryName, setSignatoryName] = useState("Registrar");
  const [signatoryTitle, setSignatoryTitle] = useState("Registrar");
  const [institutionName, setInstitutionName] = useState("");
  const [signingKeyId, setSigningKeyId] = useState("");
  const [signingConfigured, setSigningConfigured] = useState(false);
  const [signingPrivate, setSigningPrivate] = useState("");
  const [signingPublic, setSigningPublic] = useState("");
  const [createdPrivate, setCreatedPrivate] = useState("");

  useEffect(() => {
    if (!issuer) return;
    void Promise.all([
      api<ChainView>("/api/settings/chain"),
      api<Account[]>("/api/accounts"),
      api<{ name: string; signatoryName: string; signatoryTitle: string }>("/api/settings/institution"),
      api<{ configured: boolean; keyId: string }>("/api/settings/signing"),
    ])
      .then(([nextChain, nextAccounts, institution, signing]) => {
        setChain(nextChain);
        setNetwork(nextChain.chainId === 11155111 ? "sepolia" : "local");
        setRpcUrl(nextChain.rpcUrl);
        setAccounts(nextAccounts);
        setInstitutionName(institution.name);
        setSignatoryName(institution.signatoryName);
        setSignatoryTitle(institution.signatoryTitle);
        setSigningConfigured(signing.configured);
        setSigningKeyId(signing.keyId);
      })
      .catch((caught: unknown) => {
        setError(caught instanceof Error ? caught.message : "Settings could not be loaded.");
      });
  }, [issuer]);

  async function saveSigningKey(event: FormEvent) {
    event.preventDefault();
    setMessage("");
    setError("");
    try {
      const saved = await api<{ configured: boolean; keyId: string }>("/api/settings/signing", {
        method: "POST",
        body: JSON.stringify({ privateKey: signingPrivate, publicKey: signingPublic }),
      });
      setSigningConfigured(saved.configured);
      setSigningKeyId(saved.keyId);
      setSigningPrivate("");
      setSigningPublic("");
      setCreatedPrivate("");
      setMessage("The university signing key is saved. New credentials use it. The private key is not shown again.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The signing key could not be saved.");
    }
  }

  async function createSigningKey() {
    setMessage("");
    setError("");
    try {
      const created = await api<{ keyId: string; publicKey: string; privateKey: string }>("/api/settings/signing/generate", { method: "POST" });
      setSigningConfigured(true);
      setSigningKeyId(created.keyId);
      setCreatedPrivate(created.privateKey);
      setSigningPrivate("");
      setSigningPublic("");
      setMessage("Copy the private key now. CertiChain will not show it again. Credentials already signed keep their original public key.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "A signing key could not be created.");
    }
  }

  async function saveSignatory(event: FormEvent) {
    event.preventDefault();
    setMessage("");
    setError("");
    try {
      const saved = await api<{ name: string; signatoryName: string; signatoryTitle: string }>("/api/settings/institution", {
        method: "POST",
        body: JSON.stringify({ signatoryName, signatoryTitle }),
      });
      setSignatoryName(saved.signatoryName);
      setSignatoryTitle(saved.signatoryTitle);
      setMessage("The signatory will appear on certificates issued after this save.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The signatory could not be saved.");
    }
  }

  function useNetwork(next: "local" | "sepolia") {
    setNetwork(next);
    setRpcUrl(next === "sepolia" ? "https://ethereum-sepolia-rpc.publicnode.com" : "http://localhost:8545");
    setPrivateKey("");
  }

  async function saveChain(event: FormEvent) {
    event.preventDefault();
    setError("");
    setMessage("");
    try {
      const saved = await api<ChainView>("/api/settings/chain", {
        method: "POST",
        body: JSON.stringify({
          rpcUrl,
          chainId: network === "sepolia" ? 11155111 : 31337,
          privateKey,
        }),
      });
      setChain(saved);
      setPrivateKey("");
      setMessage(saved.network === "sepolia" ? "Sepolia is now the anchor network." : "The local chain is now the anchor network.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The chain settings could not be saved.");
    }
  }

  async function savePassword(event: FormEvent) {
    event.preventDefault();
    setError("");
    setMessage("");
    try {
      await api<void>("/api/auth/password", {
        method: "POST",
        body: JSON.stringify({ currentPassword, newPassword }),
      });
      setCurrentPassword("");
      setNewPassword("");
      setMessage("Password updated.");
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The password could not be changed.");
    }
  }

  async function addAccount(event: FormEvent) {
    event.preventDefault();
    setError("");
    setMessage("");
    try {
      const created = await api<Account>("/api/accounts", {
        method: "POST",
        body: JSON.stringify({ fullName, email, password }),
      });
      setAccounts((current) => [...current, created].sort((left, right) => left.name.localeCompare(right.name)));
      setFullName("");
      setEmail("");
      setPassword("");
      setMessage(`${created.email} can now sign in.`);
    } catch (caught) {
      setError(caught instanceof Error ? caught.message : "The account could not be created.");
    }
  }

  return (
    <div>
      <PageHeader
        title="Settings"
        lede={issuer ? "Change the anchor network, your password, and who else can sign in for this institution." : "Password, notifications, and what a share link can and cannot do."}
      />
      {message ? <p className="mb-4 text-sm text-seal">{message}</p> : null}
      {error ? <p className="mb-4 text-sm text-[#8c2f2f]">{error}</p> : null}
      <div className="grid gap-6 xl:grid-cols-2">
        {issuer ? (
        <div className="space-y-6">
        <form onSubmit={saveSignatory} className="panel space-y-4 p-5">
          <h2 className="font-serif text-2xl">Institution</h2>
          <p className="text-sm leading-6 text-muted">{institutionName || "This institution"} issues degree, transcript, and bonafide PDFs under this signatory. The fingerprint still uses the institution name.</p>
          <Field label="Signatory name">
            <TextInput value={signatoryName} onChange={(event) => setSignatoryName(event.target.value)} required />
          </Field>
          <Field label="Office">
            <TextInput value={signatoryTitle} onChange={(event) => setSignatoryTitle(event.target.value)} required />
          </Field>
          <Button type="submit">Save signatory</Button>
        </form>
        <form onSubmit={saveSigningKey} className="panel space-y-4 p-5">
          <h2 className="font-serif text-2xl">Signing key</h2>
          <p className="text-sm leading-6 text-muted">
            {signingConfigured ? `New credentials are signed with key ${signingKeyId}. Each credential keeps only that public key.` : "No university key is set. New credentials are not signed until you add one."}
          </p>
          <Field label="Private key" hint="Base64 PKCS8. Leave blank after it is saved.">
            <TextInput type="password" autoComplete="off" value={signingPrivate} onChange={(event) => setSigningPrivate(event.target.value)} />
          </Field>
          <Field label="Public key" hint="Base64 X509.">
            <TextInput value={signingPublic} onChange={(event) => setSigningPublic(event.target.value)} />
          </Field>
          {createdPrivate ? <p className="break-all rounded-xl bg-[#eef4f8] p-3 font-mono text-xs leading-5">{createdPrivate}</p> : null}
          <div className="flex flex-wrap gap-3">
            <Button type="submit">Save signing key</Button>
            <Button type="button" variant="secondary" onClick={() => void createSigningKey()}>Create university key</Button>
          </div>
        </form>
        <form onSubmit={saveChain} className="panel space-y-4 p-5">
          <h2 className="font-serif text-2xl">Chain</h2>
          <p className="text-sm leading-6 text-muted">
            {chain
              ? `Currently ${chain.network === "sepolia" ? "Sepolia" : "the local chain"}${chain.keyConfigured ? "" : ", waiting for a wallet key"}.`
              : "Loading the current network."}
          </p>
          <div className="flex gap-2">
            <Button type="button" variant={network === "local" ? "primary" : "secondary"} onClick={() => useNetwork("local")}>
              Local chain
            </Button>
            <Button type="button" variant={network === "sepolia" ? "primary" : "secondary"} onClick={() => useNetwork("sepolia")}>
              Sepolia
            </Button>
          </div>
          <Field label="RPC URL">
            <TextInput value={rpcUrl} onChange={(event) => setRpcUrl(event.target.value)} required />
          </Field>
          <Field label="Wallet key" hint="Leave this blank to keep the key already stored. Sepolia rejects the public local development key.">
            <TextInput
              type="password"
              autoComplete="off"
              value={privateKey}
              onChange={(event) => setPrivateKey(event.target.value)}
              placeholder={network === "sepolia" ? "0x…" : "Not required for the local chain"}
            />
          </Field>
          <p className="text-xs leading-5 text-muted">
            A Sepolia key is stored in this server's database so the API can sign. It is not shown again after you save it.
          </p>
          <Button type="submit">Save chain</Button>
        </form>
        </div>
        ) : (
          <section className="panel space-y-3 p-5 text-sm leading-6 text-muted">
            <h2 className="font-serif text-2xl text-ink">Privacy</h2>
            <p>Deleting an upload removes that file from your wallet. It does not revoke a credential an institution issued.</p>
            <p>Revoking a share link stops future visits. It does not delete a copy someone already downloaded.</p>
            <p>Closing this account does not delete institutional certificate records. Those stay with the issuer.</p>
            <p>Notifications stay in this account. Email delivery is not configured.</p>
          </section>
        )}
        <div className="space-y-6">
          <form onSubmit={savePassword} className="panel space-y-4 p-5">
            <h2 className="font-serif text-2xl">Password</h2>
            <Field label="Current password">
              <TextInput type="password" value={currentPassword} onChange={(event) => setCurrentPassword(event.target.value)} required />
            </Field>
            <Field label="New password" hint="At least 8 characters.">
              <TextInput type="password" value={newPassword} onChange={(event) => setNewPassword(event.target.value)} required />
            </Field>
            <Button type="submit">Change password</Button>
          </form>
          {issuer ? (
          <form onSubmit={addAccount} className="panel space-y-4 p-5">
            <h2 className="font-serif text-2xl">Accounts</h2>
            <ul className="space-y-2 text-sm">
              {accounts.map((account) => (
                <li key={account.id} className="flex items-center justify-between gap-3 border-b border-line py-2 last:border-0">
                  <span className="font-medium">{account.name}</span>
                  <span className="text-muted">{account.email}</span>
                </li>
              ))}
            </ul>
            <Field label="Name">
              <TextInput value={fullName} onChange={(event) => setFullName(event.target.value)} required />
            </Field>
            <Field label="Email">
              <TextInput type="email" value={email} onChange={(event) => setEmail(event.target.value)} required />
            </Field>
            <Field label="Password">
              <TextInput type="password" value={password} onChange={(event) => setPassword(event.target.value)} required />
            </Field>
            <Button type="submit">Add account</Button>
          </form>
          ) : null}
        </div>
      </div>
    </div>
  );
}
