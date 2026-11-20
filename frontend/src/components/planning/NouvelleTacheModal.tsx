"use client";

import Modal from "@/components/common/Modal";
import DatePicker from "@/components/common/DatePicker";
import TimePicker from "@/components/common/TimePicker";

export interface NouvelleTacheState {
  titre: string;
  typeTache: string;
  date: string;   // YYYY-MM-DD
  heure: string;  // HH:mm
}

interface Props {
  open: boolean;
  newTache: NouvelleTacheState;
  setNewTache: (v: NouvelleTacheState) => void;
  onClose: () => void;
  onSubmit: () => void;
}

/**
 * Modale de creation d'une tache reutilisable entre la vue "Ma journee"
 * et la vue Calendrier. Le date picker est decompose en 2 champs distincts
 * (date + heure) pour une meilleure ergonomie. La priorite n'est plus
 * demandee (le backend met NORMALE par defaut).
 */
export default function NouvelleTacheModal({ open, newTache, setNewTache, onClose, onSubmit }: Props) {
  const inputClass = "w-full rounded-lg border border-[var(--border)] bg-[var(--background)] px-4 py-2.5 text-sm text-[var(--foreground)] focus:border-accent focus:outline-none focus:ring-1 focus:ring-accent transition-colors";
  const labelClass = "mb-1 block text-xs font-medium text-[var(--color-lr-text-secondary)]";

  return (
    <Modal open={open} onClose={onClose} title="Nouvelle tâche" description="">
      <div className="space-y-4">
        <div>
          <label className={labelClass}>Titre</label>
          <input
            type="text"
            placeholder="Ex : Rappeler Dupont"
            value={newTache.titre}
            onChange={(e) => setNewTache({ ...newTache, titre: e.target.value })}
            className={inputClass}
            autoFocus
          />
        </div>

        <div>
          <label className={labelClass}>Type</label>
          <select
            value={newTache.typeTache}
            onChange={(e) => setNewTache({ ...newTache, typeTache: e.target.value })}
            className={inputClass}
          >
            <option value="APPEL">Appel</option>
            <option value="EMAIL">Email</option>
            <option value="RDV">RDV</option>
            <option value="ESSAI">Essai</option>
            <option value="RELANCE">Relance</option>
            <option value="DEVIS">Devis</option>
            <option value="AUTRE">Autre</option>
          </select>
        </div>

        <div className="grid grid-cols-2 gap-3">
          <div>
            <label className={labelClass}>Date</label>
            <DatePicker
              value={newTache.date}
              onChange={(v) => setNewTache({ ...newTache, date: v })}
              placeholder="Choisir une date"
            />
          </div>
          <div>
            <label className={labelClass}>Heure</label>
            <TimePicker
              value={newTache.heure}
              onChange={(v) => setNewTache({ ...newTache, heure: v })}
            />
          </div>
        </div>
      </div>

      <div className="mt-5 flex justify-end gap-2">
        <button
          onClick={onClose}
          className="rounded-lg border border-[var(--border)] px-4 py-2 text-sm font-medium text-[var(--color-lr-text-secondary)] hover:bg-[var(--muted)] transition-colors"
        >
          Annuler
        </button>
        <button
          onClick={onSubmit}
          disabled={!newTache.titre || !newTache.date}
          className="rounded-lg btn-primary px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
        >
          Créer la tâche
        </button>
      </div>
    </Modal>
  );
}
