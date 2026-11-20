# Skill : Toast d'annulation des actions mutantes (frontend Leadrive)

## Declencheur

Ce skill s'active des qu'une tache ajoute ou modifie une **action mutante declenchee par l'utilisateur** cote frontend (Next.js) :
- Un bouton / clic qui change un etat en base (statut, flag, report, assignation, cloture).
- Un bouton / clic qui declenche un effet de bord externe (envoi WhatsApp / SMS / email).
- Toute mutation POST/PUT/PATCH/DELETE lancee depuis un composant.

Objectif : **toute action mutante propose une annulation** (coherence UX). L'utilisateur doit toujours avoir un filet de securite apres un clic, quelle que soit la surface.

---

## Regle centrale : REVERSIBLE vs IRREVERSIBLE

La bonne approche depend d'UNE question : **l'effet est-il rappelable une fois fait ?**

| Type d'action | Exemples | Approche |
|---|---|---|
| **REVERSIBLE** (changement d'etat en base) | statut de lead, flag envoi auto, report de tache/RDV, RDV effectue/abandonne | Faire l'action, puis toast succes + bouton **"Annuler"** qui **INVERSE** via un appel API. |
| **IRREVERSIBLE** (effet de bord externe non rappelable) | envoi WhatsApp / SMS / email | **PAS d'undo apres coup.** On **DIFFERE** l'effet de quelques secondes avec un bouton **"Annuler"** ; rien ne part tant que le delai n'est pas ecoule. |

Ne jamais afficher un simple toast de succes sec (« C'est fait ») sans offrir l'annulation : c'est le pattern a remplacer.

---

## Cas REVERSIBLE : toast succes + "Annuler" qui inverse

Helpers dans `frontend/src/lib/rdvUndo.ts` (reutiliser, ne pas reinventer) :

| Helper | Action | Undo (inverse) |
|---|---|---|
| `toastUndoRdv` | RDV Effectue / Abandonner | rouvre la tache (`POST /api/feedback/rdv/annuler-action`) |
| `toastUndoStatut` | changement de statut lead | re-PATCH vers le statut precedent (source SELECTBOX) |
| `toastUndoTache` | tache marquee Effectuee | rouvre la tache (`POST /api/taches/{id}/rouvrir`) |
| `toastUndoReporter` | report de tache/RDV | re-PATCH avec l'ancienne date d'echeance |
| `toastUndoEnvoiAutoAnnule` | annulation envoi auto | re-arme l'envoi (`POST /api/leads/{id}/envoi-auto/reprogrammer` avec l'ancienne echeance) |
| `toastUndoArchiveLead` | archivage (suppression douce) d'un prospect | desarchive le prospect (`POST /api/leads/{id}/restaurer`) |
| `toastUndoDesarchiveLead` | desarchivage d'un prospect | re-archive le prospect (`DELETE /api/leads/{id}`) |

Regles communes :
- **Duration 7000 ms** (laisse le temps de cliquer "Annuler").
- **Toujours gerer l'echec de l'undo** : `catch` -> `showToast({ titre: "Annulation impossible", message: formatErreur(err), tone: "danger" })`. Jamais de silent catch.
- **Capturer l'ancienne valeur AVANT l'appel** (statut precedent, ancienne echeance) : elle sert de payload a l'undo. Si elle est absente sur la surface courante, afficher un toast de confirmation SANS bouton Annuler (graceful degrade).
- Passer un `onReverted` qui **rafraichit la surface** (refetch / increment de compteur).

Nouvel undo reversible ? Ajouter un helper dans `rdvUndo.ts` sur le meme modele (parametres nommes, `apiFetch`, `showToast`, gestion d'echec), pas de fetch inline dans le composant.

---

## Cas IRREVERSIBLE : envoi differe annulable

Helper unique : `toastEnvoiDiffere` dans `frontend/src/lib/rdvUndo.ts`.

```ts
toastEnvoiDiffere({
  showToast,
  // delaiMs par defaut 7000
  onEnvoyer: async () => {
    // L'ENVOI REEL (POST /whatsapp), fait seulement apres le delai.
    // Peut inclure un refetch + toast "Message envoyé".
  },
  onErreur: (err) => {
    showToast({ titre: "Envoi échoué", message: formatErreur(err), tone: "danger" });
  },
});
```

Comportement garanti :
- Rien n'est envoye tant que le delai n'est pas ecoule (aucun appel reseau d'envoi pendant le delai).
- Le toast "Envoi du message…" affiche un bouton "Annuler" (duration = delaiMs). Un clic annule le timeout : **aucun envoi**, toast "Envoi annulé".
- Le fonction appelante devient **synchrone** (`function`, pas `async`) : elle arme le timer et rend la main.

Point d'attention (effet de bord a connaitre) : le timer vit dans le closure du toast. Si l'utilisateur **navigue ailleurs** (demontage du composant) pendant le delai, le timer continue et l'envoi part quand meme (comportement voulu : "Envoyer maintenant" = intention ferme). Le `onEnvoyer`/`onErreur` doivent donc etre robustes a un composant potentiellement demonte (ne pas dependre d'un state React encore monte pour la partie critique de l'envoi).

Cas particulier envoi auto : le flag `premierMessageAutoPrevuLe` n'est PAS touche pendant le delai (c'est l'envoi reel via `/whatsapp` qui l'efface cote back). Donc si l'envoi differe est annule, l'envoi **automatique** normal reste programme et partira a son heure.

---

## Checklist avant de livrer une action mutante

- [ ] **(a) Reversible ou irreversible ?** (changement d'etat DB = reversible ; envoi externe = irreversible).
- [ ] **(b) Helper adapte** : undo (`toastUndo*`) pour le reversible, envoi differe (`toastEnvoiDiffere`) pour l'irreversible. Pas de toast de succes sec sans annulation.
- [ ] **(c) Rollback / echec gere** : l'undo a son `catch` -> toast danger ; l'envoi differe a son `onErreur`. Jamais de silent catch.
- [ ] **(d) Message FR accentue** (é è à ç ...), pas d'em-dash, tokens CSS du theme.
- [ ] La surface est rafraichie apres l'action ET apres l'undo (`onReverted` / refetch).

---

## Surfaces deja couvertes (reference)

- **RDV Effectue / Abandonne / Reporter** : `toastUndoRdv`, `toastUndoReporter` (fiche lead, dashboard ActionCenter, inbox).
- **Statut de lead** (dropdown Pilotage) : `toastUndoStatut` (fiche lead).
- **Archivage d'un prospect** (bouton "Archiver ce prospect", encart Pilotage) : `toastUndoArchiveLead` -> desarchive (`POST /api/leads/{id}/restaurer`). Surface : fiche lead (`app/leads/[id]/page.tsx`), après confirmation via `Modal`. Après archivage on `router.push("/leads")` ; le toast global reste cliquable après navigation.
- **Desarchivage d'un prospect** : `toastUndoDesarchiveLead` -> re-archive (`DELETE /api/leads/{id}`). Surfaces : fiche lead (`app/leads/[id]/page.tsx`, bannière "Ce prospect est archivé" + bouton "Désarchiver" dans l'encart Pilotage, refetch sur place) ET liste des prospects (`app/leads/page.tsx`, action rapide "Désarchiver" par ligne en mode Archives, refetch de la liste). Symétrique de l'archivage.
- **Envoi auto du premier message WhatsApp** :
  - **Annuler** (reversible) -> `toastUndoEnvoiAutoAnnule` (re-arme). Surfaces : fiche lead (`app/leads/[id]/page.tsx`), dashboard (`components/dashboard/ActionCenter.tsx`).
  - **Envoyer maintenant** (irreversible) -> `toastEnvoiDiffere`. Memes 2 surfaces.
