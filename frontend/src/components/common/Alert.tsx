import { ReactNode } from "react";

type Tone = "danger" | "success" | "warning" | "info";

const TONE: Record<Tone, string> = {
  danger: "bg-danger/10 border-danger/30 text-danger",
  success: "bg-success/10 border-success/30 text-success",
  warning: "bg-warning/10 border-warning/30 text-warning",
  info: "bg-accent/10 border-accent/30 text-accent",
};

interface AlertProps {
  tone?: Tone;
  children: ReactNode;
  className?: string;
}

export default function Alert({ tone = "info", children, className = "" }: AlertProps) {
  return (
    <div className={`rounded-lg border px-4 py-3 text-sm ${TONE[tone]} ${className}`}>
      {children}
    </div>
  );
}
