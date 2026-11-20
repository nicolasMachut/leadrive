# Leadrive

B2B SaaS that reactivates dormant car-sales leads for dealerships and independent garages, with an AI agent ("Nora") that talks to prospects on WhatsApp and hands qualified conversations back to salespeople.

> Side project co-built in 2026. Published as a portfolio piece: the product is not in production. All personal data in this repository is fictitious.

## What it does
- **Lead import**: CSV/Excel, free-text and email parsing (AI-assisted extraction with validation and deduplication)
- **AI reactivation sequences** on WhatsApp (Twilio), with opt-out handling, business-hours rules and human takeover
- **Real-time speed alerts** for salespeople, daily AI briefings, appointment reminders
- **Kanban pipeline, stats and ROI tracking** per dealership
- **Multi-tenant** with roles (manager / salesperson), team management, PII masking in logs

## Stack
- **Backend**: Java 25, Spring Boot 4, Spring AI with Anthropic Claude, Spring Security (JWT), JPA / PostgreSQL, Flyway, MapStruct
- **Frontend**: Next.js (React, TypeScript), Tailwind
- **Messaging**: Twilio WhatsApp Business, Brevo / SendGrid
- **Ops**: Docker Compose (local), GitHub Actions, Railway

## AI engineering highlights
- Prompts versioned as resources, with a regression test set (`backend/src/main/resources/prompts`)
- Deterministic rules in code (opt-out, scheduling, deduplication, phone normalisation); the model only writes and qualifies messages
- PII masking before logging, per-tenant AI configuration, AI call logging for cost and quality follow-up
- Built with Claude Code: project conventions in `CLAUDE.md`, specialised subagents in `.claude/agents`

## Run locally
```bash
docker compose up -d          # PostgreSQL
cd backend && mvn spring-boot:run
cd frontend && npm install && npm run dev
```
Environment variables (database, Anthropic, Twilio, email provider) are read from the environment; none are committed.

See `ARCHITECTURE.md`, `CONVENTIONS.md` and `DESIGN.md` for details.
