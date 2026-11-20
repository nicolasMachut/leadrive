import type { Metadata } from "next";

export const metadata: Metadata = {
  title: "Politique de confidentialité",
  description: "Politique de confidentialité et traitement des données personnelles de Leadrive. Conformité RGPD, hébergement en Europe, DPA.",
  alternates: { canonical: "/politique-confidentialite" },
};

export default function PolitiqueConfidentialitePage() {
  return (
    <div className="min-h-screen bg-[var(--background)] px-6 pt-32 pb-24">
      <div className="mx-auto max-w-3xl">
        <h1 className="mb-8 text-3xl font-bold text-[var(--foreground)]">Politique de confidentialité</h1>
        <p className="mb-8 text-sm text-[var(--color-lr-text-tertiary)]">Dernière mise à jour : 29 avril 2026</p>
        <div className="space-y-6 text-sm leading-relaxed text-[var(--color-lr-text-secondary)]">

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">1. Responsable du traitement</h2>
            <p>Leadrive, projet porté par Nicolas Machut. Email : contact@leadrive.fr</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">2. Données collectées</h2>
            <p className="font-medium text-[var(--foreground)] mt-3">Visiteurs du site (leadrive.fr) :</p>
            <p>Via le formulaire de demande de démo : nom, nom de la concession, email professionnel, téléphone, volume de leads estimé.</p>
            <p className="font-medium text-[var(--foreground)] mt-3">Utilisateurs de la plateforme (app.leadrive.fr) :</p>
            <p>Données de connexion (email, nom, rôle), leads importés (nom, prénom, téléphone, email, véhicule d&apos;intérêt, source du lead), conversations WhatsApp échangées avec les prospects, analyses générées par intelligence artificielle (signaux d&apos;achat, objections, résumé, scoring), statistiques d&apos;usage et de performance commerciale.</p>
            <p className="font-medium text-[var(--foreground)] mt-3">Prospects contactés par WhatsApp :</p>
            <p>Nom, prénom, numéro de téléphone, contenu des messages échangés, véhicule d&apos;intérêt, informations communiquées durant la conversation (budget, financement, véhicule de reprise, délai de projet).</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">3. Finalités du traitement</h2>
            <p>Les données des visiteurs sont collectées pour recontacter le prospect et lui présenter une démonstration du produit.</p>
            <p>Les données de la plateforme sont utilisées pour fournir le service : relance automatique des leads par WhatsApp, qualification par intelligence artificielle, génération de fiches de préparation et de briefings pour les conseillers commerciaux, suivi du pipeline de vente.</p>
            <p>Les données des prospects contactés sont traitées pour le compte de la concession cliente (responsable de traitement) dans le cadre de son activité commerciale.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">4. Base légale</h2>
            <p>Formulaire de démo : consentement de la personne (article 6.1.a du RGPD).</p>
            <p>Utilisation de la plateforme : exécution du contrat (article 6.1.b).</p>
            <p>Traitement des données prospects pour le compte des concessions : intérêt légitime de la concession cliente dans le cadre de sa relation commerciale avec ses prospects (article 6.1.f).</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">5. Traitement automatisé et intelligence artificielle</h2>
            <p>Leadrive utilise des modèles d&apos;intelligence artificielle pour analyser les conversations WhatsApp, qualifier les prospects (scoring), détecter les signaux d&apos;achat et les objections, et générer des fiches de préparation pour les conseillers commerciaux.</p>
            <p>Ces traitements automatisés ne produisent aucune décision juridique ou significative à l&apos;égard des prospects. Toute prise de décision commerciale (appel, proposition, vente) reste à la discrétion du conseiller humain.</p>
            <p>Conformément à l&apos;article 22 du RGPD, toute personne peut demander des informations sur la logique de ces traitements en écrivant à contact@leadrive.fr.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">6. Sous-traitants et transferts de données</h2>
            <p>Pour fournir le service, Leadrive fait appel aux sous-traitants suivants :</p>
            <div className="mt-2 overflow-x-auto">
              <table className="w-full text-sm border-collapse">
                <thead>
                  <tr className="border-b border-[var(--border)]">
                    <th className="py-2 pr-4 text-left font-semibold text-[var(--foreground)]">Sous-traitant</th>
                    <th className="py-2 pr-4 text-left font-semibold text-[var(--foreground)]">Fonction</th>
                    <th className="py-2 text-left font-semibold text-[var(--foreground)]">Localisation</th>
                  </tr>
                </thead>
                <tbody className="text-[var(--color-lr-text-secondary)]">
                  <tr className="border-b border-[var(--border)]">
                    <td className="py-2 pr-4">Railway</td>
                    <td className="py-2 pr-4">Hébergement serveurs et base de données</td>
                    <td className="py-2">Europe (serveurs EU)</td>
                  </tr>
                  <tr className="border-b border-[var(--border)]">
                    <td className="py-2 pr-4">Twilio</td>
                    <td className="py-2 pr-4">Envoi et réception des messages WhatsApp</td>
                    <td className="py-2">États-Unis (clauses contractuelles types)</td>
                  </tr>
                  <tr className="border-b border-[var(--border)]">
                    <td className="py-2 pr-4">Anthropic</td>
                    <td className="py-2 pr-4">Analyse des conversations par intelligence artificielle</td>
                    <td className="py-2">États-Unis (clauses contractuelles types)</td>
                  </tr>
                  <tr className="border-b border-[var(--border)]">
                    <td className="py-2 pr-4">Clerk</td>
                    <td className="py-2 pr-4">Authentification et gestion des comptes utilisateurs</td>
                    <td className="py-2">États-Unis (clauses contractuelles types)</td>
                  </tr>
                  <tr className="border-b border-[var(--border)]">
                    <td className="py-2 pr-4">Sentry</td>
                    <td className="py-2 pr-4">Surveillance des erreurs techniques (données anonymisées)</td>
                    <td className="py-2">États-Unis (clauses contractuelles types)</td>
                  </tr>
                </tbody>
              </table>
            </div>
            <p className="mt-3">Les transferts vers les États-Unis sont encadrés par des clauses contractuelles types (CCT) conformément à l&apos;article 46.2.c du RGPD.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">7. Durée de conservation</h2>
            <p>Données du formulaire de démo : 3 ans maximum à compter de la collecte.</p>
            <p>Données de la plateforme (leads, conversations, analyses) : pendant la durée du contrat avec la concession cliente, puis 1 an après la fin du contrat.</p>
            <p>Les conversations WhatsApp terminées sont purgées automatiquement après 18 mois.</p>
            <p>En cas de demande de suppression par un prospect (STOP ou droit à l&apos;effacement), les données sont supprimées sous 30 jours.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">8. Droit des personnes</h2>
            <p>Conformément au RGPD, vous disposez des droits suivants sur vos données personnelles : droit d&apos;accès, droit de rectification, droit à l&apos;effacement, droit à la portabilité, droit d&apos;opposition et droit à la limitation du traitement.</p>
            <p>Les prospects contactés par WhatsApp peuvent à tout moment envoyer STOP pour cesser de recevoir des messages. Cette demande est traitée immédiatement et de manière automatique.</p>
            <p>Pour exercer vos droits, écrivez à contact@leadrive.fr. Nous répondons sous 30 jours maximum.</p>
            <p>En cas de litige, vous pouvez introduire une réclamation auprès de la CNIL (Commission nationale de l&apos;informatique et des libertés) : www.cnil.fr.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">9. Cookies</h2>
            <p>La plateforme Leadrive utilise uniquement des cookies strictement nécessaires au fonctionnement du service (authentification, session). Aucun cookie publicitaire ou de tracking marketing n&apos;est utilisé. Ces cookies étant indispensables, ils ne nécessitent pas de consentement préalable.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">10. Sécurité</h2>
            <p>Les données sont hébergées sur des serveurs en Europe (Railway). Les connexions à la base de données sont chiffrées en transit (SSL/TLS). Les données sensibles dans les logs applicatifs sont anonymisées automatiquement. L&apos;accès à la plateforme est protégé par authentification avec contrôle des rôles (vendeur, manager, administrateur).</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">11. Relation sous-traitant / concession cliente</h2>
            <p>Dans le cadre de son activité, Leadrive agit en tant que sous-traitant au sens de l&apos;article 28 du RGPD pour le compte des concessions clientes (responsables de traitement). Un accord de traitement des données (DPA) est conclu avec chaque concession cliente, définissant les obligations respectives en matière de protection des données personnelles des prospects.</p>
          </section>

          <section>
            <h2 className="mb-2 text-lg font-semibold text-[var(--foreground)]">12. Contact</h2>
            <p>Pour toute question relative à la protection de vos données personnelles : contact@leadrive.fr</p>
          </section>

        </div>
      </div>
    </div>
  );
}
