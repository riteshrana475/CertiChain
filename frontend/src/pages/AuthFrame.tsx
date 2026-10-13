import type { ReactNode } from "react";
import { Link } from "react-router-dom";
import { LogoMark } from "../components/ui";

export function AuthFrame({ children }: { children: ReactNode }) {
  return (
    <div className="grid min-h-screen lg:grid-cols-[1.15fr_0.85fr]">
      <section className="relative flex flex-col justify-between overflow-hidden bg-navy px-8 py-10 text-paper sm:px-14">
        <div className="pointer-events-none absolute -top-16 -right-16 h-64 w-64 rounded-full border border-brass/40" />
        <div className="pointer-events-none absolute -top-4 -right-4 h-40 w-40 rounded-full border border-white/10" />
        <div className="pointer-events-none absolute bottom-16 -left-20 h-56 w-56 rounded-full bg-seal/20 blur-3xl" />
        <div className="relative">
          <div className="flex items-center gap-4">
            <LogoMark className="h-24 w-24 sm:h-28 sm:w-28" />
            <p className="font-serif text-3xl leading-none">CertiChain</p>
          </div>
          <h1 className="mt-10 max-w-lg font-serif text-5xl leading-[1.05] sm:text-6xl">
            Issue a certificate once.
            <span className="mt-2 block font-serif text-[0.92em] text-brass italic">Verify it anywhere.</span>
          </h1>
          <ul className="mt-8 max-w-md space-y-3 text-sm leading-6 text-paper/80">
            <li>An institution issues the degree, transcript, or bonafide.</li>
            <li>A student wallet shows only credentials sent to that email.</li>
            <li>A verifier checks an ID or a share link, without opening the wallet.</li>
          </ul>
        </div>
        <p className="relative mt-12 text-xs tracking-[0.16em] text-paper/45 uppercase">
          <Link to="/verify" className="hover:text-paper">Check a credential without signing in</Link>
        </p>
      </section>
      <section className="flex items-center px-6 py-12 sm:px-12">{children}</section>
    </div>
  );
}
