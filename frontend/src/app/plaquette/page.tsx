import Image from "next/image";
import { SiteLink } from "./SiteLink";

/**
 * Plaquette commerciale Leadrive, page publique accessible par URL directe.
 * Aucun lien depuis la navigation du site.
 * URL : /plaquette
 *
 * Design autonome (Atelier design system) optimisé pour :
 * - Consultation web (responsive)
 * - Impression / export PDF (Ctrl+P)
 */
export default function PlaquettePage() {
  return (
    <>
      <style>{plaquetteStyles}</style>
      <div className="plq">
        {/* ── PAGE 1 : COUVERTURE ── */}
        <div className="plq-page plq-cover">
          <div>
            <div className="plq-accent-line" />

            <div className="plq-cover-logo">
              <Image
                src="/leadrive_logo_dark.svg"
                alt="Leadrive"
                width={180}
                height={48}
                priority
              />
            </div>

            <div className="plq-eyebrow-mono">
              Assistant IA pour professionnels de l&apos;automobile
            </div>

            <h1 className="plq-cover-title">
              Exploitez le potentiel
              <br />
              <span className="plq-accent">de vos leads.</span>
            </h1>

            <p className="plq-cover-subtitle">
              L&apos;IA qui contacte vos prospects en moins de 5&nbsp;minutes
              par WhatsApp, qualifie leurs besoins et vous livre des
              rendez-vous prêts à closer.
            </p>
          </div>

          <div>
            <div className="plq-proof-rail">
              <div className="plq-proof-chip">
                <span className="plq-proof-dot" /> Prêt en 10 minutes
              </div>
              <div className="plq-proof-chip">
                <span className="plq-proof-dot" /> Sans engagement
              </div>
              <div className="plq-proof-chip">
                <span className="plq-proof-dot" /> 1er mois offert
              </div>
              <div className="plq-proof-chip">
                <span className="plq-proof-dot" /> RGPD, Hébergement Europe
              </div>
            </div>
            <div className="plq-cover-footer">
              <SiteLink className="plq-link-subtle" />
              <a href="mailto:contact@leadrive.fr" className="plq-link-subtle">contact@leadrive.fr</a>
              <span>Document commercial, 2026</span>
            </div>
          </div>
        </div>

        {/* ── PAGE 2 : CONSTAT + SOLUTION ── */}
        <div className="plq-page plq-inner">
          <PlqHeader />

          <div className="plq-eyebrow">
            <span className="plq-chapter">01 / 04</span> · Le constat
          </div>
          <h2 className="plq-h2">
            Chaque mois, des dizaines
            <br />
            de ventes s&apos;évaporent en silence.
          </h2>

          <div className="plq-stats-row">
            <div className="plq-stat-card">
              <div className="plq-stat-value">1/4</div>
              <div className="plq-stat-label">
                chance de conclure
                <br />
                si rappelé dans l&apos;heure
              </div>
              <p className="plq-stat-detail">
                Passé ce délai, votre lead est chez le concurrent.
                La durée de vie d&apos;un lead automobile est de 12&nbsp;heures.
              </p>
              <div className="plq-stat-source">Source : IMAWEB / Ubiflow</div>
            </div>
            <div className="plq-stat-card">
              <div className="plq-stat-value">65%</div>
              <div className="plq-stat-label">
                des Français ignorent
                <br />
                les numéros inconnus
              </div>
              <p className="plq-stat-detail">
                Chez les moins de 35 ans, ce chiffre monte à 82%.
                Votre vendeur appelle, le prospect ne décroche pas.
              </p>
              <div className="plq-stat-source">Source : ARCEP 2024</div>
            </div>
            <div className="plq-stat-card">
              <div className="plq-stat-value" style={{ fontSize: 22 }}>Raccourcis</div>
              <div className="plq-stat-label">
                pris à chaque appel,
                <br />
                même par les meilleurs
              </div>
              <p className="plq-stat-detail">
                Reprise, financement, projet de vie… Les étapes
                oubliées réduisent vos chances de conclure.
              </p>
              <div className="plq-stat-source">Constat terrain</div>
            </div>
          </div>

          <div className="plq-ember-divider" />

          <div className="plq-eyebrow">La solution Leadrive</div>
          <h2 className="plq-h2">
            Contactez vos prospects
            <br />
            en moins de 5 minutes.
          </h2>
          <p className="plq-body" style={{ marginBottom: 8 }}>
            Leadrive récupère automatiquement vos leads, les contacte par
            WhatsApp, qualifie leurs besoins et génère des rendez-vous
            directement dans le planning de vos vendeurs.
          </p>

          <div className="plq-steps-grid">
            {[
              ["1", "Import auto", "Vos leads arrivent automatiquement"],
              ["2", "IA WhatsApp", "L'IA contacte et qualifie en 5 min"],
              ["3", "RDV qualifié", "Fiche de préparation pour le vendeur"],
              ["4", "Closing", "Vendeur préparé, taux de closing ++"],
            ].map(([num, title, desc]) => (
              <div key={num} className="plq-step">
                <div className="plq-step-num">{num}</div>
                <div className="plq-step-title">{title}</div>
                <div className="plq-step-desc">{desc}</div>
              </div>
            ))}
          </div>

          <ul className="plq-feature-list">
            <li>Import automatique des leads depuis vos portails et DMS</li>
            <li>IA conversationnelle WhatsApp spécialisée automobile</li>
            <li>Pipeline Kanban + planning vendeurs</li>
            <li>Fiche de préparation IA + briefings avant chaque appel</li>
            <li>Statistiques et reporting en temps réel</li>
          </ul>

          <PlqFooter />
        </div>

        {/* ── PAGE 3 : TARIFS ── */}
        <div className="plq-page plq-inner">
          <PlqHeader />

          <div className="plq-eyebrow">
            <span className="plq-chapter">02 / 04</span> · Tarification
          </div>
          <h2 className="plq-h2">
            Simple, transparent,
            <br />
            sans engagement.
          </h2>
          <p className="plq-body">
            Une seule offre, tout inclus. Pas de plans, pas de features
            cachées. Vous ne payez que pour les résultats.
          </p>

          <div className="plq-pricing-card">
            <div className="plq-pricing-badge">Offre unique</div>
            <div className="plq-pricing-sub">par point de vente / mois</div>
            <div className="plq-pricing-amount">
              99<span className="plq-currency">€</span>
            </div>
            <div className="plq-pricing-per">HT / mois / point de vente</div>

            <div className="plq-pricing-includes">
              <div className="plq-pricing-item">
                <div className="plq-pricing-item-value">15</div>
                <div className="plq-pricing-item-label">
                  RDV confirmés inclus / mois
                </div>
              </div>
              <div className="plq-pricing-item plq-pricing-item--ember">
                <div className="plq-pricing-item-value">8€</div>
                <div className="plq-pricing-item-label">
                  par RDV supplémentaire
                </div>
              </div>
              <div className="plq-pricing-item">
                <div className="plq-pricing-item-value">Illimité</div>
                <div className="plq-pricing-item-label">
                  Nombre de vendeurs
                </div>
              </div>
              <div className="plq-pricing-item">
                <div className="plq-pricing-item-value">Toutes</div>
                <div className="plq-pricing-item-label">
                  Fonctionnalités incluses
                </div>
              </div>
            </div>

            <div className="plq-guarantees">
              <div className="plq-guarantee">
                <span className="plq-guarantee-check">✓</span> 1er mois offert
              </div>
              <div className="plq-guarantee">
                <span className="plq-guarantee-check">✓</span> Sans engagement
              </div>
              <div className="plq-guarantee">
                <span className="plq-guarantee-check">✓</span> Résiliable à tout
                moment
              </div>
            </div>
          </div>

          <div className="plq-eyebrow" style={{ marginTop: 24 }}>
            Tarif multi-sites
          </div>
          <h3 className="plq-h3">Plus de sites, moins cher.</h3>
          <p className="plq-body">
            Vous gérez plusieurs points de vente ? Le tarif baisse
            automatiquement.
          </p>

          <table className="plq-degr-table">
            <thead>
              <tr>
                <th>Points de vente</th>
                <th>Prix / site / mois</th>
                <th>Réduction</th>
                <th>Exemple</th>
              </tr>
            </thead>
            <tbody>
              <tr>
                <td>1 – 2 sites</td>
                <td>99€</td>
                <td>—</td>
                <td>198€ (2 sites)</td>
              </tr>
              <tr>
                <td>3 – 5 sites</td>
                <td className="plq-highlight">89€</td>
                <td className="plq-highlight">-10%</td>
                <td>267€ (3 sites)</td>
              </tr>
              <tr>
                <td>6 – 9 sites</td>
                <td className="plq-highlight">84€</td>
                <td className="plq-highlight">-15%</td>
                <td>504€ (6 sites)</td>
              </tr>
              <tr>
                <td>10+ sites</td>
                <td className="plq-highlight">79€</td>
                <td className="plq-highlight">-20%</td>
                <td>790€ (10 sites)</td>
              </tr>
            </tbody>
          </table>

          <p className="plq-footnote">
            Le prix du RDV supplémentaire (8€) reste identique quel que soit le
            nombre de sites. Les 15 RDV sont comptabilisés par site.
          </p>

          <PlqFooter />
        </div>

        {/* ── PAGE 4 : ROI + AVANTAGES ── */}
        <div className="plq-page plq-inner">
          <PlqHeader />

          <div className="plq-eyebrow">
            <span className="plq-chapter">03 / 04</span> · Retour sur
            investissement
          </div>
          <h2 className="plq-h2">
            Une vente suffit pour
            <br />
            rentabiliser le mois.
          </h2>

          <div className="plq-roi-comparison">
            <div className="plq-roi-box plq-roi-box--old">
              <div className="plq-roi-label">Lead qualifié classique</div>
              <div className="plq-roi-value">60 – 200€</div>
              <div className="plq-roi-detail">BDC, call center, portail</div>
            </div>
            <div className="plq-roi-arrow">→</div>
            <div className="plq-roi-box plq-roi-box--new">
              <div className="plq-roi-label">Avec Leadrive</div>
              <div className="plq-roi-value">4 – 7€</div>
              <div className="plq-roi-detail">par RDV qualifié confirmé</div>
            </div>
          </div>

          <h3 className="plq-h3">Exemple : 4 vendeurs, 25 RDV / mois</h3>
          <table className="plq-roi-example">
            <tbody>
              <tr>
                <td>Abonnement Leadrive</td>
                <td>99€ / mois</td>
              </tr>
              <tr>
                <td>10 RDV supplémentaires × 8€</td>
                <td>+ 80€</td>
              </tr>
              <tr>
                <td>Facture mensuelle totale</td>
                <td>179€</td>
              </tr>
              <tr>
                <td>Taux de transformation RDV → vente</td>
                <td>~8% (standard marché)</td>
              </tr>
              <tr>
                <td>Ventes générées</td>
                <td>2 par mois</td>
              </tr>
              <tr>
                <td>Marge moyenne par vente (VN + VO)</td>
                <td>1 500 – 2 500€</td>
              </tr>
              <tr className="plq-result">
                <td>ROI mensuel</td>
                <td>17x – 28x</td>
              </tr>
            </tbody>
          </table>

          <div className="plq-ember-divider" />

          <div className="plq-eyebrow">
            <span className="plq-chapter">04 / 04</span> · Pourquoi Leadrive
          </div>
          <h2 className="plq-h2">
            Conçu pour les pros
            <br />
            de l&apos;automobile.
          </h2>

          <div className="plq-advantages-grid">
            {[
              [
                "Contact en moins de 5 min",
                "L'IA contacte chaque lead par WhatsApp, le canal préféré de vos clients.",
              ],
              [
                "Zéro friction technique",
                "Vos leads remontent automatiquement. Aucune intégration lourde. Opérationnel en 10 min.",
              ],
              [
                "IA spécialisée automobile",
                "Pas un chatbot générique. Notre IA connaît les objections de vos prospects.",
              ],
              [
                "Données sécurisées",
                "Hébergement Europe, chiffrement, isolation multi-tenant, conformité RGPD.",
              ],
              [
                "Vendeurs mieux préparés",
                "Fiche détaillée avant chaque appel : véhicule, budget, reprise, timing.",
              ],
              [
                "Support réactif",
                "Équipe française, disponible pour la mise en place et l'optimisation.",
              ],
            ].map(([title, desc]) => (
              <div key={title} className="plq-advantage">
                <div className="plq-advantage-dot" />
                <div>
                  <div className="plq-advantage-title">{title}</div>
                  <div className="plq-advantage-desc">{desc}</div>
                </div>
              </div>
            ))}
          </div>

          <PlqFooter />
        </div>

        {/* ── PAGE 5 : CTA ── */}
        <div className="plq-page plq-cta">
          <div className="plq-cta-eyebrow">Prêt à démarrer ?</div>
          <div className="plq-cta-title">
            Demandez votre démo personnalisée.
          </div>
          <div className="plq-cta-subtitle">
            Découvrez Leadrive en action sur vos propres leads. 1er mois
            offert, sans engagement, opérationnel en 10 minutes.
          </div>

          <a href="/#demo" className="plq-cta-button plq-no-print">
            Demander une démo →
          </a>

          <div className="plq-cta-contact">
            <SiteLink className="plq-cta-link" />
            <a href="mailto:contact@leadrive.fr" className="plq-cta-link">contact@leadrive.fr</a>
          </div>

          <div className="plq-cta-proof">
            Hébergement Europe · Conformité RGPD · Chiffrement des données ·
            Support français
          </div>
        </div>
      </div>
    </>
  );
}

/* ── Composants réutilisés ── */

function PlqHeader() {
  return (
    <div className="plq-inner-header">
      <div className="plq-inner-logo">
        <Image
          src="/leadrive_logo_light.svg"
          alt="Leadrive"
          width={120}
          height={32}
        />
      </div>
      <div className="plq-inner-dot" />
    </div>
  );
}

function PlqFooter() {
  return (
    <div className="plq-inner-footer">
      <div className="plq-inner-footer-line" />
      <SiteLink className="plq-inner-footer-text" />
    </div>
  );
}

/* ── Styles scopés ── */

const plaquetteStyles = `
/* Scope everything under .plq to avoid conflicts with global styles */
.plq {
  font-family: var(--font-sans), 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
  color: #1A1B23;
  background: #F4EFE5;
  -webkit-font-smoothing: antialiased;
  padding: 0;
}

.plq *, .plq *::before, .plq *::after {
  box-sizing: border-box;
}

/* Print */
@media print {
  /* Forcer les couleurs et backgrounds à l'impression */
  .plq, .plq *, .plq *::before, .plq *::after {
    -webkit-print-color-adjust: exact !important;
    print-color-adjust: exact !important;
    color-adjust: exact !important;
  }
  .plq-page { break-after: page; box-shadow: none !important; margin: 0 !important; border-radius: 0 !important; }
  .plq-no-print { display: none !important; }
  @page { margin: 0; size: A4; }
}

/* Pages */
.plq-page {
  width: 210mm;
  min-height: 297mm;
  margin: 0 auto 2rem;
  overflow: hidden;
  position: relative;
}

@media screen {
  .plq-page {
    box-shadow: 0 4px 24px rgba(26,27,35,0.08), 0 1px 4px rgba(26,27,35,0.04);
    border-radius: 4px;
  }
}

/* ── COVER ── */
.plq-cover {
  background: #13141A;
  display: flex;
  flex-direction: column;
  justify-content: space-between;
  padding: 48px;
  position: relative;
}

.plq-cover::after {
  content: '';
  position: absolute;
  right: -80px;
  bottom: -80px;
  width: 400px;
  height: 400px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(217,96,58,0.15) 0%, transparent 70%);
  pointer-events: none;
}

.plq-accent-line {
  width: 60px;
  height: 3px;
  background: #D9603A;
  margin-bottom: 48px;
}

.plq-cover-logo { margin-bottom: 48px; }
.plq-cover-logo img { height: 42px; width: auto; }

.plq-eyebrow-mono {
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 11px;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #D9603A;
  margin-bottom: 20px;
}

.plq-cover-title {
  font-family: var(--font-display), 'Bricolage Grotesque', sans-serif;
  font-size: 52px;
  font-weight: 700;
  line-height: 1.02;
  letter-spacing: -0.035em;
  color: white;
  margin-bottom: 24px;
}

.plq-accent { color: #E07B5A; }

.plq-cover-subtitle {
  font-size: 16px;
  line-height: 1.65;
  color: rgba(255,255,255,0.55);
  max-width: 520px;
}

.plq-proof-rail {
  display: flex;
  gap: 24px;
  padding-top: 24px;
  border-top: 1px solid rgba(255,255,255,0.08);
  margin-top: auto;
}

.plq-proof-chip {
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 11px;
  color: rgba(255,255,255,0.4);
}

.plq-proof-dot {
  display: inline-block;
  width: 5px;
  height: 5px;
  border-radius: 50%;
  background: #D9603A;
  flex-shrink: 0;
}

.plq-cover-footer {
  display: flex;
  justify-content: space-between;
  font-size: 11px;
  color: rgba(255,255,255,0.3);
  margin-top: 32px;
}

.plq-link-subtle { color: rgba(255,255,255,0.3); text-decoration: none; transition: color 0.2s; }
.plq-link-subtle:hover { color: #E07B5A; }

/* ── INNER PAGES ── */
.plq-inner {
  background: #F4EFE5;
  padding: 16px 48px 48px;
  position: relative;
}

.plq-inner::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 56px;
  background: #FBF8F1;
  border-bottom: 1px solid #D8CFB8;
}

.plq-inner-header {
  position: relative;
  z-index: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 32px;
  height: 24px;
}

.plq-inner-logo img { height: 24px; width: auto; display: block; }
.plq-inner-dot { width: 6px; height: 6px; border-radius: 50%; background: #D9603A; }

.plq-inner-footer {
  position: absolute;
  bottom: 24px;
  left: 48px;
  right: 48px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.plq-inner-footer-line { width: 24px; height: 2px; background: #D9603A; }
.plq-inner-footer-text { font-size: 10px; color: #8B8676; text-decoration: none; transition: color 0.2s; }
.plq-inner-footer-text:hover { color: #D9603A; }

/* ── TYPOGRAPHY ── */
.plq-eyebrow {
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.18em;
  text-transform: uppercase;
  color: #D9603A;
  margin-bottom: 12px;
}

.plq-chapter { color: #8B8676; }

.plq-h2 {
  font-family: var(--font-display), 'Bricolage Grotesque', sans-serif;
  font-size: 36px;
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.03em;
  color: #1A1B23;
  margin-bottom: 16px;
}

.plq-h3 {
  font-family: var(--font-display), 'Bricolage Grotesque', sans-serif;
  font-size: 18px;
  font-weight: 600;
  color: #1A1B23;
  margin-bottom: 8px;
}

.plq-body {
  font-size: 14px;
  line-height: 1.65;
  color: #4B4D58;
}

/* ── STATS ── */
.plq-stats-row {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
  margin: 24px 0;
}

.plq-stat-card {
  background: #FBF8F1;
  border-radius: 8px;
  padding: 20px 16px;
  text-align: center;
}

.plq-stat-value {
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 32px;
  font-weight: 600;
  color: #D9603A;
  letter-spacing: -0.02em;
}

.plq-stat-label {
  font-size: 11px;
  color: #8B8676;
  margin-top: 4px;
  line-height: 1.4;
  font-weight: 600;
}

.plq-stat-detail {
  font-size: 10px;
  color: #8B8676;
  line-height: 1.5;
  margin-top: 8px;
}

.plq-stat-source {
  font-size: 9px;
  color: #D9603A;
  margin-top: 8px;
  font-style: italic;
}

.plq-ember-divider {
  width: 48px;
  height: 3px;
  background: #D9603A;
  margin: 32px 0;
}

/* ── STEPS ── */
.plq-steps-grid {
  display: grid;
  grid-template-columns: repeat(4, 1fr);
  gap: 1px;
  background: #D8CFB8;
  border-radius: 8px;
  overflow: hidden;
  margin: 20px 0;
}

.plq-step {
  background: #FBF8F1;
  padding: 20px 14px;
  text-align: center;
}

.plq-step-num {
  font-family: var(--font-display), 'Bricolage Grotesque', sans-serif;
  font-size: 28px;
  font-weight: 700;
  color: #D9603A;
  margin-bottom: 6px;
}

.plq-step-title {
  font-size: 12px;
  font-weight: 600;
  color: #1A1B23;
  margin-bottom: 4px;
}

.plq-step-desc {
  font-size: 10px;
  color: #8B8676;
  line-height: 1.4;
}

/* ── FEATURE LIST ── */
.plq-feature-list {
  list-style: none;
  margin: 16px 0;
  padding: 0;
}

.plq-feature-list li {
  font-size: 13px;
  color: #4B4D58;
  padding: 5px 0;
  display: flex;
  align-items: flex-start;
  gap: 10px;
}

.plq-feature-list li::before {
  content: '';
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: #D9603A;
  flex-shrink: 0;
  margin-top: 5px;
}

/* ── PRICING ── */
.plq-pricing-card {
  background: #FBF8F1;
  border-radius: 12px;
  border: 1px solid #D8CFB8;
  padding: 32px;
  text-align: center;
  margin: 24px 0;
  position: relative;
}

.plq-pricing-badge {
  position: absolute;
  top: -12px;
  left: 50%;
  transform: translateX(-50%);
  background: #D9603A;
  color: white;
  font-size: 10px;
  font-weight: 600;
  padding: 4px 16px;
  border-radius: 20px;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.plq-pricing-sub {
  margin-top: 8px;
  font-size: 13px;
  color: #8B8676;
}

.plq-pricing-amount {
  font-family: var(--font-display), 'Bricolage Grotesque', sans-serif;
  font-size: 56px;
  font-weight: 700;
  color: #1A1B23;
  line-height: 1;
  margin: 12px 0 4px;
}

.plq-currency {
  font-size: 24px;
  color: #8B8676;
  font-weight: 600;
}

.plq-pricing-per {
  font-size: 13px;
  color: #8B8676;
  margin-bottom: 20px;
}

.plq-pricing-includes {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
  margin: 20px 0;
  text-align: left;
}

.plq-pricing-item {
  background: #F4EFE5;
  border-radius: 8px;
  padding: 14px 16px;
}

.plq-pricing-item--ember {
  background: #FBEEE6;
}

.plq-pricing-item-value {
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 18px;
  font-weight: 600;
  color: #1A1B23;
}

.plq-pricing-item--ember .plq-pricing-item-value {
  color: #A8401F;
}

.plq-pricing-item-label {
  font-size: 11px;
  color: #8B8676;
  margin-top: 2px;
}

.plq-guarantees {
  display: flex;
  justify-content: center;
  gap: 24px;
  margin: 20px 0;
  flex-wrap: wrap;
}

.plq-guarantee {
  display: flex;
  align-items: center;
  gap: 6px;
  font-size: 12px;
  color: #4B4D58;
}

.plq-guarantee-check {
  color: #0E9F6E;
  font-weight: 700;
  font-size: 14px;
}

/* ── DEGRESSIVE TABLE ── */
.plq-degr-table {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border-radius: 8px;
  overflow: hidden;
  margin: 16px 0;
  font-size: 13px;
}

.plq-degr-table thead th {
  background: #13141A;
  color: white;
  font-weight: 600;
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.05em;
  padding: 12px 16px;
  text-align: left;
}

.plq-degr-table tbody td {
  padding: 12px 16px;
  border-bottom: 1px solid #D8CFB8;
  background: #FBF8F1;
}

.plq-degr-table tbody tr:nth-child(even) td {
  background: #F4EFE5;
}

.plq-highlight {
  color: #D9603A;
  font-weight: 600;
}

.plq-footnote {
  font-size: 11px;
  color: #8B8676;
  margin-top: 8px;
}

/* ── ROI ── */
.plq-roi-comparison {
  display: grid;
  grid-template-columns: 1fr auto 1fr;
  gap: 16px;
  align-items: center;
  margin: 24px 0;
}

.plq-roi-box {
  border-radius: 10px;
  padding: 24px;
  text-align: center;
}

.plq-roi-box--old {
  background: #FBF8F1;
  border: 1px solid #D8CFB8;
}

.plq-roi-box--new {
  background: #FBEEE6;
  border: 1px solid rgba(217,96,58,0.2);
}

.plq-roi-label {
  font-size: 11px;
  text-transform: uppercase;
  letter-spacing: 0.1em;
  font-weight: 600;
  margin-bottom: 8px;
}

.plq-roi-box--old .plq-roi-label { color: #8B8676; }
.plq-roi-box--new .plq-roi-label { color: #D9603A; }

.plq-roi-value {
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 28px;
  font-weight: 600;
}

.plq-roi-box--old .plq-roi-value { color: #8B8676; text-decoration: line-through; }
.plq-roi-box--new .plq-roi-value { color: #A8401F; }

.plq-roi-detail {
  font-size: 11px;
  color: #8B8676;
  margin-top: 4px;
}

.plq-roi-arrow {
  font-size: 24px;
  color: #D9603A;
}

/* ── ROI EXAMPLE TABLE ── */
.plq-roi-example {
  width: 100%;
  border-collapse: separate;
  border-spacing: 0;
  border-radius: 8px;
  overflow: hidden;
  margin: 16px 0;
  font-size: 13px;
}

.plq-roi-example td {
  padding: 10px 16px;
  border-bottom: 1px solid #D8CFB8;
  background: #FBF8F1;
}

.plq-roi-example td:first-child { font-weight: 500; }
.plq-roi-example td:last-child { text-align: right; color: #4B4D58; }

.plq-roi-example tr.plq-result td {
  background: #FBEEE6;
  font-weight: 600;
}

.plq-roi-example tr.plq-result td:last-child {
  color: #A8401F;
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 15px;
}

/* ── ADVANTAGES ── */
.plq-advantages-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 20px;
  margin: 20px 0;
}

.plq-advantage {
  display: flex;
  gap: 12px;
}

.plq-advantage-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #D9603A;
  flex-shrink: 0;
  margin-top: 5px;
}

.plq-advantage-title {
  font-size: 13px;
  font-weight: 600;
  color: #1A1B23;
  margin-bottom: 3px;
}

.plq-advantage-desc {
  font-size: 11px;
  line-height: 1.5;
  color: #8B8676;
}

/* ── CTA PAGE ── */
.plq-cta {
  background: #13141A;
  display: flex;
  flex-direction: column;
  justify-content: center;
  align-items: center;
  text-align: center;
  padding: 48px;
  position: relative;
}

.plq-cta::after {
  content: '';
  position: absolute;
  right: -60px;
  bottom: -60px;
  width: 350px;
  height: 350px;
  border-radius: 50%;
  background: radial-gradient(circle, rgba(217,96,58,0.12) 0%, transparent 70%);
  pointer-events: none;
}

.plq-cta-eyebrow {
  font-family: var(--font-mono), 'JetBrains Mono', monospace;
  font-size: 10px;
  font-weight: 600;
  letter-spacing: 0.22em;
  text-transform: uppercase;
  color: #D9603A;
  margin-bottom: 20px;
}

.plq-cta-title {
  font-family: var(--font-display), 'Bricolage Grotesque', sans-serif;
  font-size: 40px;
  font-weight: 700;
  line-height: 1.08;
  letter-spacing: -0.03em;
  color: white;
  margin-bottom: 16px;
  max-width: 500px;
}

.plq-cta-subtitle {
  font-size: 15px;
  line-height: 1.6;
  color: rgba(255,255,255,0.5);
  max-width: 440px;
  margin-bottom: 36px;
}

.plq-cta-button {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  background: linear-gradient(135deg, #A8401F 0%, #D9603A 55%, #E07B5A 100%);
  color: white;
  font-size: 15px;
  font-weight: 600;
  padding: 16px 36px;
  border-radius: 12px;
  text-decoration: none;
  margin-bottom: 32px;
  border: 1px solid rgba(255,255,255,0.1);
  transition: opacity 0.2s;
}

.plq-cta-button:hover { opacity: 0.9; }

.plq-cta-contact {
  display: flex;
  gap: 40px;
  font-size: 14px;
  color: rgba(255,255,255,0.5);
  margin-bottom: 40px;
}

.plq-cta-link { color: rgba(255,255,255,0.5); text-decoration: none; transition: color 0.2s; }
.plq-cta-link:hover { color: #E07B5A; }

.plq-cta-proof {
  font-size: 10px;
  color: rgba(255,255,255,0.25);
  letter-spacing: 0.03em;
}

/* ── RESPONSIVE ── */
@media (max-width: 800px) {
  .plq-page {
    width: 100%;
    min-height: auto;
    margin: 0 auto 1rem;
    border-radius: 0;
  }
  .plq-cover, .plq-cta { padding: 32px 24px; }
  /* Garde padding-top a 16px pour que le logo reste vertical-centre dans la
     bande beige du header (.plq-inner::before fait 56px = 16 + 24 + 16). */
  .plq-inner { padding: 16px 24px 32px; }
  .plq-cover-title { font-size: 36px; }
  .plq-h2 { font-size: 28px; }
  .plq-steps-grid { grid-template-columns: repeat(2, 1fr); }
  .plq-advantages-grid { grid-template-columns: 1fr; }
  .plq-pricing-includes { grid-template-columns: 1fr; }
  .plq-roi-comparison { grid-template-columns: 1fr; gap: 12px; }
  .plq-roi-arrow { transform: rotate(90deg); text-align: center; }
  .plq-proof-rail { flex-wrap: wrap; gap: 12px; }
  .plq-stats-row { grid-template-columns: 1fr; }
  .plq-cta-title { font-size: 28px; }
  .plq-degr-table { font-size: 11px; }
  .plq-degr-table thead th, .plq-degr-table tbody td { padding: 8px 10px; }
}
`;
