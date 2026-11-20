"use client";

import { useEffect, useState } from "react";

interface Message {
  from: "ia" | "prospect";
  contenu: string;
}

interface ConversationExemple {
  id: string;
  titre: string;
  description: string;
  messages: Message[];
}

const CONVERSATIONS: ConversationExemple[] = [
  {
    id: "ex1-parcours-ideal",
    titre: "Lead frais, Parcours idéal",
    description: "Un prospect intéressé par un véhicule neuf. L'IA qualifie son projet et décroche un RDV.",
    messages: [
      { from: "ia", contenu: "Bonjour Sophie, je suis Nora, l'assistante virtuelle de Garage Central. L'un de nos conseillers m'a chargée de vous contacter au sujet de votre projet de Peugeot 3008. À quel moment de la journée pourrait-il vous appeler pour en discuter ?" },
      { from: "prospect", contenu: "Demain après-midi ça irait" },
      { from: "ia", contenu: "Très bien, je vous propose un appel demain à 14h. Vous me confirmez que ce créneau vous convient ?" },
      { from: "prospect", contenu: "Oui c'est bon !" },
      { from: "ia", contenu: "C'est confirmé ! Je note votre appel demain à 14h. Pour que notre conseiller prépare au mieux votre échange, pour quand est prévu votre projet d'achat ?" },
      { from: "prospect", contenu: "Ce mois-ci, ma voiture actuelle est en fin de leasing" },
      { from: "ia", contenu: "Parfait, c'est le bon timing pour avancer ! Êtes-vous arrêtée sur la Peugeot 3008 ou peut-on vous aider à peaufiner votre réflexion ?" },
      { from: "prospect", contenu: "La 3008 me plaît bien, j'hésite juste entre la version Allure et GT" },
      { from: "ia", contenu: "Excellent choix ! Avez-vous un véhicule à faire reprendre ?" },
      { from: "prospect", contenu: "Oui j'ai une Renault Clio 4 de 2020" },
      { from: "ia", contenu: "C'est noté ! La reprise de votre Clio conditionne-t-elle votre achat ?" },
      { from: "prospect", contenu: "Oui, ça dépend de la somme" },
      { from: "ia", contenu: "C'est noté. Avez-vous déjà une idée du budget que vous souhaitez allouer ?" },
      { from: "prospect", contenu: "Autour de 30 000 euros" },
      { from: "ia", contenu: "C'est noté ! Êtes-vous déjà allée voir votre banque pour le financement, ou pas encore ?" },
      { from: "prospect", contenu: "Pas encore, je pensais regarder en LOA" },
      { from: "ia", contenu: "C'est noté ! Notre conseiller Marc vous appellera demain à 14h. Il aura préparé une estimation de votre Clio et les meilleures conditions pour la Peugeot 3008 adaptées à votre budget, vous n'aurez plus qu'à comparer et décider. À très bientôt !" },
      { from: "prospect", contenu: "Super, merci beaucoup ! À demain" },
    ],
  },
  {
    id: "ex2-prospect-hesitant",
    titre: "Prospect hésitant, L'IA rassure sans forcer",
    description: "Un prospect pas encore décidé. L'IA s'adapte, ne force pas, et sécurise un créneau en douceur.",
    messages: [
      { from: "ia", contenu: "Bonjour Karim, je suis Nora, l'assistante virtuelle de Garage Central. L'un de nos conseillers m'a chargée de vous contacter au sujet de votre projet de Renault Captur. À quel moment de la journée pourrait-il vous appeler pour en discuter ?" },
      { from: "prospect", contenu: "Je sais pas trop, c'est pas urgent en fait" },
      { from: "ia", contenu: "Je comprends, c'est un projet important. Notre conseiller est là pour répondre à toutes vos questions, sans engagement. Je vous renvoie un petit message demain pour qu'on cale ça ensemble, ça vous convient ?" },
      { from: "prospect", contenu: "Ok oui, demain ça va" },
      { from: "ia", contenu: "Très bien ! En attendant, juste pour que notre conseiller prépare au mieux, pour quand est prévu votre projet ?" },
      { from: "prospect", contenu: "Dans 2-3 mois peut-être, je regarde juste pour l'instant" },
      { from: "ia", contenu: "C'est le bon moment pour commencer à regarder, comme ça vous aurez le temps de comparer tranquillement. Êtes-vous arrêté sur le Captur ou vous hésitez avec d'autres modèles ?" },
      { from: "prospect", contenu: "J'hésite un peu avec le Peugeot 2008 aussi" },
      { from: "ia", contenu: "C'est une bonne démarche de comparer. Justement, notre conseiller connaît très bien les deux modèles et pourra vous orienter. On se recontacte demain pour caler un créneau ?" },
      { from: "prospect", contenu: "Oui d'accord, à demain" },
    ],
  },
  {
    id: "ex3-lead-abandonne",
    titre: "Lead abandonné, Reprise de contact",
    description: "Un prospect qu'on n'a pas réussi à joindre. L'IA reprend le fil naturellement.",
    messages: [
      { from: "ia", contenu: "Bonjour Thomas, je suis Nora, l'assistante virtuelle de Garage Central. L'un de nos conseillers m'a chargée de vous contacter au sujet de votre projet de Dacia Sandero. À quel moment de la journée pourrait-il vous appeler pour en discuter ?" },
      { from: "prospect", contenu: "Ah oui pardon j'étais en vacances ! Jeudi 10h ça marche ?" },
      { from: "ia", contenu: "Très bien, je vous propose un appel jeudi à 10h. Vous me confirmez ?" },
      { from: "prospect", contenu: "Oui c'est bon pour moi" },
      { from: "ia", contenu: "C'est confirmé ! Je note votre appel jeudi à 10h. Pour quand est prévu votre projet d'achat ?" },
      { from: "prospect", contenu: "Rapidement, j'ai besoin d'une voiture pour le travail" },
      { from: "ia", contenu: "Parfait, c'est le bon timing pour avancer ! Avez-vous un véhicule à faire reprendre ?" },
      { from: "prospect", contenu: "Non, c'est ma première voiture" },
      { from: "ia", contenu: "C'est noté. Avez-vous une idée du budget ?" },
      { from: "prospect", contenu: "Pas plus de 15 000 euros, je suis en début de carrière" },
      { from: "ia", contenu: "C'est noté ! Êtes-vous déjà allé voir votre banque ?" },
      { from: "prospect", contenu: "Non pas encore" },
      { from: "ia", contenu: "C'est noté. Notre conseiller Marc vous rappellera jeudi à 10h. Il aura préparé les meilleures conditions pour la Dacia Sandero dans votre budget. À jeudi !" },
      { from: "prospect", contenu: "Merci, à jeudi !" },
    ],
  },
  {
    id: "ex4-pivot-canal",
    titre: "Prospect qui préfère les messages, Pivot de canal",
    description: "Un prospect qui ne veut pas d'appel. L'IA bascule sur la conversation par message et obtient un RDV sur place.",
    messages: [
      { from: "ia", contenu: "Bonjour Claire, je suis Nora, l'assistante virtuelle de Garage Central. L'un de nos conseillers m'a chargée de vous contacter au sujet de votre projet de Citroën C5 Aircross. À quel moment de la journée pourrait-il vous appeler pour en discuter ?" },
      { from: "prospect", contenu: "Je ne souhaite pas être appelée, je préfère par message" },
      { from: "ia", contenu: "Je comprends ! Souhaitez-vous que nous discutions par message pour voir comment je peux vous aider dans votre projet ?" },
      { from: "prospect", contenu: "Oui par message c'est mieux pour moi" },
      { from: "ia", contenu: "Parfait ! Pour quand est prévu votre projet d'achat ?" },
      { from: "prospect", contenu: "Dans un mois environ, mon leasing se termine" },
      { from: "ia", contenu: "C'est le bon moment pour anticiper. Êtes-vous arrêtée sur le C5 Aircross ?" },
      { from: "prospect", contenu: "Oui, c'est le modèle qui me plaît. Par contre j'aimerais le voir en vrai avant de décider" },
      { from: "ia", contenu: "C'est noté ! Avez-vous un véhicule à faire reprendre ?" },
      { from: "prospect", contenu: "Oui, mon Peugeot 3008 en fin de leasing" },
      { from: "ia", contenu: "C'est noté. Avez-vous une idée du budget pour la suite ?" },
      { from: "prospect", contenu: "Entre 30 et 35 000 euros" },
      { from: "ia", contenu: "C'est noté ! Justement, est-ce que vous accepteriez de passer nous voir pour le C5 Aircross ? Notre conseiller pourrait vous faire une présentation complète et discuter des meilleures conditions dans votre budget. Samedi matin, ça vous irait ?" },
      { from: "prospect", contenu: "Samedi matin c'est bien, vers 10h ?" },
      { from: "ia", contenu: "Je vous propose samedi à 10h sur place. Vous me confirmez ?" },
      { from: "prospect", contenu: "Oui c'est confirmé !" },
      { from: "ia", contenu: "C'est confirmé ! Notre conseiller Marc vous accueillera samedi à 10h. Il aura préparé une présentation du C5 Aircross et les options adaptées à votre budget. À samedi !" },
    ],
  },
];

export default function ExemplesIaSection() {
  const [exempleOuvert, setExempleOuvert] = useState<string | null>(null);

  // Scroll vers l'accordeon ouvert pour que l'utilisateur voie le debut de la conversation.
  useEffect(() => {
    if (!exempleOuvert) return;
    const el = document.getElementById(`exemple-${exempleOuvert}`);
    el?.scrollIntoView({ behavior: "smooth", block: "start" });
  }, [exempleOuvert]);

  return (
    <div className="space-y-3">
      <p>
        Voici comment l&apos;IA Leadrive interagit avec vos prospects sur WhatsApp. Ces exemples illustrent les cas les plus courants. L&apos;IA s&apos;adapte automatiquement au profil et aux réponses du prospect.
      </p>

      <div className="space-y-2">
        {CONVERSATIONS.map((conv) => {
          const ouvert = exempleOuvert === conv.id;
          return (
            <div
              key={conv.id}
              id={`exemple-${conv.id}`}
              className="rounded-lg border border-[var(--border)] overflow-hidden scroll-mt-6"
            >
              <button
                type="button"
                onClick={() => setExempleOuvert(ouvert ? null : conv.id)}
                aria-expanded={ouvert}
                className="flex w-full items-start gap-3 px-4 py-3 text-left transition-colors hover:bg-[var(--muted)]/50"
              >
                <span className={`mt-0.5 flex-shrink-0 text-accent transition-transform ${ouvert ? "rotate-90" : ""}`}>
                  ▸
                </span>
                <span className="flex-1">
                  <span className="block text-sm font-semibold text-[var(--foreground)]">{conv.titre}</span>
                  <span className="block mt-0.5 text-xs text-[var(--color-lr-text-secondary)]">{conv.description}</span>
                </span>
              </button>

              {ouvert && (
                <div className="border-t border-[var(--border)] bg-[var(--muted)]/30 px-4 py-4 space-y-2 animate-fade-in">
                  {conv.messages.map((msg, i) => (
                    <div
                      key={i}
                      className={`flex ${msg.from === "ia" ? "justify-start" : "justify-end"}`}
                    >
                      <div
                        className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-sm ${
                          msg.from === "ia"
                            ? "rounded-tl-sm bg-[var(--card)] border border-[var(--border)] text-[var(--foreground)]"
                            : "rounded-tr-sm bg-success/10 border border-success/20 text-[var(--foreground)]"
                        }`}
                      >
                        <div
                          className={`mb-0.5 text-[10px] font-medium uppercase tracking-wide ${
                            msg.from === "ia"
                              ? "text-accent"
                              : "text-success"
                          }`}
                        >
                          {msg.from === "ia" ? "Nora (IA)" : "Prospect"}
                        </div>
                        <div className="whitespace-pre-wrap leading-relaxed">{msg.contenu}</div>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          );
        })}
      </div>

      <p className="mt-2 text-xs italic text-[var(--color-lr-text-tertiary)]">
        Les conversations réelles varient selon la configuration de votre établissement (horaires, adresse, téléphone) et les réponses du prospect.
      </p>
    </div>
  );
}
