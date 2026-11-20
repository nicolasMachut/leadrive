# Skill : Gestion des erreurs (frontend Leadrive)

## Declencheur

Ce skill s'active des qu'une tache touche un appel API cote frontend (Next.js) :
- Creation / modification d'une page ou d'un composant qui `fetch` / `apiFetch`.
- Ajout d'un bouton ou formulaire qui declenche une mutation (POST/PUT/PATCH/DELETE).
- Revue ou correction de la gestion d'erreur d'un ecran.

Objectif : **toute erreur est geree de la MEME facon partout**. Jamais d'echec silencieux, jamais de contenu vide trompeur, jamais de doublon de message.

---

## Principe central : CHARGEMENT vs ACTION

Le bon traitement ne depend PAS de "chargement vs action" par dogme, mais de la question :
**est-ce que la zone reste comprehensible une fois le message parti ?**

| Type d'erreur | Traitement | Pourquoi |
|---|---|---|
| **Chargement** (le fetch initial qui peuple l'ecran echoue) | Etat **persistant** dans la zone des donnees + bouton **Reessayer**. JAMAIS un toast. | Un toast est ephemere (il part, la zone vide devient ambigue) et se duplique en React StrictMode. |
| **Action** (mutation : enregistrer, envoyer, basculer, reassigner, relancer) | **Toast** `tone:"danger"`. | La page reste utilisable ; feedback transitoire legitime. L'utilisateur re-declenche via le bouton existant. |

---

## Erreur de CHARGEMENT : le composant `ErrorState`

Composant unique : `src/components/common/ErrorState.tsx`.

```tsx
import ErrorState from "@/components/common/ErrorState";
// ...
<ErrorState message={erreur} onRetry={recharger} />
// rend : icone danger + "Chargement impossible" + message + bouton "Reessayer", role="alert"
```

### Regles d'affichage

1. **Page mono-colonne** (config, stats, planning, formulaire) :
   - Garder le `PageHeader` / titre **au-dessus** (comme en mode normal).
   - Remplacer la zone de donnees par `<ErrorState message={erreur} onRetry={refetch} />`.
   - Ne PAS afficher le contenu vide, ni un `EmptyState "Aucun X"` (trompeur : c'est une erreur, pas un vide).

2. **Zone / colonne d'un layout** (liste inbox 2-colonnes, panneau detail) :
   - Afficher `<ErrorState>` **dans la zone** qui aurait du contenir les donnees (occupe l'espace deja reserve, ne decale pas le layout).

3. **Page composee de plusieurs widgets** (tableau de bord) :
   - Le CONTENEUR court-circuite : si le fetch principal echoue sans donnees, afficher **UN SEUL** `ErrorState` (header conserve) et **ne pas monter** les widgets enfants (sinon chacun affiche sa propre erreur => empilement).
   - L'en-tete en mode erreur doit etre **identique** au mode normal (reutiliser le composant de header existant, ex. `<DashboardHero ... />` en mode minimal, PAS un header reinvente inline).
   - Un widget garde son propre `ErrorState` uniquement pour le cas ou SON endpoint echoue isolement (serveur up).

4. **Donnees deja chargees + refetch echoue** : garder les donnees affichees + une **banniere** en haut (cas rare) :
   ```tsx
   {erreur && data && (
     <div role="alert">
       <Alert tone="danger" className="flex flex-wrap items-center justify-between gap-3">
         <span>{erreur}</span>
         <Button variant="secondary" size="sm" onClick={refetch}>Reessayer</Button>
       </Alert>
     </div>
   )}
   ```

### Pattern de reference (page mono-colonne)

```tsx
const [data, setData] = useState<T | null>(null);
const [loading, setLoading] = useState(true);
const [erreur, setErreur] = useState("");

const charger = useCallback(async () => {
  setLoading(true);
  setErreur("");
  try {
    const res = await apiFetch(URL);
    if (!res.ok) throw new Error(`Erreur ${res.status}`); // fetch ne throw PAS sur 4xx/5xx
    setData(await res.json());
  } catch (err) {
    setErreur(formatErreur(err)); // pas de toast, pas de message brut
  } finally {
    setLoading(false);
  }
}, [apiFetch]);

// rendu : le HEADER est TOUJOURS rendu (des le 1er paint), JAMAIS d'early-return
// qui afficherait skeleton ou erreur sans le titre. Seule la ZONE bascule.
return (
  <AppLayout>
    <Header/>  {/* titre visible immediatement : chargement, erreur, ou succes */}
    {loading && !data ? (
      <SkeletonZone/>                                   // skeleton, pas spinner rond
    ) : erreur && !data ? (
      <ErrorState message={erreur} onRetry={charger} />
    ) : (
      <>{/* contenu */}</>
    )}
  </AppLayout>
);
```

### Le header est toujours rendu des le 1er paint

**Interdit** : `if (loading) return <skeleton sans header>` ou `if (erreur) return <ErrorState sans header>`. Le titre/`PageHeader` (ou le hero d'un dashboard, en mode minimal) doit etre visible **immediatement**, que le chargement soit en cours, en erreur, ou termine. Seule la zone de contenu sous le header change. Un page qui affiche son titre seulement une fois charge donne une impression de saut / page qui se construit en deux temps.

### Separer `loadError` de `error`

Si une meme variable sert au chargement ET aux actions, introduire un etat distinct `loadError` : ainsi "Reessayer" (re-fetch) ne s'affiche QUE pour le chargement (jamais sur une erreur d'action ou il risquerait d'ecraser une saisie).

---

## Erreur d'ACTION : toast

```tsx
const { showToast } = useToast(); // de "@/lib/realtime"

async function enregistrer() {
  setSaving(true);
  try {
    const res = await apiFetch(URL, { method: "PUT", body });
    if (!res.ok) throw new Error(`Erreur ${res.status}`);
    showToast({ titre: "Enregistre", tone: "success" });
  } catch (err) {
    showToast({ titre: "Enregistrement echoue", message: formatErreur(err), tone: "danger" });
  } finally {
    setSaving(false);
  }
}
```

**Toute mutation a un `catch`.** Un `try/finally` SANS `catch` = echec d'action silencieux (l'utilisateur clique, rien ne se passe, aucune explication). Interdit.

---

## `formatErreur`

Toujours passer l'erreur par `formatErreur` (de `@/lib/auth`) : il transforme les `Erreur 500` / `failed to fetch` en messages clairs et masque les codes techniques. Ne jamais afficher `err.message` brut ni `Erreur ${status}` a l'utilisateur.

---

## Interdits (anti-patterns)

- `.catch(() => {})` ou `catch {}` silencieux sur un fetch de donnees.
- `showToast` pour signaler un echec de CHARGEMENT (ephemere + double en StrictMode).
- Afficher le contenu vide / un `EmptyState "Aucun X"` quand le fetch a echoue.
- Empiler plusieurs messages d'erreur sur une page composee (court-circuiter au conteneur).
- Supprimer le header/titre en mode erreur (le garder comme en mode normal).
- `try/finally` sans `catch` sur une mutation.
- Spinner rond generique pour un chargement de contenu (utiliser un skeleton).

---

## Checklist avant de livrer un ecran

- [ ] Le fetch teste `!res.ok` (fetch ne throw pas sur 4xx/5xx).
- [ ] Chargement echoue sans donnees -> `ErrorState` + Reessayer, header conserve, pas de contenu vide.
- [ ] Page multi-widgets -> un seul message (court-circuit conteneur).
- [ ] Chaque mutation a un `catch` -> toast danger via `formatErreur`.
- [ ] Aucun `.catch(() => {})` sur un fetch de donnees.
- [ ] Aucun message brut (`Erreur 500`, `err.message`) affiche : tout via `formatErreur`.
- [ ] Header/titre rendu des le 1er paint : skeleton ET ErrorState s'affichent SOUS le header, pas d'`if (loading/erreur) return` qui zappe le titre.

> La regle complete et les cas reels corriges sont aussi traces dans `.claude/agents/bug-hunter.md` (categorie gestion d'erreurs).
