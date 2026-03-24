# Archive Policy

This directory stores historical delivery artifacts that preserve traceability but should not dominate portfolio-facing navigation.

## Structure

- `docs/archive/sprints/`: sprint reports, indexes, and milestone handoff files moved from root
- `docs/archive/notes/`: legacy narrative notes and language-specific summaries superseded by canonical docs

## What belongs here

- Sprint-by-sprint reports and indexes (`SPRINT*.md`, `README_SPRINT*.md`)
- One-off delivery notes and completion summaries
- Legacy handoff documents superseded by canonical docs in `docs/`

## What does not belong here

- Canonical architecture and flow docs (`docs/*.md`)
- ADRs (`docs/decisions/`)
- Operational scripts/config used during runtime validation (`ops/`, `monitoring/`)

## Curation rules

1. Keep root focused on runtime and engineering entry points.
2. Preserve historical docs only when they add decision traceability.
3. If a historical document remains relevant, summarize it in canonical docs and archive the original source.
4. When linking archived artifacts, prefer explicit paths (for example `docs/archive/sprints/SPRINT18_OPERATIONS_RUNBOOK_ENHANCED.md`).


