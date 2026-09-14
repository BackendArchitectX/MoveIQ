# MoveIQ

**Evidence-governed mobility operations intelligence for MoveInSync data.**

MoveIQ is designed around one auditable loop:

```text
official dataset
    ↓
data trust
    ↓
contextual metrics
    ↓
autonomous detection
    ↓
situation
    ↓
evidence-backed reasoning
    ↓
decision
    ↓
human approval
    ↓
revalidation
    ↓
action
    ↓
outcome verification
    ↓
leadership brief
```

## Repository layout

```text
moveiq/
├── backend/                 FastAPI application
├── frontend/                React/Vite UI
├── data/
│   └── moveinsync/          official MoveInSync source-data contract
├── db/                      operational persistence schema
├── docs/                    architecture and demo notes
├── scripts/                 developer utilities
├── .github/workflows/       CI
├── Makefile
├── docker-compose.yml
└── .env.example
```

## Official MoveInSync dataset

The source dataset is intentionally **not committed** because the files are large.

Place the seven supplied files in:

```text
data/moveinsync/raw/
├── emp_Data.csv
├── bill_data.csv
├── Ride_data_trip-may_2026.csv
├── Ride_data_trip-June_2026.csv
├── Ride_data_trip-July_2026.csv
├── trip_feedback.csv
└── alerts_data.csv
```

Alternative filenames documented in `data/moveinsync/manifest.yaml` are also accepted.

## Quick start

```bash
make setup
make data-check
make profile
make ingest
make backend
make frontend
```

Or:

```bash
make demo
```

Backend: `http://localhost:8000`  
Frontend: `http://localhost:5173`  
API docs: `http://localhost:8000/docs`

## Engineering laws

1. **No metric without context.**
2. **No evidence from silently repaired data.**
3. **No AI number without provenance.**
4. **No action without fresh revalidation.**
5. **No claimed impact without observation or an explicit estimate label.**
6. **Replay cannot see the future.**
7. **Reprocessing must be idempotent.**
