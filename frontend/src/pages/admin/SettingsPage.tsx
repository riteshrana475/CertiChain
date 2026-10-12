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

  useEffect(() => {
    if (!issuer) return;
    void Promise.all([api<ChainView>("/api/settings/chain"), api<Account[]>("/api/accounts")])
      .then(([nextChain, nextAccounts]) => {
        setChain(nextChain);
        setNetwork(nextChain.chainId === 11155111 ? "sepolia" : "local");
        setRpcUrl(nextChain.rpcUrl);
        setAccounts(nextAccounts);
      })
      .catch((caught: unknown) => {
        setError(caught instanceof Error ? caught.message : "Settings could not be loaded.");
      });
  }, [issuer]);

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
