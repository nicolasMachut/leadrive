"use client";

import { useEffect, useState } from "react";
import { createPortal } from "react-dom";
import { useApiClient } from "@/lib/auth";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface RepImpact {
  feedbackId: number;
  leadId: number;
  nomProspect: string;
}

export default function ImpactToast() {
  const apiFetch = useApiClient();
  const [mounted, setMounted] = useState(false);
  const [queue, setQueue] = useState<RepImpact[]>([]);
  const [current, setCurrent] = useState<RepImpact | null>(null);

  useEffect(() => { setMounted(true); }, []);

  useEffect(() => {
    apiFetch(`${API_BASE}/api/feedback/impacts-en-attente`)
      .then((res) => (res.ok ? res.json() : []))
      .then((data: RepImpact[]) => setQueue(data))
      .catch(() => {});
  }, [apiFetch]);

  useEffect(() => {
    if (!current && queue.length > 0) {
      setCurrent(queue[0]);
      setQueue((q) => q.slice(1));
    }
  }, [current, queue]);

  useEffect(() => {
    if (!current) return;
    const timer = setTimeout(() => dismiss(current.feedbackId), 6000);
    return () => clearTimeout(timer);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [current]);

  function dismiss(feedbackId: number) {
    apiFetch(`${API_BASE}/api/feedback/${feedbackId}/impact-vu`, { method: "POST" })
      .catch(() => {});
    setCurrent(null);
  }

  if (!mounted || !current) return null;

  return createPortal(
    <div className="fixed bottom-6 right-6 z-[200] max-w-sm animate-fade-in">
      <div className="flex items-start gap-3 rounded-xl border border-success/30 bg-success/10 p-4 shadow-lg">
        <span className="text-xl" aria-hidden="true">✅</span>
        <div className="flex-1">
          <p className="text-sm text-[var(--foreground)] leading-relaxed">
            Votre retour sur <strong>{current.nomProspect}</strong> a été enregistré. Il sera utilisé pour affiner la qualification IA de votre établissement.
          </p>
        </div>
        <button
          onClick={() => dismiss(current.feedbackId)}
          className="shrink-0 text-xs text-success hover:underline"
        >
          OK
        </button>
      </div>
    </div>,
    document.body
  );
}
