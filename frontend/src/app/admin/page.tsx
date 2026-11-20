"use client";

import { useEffect, useState, useCallback } from "react";
import { useApiClient, formatErreur } from "@/lib/auth";
import Alert from "@/components/common/Alert";
import { KpiCard } from "@/components/common/Card";
import { SkeletonGrid } from "@/components/common/Skeleton";
import PageHeader from "@/components/common/PageHeader";
import {
  PieChart,
  Pie,
  Cell,
  ResponsiveContainer,
  Tooltip,
  BarChart,
  Bar,
  XAxis,
  YAxis,
  CartesianGrid,
} from "recharts";

const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080";

interface AdminDashboard {
  concessionsActives: number;
  utilisateursActifs30j: number;
  leadsImportesMois: number;
  conversationsLanceesMois: number;
  messagesEnvoyesMois: number;
  messagesRecusMois: number;
  rdvObtenusDepuis30j: number;
  messagesEnEchecMois: number;
  generationsIaMois: number;
  budgetIaMois: number;
  alertes: string[];
}

interface UsageIa {
  concessionId: number;
  nomConcession: string;
  nbAppels: number;
  coutEstime: number;
  lastAppel: string | null;
}

// No-Blue Rule (DESIGN.md) : palette ember/success/danger pour le donut messages.
const COLORS_PIE = ["#D9603A", "#0E9F6E", "#E5484D"];
const PLACEHOLDER_PIE = [{ name: "Aucune donnée", value: 1 }];

export default function AdminDashboardPage() {
  const apiFetch = useApiClient();
  const [dashboard, setDashboard] = useState<AdminDashboard | null>(null);
  const [usageIa, setUsageIa] = useState<UsageIa[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const fetchAll = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const [dashRes, usageRes] = await Promise.all([
        apiFetch(`${API_BASE}/api/admin/dashboard`),
        apiFetch(`${API_BASE}/api/admin/monitoring/usage-ia?jours=30`),
      ]);
      if (!dashRes.ok) throw new Error(`Erreur dashboard ${dashRes.status}`);
      if (!usageRes.ok) throw new Error(`Erreur usage IA ${usageRes.status}`);
      setDashboard(await dashRes.json());
      setUsageIa(await usageRes.json());
    } catch (err) {
      setError(formatErreur(err));
    } finally {
      setLoading(false);
    }
  }, [apiFetch]);

  useEffect(() => { fetchAll(); }, [fetchAll]);

  const pieData = dashboard ? [
    { name: "Envoyés", value: dashboard.messagesEnvoyesMois },
    { name: "Reçus", value: dashboard.messagesRecusMois },
    { name: "En echec", value: dashboard.messagesEnEchecMois },
  ].filter((d) => d.value > 0) : [];

  const totalMessages = pieData.reduce((s, d) => s + d.value, 0);
  const hasPieData = totalMessages > 0;

  const barData = usageIa
    .sort((a, b) => b.coutEstime - a.coutEstime)
    .slice(0, 10)
    .map((u) => ({ nom: u.nomConcession, appels: u.nbAppels, cout: Number(u.coutEstime) }));

  return (
    <div className="p-8 animate-fade-in">
      <PageHeader eyebrow="Back-office Leadrive" title="Vue d'ensemble" />

      {error && <Alert tone="danger" className="mb-6">{error}</Alert>}

      {loading && <SkeletonGrid count={4} />}

      {!loading && dashboard && (
        <>
          {dashboard.alertes && dashboard.alertes.length > 0 && (
            <div className="mb-6 space-y-2">
              {dashboard.alertes.map((alerte, i) => (
                <Alert key={i} tone="warning">{alerte}</Alert>
              ))}
            </div>
          )}

          {/* Plateforme */}
          <h2 className="eyebrow-muted mb-3">Plateforme</h2>
          <div className="grid grid-cols-1 gap-4 md:grid-cols-2 lg:grid-cols-4 mb-6">
            <KpiCard label="Établissements actifs" value={String(dashboard.concessionsActives ?? 0)} tooltip="Nombre total d'organisations clientes actuellement enregistrées sur la plateforme." />
            <KpiCard label="Utilisateurs actifs (30j)" value={String(dashboard.utilisateursActifs30j ?? 0)} tooltip="Nombre d'utilisateurs (managers + vendeurs) qui se sont connectés au moins une fois ces 30 derniers jours." />
            <KpiCard label="Leads importés ce mois" value={String(dashboard.leadsImportesMois ?? 0)} tooltip="Total des leads créés ce mois-ci, tous établissements et toutes sources confondus (import automatique par email, fichier CSV/Excel, copier-coller texte)." />
            <KpiCard label="RDV obtenus (30j)" value={String(dashboard.rdvObtenusDepuis30j ?? 0)} tooltip="Nombre de RDV pris automatiquement par l'IA via WhatsApp ces 30 derniers jours, tous établissements confondus." />
          </div>

          {/* Activité WhatsApp, KPIs + donut cote a cote */}
          <h2 className="eyebrow-muted mb-3">Activité WhatsApp</h2>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-6">
            {/* KPI cards (2 colonnes sur 3) */}
            <div className="lg:col-span-2 grid grid-cols-2 gap-4">
              <KpiCard label="Conversations lancées" value={String(dashboard.conversationsLanceesMois ?? 0)} hint="Ce mois" tooltip="Nombre de conversations WhatsApp ouvertes ce mois (1 conversation par lead contacté)." />
              <KpiCard label="Messages envoyés" value={String(dashboard.messagesEnvoyesMois ?? 0)} hint="Ce mois" tooltip="Total des messages WhatsApp envoyés par l'IA aux prospects." />
              <KpiCard label="Messages reçus" value={String(dashboard.messagesRecusMois ?? 0)} hint="Ce mois" tooltip="Total des messages WhatsApp envoyés par les prospects en réponse." />
              <KpiCard
                label="Messages en echec"
                value={String(dashboard.messagesEnEchecMois ?? 0)}
                hint="Ce mois"
                iconBg={(dashboard.messagesEnEchecMois ?? 0) > 0 ? "bg-danger/10 text-danger" : undefined}
                tooltip="Messages que Twilio n'a pas pu délivrer (numéro invalide, fenêtre 24h dépassée, opt-out, etc.)."
              />
            </div>

            {/* Donut (1 colonne sur 3) */}
            <div className="card-leadrive p-5 flex flex-col items-center justify-center">
              <h3 className="text-sm font-medium text-[var(--color-lr-text-secondary)] mb-3 self-start">Répartition messages</h3>
              <div className="w-44 h-44 relative">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <Pie
                      data={hasPieData ? pieData : PLACEHOLDER_PIE}
                      cx="50%"
                      cy="50%"
                      innerRadius={45}
                      outerRadius={70}
                      paddingAngle={hasPieData ? 3 : 0}
                      dataKey="value"
                      strokeWidth={0}
                    >
                      {hasPieData ? (
                        pieData.map((_, i) => (
                          <Cell key={i} fill={COLORS_PIE[i % COLORS_PIE.length]} />
                        ))
                      ) : (
                        <Cell fill="var(--border)" />
                      )}
                    </Pie>
                    {hasPieData && (
                      <Tooltip
                        contentStyle={{
                          backgroundColor: "var(--card)",
                          border: "1px solid var(--border)",
                          borderRadius: "8px",
                          fontSize: "13px",
                        }}
                        formatter={(value) => [String(value), ""]}
                      />
                    )}
                  </PieChart>
                </ResponsiveContainer>
                {/* Centre du donut */}
                <div className="absolute inset-0 flex items-center justify-center pointer-events-none">
                  <span className="text-lg font-bold text-[var(--foreground)]">{totalMessages}</span>
                </div>
              </div>
              <div className="flex flex-wrap justify-center gap-x-4 gap-y-1 mt-3">
                {(hasPieData ? pieData : [{ name: "Envoyés", value: 0 }, { name: "Reçus", value: 0 }, { name: "En echec", value: 0 }]).map((d, i) => (
                  <div key={d.name} className="flex items-center gap-1.5">
                    <div className="w-2.5 h-2.5 rounded-full" style={{ backgroundColor: hasPieData ? COLORS_PIE[i % COLORS_PIE.length] : "var(--border)" }} />
                    <span className="text-xs text-[var(--color-lr-text-secondary)]">{d.name}</span>
                    {hasPieData && (
                      <span className="text-xs font-semibold text-[var(--foreground)]">{d.value}</span>
                    )}
                  </div>
                ))}
              </div>
            </div>
          </div>

          {/* Intelligence artificielle, KPIs + bar chart cote a cote */}
          <h2 className="eyebrow-muted mb-3">Intelligence artificielle</h2>
          <div className="grid grid-cols-1 lg:grid-cols-3 gap-4 mb-8">
            {/* KPI cards */}
            <div className="flex flex-col gap-4">
              <KpiCard label="Générations IA" value={String(dashboard.generationsIaMois ?? 0)} hint="Ce mois" tooltip="Total des appels au modèle Claude (réponses WhatsApp, fiches préparation, briefings, analyses) tous établissements confondus." />
              <KpiCard label="Budget IA" value={`${Number(dashboard.budgetIaMois ?? 0).toFixed(2)} EUR`} mono hint="Ce mois" tooltip="Coût total Anthropic Claude pour ce mois, calculé à partir des tokens utilisés (input + output)." />
            </div>

            {/* Bar chart cout par concession */}
            <div className="lg:col-span-2 card-leadrive p-5">
              <h3 className="text-sm font-medium text-[var(--color-lr-text-secondary)] mb-4">Coût IA par établissement (30j)</h3>
              {barData.length > 0 ? (
                <ResponsiveContainer width="100%" height={Math.max(barData.length * 45, 150)}>
                  <BarChart data={barData} layout="vertical" margin={{ top: 0, right: 20, left: 0, bottom: 0 }}>
                    <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" horizontal={false} />
                    <XAxis
                      type="number"
                      tick={{ fontSize: 12, fill: "var(--color-lr-text-secondary)" }}
                      axisLine={{ stroke: "var(--border)" }}
                      tickLine={false}
                      tickFormatter={(v) => `${Number(v).toFixed(1)}€`}
                    />
                    <YAxis
                      dataKey="nom"
                      type="category"
                      width={140}
                      tick={{ fontSize: 12, fill: "var(--color-lr-text-secondary)" }}
                      axisLine={false}
                      tickLine={false}
                    />
                    <Tooltip
                      contentStyle={{
                        backgroundColor: "var(--card)",
                        border: "1px solid var(--border)",
                        borderRadius: "8px",
                        fontSize: "13px",
                      }}
                      formatter={(value, name) => [
                        name === "cout" ? `${Number(value).toFixed(2)} EUR` : String(value),
                        name === "cout" ? "Coût" : "Appels",
                      ]}
                    />
                    <Bar dataKey="cout" fill="#D9603A" radius={[0, 4, 4, 0]} barSize={20} />
                  </BarChart>
                </ResponsiveContainer>
              ) : (
                <div className="flex items-center justify-center h-36 text-sm text-[var(--color-lr-text-tertiary)]">
                  Aucune donnée d&apos;usage IA pour le moment
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  );
}
