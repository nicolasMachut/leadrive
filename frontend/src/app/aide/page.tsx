"use client";

import AppLayout from "@/components/AppLayout";
import ExemplesIaSection from "@/components/aide/ExemplesIaSection";
import PageHeader from "@/components/common/PageHeader";
import Link from "next/link";
import { useEffect, useState } from "react";

interface Section {
  id: string;
  titre: string;
  contenu: React.ReactNode;
}

const SECTIONS: Section[] = [
  {
    id: "demarrage",
    titre: "Premiers pas",
    contenu: (
      <div className="space-y-3">
        <p>Leadrive automatise la qualification de vos leads automobiles via WhatsApp + IA.</p>
        <ol className="space-y-2 list-decimal list-inside">
          <li><strong>Configurez votre établissement</strong> : <Link href="/admin/config-ia" className="text-accent hover:underline">Mon établissement</Link>, vos coordonnées (horaires, adresse, téléphone) utilisées par l&apos;IA et affichées au prospect.</li>
          <li><strong>Activez WhatsApp Business</strong> : <Link href="/parametres/whatsapp/activation" className="text-accent hover:underline">Activation WhatsApp Business</Link>, connectez votre compte WhatsApp Business via Facebook ; les modèles de messages sont validés automatiquement (quelques minutes à quelques heures).</li>
          <li><strong>Invitez vos vendeurs</strong> : <Link href="/equipe" className="text-accent hover:underline">Mon équipe</Link>, chacun recevra un email d&apos;invitation.</li>
          <li><strong>Importez vos leads</strong> : <Link href="/import" className="text-accent hover:underline">Importer</Link>, uploadez un fichier (CSV, Excel), collez vos données, ou collez un email de lead. L&apos;IA extrait automatiquement toutes les informations.</li>
          <li><strong>Suivez l&apos;activité</strong> : <Link href="/dashboard" className="text-accent hover:underline">Tableau de bord</Link> (conversations a reprendre, RDV), <Link href="/inbox" className="text-accent hover:underline">Conversations</Link> et <Link href="/stats" className="text-accent hover:underline">Statistiques</Link> (KPIs et graphiques).</li>
        </ol>
        <div className="mt-4 rounded-lg border border-accent/30 bg-accent/5 p-3 text-sm">
          <strong className="font-mono text-[10px] uppercase tracking-[0.18em] text-[var(--color-accent)]">Astuce</strong>{" "}à tout moment, cliquez sur le bouton <strong>?</strong> en bas à droite pour discuter avec Nora. Posez vos questions sur l&apos;outil ou activez le mode <strong>Simuler un prospect</strong> pour tester l&apos;IA WhatsApp en conditions réelles.
        </div>
      </div>
    ),
  },
  {
    id: "vendeur",
    titre: "Pour les vendeurs",
    contenu: (
      <div className="space-y-3">
        <p>Sur la page <Link href="/leads" className="text-accent hover:underline">/leads</Link>, basculez entre vue liste et <strong>vue Kanban</strong> (icone en haut a droite). Le Kanban affiche 5 colonnes (Nouveau, Contacte, Conversation en cours, RDV pris, Terminé) avec glisser-déposer. Le tri des cartes est intelligent : les nouveaux leads récents en haut, les conversations dormantes (Contacté / En cours) en haut pour les relancer en priorité, et le RDV imminent en haut dans RDV pris. Le drag &amp; drop autorise toutes les transitions (y compris retour en arrière) pour pouvoir corriger une erreur de clic.</p>
        <p>Organisez vos tâches sur le <Link href="/planning" className="text-accent hover:underline">planning</Link> (vue journée ou calendrier). Suivez vos stats sur <Link href="/stats" className="text-accent hover:underline">/stats</Link>.</p>
        <p>Votre dashboard <Link href="/dashboard" className="text-accent hover:underline">/dashboard</Link> vous montre :</p>
        <ul className="space-y-1.5 list-disc list-inside">
          <li><strong>Conversations à reprendre</strong> : l&apos;IA a passé la main, vous devez intervenir</li>
          <li><strong>RDV</strong> : vos rendez-vous à venir et en retard, avec boutons d&apos;action (Effectué / Reporter / Abandonner)</li>
          <li><strong>Briefing du jour</strong> : recommandations IA pour votre journée</li>
        </ul>
        <p>Pour chaque lead, depuis sa fiche prospect :</p>
        <ul className="space-y-1.5 list-disc list-inside">
          <li><strong>En-tête</strong> : nom du lead avec chips téléphone et email cliquables</li>
          <li><strong>Carte unifiee</strong> : véhicule, détails (dépliable), RDV, infos commerciales, statut et vendeur</li>
          <li><strong>Onglet Fiche IA</strong> : briefing complet pré-appel (projet, découverte, analyse comportementale)</li>
          <li><strong>Onglet WhatsApp</strong> : lancer une conversation IA ou suivre celles en cours</li>
        </ul>
        <p className="text-sm text-[var(--color-lr-text-secondary)]">Le changement de statut est sauvegardé automatiquement. Le bouton « Archiver ce prospect » (encart Pilotage) archive le prospect après confirmation : il sort de vos listes actives mais reste en base. L&apos;action est réversible, un bandeau « Annuler » permet de le désarchiver juste après. Pour retrouver vos prospects archivés, utilisez la bascule « Actifs | Archives » en haut de la page Prospects : chaque ligne y propose une action « Désarchiver ». Vous pouvez aussi désarchiver depuis la fiche d&apos;un prospect archivé (bannière « Ce prospect est archivé »).</p>
      </div>
    ),
  },
  {
    id: "manager",
    titre: "Pour les managers",
    contenu: (
      <div className="space-y-3">
        <p>Votre dashboard <Link href="/dashboard" className="text-accent hover:underline">/dashboard</Link> affiche les mêmes 2 cartes que le vendeur :</p>
        <ul className="space-y-1.5 list-disc list-inside">
          <li><strong>Conversations à reprendre</strong> : conversations ou l&apos;IA a passé la main</li>
          <li><strong>RDV</strong> : rendez-vous à venir et en retard avec boutons d&apos;action</li>
          <li><strong>Briefing du jour</strong> : resume IA de la veille avec priorités, regenerable</li>
        </ul>
        <p>Pour les statistiques détaillées, rendez-vous sur la page <Link href="/stats" className="text-accent hover:underline">/stats</Link> : KPIs (Leads reçus, RDV obtenus, Taux de RDV, Leads traités), graphique pipeline, courbe d&apos;activité, et performance de l&apos;équipe.</p>
        <p>Vous pouvez configurer votre établissement sur <Link href="/admin/config-ia" className="text-accent hover:underline">Mon établissement</Link> (coordonnées + WhatsApp Business) et gérer votre équipe sur <Link href="/equipe" className="text-accent hover:underline">Mon équipe</Link>.</p>
        <div className="mt-4 rounded-lg border border-accent/30 bg-accent/5 p-4">
          <p className="font-semibold text-[var(--foreground)]">Vous êtes manager dans un petit établissement ?</p>
          <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
            Le manager a <strong>tous les droits du vendeur</strong>. Vous pouvez envoyer des WhatsApp, générer des fiches, prendre la main sur les conversations, gérer vos propres prospects.
          </p>
        </div>
      </div>
    ),
  },
  {
    id: "statuts",
    titre: "Statuts du lead",
    contenu: (
      <div className="space-y-3">
        <p>Chaque lead avance dans un pipeline à 5 étapes :</p>
        <div className="space-y-2 rounded-lg border border-[var(--border)] bg-[var(--muted)]/30 p-4">
          <p><span className="inline-block w-2.5 h-2.5 rounded-full bg-accent mr-2 align-middle"></span><strong className="text-accent">Nouveau</strong>, vient d&apos;être importé, jamais contacté</p>
          <p><span className="inline-block w-2.5 h-2.5 rounded-full bg-warning mr-2 align-middle"></span><strong className="text-warning">Contacté</strong>, 1er WhatsApp envoyé, en attente de réponse</p>
          <p><span className="inline-block w-2.5 h-2.5 rounded-full bg-warning/70 mr-2 align-middle"></span><strong className="text-warning">Conversation en cours</strong>, prospect a répondu, dialogue actif (sans RDV)</p>
          <p><span className="inline-block w-2.5 h-2.5 rounded-full bg-success mr-2 align-middle"></span><strong className="text-success">RDV pris</strong>, prospect a proposé ou accepté un créneau</p>
          <p><span className="inline-block w-2.5 h-2.5 rounded-full bg-[var(--color-lr-text-tertiary)] mr-2 align-middle"></span><strong className="text-[var(--color-lr-text-secondary)]">Terminé</strong>, traitement du lead terminé (automatique sur refus/STOP, ou manuel)</p>
        </div>
        <p className="text-sm text-[var(--color-lr-text-secondary)]">Les transitions sont <strong>automatiques par l&apos;IA</strong> en fonction des réponses du prospect. Vous pouvez forcer un statut manuellement depuis la fiche prospect (sauvegarde automatique).</p>
      </div>
    ),
  },
  {
    id: "scoring",
    titre: "Score d'engagement",
    contenu: (
      <div className="space-y-3">
        <p>Il n&apos;y a qu&apos;un seul score dans Leadrive : le <strong>score d&apos;engagement</strong>, calculé par l&apos;IA après chaque échange WhatsApp. Il n&apos;y a pas de scoring à l&apos;import CSV, le score apparaît uniquement quand une conversation démarre.</p>
        <p>Il mesure l&apos;engagement reel du prospect base sur les signaux de la conversation (intention RDV, budget mentionne, urgence, objections). Il evolue dynamiquement avec chaque message.</p>
        <p>Un lead sans conversation n&apos;a pas encore de score, la jauge affiche &quot;—&quot;.</p>
        <p>Échelle :</p>
        <ul className="space-y-1 text-sm">
          <li><span className="text-success">●</span> <strong>70-100</strong> : Chaud, priorité haute</li>
          <li><span className="text-accent">●</span> <strong>40-69</strong> : Tiède, à relancer</li>
          <li><span className="text-[var(--color-lr-text-secondary)]">●</span> <strong>0-39</strong> : Froid, à qualifier</li>
        </ul>
        <p className="text-sm italic text-[var(--color-lr-text-tertiary)]">Différent du <strong>statut</strong> qui représente l&apos;étape dans le funnel commercial.</p>
      </div>
    ),
  },
  {
    id: "ia",
    titre: "Comment marche l'IA",
    contenu: (
      <div className="space-y-3">
        <p>L&apos;IA intervient à 3 moments :</p>
        <div className="space-y-3">
          <div className="rounded-lg border border-[var(--border)] p-4">
            <p className="font-semibold mb-1">1. Conversations WhatsApp automatiques</p>
            <p className="text-sm text-[var(--color-lr-text-secondary)]">L&apos;IA répond aux prospects en respectant votre ton commercial et la trame de découverte (projet, modele, reprise, budget, financement).</p>
          </div>
          <div className="rounded-lg border border-[var(--border)] p-4">
            <p className="font-semibold mb-1">2. Analyse de chaque message</p>
            <p className="text-sm text-[var(--color-lr-text-secondary)]">À chaque réponse du prospect, l&apos;IA extrait : intention (RDV / STOP / refus / autre), ton du prospect (enthousiaste, neutre, impatient, mécontent, hésitant), signaux d&apos;achat, objections, données de découverte, un brief vendeur (résumé pour préparer l&apos;appel) et les questions en attente du prospect. Visible dans le panneau latéral droit de la <Link href="/inbox" className="text-accent hover:underline">page Conversations</Link>.</p>
          </div>
          <div className="rounded-lg border border-[var(--border)] p-4">
            <p className="font-semibold mb-1">3. Fiche de préparation</p>
            <p className="text-sm text-[var(--color-lr-text-secondary)]">A la demande sur la fiche prospect, l&apos;IA génère un brief lisible en 5 secondes : accroche d&apos;appel personnalisée (phrase d&apos;ouverture prête à l&apos;emploi), projet reformulé, points à découvrir, et stratégie concrète pour l&apos;appel. Utilise la conversation réelle si elle existe.</p>
          </div>
        </div>
        <p>Vous pouvez à tout moment <strong>reprendre la main</strong> sur une conversation depuis la page Conversations (bouton &quot;Reprendre la main&quot;). L&apos;IA s&apos;arrête et vous tapez vous-même les messages. Le bouton est désactivé si WhatsApp Business n&apos;est pas encore configuré, ou si la conversation est terminée (STOP, refus prospect, clôture manuelle).</p>
        <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">À noter : une fois la main reprise, vous ne pouvez envoyer un message libre que si le prospect a écrit dans les dernières 24h. Au-delà, l&apos;envoi est bloqué (c&apos;est une règle de WhatsApp) : il faut attendre que le prospect réponde pour pouvoir lui réécrire. Les relances automatiques de l&apos;assistant ne s&apos;appliquent qu&apos;aux conversations laissées en mode IA (pas à celles dont vous avez repris la main).</p>
        <p className="mt-2 text-sm text-[var(--color-lr-text-secondary)]">Si un message n&apos;a pas pu partir, sa bulle devient rouge avec la mention <strong>Échec de l&apos;envoi</strong>, la cause expliquée en clair et un bouton <strong>Réessayer</strong>. Le renvoi fonctionne aussi bien pour le premier message (message approuvé) que pour un message libre, sur la fiche du lead comme sur la page Conversations.</p>
        <div className="rounded-lg border border-[var(--border)] p-4 mt-2">
          <p className="font-semibold mb-1">4. Briefing quotidien</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Chaque matin, un briefing personnalisé est généré sur votre dashboard : résumé de la veille, prospects prioritaires, chiffre-clé de la semaine. Cache d&apos;un jour, régénérable manuellement via le bouton ⟳ en haut à droite du briefing.</p>
        </div>
        <div className="rounded-lg border border-[var(--border)] p-4 mt-2">
          <p className="font-semibold mb-1">5. Relances automatiques</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Si un prospect ne répond pas, l&apos;IA relance automatiquement à J+1 et J+3. Après 2 tentatives sans réponse, une tâche &quot;Relance&quot; est créée dans votre planning pour que vous décidiez de la suite (appel, etc.).</p>
        </div>
        <div className="rounded-lg border border-[var(--border)] p-4 mt-2">
          <p className="font-semibold mb-1">6. Rappels RDV automatiques</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Lorsqu&apos;un RDV est confirmé, Leadrive envoie automatiquement un rappel WhatsApp 1h avant le rendez-vous. Le prospect peut confirmer ou reporter directement par WhatsApp.</p>
        </div>
        <div className="rounded-lg border border-success/30 bg-success/5 p-4 mt-2">
          <p className="font-semibold mb-1">7. Mode simulation</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">
            Envie de voir comment l&apos;IA répondrait à un prospect ? Cliquez sur le bouton <strong>?</strong> en bas à droite, puis activez le mode <strong>Simuler un prospect</strong>. Écrivez comme si vous étiez un prospect et Nora répondra exactement comme elle le ferait sur WhatsApp. Idéal pour se familiariser avec l&apos;IA avant de lancer vos premières conversations.
          </p>
        </div>
      </div>
    ),
  },
  {
    id: "exemples-ia",
    titre: "Exemples de conversations IA",
    contenu: <ExemplesIaSection />,
  },
  {
    id: "whatsapp-business",
    titre: "WhatsApp Business",
    contenu: (
      <div className="space-y-3">
        <p>
          Chaque établissement utilise <strong>son propre numéro WhatsApp Business</strong> avec son
          propre nom et logo. Le prospect voit le nom de votre établissement (ex: &quot;Peugeot Lyon Sud&quot;),
          pas &quot;Leadrive&quot;.
        </p>

        <h3 className="text-sm font-semibold mt-4">Activation, procédure guidée</h3>
        <div className="rounded-lg border border-success/30 bg-success/5 p-3 space-y-2">
          <p className="text-sm text-[var(--color-lr-text-secondary)]">
            Allez sur <Link href="/parametres/whatsapp/activation" className="text-accent hover:underline">Activation WhatsApp Business</Link> et suivez les 4 étapes :
          </p>
          <ol className="list-decimal list-inside text-sm space-y-1 text-[var(--foreground)]">
            <li>Cliquez sur <strong>Activer WhatsApp Business</strong>, un compte dédié est créé automatiquement pour votre établissement.</li>
            <li>Vous êtes redirigé sur Facebook pour connecter votre compte WhatsApp Business (choix du numéro + validation SMS dans le flow Facebook).</li>
            <li>Les modèles de messages sont soumis automatiquement à validation (5 minutes à 24 heures).</li>
            <li>La page suit l&apos;avancement de chaque modèle en temps réel.</li>
          </ol>
          <p className="text-xs text-[var(--color-lr-text-secondary)]">
            Votre établissement garde la <strong>maîtrise complète</strong> de son compte WhatsApp Business, un compte par établissement, sans partage.
          </p>
          <p className="text-xs text-[var(--color-lr-text-secondary)]">
            Trompé de compte, ou besoin de changer de numéro ? Cliquez sur <strong>Changer de compte WhatsApp</strong> en bas de la page Activation : le compte actuel est déconnecté (l&apos;envoi est interrompu jusqu&apos;à la reconnexion, vos conversations sont conservées), puis vous reconnectez le bon compte.
          </p>
        </div>

        <p className="text-sm text-[var(--color-lr-text-secondary)]">
          Le nom, les horaires et l&apos;adresse sont récupérés automatiquement depuis{" "}
          <Link href="/admin/config-ia" className="text-accent hover:underline">
            Mon établissement
          </Link>.
        </p>

        <h3 className="text-sm font-semibold mt-4">Compte restreint, envoi suspendu</h3>
        <div className="rounded-lg border border-danger/30 bg-danger/5 p-3 space-y-2 text-sm">
          <p className="text-[var(--color-lr-text-secondary)]">
            Un compte activé peut être <strong>restreint</strong> par la suite (par exemple si WhatsApp
            Business limite votre compte). Dans ce cas, une bannière rouge <strong>Compte WhatsApp
            Business restreint</strong> s&apos;affiche en haut des écrans et l&apos;<strong>envoi de
            messages est automatiquement suspendu</strong> jusqu&apos;au rétablissement.
          </p>
          <p className="text-xs text-[var(--color-lr-text-secondary)]">
            Vérifiez l&apos;état de votre compte WhatsApp Business. Dès que votre numéro redevient
            utilisable, l&apos;envoi reprend automatiquement (sous quelques minutes). L&apos;onglet{" "}
            <strong>WhatsApp</strong> reste toujours accessible dans le menu pour suivre cet état.
          </p>
        </div>

        <h3 className="text-sm font-semibold mt-4">Vos messages ne partent plus</h3>
        <div className="rounded-lg border border-danger/30 bg-danger/5 p-3 space-y-2 text-sm">
          <p className="text-[var(--color-lr-text-secondary)]">
            Même quand le compte n&apos;est pas restreint, votre numéro peut devenir injoignable
            (problème de configuration côté opérateur). Si plusieurs envois récents échouent pour cette
            raison, une bannière rouge <strong>Envoi WhatsApp en échec</strong> s&apos;affiche en haut
            des écrans.
          </p>
          <p className="text-xs text-[var(--color-lr-text-secondary)]">
            Vérifiez la configuration de votre numéro sur la page Activation. La bannière disparaît
            automatiquement dès que les envois repartent.
          </p>
        </div>

        <h3 className="text-sm font-semibold mt-4">Pré-requis Facebook, pour les non-techniques</h3>
        <div className="rounded-lg border border-[var(--border)] bg-[var(--background)] p-3 space-y-3 text-sm">
          <p>
            Avant d&apos;activer côté Leadrive, vous devez avoir un{" "}
            <strong>compte Facebook professionnel</strong> (espace Facebook pour entreprises)
            au nom de votre établissement. C&apos;est gratuit et ça prend 10
            minutes si vous n&apos;en avez pas encore.
          </p>
          <ul className="ml-4 list-disc space-y-1 text-[var(--color-lr-text-secondary)]">
            <li>
              <strong>Vérifier si vous en avez un</strong> : connectez-vous sur{" "}
              <a href="https://business.facebook.com" target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">
                business.facebook.com
              </a>{" "}
              avec le compte Facebook du dirigeant. Si vous voyez un tableau de
              bord avec le nom de l&apos;établissement, c&apos;est bon.
            </li>
            <li>
              <strong>En créer un</strong> :{" "}
              <a href="https://business.facebook.com/overview" target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">
                business.facebook.com/overview
              </a>{" "}
              → « Créer un compte » → raison sociale, email pro, accepter les
              conditions. Aucune carte bancaire demandée. Rattachez ensuite une
              page Facebook professionnelle (obligatoire).
            </li>
            <li>
              <strong>Numéro WhatsApp</strong> : un mobile ou fixe dédié,
              <strong> jamais utilisé sur l&apos;app WhatsApp</strong> (ni
              classique, ni Business). Idéal : votre standard pro (01/02/03/04/05).
            </li>
            <li>
              <strong>Coût WhatsApp Business</strong> : la création est gratuite. Les
              envois sont facturés quelques centimes par conversation, inclus
              dans votre abonnement Leadrive, aucun surcoût pour vous.
            </li>
          </ul>
        </div>

        <div className="mt-2 rounded-lg border border-accent/40 bg-accent/5 p-4 text-sm">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-accent">Besoin d&apos;aide pour l&apos;activation ?</p>
          <p className="mt-1.5">
            Notre équipe vous accompagne gratuitement. Écrivez-nous à{" "}
            <a href="mailto:contact@leadrive.fr?subject=Aide%20activation%20WhatsApp%20Business" className="font-semibold text-accent hover:underline">
              contact@leadrive.fr
            </a>{" "}
           , réponse sous 24h ouvrées. Vous pouvez aussi cliquer sur le bouton{" "}
            <strong>« ? »</strong> en bas à droite : le chatbot d&apos;aide répond
            en direct à toutes vos questions sur l&apos;activation.
          </p>
        </div>

        <div className="mt-2 rounded-lg bg-danger/5 border border-danger/30 p-4 text-sm">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-danger">Si l&apos;activation n&apos;est pas terminée</p>
          <p className="mt-1.5">Une bannière rouge en haut de chaque page signale que l&apos;envoi est bloqué et redirige vers la page d&apos;activation. Les vendeurs voient le même message en lecture seule (avec invitation à contacter leur manager).</p>
        </div>

        <div className="mt-4 rounded-lg bg-warning/5 border border-warning/30 p-4 text-sm">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-warning">Changer de numéro plus tard</p>
          <p className="mt-1.5">Possible, mais coupure temporaire des envois, réinitialisation du score de qualité, et vos prospects en cours verront un nouveau numéro. Une modale liste tous les impacts avant la confirmation.</p>
        </div>

        <div className="mt-2 rounded-lg bg-accent/5 border border-accent/30 p-4 text-sm">
          <p className="font-mono text-[10px] font-semibold uppercase tracking-[0.18em] text-[var(--color-accent)]">Modération IA</p>
          <p className="mt-1.5">Tous les messages manuels envoyés par les vendeurs et managers sont vérifiés par une IA avant envoi pour protéger la qualité WhatsApp de la flotte. Les messages commerciaux normaux passent sans problème, seuls les contenus problématiques (spam, arnaques, demande de données sensibles) sont bloqués.</p>
        </div>
      </div>
    ),
  },
  {
    id: "import-email",
    titre: "Import par email",
    contenu: (
      <div className="space-y-3">
        <p>Chaque établissement dispose d&apos;une <strong>adresse email dédiée</strong> pour recevoir automatiquement les leads des plateformes auto (Le Bon Coin, La Centrale…). L&apos;adresse est visible dans <Link href="/email-import" className="text-accent hover:underline">Acquisition &gt; Import par email</Link> (menu latéral, section manager).</p>
        <p>Format : <code className="font-mono text-xs bg-[var(--muted)] px-1.5 py-0.5 rounded">nom-etablissement-xxxx@leads.leadrive.fr</code>. Une seule adresse par établissement.</p>
        <p className="font-semibold text-[var(--foreground)] mt-4">Comment ça marche</p>
        <ol className="space-y-2 list-decimal list-inside text-sm">
          <li>Vous configurez une <strong>redirection automatique</strong> depuis votre boîte mail (Gmail, Outlook, OVH...) vers cette adresse Leadrive.</li>
          <li>Quand un email de lead arrive (LeBonCoin, etc.), il est automatiquement transféré.</li>
          <li>L&apos;IA Leadrive parse l&apos;email : extrait nom, téléphone, email, véhicule, et 50+ champs supplémentaires (immat, km, énergie, etc.). La <strong>source est détectée automatiquement</strong> (LEBONCOIN, LACENTRALE, etc.).</li>
          <li>Un nouveau lead est créé dans le dashboard, prêt à être traité par l&apos;IA WhatsApp ou un vendeur.</li>
        </ol>
        <p className="font-semibold text-[var(--foreground)] mt-4">Configurer la redirection</p>
        <p className="text-sm">Les procédures détaillées par fournisseur (Gmail, Outlook, OVH, IONOS) sont accessibles directement sur la page <Link href="/email-import" className="text-accent hover:underline">Import par email</Link> (bloc « Comment configurer la redirection ? » repliable).</p>
        <p className="font-semibold text-[var(--foreground)] mt-4">Anti-spam et déduplication</p>
        <ul className="space-y-1.5 list-disc list-inside text-sm">
          <li>L&apos;IA <strong>rejette automatiquement</strong> les newsletters, confirmations auto et notifications de plateforme (pas de lead créé pour ces emails).</li>
          <li>Si le même prospect arrive sur 2 plateformes en moins de 48h, le 2e email <strong>enrichit le lead existant</strong> au lieu de créer un doublon (critère : téléphone, fallback email).</li>
        </ul>
        <p className="font-semibold text-[var(--foreground)] mt-4">Et si un lead n&apos;arrive pas ?</p>
        <p className="text-sm">Consultez la section <strong>Historique des emails reçus</strong> sur la page <Link href="/email-import" className="text-accent hover:underline">Import par email</Link> : vous y voyez le statut de chaque email (Lead créé, Doublon enrichi, Spam rejeté, Échec parsing). Si un email apparaît en <em>Échec parsing</em>, contactez le support. Si rien n&apos;apparaît, vérifiez la redirection dans votre boîte mail.</p>
        <p className="font-semibold text-[var(--foreground)] mt-4">Envoi automatique du premier message</p>
        <p className="text-sm">Vous pouvez activer l&apos;<strong>envoi automatique du premier message WhatsApp</strong> pour les leads reçus par email, depuis <Link href="/admin/config-ia" className="text-accent hover:underline">Mon établissement</Link> (section « Premier message automatique », désactivé par défaut). Vous réglez un délai (défaut 5 minutes) et une plage horaire (défaut 8h à 20h, l&apos;heure de fin peut aller jusqu&apos;à minuit). Cela ne concerne que les leads email disposant d&apos;un téléphone, et nécessite WhatsApp Business activé. Un lead programmé affiche un bandeau sur sa fiche, un badge dans la liste, et apparaît dans la section « Messages auto à venir » du tableau de bord (l&apos;heure indiquée est l&apos;heure d&apos;envoi réelle : hors plage, l&apos;application affiche l&apos;heure du prochain créneau), avec les boutons <strong>Envoyer maintenant</strong> et <strong>Annuler</strong>. <strong>Envoyer maintenant</strong> laisse un court délai de quelques secondes pendant lequel vous pouvez encore annuler l&apos;envoi ; <strong>Annuler</strong> propose de même un bouton « Annuler » pendant quelques secondes pour reprogrammer l&apos;envoi à l&apos;heure prévue.</p>
        <p className="text-sm text-[var(--color-lr-text-secondary)] mt-4">Page connexe : <Link href="/stats/sources" className="text-accent hover:underline">Sources de leads</Link>, vous voyez quelle plateforme génère le plus de leads et de RDV pour votre établissement.</p>
      </div>
    ),
  },
  {
    id: "actions",
    titre: "Dashboard",
    contenu: (
      <div className="space-y-3">
        <p>Le dashboard affiche des <strong>cartes</strong> regroupant tout ce que vous devez traiter :</p>
        <div className="space-y-2 rounded-lg border border-[var(--border)] p-4">
          <p><strong className="text-warning">Conversations à reprendre</strong>, L&apos;IA a détecté une situation complexe et a passé la main. Cliquez pour ouvrir la conversation.</p>
          <p><strong className="text-success">RDV</strong>, Rendez-vous à venir et en retard. Pour chaque RDV, 3 boutons d&apos;action :</p>
          <ul className="list-disc list-inside ml-4 text-sm text-[var(--color-lr-text-secondary)]">
            <li><strong>Effectué</strong> : marque le RDV comme réalisé</li>
            <li><strong>Reporter</strong> : reporte le RDV à une autre date</li>
            <li><strong>Abandonner</strong> : demande la raison (absence, annulation, doublon, autre) puis annule le RDV</li>
          </ul>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Les RDV en retard sont présentés comme une tâche à clôturer (fond ambre), pas comme une erreur.</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Après un « Effectué » ou un « Abandonner », une notification « Annuler » s&apos;affiche quelques secondes pour revenir en arrière (le RDV redevient à traiter).</p>
          <p><strong className="text-accent">Messages auto à venir</strong>, affichée uniquement si l&apos;envoi automatique du premier message est activé et qu&apos;au moins un lead email est programmé. Pour chaque ligne : <strong>Envoyer maintenant</strong> ou <strong>Annuler</strong>.</p>
        </div>
        <p className="text-sm text-[var(--color-lr-text-secondary)]">
          Un <strong>briefing quotidien IA</strong> personnalisé est également affiché : résumé de la veille, prospects prioritaires, chiffre-clé de la semaine.
        </p>
      </div>
    ),
  },
  {
    id: "stats",
    titre: "Statistiques",
    contenu: (
      <div className="space-y-3">
        <p>La page <Link href="/stats" className="text-accent hover:underline">/stats</Link> offre un tableau de bord analytique avec sélection de période (7j, 30j, 90j, 12m) :</p>
        <div className="space-y-2 rounded-lg border border-[var(--border)] p-4">
          <p className="font-semibold mb-2">4 KPIs en haut de page</p>
          <ul className="list-disc list-inside text-sm space-y-1 text-[var(--color-lr-text-secondary)]">
            <li><strong>Leads reçus</strong> : nombre de leads importés sur la période</li>
            <li><strong>RDV obtenus</strong> : nombre de RDV décrochés</li>
            <li><strong>Taux de prise de RDV</strong> : pourcentage de leads ayant obtenu un RDV confirmé via WhatsApp (à ne pas confondre avec le taux de transformation RDV vers vente)</li>
            <li><strong>Leads traités</strong> : leads terminés sur la période</li>
          </ul>
          <p className="text-sm text-[var(--color-lr-text-secondary)] mt-2">Chaque KPI affiche l&apos;évolution par rapport à la période précédente.</p>
        </div>
        <ul className="space-y-1.5 list-disc list-inside text-sm">
          <li><strong>Graphique pipeline</strong> : répartition par statut en barres</li>
          <li><strong>Courbe d&apos;activité</strong> : evolution temporelle des leads et RDV</li>
          <li><strong>Performance équipe</strong> (managers uniquement) : classement des vendeurs par activité</li>
        </ul>
      </div>
    ),
  },
  {
    id: "inbox",
    titre: "Conversations",
    contenu: (
      <div className="space-y-3">
        <p>La page <Link href="/inbox" className="text-accent hover:underline">/inbox</Link> centralise toutes les conversations WhatsApp :</p>
        <ul className="space-y-1.5 list-disc list-inside text-sm">
          <li><strong>3 colonnes</strong> : liste des conversations, détail de la conversation, panneau d&apos;analyse IA</li>
          <li><strong>Nom du vendeur</strong> affiche sur chaque conversation</li>
          <li><strong>Indicateurs de statut</strong> des messages : envoyé (&#x2713;), délivré (&#x2713;&#x2713;), lu (&#x2713;&#x2713; bleus)</li>
          <li><strong>Réessayer</strong> : en cas d&apos;échec d&apos;envoi, un bouton permet de renvoyer le message</li>
          <li><strong>Badge RDV</strong> : lien cliquable vers la fiche du lead quand un RDV est détecté</li>
          <li><strong>Filtres</strong> : statut, mode pilotage (IA/Humain), non-lus</li>
          <li><strong>Notifications sonores</strong> temps réel quand un prospect repond</li>
        </ul>
        <p className="text-sm text-[var(--color-lr-text-secondary)]">Badges : &quot;À reprendre&quot; (ambre) = l&apos;IA a passé la main. &quot;Manuel&quot; (gris) = vendeur a repris la main.</p>
      </div>
    ),
  },
  {
    id: "notifications",
    titre: "Notifications en temps réel",
    contenu: (
      <div className="space-y-3">
        <p>Dès qu&apos;un nouveau prospect arrive (import automatique par email, import CSV, copier-coller texte), Leadrive vous alerte instantanément sans que vous ayez à recharger la page :</p>
        <ul className="space-y-1.5 list-disc list-inside">
          <li><strong>Toast en bas à droite</strong> avec le nom du prospect et la source (Le Bon Coin, La Centrale…). Cliquez sur « Voir » pour ouvrir la fiche.</li>
          <li><strong>Signal sonore</strong> discret (deux notes courtes) pour attirer l&apos;attention.</li>
          <li><strong>Badge bleu</strong> sur l&apos;entrée « Prospects » du menu latéral avec le compteur de nouveaux leads. Le badge se réinitialise dès que vous ouvrez la page Prospects.</li>
          <li><strong>Pages auto-rafraîchies</strong> : Prospects, Statistiques et Import par email se mettent à jour toutes seules avec le nouveau lead.</li>
        </ul>
        <p className="text-sm text-[var(--color-lr-text-secondary)]">Les notifications ne s&apos;affichent que si l&apos;onglet Leadrive est ouvert. Si vous arrivez le matin avec l&apos;outil fermé, vous retrouvez quand même tous les leads reçus pendant la nuit dans la liste, le badge sera à jour à la reconnexion.</p>
      </div>
    ),
  },
  {
    id: "onboarding",
    titre: "Prise en main",
    contenu: (
      <div className="space-y-3">
        <p>À la première connexion, Leadrive vous guide pour démarrer rapidement :</p>
        <div className="space-y-2 rounded-lg border border-[var(--border)] p-4">
          <p><strong>Managers</strong> : un assistant de configuration en 4 étapes (établissement, import de prospects, invitation de l&apos;équipe). L&apos;étape d&apos;import met en avant la solution recommandée, l&apos;import automatique par email avec votre adresse Leadrive dédiée, et propose en option l&apos;import manuel (CSV, copier-coller, email). Chaque étape est facultative.</p>
          <p><strong>Vendeurs</strong> : une visite guidée interactive vous montre les éléments clés de l&apos;interface (tableau de bord, centre d&apos;actions, conversations, prospects, planning).</p>
          <p><strong>Manager-vendeur</strong> : vous bénéficiez des deux ! Le wizard de configuration puis la visite guidée de l&apos;interface.</p>
        </div>
        <p className="text-sm text-[var(--color-lr-text-secondary)]">
          Vous pouvez relancer ces parcours a tout moment :
        </p>
        <div className="flex flex-wrap gap-3">
          <Link
            href="/dashboard?tour=1"
            className="inline-block rounded-lg bg-accent/10 px-4 py-2 text-sm font-medium text-accent hover:bg-accent/20 transition-colors"
          >
            Relancer la visite guidée
          </Link>
          <Link
            href="/onboarding?reset=1"
            className="inline-block rounded-lg border border-[var(--border)] px-4 py-2 text-sm font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] transition-colors"
          >
            Relancer la configuration
          </Link>
        </div>
      </div>
    ),
  },
  {
    id: "faq",
    titre: "Questions fréquentes",
    contenu: (
      <div className="space-y-4">
        <div>
          <p className="font-semibold mb-1">Mon import a échoué, pourquoi ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Leadrive accepte les fichiers CSV, TSV et Excel (.xlsx, .xls), ainsi que le copier-coller. Le mapping des colonnes est proposé par l&apos;IA et vous pouvez le corriger avant de valider. Les leads avec email/téléphone déjà en base sont marqués comme doublons et ignorés. Voir l&apos;<Link href="/imports" className="text-accent hover:underline">historique des imports</Link> pour le détail des erreurs.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">Comment fonctionne le mapping intelligent ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Quand vous importez un fichier pour la première fois, l&apos;IA analyse les colonnes et propose un mapping automatique. Vous vérifiez et validez, puis le mapping est sauvegardé en template. Les imports suivants avec le même format sont quasi-instantanés. Gérez vos templates sur <Link href="/import/templates" className="text-accent hover:underline">/import/templates</Link>.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">Quel nom voient mes prospects sur WhatsApp ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Vos prospects voient le nom de votre établissement tel qu&apos;il est enregistré et vérifié sur votre compte Facebook professionnel. Ce nom exact est affiché sur la page d&apos;activation WhatsApp (rubrique « Nom affiché à vos prospects ») : il peut différer du nom que vous avez saisi dans « Mon établissement » si votre compte Facebook porte une autre raison sociale. Ce nom est validé par Facebook, il ne peut pas être modifié depuis Leadrive. Pour le changer, rendez-vous sur <a href="https://business.facebook.com/settings" target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">business.facebook.com</a>.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">L&apos;activation WhatsApp est bloquée, que faire ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Vérifiez que votre compte Facebook professionnel est bien vérifié sur <a href="https://business.facebook.com/settings/security" target="_blank" rel="noopener noreferrer" className="text-accent hover:underline">business.facebook.com</a>. Si la vérification est en cours, patientez 2 à 5 jours puis réessayez. Si le problème persiste, contactez-nous à <a href="mailto:contact@leadrive.fr" className="text-accent hover:underline">contact@leadrive.fr</a> ou via le chatbot.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">L&apos;activation indique &quot;Ce numéro est déjà enregistré sur WhatsApp&quot;</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Le numéro choisi est déjà utilisé sur WhatsApp ailleurs (application WhatsApp classique, application WhatsApp Business verte, ou un autre outil), et un numéro ne peut être actif que sur un seul service à la fois. Deux solutions : supprimez d&apos;abord le compte WhatsApp existant sur ce numéro (dans l&apos;application concernée) puis relancez la connexion, ou utilisez un numéro dédié jamais encore utilisé sur WhatsApp (recommandé : un numéro réservé à l&apos;établissement). Si vous venez d&apos;enchaîner plusieurs tentatives, patientez quelques minutes avant de réessayer.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">L&apos;IA n&apos;envoie pas mon premier message WhatsApp</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Le numéro doit être valide (06/07 français ou format international +XX...). Vérifiez aussi que le prospect n&apos;a pas demandé l&apos;arrêt du contact (opt-out).</p>
        </div>
        <div>
          <p className="font-semibold mb-1">Quelle est la différence entre statut et scoring ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Le <strong>statut</strong> = l&apos;étape dans le pipeline (Nouveau, Contacté, En cours, RDV pris, Terminé). Le <strong>score d&apos;engagement</strong> = la chaleur du lead (0-100), calculé par l&apos;IA uniquement à partir des échanges WhatsApp. Un lead sans conversation n&apos;a pas de score.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">Puis-je modifier le texte du premier message WhatsApp ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Pas directement depuis l&apos;application : le premier message utilise un template WhatsApp validé. Si vous souhaitez le faire ajuster (formulation, mention de votre établissement), contactez l&apos;équipe Leadrive à <a href="mailto:contact@leadrive.fr" className="text-accent hover:underline">contact@leadrive.fr</a>. Les coordonnées de votre établissement (horaires, adresse, téléphone) restent modifiables depuis <Link href="/admin/config-ia" className="text-accent hover:underline">Mon établissement</Link> et influencent les messages suivants envoyés par l&apos;IA.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">Comment l&apos;IA décide qu&apos;un lead doit passer "RDV pris" ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">À chaque réponse du prospect, l&apos;IA détecte une intention. Si elle détecte un créneau proposé ou accepté (ex: "demain 14h, samedi matin..."), elle passe automatiquement le lead à RDV pris.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">L&apos;IA relance-t-elle automatiquement les prospects qui ne répondent pas ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Oui, 2 relances automatiques (à J+1 et J+3). Après ça, une tâche est créée dans votre planning pour que vous décidiez de la suite (appel, etc.). Les relances s&apos;arrêtent si le prospect répond, demande l&apos;arrêt, ou si un RDV est déjà pris.</p>
        </div>
        <div>
          <p className="font-semibold mb-1">Comment tester les réponses de l&apos;IA avant de contacter mes prospects ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">
            Ouvrez le chatbot Nora (bouton <strong>?</strong> en bas à droite) et cliquez sur <strong>Simuler un prospect</strong>. Vous pouvez écrire n&apos;importe quel message comme si vous étiez un prospect : « Bonjour, c&apos;est combien la 3008 ? », « Je ne veux pas être appelé », « C&apos;est trop cher »… Nora répondra avec les mêmes règles que sur WhatsApp. Testez les cas difficiles pour voir comment l&apos;IA s&apos;en sort !
          </p>
        </div>
        <div>
          <p className="font-semibold mb-1">Pourquoi me demande-t-on mon avis après chaque RDV ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">
            Quand vous cliquez sur <strong>Effectué</strong>, un volet rapide vous demande en 2 taps si l&apos;IA avait bien qualifié le prospect (Parfait, Correct, À côté) et la suite prévue (devis, essai, relance, perdu). Plus vous donnez de retours, plus l&apos;IA comprend les spécificités de votre établissement et qualifie mieux vos prospects. C&apos;est totalement optionnel : si vous fermez le volet sans répondre, le RDV reste marqué comme effectué.
          </p>
        </div>
        <div>
          <p className="font-semibold mb-1">Que signifient les paliers d&apos;apprentissage de l&apos;IA ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">
            Ils indiquent le volume de retours que votre équipe a donnés à l&apos;IA : <strong>Premiers pas</strong> (0-4 retours), <strong>En croissance</strong> (5-14), <strong>Fiable</strong> (15-29), <strong>Expert</strong> (30-49), <strong>Maître</strong> (50+). À partir de 20 retours, un score de précision IA apparaît dans le dashboard manager pour mesurer la qualité de la qualification.
          </p>
        </div>
        <div>
          <p className="font-semibold mb-1">Une question pas listée ici ?</p>
          <p className="text-sm text-[var(--color-lr-text-secondary)]">Cliquez sur le <strong>bouton ? en bas à droite</strong> pour discuter avec Nora, l&apos;assistant IA. Elle connaît toute l&apos;application et peut répondre en temps réel.</p>
        </div>
      </div>
    ),
  },
];

export default function AidePage() {
  const [active, setActive] = useState(SECTIONS[0].id);

  // Scroll-spy : highlight la section dont le top vient de passer une ligne fixe en haut du viewport.
  // Les scroll events ne bubblent pas, donc on utilise capture:true pour intercepter le scroll
  // de N'IMPORTE quel descendant (notamment <main> avec overflow-y-auto dans AppLayout).
  // getBoundingClientRect reste relatif au viewport, ce qui permet de comparer avec un seuil en px.
  useEffect(() => {
    let raf = 0;
    function update() {
      const seuil = 120; // px depuis le top du viewport (~ hauteur header + marge confort)
      let candidat = SECTIONS[0].id;
      for (const s of SECTIONS) {
        const el = document.getElementById(s.id);
        if (!el) continue;
        if (el.getBoundingClientRect().top - seuil <= 0) {
          candidat = s.id;
        } else {
          break;
        }
      }
      setActive((prev) => (prev === candidat ? prev : candidat));
    }
    function onScroll() {
      // throttle via requestAnimationFrame pour eviter les calculs en rafale
      if (raf) return;
      raf = window.requestAnimationFrame(() => {
        raf = 0;
        update();
      });
    }
    update();
    document.addEventListener("scroll", onScroll, { capture: true, passive: true });
    window.addEventListener("resize", onScroll);
    return () => {
      document.removeEventListener("scroll", onScroll, { capture: true });
      window.removeEventListener("resize", onScroll);
      if (raf) window.cancelAnimationFrame(raf);
    };
  }, []);

  return (
    <AppLayout>
      <div className="px-6 py-6 max-w-6xl mx-auto animate-fade-in">
        <PageHeader
          eyebrow="Centre d'aide"
          title="Aide"
          subtitle="Tout ce qu'il faut savoir pour utiliser Leadrive."
        />

        {/* Bandeau de mise en avant : chatbot Nora + mode simulation */}
        <div className="mb-8 rounded-xl border border-accent/30 bg-gradient-to-br from-accent/5 to-accent/10 p-5">
          <div className="flex items-start gap-4">
            <div className="flex h-10 w-10 flex-shrink-0 items-center justify-center rounded-full bg-accent/15">
              <svg className="h-5 w-5 text-accent" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M9.663 17h4.673M12 3v1m6.364 1.636l-.707.707M21 12h-1M4 12H3m3.343-5.657l-.707-.707m2.828 9.9a5 5 0 117.072 0l-.548.547A3.374 3.374 0 0014 18.469V19a2 2 0 11-4 0v-.531c0-.895-.356-1.754-.988-2.386l-.548-.547z" />
              </svg>
            </div>
            <div className="flex-1">
              <p className="font-semibold text-[var(--foreground)]">Discutez avec Nora, votre assistante IA</p>
              <p className="mt-1 text-sm text-[var(--color-lr-text-secondary)]">
                Cliquez sur le bouton <strong>?</strong> en bas à droite pour ouvrir le chatbot. Deux modes disponibles :
              </p>
              <ul className="mt-2 space-y-1 text-sm text-[var(--color-lr-text-secondary)]">
                <li>
                  <strong className="text-[var(--foreground)]">Aide</strong> : posez n&apos;importe quelle question sur le fonctionnement de l&apos;outil et Nora vous répond instantanément (statuts, scoring, RDV, configuration…).
                </li>
                <li>
                  <strong className="text-[var(--foreground)]">Simuler un prospect</strong> : testez comment l&apos;IA WhatsApp répondrait à un prospect en écrivant vous-même comme si vous étiez prospect. Idéal pour tester les cas difficiles (refus, mécontentement, demande de prix…) avant vos premières vraies conversations.
                </li>
              </ul>
            </div>
          </div>
        </div>

        <div className="grid grid-cols-1 lg:grid-cols-4 gap-6">
          {/* Sommaire sticky */}
          <nav className="lg:col-span-1">
            <div className="sticky top-6 space-y-1">
              {SECTIONS.map((s) => (
                <button
                  key={s.id}
                  onClick={() => {
                    setActive(s.id);
                    document.getElementById(s.id)?.scrollIntoView({ behavior: "smooth", block: "start" });
                  }}
                  className={`block w-full rounded-lg px-3 py-2 text-left text-sm transition-colors ${
                    active === s.id
                      ? "bg-accent/10 text-accent font-medium"
                      : "text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] hover:text-[var(--foreground)]"
                  }`}
                >
                  {s.titre}
                </button>
              ))}
            </div>
          </nav>

          {/* Contenu : scroll-reveal-fast = chaque section fade-in lorsqu'elle entre
              dans le viewport (lecture séquentielle naturelle, focus sur la section
              courante sans distraction des suivantes). */}
          <div className="lg:col-span-3 space-y-8">
            {SECTIONS.map((s) => (
              <section
                key={s.id}
                id={s.id}
                className="rounded-xl border border-[var(--border)] bg-[var(--card)] p-6 scroll-mt-6 scroll-reveal-fast"
              >
                <h2 className="display-h2 mb-4 text-[var(--foreground)]">{s.titre}</h2>
                <div className="text-sm text-[var(--foreground)] leading-relaxed">
                  {s.contenu}
                </div>
              </section>
            ))}

            {/* CTA en bas */}
            <div className="rounded-xl border border-accent/30 bg-accent/5 p-6 text-center">
              <p className="text-sm font-medium text-[var(--foreground)] mb-2">Vous ne trouvez pas votre réponse ?</p>
              <p className="text-xs text-[var(--color-lr-text-secondary)]">
                Cliquez sur le bouton <strong>?</strong> en bas à droite pour poser votre question à Nora, ou contactez{" "}
                <a href="mailto:contact@leadrive.fr" className="text-accent hover:underline">contact@leadrive.fr</a>.
              </p>
            </div>
          </div>
        </div>
      </div>
    </AppLayout>
  );
}
