import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Mentions légales",
  description: "Mentions légales du site leadrive.fr, SaaS de performance commerciale pour les établissements automobiles (concessions, garages, revendeurs multimarques).",
  alternates: { canonical: "/mentions-legales" },
};

export default function MentionsLegalesPage() {
  return (
    <div className="min-h-screen bg-[var(--background)] px-6 pt-32 pb-24">
      <div className="mx-auto max-w-3xl">
        <h1 className="mb-8 text-3xl font-bold text-[var(--foreground)]">Mentions légales</h1>
        <div className="space-y-6 text-sm leading-relaxed text-[var(--color-lr-text-secondary)]">
          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">Éditeur du site</h2>
            <p>Leadrive, projet porté par Nicolas Machut, personne physique</p>
            <p>Responsable de la publication : Nicolas Machut</p>
            <p>Email : contact@leadrive.fr</p>
          </section>
          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">Hébergement</h2>
            <p>Railway Corporation</p>
            <p>548 Market St, PMB 68956, San Francisco, CA 94104-5401, USA</p>
            <p>Serveurs localisés en Europe</p>
          </section>
          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">Propriété intellectuelle</h2>
            <p>L&apos;ensemble du contenu du site (textes, images, code, marques) est la propriété exclusive de Leadrive, sauf mention contraire. Toute reproduction est interdite sans autorisation préalable.</p>
          </section>
        </div>
      </div>
    </div>
  );
}
